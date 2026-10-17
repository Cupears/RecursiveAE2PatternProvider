package com.lumengrid.recursiveae2patternprovider;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.fml.ModList;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/** Pack-aware examples, restricted to items the ingredient actually accepts. */
final class IngredientChoices {
    private static final TagKey<Item> HIDDEN = TagKey.create(Registries.ITEM,
            Identifier.parse("c:hidden_from_recipe_viewers"));

    static List<Item> items(Ingredient ingredient) {
        return prefer(ingredient.items().map(net.minecraft.core.Holder::value).toList());
    }

    static List<Item> prefer(List<Item> accepted) {
        return prefer(accepted, item -> new ItemStack(item).is(HIDDEN), Unifier.LOOKUP);
    }

    static boolean replaced(Item item) {
        Item replacement = Unifier.LOOKUP.apply(item);
        return replacement != null && replacement != item;
    }

    static List<Item> prefer(List<Item> accepted, Predicate<Item> hidden, Function<Item, Item> replacement) {
        var unique = accepted.stream().distinct().toList();
        var canonical = unique.stream().filter(item -> {
            Item mapped = replacement.apply(item);
            return mapped == null || mapped == item || !unique.contains(mapped);
        }).toList();
        var visible = canonical.stream().filter(hidden.negate()).toList();
        // Hidden is a presentation hint, not proof that an exact ingredient is unobtainable.
        if (!visible.isEmpty()) return visible;
        return canonical.isEmpty() ? unique : canonical;
    }

    private static final class Unifier {
        private static final Function<Item, Item> LOOKUP = lookup();
        private static Function<Item, Item> lookup() {
            if (!ModList.get().isLoaded("ftbmaterials")) return item -> null;
            try {
                var manager = Class.forName("dev.ftb.mods.ftbmaterials.unification.recipe.UnifierManager");
                var instance = manager.getField("INSTANCE").get(null);
                var database = manager.getMethod("unifierDB");
                var lookup = database.getReturnType().getMethod("lookupItem", Item.class);
                return item -> {
                    try {
                        var result = lookup.invoke(database.invoke(instance), item);
                        return result instanceof Optional<?> optional && optional.orElse(null) instanceof Item mapped ? mapped : null;
                    } catch (ReflectiveOperationException | RuntimeException e) {
                        return null; // Keep accepted items if an optional integration is unavailable.
                    }
                };
            } catch (ReflectiveOperationException | LinkageError e) {
                RecursiveAE2PatternProvider.LOGGER.warn("FTB Materials ingredient preferences unavailable: {}", e.toString());
                return item -> null;
            }
        }
    }
}
