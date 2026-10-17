package com.lumengrid.recursiveae2patternprovider;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import java.util.*;

/** Match ingredient roles to the encoded slots, regardless of JEI's transfer order. */
public final class ShapelessIngredients {
    private ShapelessIngredients() {}
    public static Optional<Ingredient> at(CraftingRecipe recipe, List<GenericStack> slots, int slot) {
        if (slot < 0 || slot >= slots.size() || slots.get(slot) == null) return Optional.empty();
        var ingredients = recipe.placementInfo().ingredients();
        int[] owners = new int[ingredients.size()]; Arrays.fill(owners, -1);
        for (int s = 0; s < slots.size(); s++) {
            if (slots.get(s) != null && !assign(s, slots, ingredients, owners, new boolean[ingredients.size()])) return Optional.empty();
        }
        for (int i = 0; i < owners.length; i++) if (owners[i] == slot) return Optional.of(ingredients.get(i));
        return Optional.empty();
    }
    private static boolean assign(int slot, List<GenericStack> slots, List<Ingredient> ingredients, int[] owners, boolean[] visited) {
        if (!(slots.get(slot).what() instanceof AEItemKey key)) return false;
        for (int i = 0; i < ingredients.size(); i++) {
            if (visited[i] || !ingredients.get(i).test(key.toStack())) continue;
            visited[i] = true;
            if (owners[i] < 0 || assign(owners[i], slots, ingredients, owners, visited)) { owners[i] = slot; return true; }
        }
        return false;
    }
}
