package com.lumengrid.recursiveae2patternprovider;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import java.util.*;

/** Branches load on demand; packet size is bounded, the browsable tree is not. */
public record RecipeTree(List<Node> nodes, boolean truncated) {
    public static final int MAX_NODES = 320; // One wire message, never the whole tree.
    public static final int RECIPES_PER_PAGE = 24;
    public record Node(int parent, boolean recipe, ItemStack item, String recipeId, List<ItemStack> grid, int note) {
        public Node { grid = List.copyOf(grid); }
    }
    public record Page(List<Node> nodes, int nextOffset, boolean done, int note) {}
    public Set<String> recipeIds() {
        Set<String> ids = new HashSet<>();
        for (var node : nodes) if (node.recipe()) ids.add(node.recipeId());
        return Set.copyOf(ids);
    }
    public RecipeTree opening() {
        return new RecipeTree(List.copyOf(nodes.subList(0, Math.min(MAX_NODES, nodes.size()))), nodes.size() > MAX_NODES);
    }
    public Page rootPage(int offset) {
        int remaining = Math.max(0, nodes.size() - MAX_NODES);
        if (offset < 0 || offset > remaining) throw new IllegalArgumentException("Invalid root page offset");
        int end = offset + Math.min(MAX_NODES, remaining - offset);
        return new Page(List.copyOf(nodes.subList(Math.min(MAX_NODES, nodes.size()) + offset,
                Math.min(MAX_NODES, nodes.size()) + end)), end, end == remaining, 0);
    }
    static int displayAmount(long amount, long multiplier) {
        return (int)Math.min(64, Math.clamp(amount, 0, 64) * Math.clamp(multiplier, 0, 64));
    }
    public static RecipeTree build(IPatternDetails pattern, Level level) {
        var nodes = new ArrayList<Node>();
        var primary = pattern.getPrimaryOutput();
        ItemStack output = primary.what() instanceof AEItemKey key ? key.toStack((int)Math.min(64, primary.amount())) : ItemStack.EMPTY;
        nodes.add(new Node(-1, false, output, "", List.of(), 0));
        Set<AEItemKey> inputs = new HashSet<>();
        for (var input : pattern.getInputs()) for (var candidate : input.getPossibleInputs()) {
            if (!(candidate.what() instanceof AEItemKey key) || !inputs.add(key)) continue;
            var stack = key.toStack(displayAmount(candidate.amount(), input.getMultiplier()));
            nodes.add(new Node(0, false, stack, "", List.of(), stack.is(output.getItem()) ? 2 : 0));
        }
        return new RecipeTree(List.copyOf(nodes), false);
    }
    public static Page expand(List<Node> nodes, int parent, int offset, Level level) {
        var task = nodes.get(parent);
        if (task.recipe() || task.note() == 2 || task.item().isEmpty()) return new Page(List.of(), offset, true, task.note());
        var candidates = RecursivePatternGenerator.editorRecipes(level, task.item().getItem());
        var added = new ArrayList<Node>();
        int end = Math.min(candidates.size(), offset + RECIPES_PER_PAGE);
        for (int cursor = offset; cursor < end; cursor++) {
            var recipe = candidates.get(cursor);
            try {
                var result = recipe.value().assemble(net.minecraft.world.item.crafting.CraftingInput.EMPTY);
                if (!ItemStack.isSameItemSameComponents(task.item(), result)) continue;
                var grid = RecursivePatternGenerator.getRecipeInputsAs3x3Grid(recipe.value());
                int recipeNode = nodes.size() + added.size();
                added.add(new Node(parent, true, result, recipe.id().identifier().toString(), Arrays.asList(grid), 0));
                var unique = new HashSet<Item>();
                for (var ingredient : grid) {
                    if (ingredient.isEmpty() || !unique.add(ingredient.getItem())) continue;
                    boolean cycle = false;
                    for (int ancestor = parent; ancestor >= 0; ancestor = nodes.get(ancestor).parent()) {
                        var earlier = nodes.get(ancestor);
                        if (!earlier.recipe() && ingredient.is(earlier.item().getItem())) { cycle = true; break; }
                    }
                    added.add(new Node(recipeNode, false, ingredient.copy(), "", List.of(), cycle ? 2 : 0));
                }
            } catch (RuntimeException ignored) { /* Dynamic recipes have no static example to display. */ }
        }
        return new Page(List.copyOf(added), end, end == candidates.size(), end == candidates.size() && added.isEmpty() && offset == 0 ? 1 : 0);
    }
}
