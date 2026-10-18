package com.lumengrid.recursiveae2patternprovider;

import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import java.util.*;

final class RecipeIngredientGroups {
    record Group(List<Item> items, List<Integer> slots) {}
    static List<Group> of(CraftingRecipe recipe, ItemStack[] grid) {
        var placement = recipe.placementInfo(); var ingredients = placement.ingredients();
        Map<List<Item>, List<Integer>> groups = new LinkedHashMap<>();
        for (int slot = 0; slot < 9; slot++) {
            if (grid[slot].isEmpty()) continue;
            int index = slot;
            if (recipe instanceof ShapedRecipe shaped) {
                int x = slot % 3, y = slot / 3;
                if (x >= shaped.getWidth() || y >= shaped.getHeight()) continue;
                index = placement.slotsToIngredientIndex().getInt(y * shaped.getWidth() + x);
            }
            var options = index >= 0 && index < ingredients.size() ? IngredientChoices.items(ingredients.get(index)) : List.of(grid[slot].getItem());
            if (!options.isEmpty()) groups.computeIfAbsent(options, ignored -> new ArrayList<>()).add(slot);
        }
        return groups.entrySet().stream().map(e -> new Group(e.getKey(), List.copyOf(e.getValue()))).toList();
    }
}
