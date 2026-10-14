package com.lumengrid.recursiveae2patternprovider;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/** Bounded look-ahead for static crafting dependencies; never assumes machine execution. */
final class RecipeSelection {
    private static final int MAX_DEPTH = 24;
    private static final int MAX_VISITS = 2048;
    private final Map<Item, List<RecipeHolder<CraftingRecipe>>> index;
    private final Set<Item> furnaceOutputs;
    private final Predicate<RecipeHolder<CraftingRecipe>> allowed;
    private final Function<CraftingRecipe, ItemStack[]> grid;

    RecipeSelection(Map<Item, List<RecipeHolder<CraftingRecipe>>> index, Set<Item> furnaceOutputs,
                    Predicate<RecipeHolder<CraftingRecipe>> allowed,
                    Function<CraftingRecipe, ItemStack[]> grid) {
        this.index = index;
        this.furnaceOutputs = furnaceOutputs;
        this.allowed = allowed;
        Map<CraftingRecipe, ItemStack[]> grids = new IdentityHashMap<>();
        this.grid = recipe -> grids.computeIfAbsent(recipe, grid);
    }

    record Choice(RecipeHolder<CraftingRecipe> recipe, double cost) {}

    List<Choice> alternatives(Item target, Set<Item> ancestors) {
        Map<RecipeHolder<CraftingRecipe>, Choice> choices = new LinkedHashMap<>();
        // Registration is global, not specific to the first path that reaches
        // this item. AE2 rejects recursive branches during each calculation.
        for (var choice : rank(target, Set.of())) choices.put(choice.recipe(), choice);
        for (var recipe : index.getOrDefault(target, List.of())) {
            try {
                if (!allowed.test(recipe) || !isConversion(recipe.value(), target)) continue;
                var output = recipe.value().assemble(net.minecraft.world.item.crafting.CraftingInput.EMPTY);
                if (output.isEmpty() || !output.is(target)) continue;
                double inputs = 1;
                boolean cyclic = false;
                for (var input : grid.apply(recipe.value())) {
                    if (input.isEmpty()) continue;
                    if (input.is(target)) { cyclic = true; break; }
                    inputs += input.getCount();
                }
                if (!cyclic) choices.putIfAbsent(recipe, new Choice(recipe, inputs / output.getCount()));
            } catch (RuntimeException ignored) {
                // An unreadable conversion is not a usable alternative.
            }
        }
        var ordered = new ArrayList<>(choices.values());
        // Common recipes first, but keep alternatives for AE2's stock-aware search.
        ordered.sort(Comparator.comparingInt((Choice c) -> c.recipe().id().identifier().getNamespace()
                        .equals(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(target).getNamespace()) ? 0 : 1)
                .thenComparingDouble(Choice::cost)
                .thenComparing(c -> c.recipe().id().identifier().toString()));
        return ordered;
    }

    boolean isConversion(CraftingRecipe recipe, Item target) {
        return isConversion(grid.apply(recipe), target);
    }

    boolean isConversion(ItemStack[] inputs, Item target) {
        Item source = null;
        for (var input : inputs) {
            if (input.isEmpty()) continue;
            if (source != null && source != input.getItem()) return false;
            source = input.getItem();
        }
        return source != null && source != target && reverses(source, target);
    }

    List<Choice> rank(Item target, Set<Item> ancestors) {
        List<Choice> choices = new ArrayList<>();
        // A fresh budget per candidate makes results independent of recipe enumeration order.
        for (var candidate : index.getOrDefault(target, List.of())) {
            var path = new HashSet<>(ancestors);
            path.add(target);
            double cost = cost(candidate, target, path, 0, new int[]{MAX_VISITS});
            if (Double.isFinite(cost)) choices.add(new Choice(candidate, cost));
        }
        choices.sort(Comparator.comparingDouble(Choice::cost)
                .thenComparing(c -> c.recipe().id().identifier().toString()));
        return choices;
    }

    private double cost(RecipeHolder<CraftingRecipe> holder, Item target,
                        Set<Item> path, int depth, int[] budget) {
        if (depth >= MAX_DEPTH || --budget[0] < 0) return Double.POSITIVE_INFINITY;
        try {
            if (!allowed.test(holder)) return Double.POSITIVE_INFINITY;
            ItemStack output = holder.value().assemble(net.minecraft.world.item.crafting.CraftingInput.EMPTY);
            if (output.isEmpty() || !output.is(target)) return Double.POSITIVE_INFINITY;
            ItemStack[] inputs = grid.apply(holder.value());
            double total = 1;
            Map<Item, Double> inputCosts = new HashMap<>();
            for (ItemStack input : inputs) {
                if (input.isEmpty()) continue;
                Item item = input.getItem();
                if (path.contains(item)) return Double.POSITIVE_INFINITY;
                if (inputCosts.containsKey(item)) {
                    total += inputCosts.get(item) * input.getCount();
                    continue;
                }
                boolean reversible = reverses(item, target);
                // An ingot's block/nugget conversions aren't a source of new metal.
                if (furnaceOutputs.contains(target) && reversible) return Double.POSITIVE_INFINITY;
                double child = 1;
                if (!furnaceOutputs.contains(item) && index.containsKey(item)) {
                    path.add(item);
                    child = Double.POSITIVE_INFINITY;
                    try {
                        for (var dependency : index.get(item)) {
                            child = Math.min(child, cost(dependency, item, path, depth + 1, budget));
                        }
                    } finally {
                        path.remove(item);
                    }
                    // Conversion-only resources may be supplied from storage. Do not
                    // use that fallback to justify the reverse half of a conversion.
                    if (!Double.isFinite(child) && budget[0] >= 0 && depth + 1 < MAX_DEPTH
                            && !reachesPath(item, path, new HashSet<>(), new int[]{MAX_VISITS})) child = 1;
                }
                total += child * input.getCount();
                inputCosts.put(item, child);
            }
            return total / output.getCount();
        } catch (RuntimeException e) {
            return Double.POSITIVE_INFINITY;
        }
    }

    private boolean reachesPath(Item item, Set<Item> path, Set<Item> visited, int[] budget) {
        if (path.contains(item)) return true;
        if (!visited.add(item)) return false;
        if (--budget[0] < 0 || visited.size() >= MAX_DEPTH) return true; // Fail closed for large graphs.
        for (var recipe : index.getOrDefault(item, List.of())) {
            try {
                if (!allowed.test(recipe)) continue;
                for (var input : grid.apply(recipe.value())) {
                    if (!input.isEmpty() && reachesPath(input.getItem(), path, visited, budget)) return true;
                }
            } catch (RuntimeException ignored) {
                // Unreadable routes cannot establish a usable dependency.
            }
        }
        return false;
    }

    private boolean reverses(Item input, Item output) {
        for (var recipe : index.getOrDefault(input, List.of())) {
            try {
                if (!allowed.test(recipe)) continue;
                boolean found = false;
                for (var stack : grid.apply(recipe.value())) {
                    if (stack.isEmpty()) continue;
                    if (!stack.is(output)) { found = false; break; }
                    found = true;
                }
                if (found) return true;
            } catch (RuntimeException ignored) {
                // Unreadable recipes are not evidence of a reversible conversion.
            }
        }
        return false;
    }
}
