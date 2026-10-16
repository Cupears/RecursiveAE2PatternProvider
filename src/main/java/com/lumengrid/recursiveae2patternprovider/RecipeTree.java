package com.lumengrid.recursiveae2patternprovider;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import java.util.*;

/** Bounded server-side preview. Recipe cards are alternatives, not an AE2 stock calculation. */
public record RecipeTree(List<Node> nodes, boolean truncated) {
    public static final int MAX_NODES = 320;
    public static final int MAX_DEPTH = 6;
    public static final int MAX_RECIPES = 32;
    public record Node(int parent, boolean recipe, ItemStack item, String recipeId, List<ItemStack> grid, int note) {
        public Node { grid = List.copyOf(grid); }
    }
    private record Pending(int node, ItemStack item, int depth, Set<Item> ancestors) {}

    public Set<String> recipeIds() {
        Set<String> ids = new HashSet<>();
        for (var node : nodes) if (node.recipe()) ids.add(node.recipeId());
        return Set.copyOf(ids);
    }

    public static RecipeTree build(IPatternDetails pattern, Level level) {
        var nodes = new ArrayList<Node>();
        var queue = new ArrayDeque<Pending>();
        var primary = pattern.getPrimaryOutput();
        ItemStack output = primary.what() instanceof AEItemKey key ? key.toStack((int)Math.min(64, primary.amount())) : ItemStack.EMPTY;
        nodes.add(new Node(-1, false, output, "", List.of(), 0));
        Set<Item> roots = output.isEmpty() ? Set.of() : Set.of(output.getItem());
        boolean limited = false;
        Set<AEItemKey> inputs = new HashSet<>();
        for (var input : pattern.getInputs()) for (var candidate : input.getPossibleInputs()) {
            if (!(candidate.what() instanceof AEItemKey key) || !inputs.add(key)) continue;
            if (nodes.size() >= MAX_NODES) { limited = true; break; }
            int id = nodes.size();
            var stack = key.toStack((int)Math.min(64, candidate.amount() * input.getMultiplier()));
            nodes.add(new Node(0, false, stack, "", List.of(), 0));
            queue.add(new Pending(id, stack, 0, roots));
        }
        var selection = RecursivePatternGenerator.editorSelection(level);
        Map<Item, List<RecipeSelection.Choice>> choices = new HashMap<>();
        while (!queue.isEmpty()) {
            var task = queue.removeFirst();
            if (task.ancestors().contains(task.item().getItem())) { note(nodes, task.node(), 2); continue; }
            if (task.depth() >= MAX_DEPTH) { note(nodes, task.node(), 3); limited = true; continue; }
            var candidates = choices.computeIfAbsent(task.item().getItem(), item -> selection.alternatives(item, Set.of()));
            int shown = 0;
            for (var choice : candidates) {
                if (shown >= MAX_RECIPES || nodes.size() + 10 > MAX_NODES) { limited = true; note(nodes, task.node(), 4); break; }
                var recipe = choice.recipe();
                try {
                    var result = recipe.value().assemble(net.minecraft.world.item.crafting.CraftingInput.EMPTY);
                    if (!ItemStack.isSameItemSameComponents(task.item(), result)) continue;
                    var grid = RecursivePatternGenerator.getRecipeInputsAs3x3Grid(recipe.value());
                    int recipeNode = nodes.size();
                    nodes.add(new Node(task.node(), true, result, recipe.id().identifier().toString(), Arrays.asList(grid), 0));
                    shown++;
                    Set<Item> ancestors = new HashSet<>(task.ancestors());
                    ancestors.add(task.item().getItem());
                    var unique = new HashSet<Item>();
                    for (var ingredient : grid) {
                        if (ingredient.isEmpty() || !unique.add(ingredient.getItem())) continue;
                        int child = nodes.size();
                        nodes.add(new Node(recipeNode, false, ingredient.copy(), "", List.of(), 0));
                        queue.add(new Pending(child, ingredient.copy(), task.depth() + 1, Set.copyOf(ancestors)));
                    }
                } catch (RuntimeException ignored) { /* Dynamic/unreadable recipes are not editable routes. */ }
            }
            if (shown == 0 && nodes.get(task.node()).note() == 0) note(nodes, task.node(), 1);
        }
        return new RecipeTree(List.copyOf(nodes), limited);
    }

    private static void note(List<Node> nodes, int id, int note) {
        var old = nodes.get(id);
        nodes.set(id, new Node(old.parent(), old.recipe(), old.item(), old.recipeId(), old.grid(), note));
    }
}
