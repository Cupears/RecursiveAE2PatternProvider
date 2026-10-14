package com.lumengrid.recursiveae2patternprovider;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.core.definitions.AEItems;
import appeng.crafting.pattern.AECraftingPattern;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class RecursivePatternGenerator {

    private RecursivePatternGenerator() {}

    private record CachedIndex(WeakReference<Object> manager, int recipeCount,
                               Map<Item, List<RecipeHolder<CraftingRecipe>>> index, Set<Item> furnaceOutputs) {
        boolean matches(Object current, int currentRecipeCount) {
            return manager.get() == current && recipeCount == currentRecipeCount;
        }
    }

    private record Settings(int maxAlternativesPerItem, boolean retainAlternatives, boolean skipSelfReferential,
                            Set<String> blacklistExact, List<String> blacklistPrefixes) {

        static Settings fromConfig() {
            int maxAlternatives = 1;
            boolean retainAlternatives = true;
            boolean skipSelf = true;
            Set<String> exact = new HashSet<>();
            List<String> prefixes = new ArrayList<>();
            try {
                maxAlternatives = Math.max(1, Config.MAX_ALTERNATIVES_PER_ITEM.get());
                retainAlternatives = Config.RETAIN_RECIPE_ALTERNATIVES.get();
                skipSelf = Config.SKIP_SELF_REFERENTIAL_RECIPES.get();
                for (Object entry : Config.RECIPE_BLACKLIST.get()) {
                    if (entry == null) continue;
                    String s = entry.toString().trim();
                    if (s.isEmpty()) continue;
                    if (s.endsWith("*")) {
                        prefixes.add(s.substring(0, s.length() - 1));
                    } else {
                        exact.add(s);
                    }
                }
            } catch (Exception e) {
                RecursiveAE2PatternProvider.LOGGER.warn("Failed to read recipe selection config, using defaults: {}",
                        e.getMessage());
            }
            return new Settings(maxAlternatives, retainAlternatives, skipSelf, exact, prefixes);
        }

        boolean isBlacklisted(Identifier recipeId) {
            String id = recipeId.toString();
            if (blacklistExact.contains(id)) return true;
            for (String prefix : blacklistPrefixes) {
                if (id.startsWith(prefix)) return true;
            }
            return false;
        }
    }

    private static final class Context {
        final Level level;
        final int maxDepth;
        final Map<Item, List<RecipeHolder<CraftingRecipe>>> index;
        final Settings settings;
        final Set<String> processedRecipes = new HashSet<>();
        final Set<AEItemKey> processedItems = new HashSet<>();
        final Set<Item> ancestors = new HashSet<>();
        final List<IPatternDetails> generated = new ArrayList<>();
        final RecipeSelection selection;

        Context(Level level, int maxDepth, Map<Item, List<RecipeHolder<CraftingRecipe>>> index, Settings settings) {
            this.level = level;
            this.maxDepth = maxDepth;
            this.index = index;
            this.settings = settings;
            this.selection = new RecipeSelection(index, getFurnaceOutputs(level.getServer().getRecipeManager()),
                    r -> !settings.isBlacklisted(r.id().identifier())
                            && (!settings.skipSelfReferential() || !usesItem(r.value(),
                            r.value().assemble(CraftingInput.EMPTY).getItem())),
                    RecursivePatternGenerator::getRecipeInputsAs3x3Grid);
        }
    }

    private static volatile CachedIndex cached;

    private static Map<Item, List<RecipeHolder<CraftingRecipe>>> getRecipeIndex(Level level) {
        if (level.getServer() == null) {
            return new HashMap<>();
        }
        RecipeManager manager = level.getServer().getRecipeManager();
        return getRecipeIndex(manager);
    }

    private static Map<Item, List<RecipeHolder<CraftingRecipe>>> getRecipeIndex(RecipeManager manager) {
        int recipeCount = manager.getRecipes().size();
        CachedIndex snapshot = cached;
        if (snapshot != null && snapshot.matches(manager, recipeCount)) {
            return snapshot.index();
        }
        synchronized (RecursivePatternGenerator.class) {
            snapshot = cached;
            if (snapshot != null && snapshot.matches(manager, recipeCount)) {
                return snapshot.index();
            }
            long start = System.nanoTime();
            Map<Item, List<RecipeHolder<CraftingRecipe>>> newIndex = new HashMap<>();
            Set<Item> furnaceOutputs = new HashSet<>();
            for (RecipeHolder<?> holder : manager.getRecipes()) {
                if (holder.value() instanceof AbstractCookingRecipe cooking) {
                    try {
                        var result = cooking.assemble(new SingleRecipeInput(ItemStack.EMPTY));
                        if (!result.isEmpty()) furnaceOutputs.add(result.getItem());
                    } catch (RuntimeException e) {
                        RecursiveAE2PatternProvider.LOGGER.debug("Skipping cooking recipe {}: {}", holder.id(), e.getMessage());
                    }
                }
                if (holder.value() instanceof CraftingRecipe craftingRecipe) {
                    @SuppressWarnings("unchecked")
                    RecipeHolder<CraftingRecipe> recipe = (RecipeHolder<CraftingRecipe>) holder;
                    try {
                        ItemStack output = craftingRecipe.assemble(CraftingInput.EMPTY);
                        if (!output.isEmpty()) {
                            newIndex.computeIfAbsent(output.getItem(), k -> new ArrayList<>()).add(recipe);
                        }
                    } catch (Exception e) {
                        RecursiveAE2PatternProvider.LOGGER.debug("Skipping recipe {} while indexing: {}",
                                recipe.id(), e.getMessage());
                    }
                }
            }
            newIndex.values().forEach(recipes -> recipes.sort(java.util.Comparator.comparing(r -> r.id().identifier().toString())));
            cached = new CachedIndex(new WeakReference<>(manager), recipeCount, newIndex, furnaceOutputs);
            RecursiveAE2PatternProvider.LOGGER.info(
                    "Indexed {} crafting recipes by output item in {} ms",
                    newIndex.values().stream().mapToInt(List::size).sum(),
                    (System.nanoTime() - start) / 1_000_000);
            return newIndex;
        }
    }

    private static Set<Item> getFurnaceOutputs(RecipeManager manager) {
        getRecipeIndex(manager);
        return cached == null ? Set.of() : cached.furnaceOutputs();
    }

    public static List<IPatternDetails> collectRecursivePatterns(Iterable<ItemStack> patternInventory, Level level) {
        List<IPatternDetails> recursivePatterns = new ArrayList<>();
        if (patternInventory == null || level == null) {
            return recursivePatterns;
        }

        for (ItemStack stack : patternInventory) {
            if (PatternUtil.isRecursive(stack)) {
                var details = PatternDetailsHelper.decodePattern(stack, level);
                if (details != null) {
                    recursivePatterns.add(details);
                }
            }
        }
        return recursivePatterns;
    }

    public static List<IPatternDetails> generate(List<IPatternDetails> recursivePatterns,
                                                 Level level, int maxDepth) {
        return generate(recursivePatterns, List.of(), level, maxDepth);
    }

    public static List<IPatternDetails> generate(List<IPatternDetails> recursivePatterns,
                                                 Collection<IPatternDetails> existingPatterns,
                                                 Level level, int maxDepth) {
        if (level == null || level.isClientSide() || recursivePatterns.isEmpty()) {
            return new ArrayList<>();
        }

        Context ctx = new Context(level, maxDepth, getRecipeIndex(level), Settings.fromConfig());

        if (existingPatterns != null) {
            for (IPatternDetails existing : existingPatterns) {
                markOutputsProcessed(existing, ctx);
            }
        }
        for (IPatternDetails recursivePattern : recursivePatterns) {
            markOutputsProcessed(recursivePattern, ctx);
        }

        for (IPatternDetails recursivePattern : recursivePatterns) {
            List<Item> rootOutputs = new ArrayList<>();
            for (var output : recursivePattern.getOutputs()) {
                if (output.what() instanceof AEItemKey itemKey && ctx.ancestors.add(itemKey.getItem())) {
                    rootOutputs.add(itemKey.getItem());
                }
            }
            try {
                generateDependencyPatterns(recursivePattern, 0, ctx);
            } finally {
                rootOutputs.forEach(ctx.ancestors::remove);
            }
        }
        return ctx.generated;
    }

    private static void markOutputsProcessed(IPatternDetails pattern, Context ctx) {
        try {
            for (var output : pattern.getOutputs()) {
                if (output.what() instanceof AEItemKey itemKey) {
                    ctx.processedItems.add(itemKey);
                }
            }
        } catch (Exception e) {
            RecursiveAE2PatternProvider.LOGGER.debug("Failed to read pattern outputs: {}", e.getMessage());
        }
    }

    public static void appendGenerated(List<IPatternDetails> generated,
                                       List<IPatternDetails> patterns,
                                       Collection<? super AEKey> patternInputs) {
        for (var autoPattern : generated) {
            patterns.add(autoPattern);
            if (patternInputs == null) {
                continue;
            }
            for (var patternInput : autoPattern.getInputs()) {
                for (var inputCandidate : patternInput.getPossibleInputs()) {
                    patternInputs.add(inputCandidate.what().dropSecondary());
                }
            }
        }
    }

    private static void generateDependencyPatterns(IPatternDetails pattern, int currentDepth, Context ctx) {
        if (ctx.maxDepth >= 0 && currentDepth >= ctx.maxDepth) {
            return;
        }

        for (var patternInput : pattern.getInputs()) {
            for (var inputCandidate : patternInput.getPossibleInputs()) {
                try {
                    var key = inputCandidate.what();
                    if (!(key instanceof AEItemKey itemKey)) {
                        continue;
                    }

                    if (!ctx.processedItems.add(itemKey)) {
                        continue;
                    }

                    findAndCreatePatternsForItem(itemKey.toStack(), pattern, currentDepth, ctx);

                } catch (Exception e) {
                    RecursiveAE2PatternProvider.LOGGER.debug("Error processing input candidate: {}", e.getMessage());
                }
            }
        }
    }

    private static void findAndCreatePatternsForItem(ItemStack targetItem, IPatternDetails parentPattern,
                                                     int currentDepth, Context ctx) {
        Item targetType = targetItem.getItem();

        List<RecipeHolder<CraftingRecipe>> candidates = ctx.index.get(targetType);
        if (candidates == null || candidates.isEmpty()) {
            return;
        }

        var choices = ctx.settings.retainAlternatives()
                ? ctx.selection.alternatives(targetType, ctx.ancestors)
                : ctx.selection.rank(targetType, ctx.ancestors);
        List<RecipeHolder<CraftingRecipe>> ordered = choices.stream()
                .map(RecipeSelection.Choice::recipe)
                .filter(r -> !ctx.processedRecipes.contains(r.id().toString()))
                .filter(r -> !ctx.settings.isBlacklisted(r.id().identifier()))
                .filter(r -> !ctx.settings.skipSelfReferential() || !usesItem(r.value(), targetType))
                .toList();

        int created = 0;
        for (var recipe : ordered) {
            if (created >= (ctx.settings.retainAlternatives() ? 64 : ctx.settings.maxAlternativesPerItem())) {
                break;
            }

            try {
                ItemStack recipeOutput = recipe.value().assemble(CraftingInput.EMPTY);
                if (recipeOutput.isEmpty() || !recipeOutput.is(targetType)) {
                    continue;
                }
                if (!ItemStack.isSameItemSameComponents(targetItem, recipeOutput)) {
                    continue;
                }

                ItemStack[] original = getRecipeInputsAs3x3Grid(recipe.value());
                int remaining = (ctx.settings.retainAlternatives() ? 64 : ctx.settings.maxAlternativesPerItem()) - created;
                List<ItemStack[]> variants = Config.GENERATED_INGREDIENT_ALTERNATIVES.get()
                        ? RecipeInputVariants.generate(recipe.value(), original, recipeOutput, ctx.level, remaining)
                        : java.util.Collections.singletonList(original);
                for (ItemStack[] inputs : variants) {
                    if (shouldSkipDependency(inputs, targetType, ctx.ancestors, ctx.settings.retainAlternatives())) continue;
                    try {
                        AECraftingPattern aePattern = encodePattern(recipe, inputs, recipeOutput, parentPattern, ctx.level);
                        if (aePattern == null || aePattern.getOutputs() == null || aePattern.getOutputs().isEmpty()) continue;
                        ctx.generated.add(aePattern);
                        PlanExplanation.registerGenerated(aePattern);
                        created++;
                        ctx.processedRecipes.add(recipe.id().toString());

                        if (ctx.settings.retainAlternatives() && ctx.selection.isConversion(inputs, targetType)) continue;
                        boolean added = ctx.ancestors.add(targetType);
                        try {
                            generateDependencyPatterns(aePattern, currentDepth + 1, ctx);
                        } finally {
                            if (added) ctx.ancestors.remove(targetType);
                        }
                    } catch (RuntimeException e) {
                        RecursiveAE2PatternProvider.LOGGER.debug("Failed ingredient variant for {}: {}", recipe.id(), e.getMessage());
                    }
                }

            } catch (Exception e) {
                RecursiveAE2PatternProvider.LOGGER.debug("Failed to create dependency pattern for recipe: {} - {}",
                        recipe.id(), e.getMessage());
            }
        }
    }

    private static AECraftingPattern encodePattern(RecipeHolder<CraftingRecipe> recipe, ItemStack[] inputs,
                                                   ItemStack output, IPatternDetails parentPattern,
                                                   Level level) {
        ItemStack patternStack = encodePatternStack(recipe, inputs, output, parentPattern);
        return new AECraftingPattern(Objects.requireNonNull(AEItemKey.of(patternStack)), level);
    }

    static ItemStack encodePatternStack(RecipeHolder<CraftingRecipe> recipe, ItemStack[] inputs,
                                       ItemStack output, IPatternDetails parentPattern) {
        ItemStack patternStack = AEItems.CRAFTING_PATTERN.stack();

        boolean allowSubstitutes = Config.DEFAULT_ALLOW_SUBSTITUTES.get();
        boolean allowFluidSubstitutes = Config.DEFAULT_ALLOW_FLUID_SUBSTITUTES.get();

        if (parentPattern instanceof AECraftingPattern parentCraftingPattern) {
            allowSubstitutes = parentCraftingPattern.canSubstitute();
            allowFluidSubstitutes = parentCraftingPattern.canSubstituteFluids();
        }

        // Each validated primary variant is a separate planning choice. Keep
        // execution bound to that choice: a runtime substitute can consume
        // stock reserved for another dependency and deadlock an otherwise valid plan.
        if (Config.GENERATED_INGREDIENT_ALTERNATIVES.get()) allowSubstitutes = false;

        AECraftingPattern.encode(patternStack, recipe, inputs, output, allowSubstitutes, allowFluidSubstitutes);
        return patternStack;
    }

    static boolean shouldSkipDependency(ItemStack[] inputs, Item target, Set<Item> ancestors, boolean retainAlternatives) {
        return containsItem(inputs, target) || (!retainAlternatives && containsAny(inputs, ancestors));
    }

    private static boolean usesItem(CraftingRecipe recipe, Item item) {
        try {
            for (Ingredient ingredient : recipe.placementInfo().ingredients()) {
                if (ingredient.items().anyMatch(holder -> holder.value() == item)) {
                    return true;
                }
            }
        } catch (Exception e) {
            return true;
        }
        return false;
    }

    private static boolean containsAny(ItemStack[] grid, Set<Item> items) {
        if (items.isEmpty()) return false;
        for (ItemStack stack : grid) {
            if (!stack.isEmpty() && items.contains(stack.getItem())) return true;
        }
        return false;
    }

    private static boolean containsItem(ItemStack[] grid, Item item) {
        for (ItemStack stack : grid) {
            if (!stack.isEmpty() && stack.is(item)) return true;
        }
        return false;
    }

    private static ItemStack[] getRecipeInputsAs3x3Grid(CraftingRecipe recipe) {
        ItemStack[] inputs = new ItemStack[9];
        Arrays.fill(inputs, ItemStack.EMPTY);

        var ingredients = recipe.placementInfo().ingredients();

        if (recipe instanceof ShapedRecipe shapedRecipe) {
            int width = shapedRecipe.getWidth();
            int height = shapedRecipe.getHeight();
            var slotsToIngredientIndex = recipe.placementInfo().slotsToIngredientIndex();

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    // Placement ingredients omit empty slots; map each recipe slot
                    // back to its ingredient to preserve holes in shaped recipes.
                    int ingredientIndex = slotsToIngredientIndex.getInt(y * width + x);
                    int gridIndex = y * 3 + x;

                    if (ingredientIndex >= 0 && ingredientIndex < ingredients.size()) {
                        var ingredient = ingredients.get(ingredientIndex);
                        var first = ingredient.items().findFirst();
                        if (first.isPresent()) {
                            inputs[gridIndex] = new ItemStack(first.get().value());
                        }
                    }
                }
            }
        } else {
            int index = 0;
            for (var ingredient : ingredients) {
                var first = ingredient.items().findFirst();
                if (first.isPresent() && index < 9) {
                    inputs[index] = new ItemStack(first.get().value());
                    index++;
                }
            }
        }

        return inputs;
    }
}
