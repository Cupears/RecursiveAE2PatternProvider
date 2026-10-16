package com.lumengrid.recursiveae2patternprovider;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * A serializable snapshot of a recursive pattern's crafting tree, shared between the
 * server builder ({@link CraftingTreeInspector}) and the client overlay.
 *
 * <p>The structure is a flat, index-based DAG so that an item reachable through several
 * routes appears once: {@link ItemNode}s point at the {@link RecipeNode}s that can make
 * them, and each recipe points back at the item nodes for its ingredients. Item 0 is the
 * pattern's own output (the tree root).
 */
public record CraftingTreeData(List<ItemNode> items, List<RecipeNode> recipes) {

    public static final CraftingTreeData EMPTY = new CraftingTreeData(List.of(), List.of());

    /** An item in the tree and the recipe nodes that can produce it. */
    public record ItemNode(ItemStack icon, List<Integer> recipeIndices) {}

    /**
     * One way to craft an item.
     *
     * @param recipeId          recipe resource id, or empty for the non-bannable root pattern
     * @param outputItemId      item id this recipe produces (the ban key's item part)
     * @param grid              nine stacks describing the crafting grid (empties preserved)
     * @param output            the produced stack, including count
     * @param banned            this exact {@code (item, recipe)} edge is banned
     * @param bannedEverywhere  this recipe is banned for every item
     * @param active            the generator's current first-choice recipe for this item
     * @param root              the pattern's own recipe (shown but not bannable)
     * @param ingredientItemIndices item-node indices for this recipe's ingredients
     */
    public record RecipeNode(String recipeId, String outputItemId, List<ItemStack> grid, ItemStack output,
                             boolean banned, boolean bannedEverywhere, boolean active, boolean root,
                             List<Integer> ingredientItemIndices) {}

    public boolean isEmpty() {
        return items.isEmpty();
    }
}
