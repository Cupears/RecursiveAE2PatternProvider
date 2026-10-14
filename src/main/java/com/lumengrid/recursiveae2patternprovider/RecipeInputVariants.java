package com.lumengrid.recursiveae2patternprovider;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

import java.util.*;

/** Primary ingredient variants let AE2 craft different accepted ingredient outputs. */
final class RecipeInputVariants {
    private record Group(List<Item> options, List<Integer> slots) {}

    static List<ItemStack[]> generate(CraftingRecipe recipe, ItemStack[] original, ItemStack expected,
                                     Level level, int limit) {
        List<ItemStack[]> result = new ArrayList<>();
        if (limit <= 0) return result;
        addIfValid(recipe, original, expected, level, result);
        var placement = recipe.placementInfo();
        Map<List<Item>, List<Integer>> groups = new LinkedHashMap<>();
        var ingredients = placement.ingredients();
        for (int slot = 0; slot < 9; slot++) {
            int ingredientIndex;
            if (recipe instanceof ShapedRecipe shaped) {
                int x = slot % 3;
                int y = slot / 3;
                if (x >= shaped.getWidth() || y >= shaped.getHeight()) continue;
                ingredientIndex = placement.slotsToIngredientIndex().getInt(y * shaped.getWidth() + x);
            } else {
                ingredientIndex = slot;
            }
            if (ingredientIndex < 0 || ingredientIndex >= ingredients.size()) continue;
            var options = ingredients.get(ingredientIndex).items().map(Holder::value).distinct()
                    .sorted(Comparator.comparingInt((Item item) -> BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("minecraft") ? 0 : 1)
                            .thenComparing(item -> BuiltInRegistries.ITEM.getKey(item).toString())).toList();
            if (options.size() > 1) groups.computeIfAbsent(options, k -> new ArrayList<>()).add(slot);
        }
        var choices = groups.entrySet().stream().map(e -> new Group(e.getKey(), e.getValue())).toList();
        enumerate(recipe, original.clone(), expected, level, choices, 0, limit, new int[]{512}, result);
        return result;
    }

    private static void enumerate(CraftingRecipe recipe, ItemStack[] inputs, ItemStack expected, Level level,
                                  List<Group> groups, int index, int limit, int[] attempts, List<ItemStack[]> result) {
        if (result.size() >= limit || attempts[0] <= 0) return;
        if (index == groups.size()) {
            attempts[0]--;
            addIfValid(recipe, inputs, expected, level, result);
            return;
        }
        var group = groups.get(index);
        for (var item : group.options()) {
            for (int slot : group.slots()) inputs[slot] = new ItemStack(item);
            enumerate(recipe, inputs, expected, level, groups, index + 1, limit, attempts, result);
            if (result.size() >= limit || attempts[0] <= 0) break;
        }
    }

    private static void addIfValid(CraftingRecipe recipe, ItemStack[] inputs, ItemStack expected, Level level,
                                   List<ItemStack[]> result) {
        var input = CraftingInput.of(3, 3, Arrays.asList(inputs));
        try {
            if (!recipe.matches(input, level) || !ItemStack.matches(expected, recipe.assemble(input))) return;
            for (var existing : result) {
                boolean same = true;
                for (int slot = 0; slot < 9; slot++) {
                    if (!ItemStack.matches(existing[slot], inputs[slot])) { same = false; break; }
                }
                if (same) return;
            }
            result.add(Arrays.stream(inputs).map(ItemStack::copy).toArray(ItemStack[]::new));
        } catch (RuntimeException ignored) {
            // Custom recipes may require a particular combination or context.
        }
    }
}
