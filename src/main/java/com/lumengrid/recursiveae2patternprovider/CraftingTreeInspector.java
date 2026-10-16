package com.lumengrid.recursiveae2patternprovider;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds a {@link CraftingTreeData} for a recursive pattern so the player can review and
 * ban individual sub-recipes. The tree mirrors what {@link RecursivePatternGenerator}
 * would explore, but it <em>keeps</em> banned recipes (greyed, still selectable) instead
 * of pruning them.
 *
 * <p>Banned recipes are <strong>not traversed</strong>: their ingredients are not pulled into
 * the tree, and after the build any item only reachable through banned recipes is pruned.
 * That way, disabling a craft hides its children. Reciprocal / compression-loop recipes
 * (e.g. ingot&harr;nugget&harr;block) are suppressed by tracking ancestors, matching the
 * guards already used by {@link RecursivePatternGenerator}.
 */
public final class CraftingTreeInspector {

    private static final int MAX_RECIPE_NODES = 160;
    private static final int MAX_ALTERNATIVES_PER_ITEM = 4;
    private static final int DEFAULT_DEPTH = 16;
    private static final int DEPTH_CAP = 24;

    private CraftingTreeInspector() {}

    private static final class ItemBuilder {
        final ItemStack icon;
        final List<Integer> recipeIndices = new ArrayList<>();
        boolean expanded;

        ItemBuilder(ItemStack icon) {
            this.icon = icon;
        }
    }

    private static final class RecipeBuilder {
        String recipeId;
        String outputItemId;
        int outputItemIndex;
        List<ItemStack> grid;
        ItemStack output;
        boolean banned;
        boolean bannedEverywhere;
        boolean active;
        boolean root;
        final List<Integer> ingredientItemIndices = new ArrayList<>();
    }

    /** One BFS step: an item to expand, with the items on the path from the root to it. */
    private record Work(int itemIndex, Item item, String itemId, int depth, Set<Item> ancestors) {}

    public static CraftingTreeData build(IPatternDetails pattern, Level level, RecipeBans bans) {
        if (pattern == null || level == null || level.isClientSide()) {
            return CraftingTreeData.EMPTY;
        }
        try {
            return buildInternal(pattern, level, bans);
        } catch (Exception e) {
            RecursiveAE2PatternProvider.LOGGER.debug("Failed to build crafting tree: {}", e.getMessage());
            return CraftingTreeData.EMPTY;
        }
    }

