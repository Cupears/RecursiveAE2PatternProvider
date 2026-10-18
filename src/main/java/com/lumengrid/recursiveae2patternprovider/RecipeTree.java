package com.lumengrid.recursiveae2patternprovider;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import java.util.*;

/** Branches load on demand; packet size is bounded, the browsable tree is not. */
public record RecipeTree(List<Node> nodes, boolean truncated) {
    public static final int MAX_NODES = 320; // One wire message, never the whole tree.
    public record Node(int parent, boolean recipe, ItemStack item, String recipeId, List<ItemStack> grid, int note, int choice) {
        public Node { grid = List.copyOf(grid); if (choice < -1) throw new IllegalArgumentException("Invalid ingredient choice"); }
        public Node(int parent, boolean recipe, ItemStack item, String recipeId, List<ItemStack> grid, int note) {
            this(parent, recipe, item, recipeId, grid, note, -1);
        }
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
        int choice = 0;
        for (var input : pattern.getInputs()) {
            Set<appeng.api.stacks.GenericStack> unique = new HashSet<>();
            var candidates = input.getPossibleInputs();
            var preferred = IngredientChoices.prefer(Arrays.stream(candidates)
                    .filter(c -> c.what() instanceof AEItemKey).map(c -> ((AEItemKey)c.what()).getItem()).toList());
            var ordered = Arrays.stream(candidates).sorted(Comparator.comparingInt(c ->
                    c.what() instanceof AEItemKey key && key.getItem() == output.getItem() ? 1 : 0)).toList();
            for (var candidate : ordered) {
                if (!(candidate.what() instanceof AEItemKey key) || !unique.add(candidate)) continue;
                if (!preferred.contains(key.getItem())) continue;
                if (!input.isValid(key, level)) continue;
                var stack = key.toStack(displayAmount(candidate.amount(), input.getMultiplier()));
                nodes.add(new Node(0, false, stack, "", List.of(), stack.is(output.getItem()) ? 2 : 0, choice));
            }
            choice++;
        }
        return new RecipeTree(List.copyOf(nodes), false);
    }
    /** Relative node indices allow a recipe's large tag choices to cross packet boundaries. */
    public static final class Branch {
        private final List<Node> source;
        private final List<Integer> emitted = new ArrayList<>();
        private final int note;
        private Branch(List<Node> source, int note) { this.source = List.copyOf(source); this.note = note; }
        public Page page(int parent, int start, int offset) {
            if (offset != emitted.size() || offset > source.size()) throw new IllegalArgumentException("Invalid branch offset");
            int end = offset + Math.min(MAX_NODES, source.size() - offset);
            var result = new ArrayList<Node>();
            for (int i = offset; i < end; i++) {
                var node = source.get(i);
                int globalParent = node.parent() == -1 ? parent : emitted.get(node.parent());
                emitted.add(start + result.size());
                result.add(new Node(globalParent, node.recipe(), node.item(), node.recipeId(), node.grid(), node.note(), node.choice()));
            }
            return new Page(List.copyOf(result), end, end == source.size(), note);
        }
    }
    public static Branch branch(List<Node> nodes, int parent, Level level) {
        var task = nodes.get(parent);
        if (task.recipe() || task.note() == 2 || task.item().isEmpty()) return new Branch(List.of(), task.note());
        var added = new ArrayList<Node>();
        for (var recipe : RecursivePatternGenerator.editorRecipes(level, task.item().getItem())) {
            try {
                var result = recipe.value().assemble(net.minecraft.world.item.crafting.CraftingInput.EMPTY);
                if (!ItemStack.isSameItemSameComponents(task.item(), result)) continue;
                var grid = RecursivePatternGenerator.getRecipeInputsAs3x3Grid(recipe.value());
                var requirements = ingredientChoices(recipe.value(), grid);
                int recipeNode = added.size();
                added.add(new Node(-1, true, result, recipe.id().identifier().toString(), Arrays.asList(grid), 0));
                int choice = 0;
                for (var requirement : requirements.entrySet()) {
                    for (Item item : requirement.getKey()) {
                        boolean cycle = false;
                        for (int ancestor = parent; ancestor >= 0; ancestor = nodes.get(ancestor).parent()) {
                            var earlier = nodes.get(ancestor);
                            if (!earlier.recipe() && item == earlier.item().getItem()) { cycle = true; break; }
                        }
                        added.add(new Node(recipeNode, false, new ItemStack(item, requirement.getValue()), "", List.of(), cycle ? 2 : 0, choice));
                    }
                    choice++;
                }
            } catch (RuntimeException ignored) { /* Dynamic recipes have no static example to display. */ }
        }
        return new Branch(added, added.isEmpty() ? (IngredientChoices.replaced(task.item().getItem()) ? 3 : 1) : 0);
    }
    private static Map<List<Item>, Integer> ingredientChoices(CraftingRecipe recipe, ItemStack[] grid) {
        Map<List<Item>, Integer> requirements = new LinkedHashMap<>();
        for (var group : RecipeIngredientGroups.of(recipe, grid)) requirements.put(group.items(), group.slots().size());
        return requirements;
    }
    public static Page expand(List<Node> nodes, int parent, int offset, Level level) {
        var branch = branch(nodes, parent, level);
        // Convenience for standalone traversal; live sessions retain their Branch across pages.
        for (int i = 0; i < offset; i++) branch.emitted.add(nodes.size() - offset + i);
        return branch.page(parent, nodes.size(), offset);
    }
}
