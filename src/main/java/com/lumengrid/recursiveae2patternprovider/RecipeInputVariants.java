package com.lumengrid.recursiveae2patternprovider;

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
        return generate(recipe, original, expected, level, limit, Map.of());
    }
    static List<ItemStack[]> generate(CraftingRecipe recipe, ItemStack[] original, ItemStack expected,
                                     Level level, int limit, Map<Integer, String> locks) {
        List<ItemStack[]> result = new ArrayList<>();
        if (limit <= 0) return result;
        var preferred = original.clone();
        var choices = new ArrayList<Group>(); int groupId = 0;
        for (var group : RecipeIngredientGroups.of(recipe, original)) {
            String lock = locks.get(groupId++);
            var options = group.items().stream().filter(item -> lock == null || BuiltInRegistries.ITEM.getKey(item).toString().equals(lock))
                    .sorted(Comparator.comparingInt((Item item) -> BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("minecraft") ? 0 : 1)
                            .thenComparing(item -> BuiltInRegistries.ITEM.getKey(item).toString())).toList();
            if (options.isEmpty()) return result;
            for (int slot : group.slots()) if (!options.contains(preferred[slot].getItem())) preferred[slot] = new ItemStack(options.getFirst());
            if (options.size() > 1) choices.add(new Group(options, group.slots()));
        }
        addIfValid(recipe, preferred, expected, level, result);
        enumerate(recipe, preferred.clone(), expected, level, choices, 0, limit, new int[]{512}, result);
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