    private static CraftingTreeData buildInternal(IPatternDetails pattern, Level level, RecipeBans bans) {
        Map<Item, List<RecipeHolder<CraftingRecipe>>> index = RecursivePatternGenerator.recipeIndex(level);

        int maxDepth = effectiveDepth();

        List<ItemBuilder> items = new ArrayList<>();
        List<RecipeBuilder> recipes = new ArrayList<>();
        Map<String, Integer> itemIndex = new LinkedHashMap<>();

        var primary = pattern.getPrimaryOutput();
        if (!(primary.what() instanceof AEItemKey rootKey)) {
            return CraftingTreeData.EMPTY;
        }
        int rootItemIdx = getOrCreateItem(rootKey.getItem(), rootKey.toStack(1), items, itemIndex);

        // The root recipe is the encoded pattern itself: shown, but not bannable.
        RecipeBuilder rootRecipe = new RecipeBuilder();
        rootRecipe.recipeId = "";
        rootRecipe.outputItemId = idOf(rootKey.getItem());
        rootRecipe.outputItemIndex = rootItemIdx;
        rootRecipe.root = true;
        rootRecipe.output = rootKey.toStack((int) Math.max(1, primary.amount()));
        rootRecipe.grid = rootGrid(pattern);
        int rootRecipeIdx = recipes.size();
        recipes.add(rootRecipe);
        items.get(rootItemIdx).recipeIndices.add(rootRecipeIdx);

        // Items reachable from the root through non-banned recipe edges. Everything else
        // is pruned so that disabling a craft hides its (now orphaned) children.
        Set<Integer> reachableItems = new HashSet<>();
        reachableItems.add(rootItemIdx);
        // Ancestors of each item (path from root to it, excluding the item itself), used to
        // suppress reciprocal / compression-loop recipes.
        Map<Integer, Set<Item>> itemAncestors = new HashMap<>();
        itemAncestors.put(rootItemIdx, Set.of());

        ArrayDeque<Work> queue = new ArrayDeque<>();
        for (var input : pattern.getInputs()) {
            Item ingredient = firstItem(input.getPossibleInputs());
            if (ingredient == null) {
                continue;
            }
            int childIdx = getOrCreateItem(ingredient, new ItemStack(ingredient), items, itemIndex);
            if (!rootRecipe.ingredientItemIndices.contains(childIdx)) {
                rootRecipe.ingredientItemIndices.add(childIdx);
            }
            if (reachableItems.add(childIdx)) {
                Set<Item> childAncestors = new HashSet<>(itemAncestors.get(rootItemIdx));
                childAncestors.add(rootKey.getItem());
                itemAncestors.put(childIdx, childAncestors);
                queue.add(new Work(childIdx, ingredient, idOf(ingredient), 1, childAncestors));
            }
        }

        while (!queue.isEmpty() && recipes.size() < MAX_RECIPE_NODES) {
            Work work = queue.poll();
            ItemBuilder itemNode = items.get(work.itemIndex());
            if (itemNode.expanded || work.depth() > maxDepth) {
                continue;
            }
            itemNode.expanded = true;

            List<RecipeHolder<CraftingRecipe>> candidates = new ArrayList<>();
            for (var holder : index.getOrDefault(work.item(), List.of())) {
                if (RecursivePatternGenerator.isSelectableRecipe(holder, work.item())) {
                    candidates.add(holder);
                }
            }
            if (candidates.isEmpty()) {
                continue;
            }
            // Simpler recipes first (fewer distinct ingredients), then stable by id.
            candidates.sort(Comparator
                    .comparingInt((RecipeHolder<CraftingRecipe> h) -> distinctIngredients(h.value()))
                    .thenComparing(h -> h.id().identifier().toString()));

            boolean activeChosen = false;
            int made = 0;
            for (var holder : candidates) {
                if (made >= MAX_ALTERNATIVES_PER_ITEM || recipes.size() >= MAX_RECIPE_NODES) {
                    break;
                }
                String recipeId = holder.id().identifier().toString();
                RecipeBuilder rb = new RecipeBuilder();
                rb.recipeId = recipeId;
                rb.outputItemId = work.itemId();
                rb.outputItemIndex = work.itemIndex();
                rb.banned = bans.isBanned(work.itemId(), recipeId);
                rb.bannedEverywhere = bans.isRecipeBannedEverywhere(recipeId);
                if (!rb.banned && !activeChosen) {
                    rb.active = true;
                    activeChosen = true;
                }
                try {
                    rb.grid = gridList(RecursivePatternGenerator.recipeGrid(holder.value()));
                    rb.output = holder.value().assemble(CraftingInput.EMPTY).copy();
                } catch (Exception e) {
                    continue;
                }
                int recipeNodeIdx = recipes.size();
                recipes.add(rb);
                itemNode.recipeIndices.add(recipeNodeIdx);
                made++;

                // Only non-banned recipes pull their ingredients into the tree: a disabled
                // craft's children are hidden unless reachable through another producer.
                if (!rb.banned) {
                    for (ItemStack ing : rb.grid) {
                        if (ing.isEmpty()) {
                            continue;
                        }
                        Item ingItem = ing.getItem();
                        int ingIdx = getOrCreateItem(ingItem, new ItemStack(ingItem), items, itemIndex);
                        if (!rb.ingredientItemIndices.contains(ingIdx)) {
                            rb.ingredientItemIndices.add(ingIdx);
                        }
                        // Suppress compression / reciprocal loops: never traverse into an
                        // ingredient that is already an ancestor of this item (or the item
                        // itself, a self-referential recipe).
                        if (work.ancestors().contains(ingItem) || ingItem == work.item()) {
                            continue;
                        }
                        if (reachableItems.add(ingIdx)) {
                            Set<Item> ingAncestors = new HashSet<>(work.ancestors());
                            ingAncestors.add(work.item());
                            itemAncestors.put(ingIdx, ingAncestors);
                            if (!items.get(ingIdx).expanded && work.depth() + 1 <= maxDepth) {
                                queue.add(new Work(ingIdx, ingItem, idOf(ingItem), work.depth() + 1, ingAncestors));
                            }
                        }
                    }
                }
            }
        }

        // Prune orphaned item nodes: keep exactly the items reachable from the root through
        // non-banned recipe edges, and the recipes that produce them (plus the root recipe).
        // Banned recipes for kept items stay greyed so the player can un-ban them.
        List<Integer> keepRecipeIndices = new ArrayList<>();
        keepRecipeIndices.add(rootRecipeIdx);
        for (int i = 0; i < recipes.size(); i++) {
            if (i == rootRecipeIdx) {
                continue;
            }
            if (reachableItems.contains(recipes.get(i).outputItemIndex)) {
                keepRecipeIndices.add(i);
            }
        }

        Map<Integer, Integer> itemRemap = new HashMap<>();
        List<ItemBuilder> keptItems = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            if (reachableItems.contains(i)) {
                itemRemap.put(i, keptItems.size());
                keptItems.add(items.get(i));
            }
        }

        Map<Integer, Integer> recipeRemap = new HashMap<>();
        List<RecipeBuilder> keptRecipes = new ArrayList<>();
        for (int i : keepRecipeIndices) {
            recipeRemap.put(i, keptRecipes.size());
            keptRecipes.add(recipes.get(i));
        }

        for (ItemBuilder ib : keptItems) {
            List<Integer> remapped = new ArrayList<>();
            for (int r : ib.recipeIndices) {
                Integer nr = recipeRemap.get(r);
                if (nr != null) {
                    remapped.add(nr);
                }
            }
            ib.recipeIndices.clear();
            ib.recipeIndices.addAll(remapped);
        }
        for (RecipeBuilder rb : keptRecipes) {
            List<Integer> remapped = new ArrayList<>();
            for (int ing : rb.ingredientItemIndices) {
                Integer ni = itemRemap.get(ing);
                if (ni != null) {
                    remapped.add(ni);
                }
            }
            rb.ingredientItemIndices.clear();
            rb.ingredientItemIndices.addAll(remapped);
        }

        return toData(keptItems, keptRecipes);
    }

    private static int effectiveDepth() {
        int configured = Config.RECURSION_DEPTH.get();
        if (configured <= 0) {
            return DEFAULT_DEPTH;
        }
        return Math.min(configured, DEPTH_CAP);
    }

    private static int getOrCreateItem(Item item, ItemStack icon, List<ItemBuilder> items, Map<String, Integer> itemIndex) {
        String id = idOf(item);
        Integer existing = itemIndex.get(id);
        if (existing != null) {
            return existing;
        }
        int idx = items.size();
        items.add(new ItemBuilder(icon.copy()));
        itemIndex.put(id, idx);
        return idx;
    }

    private static List<ItemStack> rootGrid(IPatternDetails pattern) {
        ItemStack[] grid = new ItemStack[9];
        Arrays.fill(grid, ItemStack.EMPTY);
        int slot = 0;
        for (var input : pattern.getInputs()) {
            if (slot >= 9) {
                break;
            }
            GenericStack[] possible = input.getPossibleInputs();
            if (possible.length == 0 || !(possible[0].what() instanceof AEItemKey key)) {
                continue;
            }
            grid[slot++] = key.toStack((int) Math.max(1, possible[0].amount()));
        }
        return gridList(grid);
    }

    private static List<ItemStack> gridList(ItemStack[] grid) {
        List<ItemStack> list = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            list.add(i < grid.length && grid[i] != null ? grid[i].copy() : ItemStack.EMPTY);
        }
        return list;
    }

    private static Item firstItem(GenericStack[] possible) {
        for (GenericStack stack : possible) {
            if (stack.what() instanceof AEItemKey key) {
                return key.getItem();
            }
        }
        return null;
    }

    private static int distinctIngredients(CraftingRecipe recipe) {
        try {
            ItemStack[] grid = RecursivePatternGenerator.recipeGrid(recipe);
            int count = 0;
            var seen = new HashSet<Item>();
            for (ItemStack stack : grid) {
                if (!stack.isEmpty() && seen.add(stack.getItem())) {
                    count++;
                }
            }
            return count;
        } catch (Exception e) {
            return Integer.MAX_VALUE;
        }
    }

    private static String idOf(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    private static CraftingTreeData toData(List<ItemBuilder> items, List<RecipeBuilder> recipes) {
        List<CraftingTreeData.ItemNode> itemNodes = new ArrayList<>(items.size());
        for (ItemBuilder ib : items) {
            itemNodes.add(new CraftingTreeData.ItemNode(ib.icon, List.copyOf(ib.recipeIndices)));
        }
        List<CraftingTreeData.RecipeNode> recipeNodes = new ArrayList<>(recipes.size());
        for (RecipeBuilder rb : recipes) {
            recipeNodes.add(new CraftingTreeData.RecipeNode(rb.recipeId, rb.outputItemId, List.copyOf(rb.grid),
                    rb.output, rb.banned, rb.bannedEverywhere, rb.active, rb.root,
                    List.copyOf(rb.ingredientItemIndices)));
        }
        return new CraftingTreeData(List.copyOf(itemNodes), List.copyOf(recipeNodes));
    }
}
