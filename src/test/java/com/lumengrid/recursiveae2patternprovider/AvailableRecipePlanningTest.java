package com.lumengrid.recursiveae2patternprovider;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.*;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.*;
import appeng.crafting.CraftingCalculation;
import appeng.crafting.pattern.AEProcessingPattern;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

/** Exercise AE2's real calculation/backtracking with a small in-memory grid. */
@ExtendWith(EphemeralTestServerProvider.class)
class AvailableRecipePlanningTest {
    @Test
    void runtimeSubstitutionCanSpendLogsReservedForAnotherTask(MinecraftServer server) throws Exception {
        var wood = processing(List.of(new GenericStack(AEItemKey.of(Items.CHERRY_LOG), 4)), Items.CHERRY_WOOD, 3);
        var planks = flexibleCherryLogs(processing(List.of(new GenericStack(AEItemKey.of(Items.CHERRY_LOG), 1)), Items.CHERRY_PLANKS, 4));
        var root = processing(List.of(new GenericStack(AEItemKey.of(Items.CHERRY_WOOD), 3),
                new GenericStack(AEItemKey.of(Items.CHERRY_PLANKS), 4)), Items.CLOCK, 1);
        var plan = calculate(List.of(root, wood, planks), Map.of(Items.CHERRY_LOG, 4L, Items.CHERRY_WOOD, 1L), Items.CLOCK, 1);
        assertFalse(plan.simulation());
        var inventory = new appeng.crafting.inv.ListCraftingInventory(ignored -> {});
        inventory.insert(AEItemKey.of(Items.CHERRY_LOG), 4, appeng.api.config.Actionable.MODULATE);
        inventory.insert(AEItemKey.of(Items.CHERRY_WOOD), 1, appeng.api.config.Actionable.MODULATE);
        assertNotNull(extract(planks, inventory));
        assertNull(extract(wood, inventory), "Execution substituted a log that the valid plan reserved for the wood task");
    }

    @Test
    void generatedWoodVariantsFinishRegardlessOfDependencyExecutionOrder(MinecraftServer server) throws Exception {
        var recipe = new ShapedRecipe(null, null, ShapedRecipePattern.of(
                Map.of('L', Ingredient.of(Items.CHERRY_LOG, Items.CHERRY_WOOD)), "L"), new ItemStackTemplate(Items.CHERRY_PLANKS, 4));
        var holder = new net.minecraft.world.item.crafting.RecipeHolder<CraftingRecipe>(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.RECIPE, net.minecraft.resources.Identifier.parse("test:cherry_planks")), recipe);
        var original = new ItemStack[9]; Arrays.fill(original, ItemStack.EMPTY); original[0] = new ItemStack(Items.CHERRY_LOG);
        List<IPatternDetails> patterns = new ArrayList<>();
        for (var variant : RecipeInputVariants.generate(recipe, original, new ItemStack(Items.CHERRY_PLANKS, 4), null, 64)) {
            var encoded = RecursivePatternGenerator.encodePatternStack(holder, variant, new ItemStack(Items.CHERRY_PLANKS, 4), null);
            IPatternDetails pattern = processing(List.of(new GenericStack(AEItemKey.of(variant[0]), 1)), Items.CHERRY_PLANKS, 4);
            if (encoded.get(appeng.api.ids.AEComponents.ENCODED_CRAFTING_PATTERN).canSubstitute()) pattern = flexibleCherryLogs(pattern);
            patterns.add(pattern);
        }
        var wood = processing(List.of(new GenericStack(AEItemKey.of(Items.CHERRY_LOG), 4)), Items.CHERRY_WOOD, 3);
        var root = processing(List.of(new GenericStack(AEItemKey.of(Items.CHERRY_WOOD), 3),
                new GenericStack(AEItemKey.of(Items.CHERRY_PLANKS), 4)), Items.CLOCK, 1);
        patterns.add(wood); patterns.add(root);
        var plan = calculate(patterns, Map.of(Items.CHERRY_LOG, 4L, Items.CHERRY_WOOD, 1L), Items.CLOCK, 1);
        assertFalse(plan.simulation());
        var selectedPlanks = plan.patternTimes().keySet().stream().filter(p -> p.getOutputs().getFirst().what().equals(AEItemKey.of(Items.CHERRY_PLANKS))).findFirst().orElseThrow();
        for (var order : List.of(List.of(selectedPlanks, wood), List.of(wood, selectedPlanks))) {
            var inventory = new appeng.crafting.inv.ListCraftingInventory(ignored -> {});
            inventory.insert(AEItemKey.of(Items.CHERRY_LOG), 4, appeng.api.config.Actionable.MODULATE);
            inventory.insert(AEItemKey.of(Items.CHERRY_WOOD), 1, appeng.api.config.Actionable.MODULATE);
            var pending = new ArrayList<>(order);
            while (!pending.isEmpty()) {
                boolean progress = false;
                for (var iterator = pending.iterator(); iterator.hasNext();) {
                    var pattern = iterator.next();
                    if (extract(pattern, inventory) == null) continue;
                    for (var output : pattern.getOutputs()) inventory.insert(output.what(), output.amount(), appeng.api.config.Actionable.MODULATE);
                    iterator.remove(); progress = true;
                }
                assertTrue(progress, "A valid plan stalled because execution changed the ingredient source");
            }
            assertNotNull(extract(root, inventory));
        }
    }

    private KeyCounter[] extract(IPatternDetails pattern, appeng.crafting.inv.ListCraftingInventory inventory) {
        return appeng.crafting.execution.CraftingCpuHelper.extractPatternInputs(pattern, inventory, null, new KeyCounter(), new KeyCounter());
    }

    private IPatternDetails flexibleCherryLogs(IPatternDetails primary) {
        var original = primary.getInputs()[0];
        var options = new LinkedHashSet<AEKey>();
        options.add(original.getPossibleInputs()[0].what());
        options.add(AEItemKey.of(Items.CHERRY_LOG)); options.add(AEItemKey.of(Items.CHERRY_WOOD));
        var flexible = new IPatternDetails.IInput() {
            public GenericStack[] getPossibleInputs() { return options.stream().map(k -> new GenericStack(k, 1)).toArray(GenericStack[]::new); }
            public long getMultiplier() { return 1; }
            public boolean isValid(AEKey key, net.minecraft.world.level.Level level) { return options.contains(key); }
            public AEKey getRemainingKey(AEKey key) { return null; }
        };
        return (IPatternDetails) Proxy.newProxyInstance(IPatternDetails.class.getClassLoader(), new Class<?>[]{IPatternDetails.class},
                (proxy, method, args) -> method.getName().equals("getInputs") ? new IPatternDetails.IInput[]{flexible} : method.invoke(primary, args));
    }

    @Test
    void partialPlanExplainsGeneratedSourceButDoesNotClaimItIsRequired(MinecraftServer server) throws Exception {
        var hats = pattern(Items.LEATHER_HELMET, 3, 6);
        var root = processing(List.of(new GenericStack(AEItemKey.of(Items.REDSTONE), 48)), Items.CLOCK, 1);
        PlanExplanation.registerGenerated(hats);
        var plan = calculate(List.of(root, hats), Map.of(Items.LEATHER_HELMET, 6L), Items.CLOCK, 1);
        assertTrue(plan.simulation());
        assertEquals(List.of(AEItemKey.of(Items.REDSTONE)), PlanExplanation.analyze(plan).get(AEItemKey.of(Items.LEATHER_HELMET)));
        // Supplying the intermediate directly removes the source shortage.
        var supplied = calculate(List.of(root, hats), Map.of(Items.REDSTONE, 48L), Items.CLOCK, 1);
        assertFalse(supplied.simulation());
        assertTrue(PlanExplanation.analyze(supplied).isEmpty());
    }

    @Test
    void doesNotAnnotateManuallyEncodedPatterns(MinecraftServer server) throws Exception {
        var manual = pattern(Items.DIRT, 1, 1);
        var plan = calculate(List.of(manual), Map.of(), 1);
        assertTrue(plan.simulation());
        assertTrue(PlanExplanation.analyze(plan).isEmpty());
    }

    @Test
    void generatedPrimaryVariantsSupplyDrawerAndChestsFromOakLogs(MinecraftServer server) throws Exception {
        var planks = Ingredient.of(Items.DARK_OAK_PLANKS, Items.OAK_PLANKS);
        var chestRecipe = new ShapedRecipe(null, null, ShapedRecipePattern.of(Map.of('P', planks), "PPP", "P P", "PPP"),
                new ItemStackTemplate(Items.CHEST));
        // Barrel identity stands in for a Functional Storage drawer. Its
        // 8-plank + chest recipe has the same dependency shape as the mod.
        var drawerRecipe = new ShapedRecipe(null, null, ShapedRecipePattern.of(
                Map.of('P', planks, 'C', Ingredient.of(Items.CHEST)), "PPP", "PCP", "PPP"), new ItemStackTemplate(Items.BARREL));
        var original = new ItemStack[9];
        Arrays.setAll(original, i -> new ItemStack(Items.DARK_OAK_PLANKS));
        original[4] = ItemStack.EMPTY;
        List<IPatternDetails> patterns = new ArrayList<>();
        for (var grid : RecipeInputVariants.generate(chestRecipe, original, new ItemStack(Items.CHEST), null, 64)) {
            patterns.add(processing(Arrays.stream(grid).filter(s -> !s.isEmpty()).map(s -> new GenericStack(AEItemKey.of(s), 1)).toList(), Items.CHEST, 1));
        }
        original[4] = new ItemStack(Items.CHEST);
        for (var grid : RecipeInputVariants.generate(drawerRecipe, original, new ItemStack(Items.BARREL), null, 64)) {
            patterns.add(processing(Arrays.stream(grid).filter(s -> !s.isEmpty()).map(s -> new GenericStack(AEItemKey.of(s), 1)).toList(), Items.BARREL, 1));
        }
        patterns.add(processing(List.of(new GenericStack(AEItemKey.of(Items.BARREL), 1), new GenericStack(AEItemKey.of(Items.CHEST), 2)), Items.CLOCK, 1));
        patterns.add(processing(List.of(new GenericStack(AEItemKey.of(Items.OAK_LOG), 1)), Items.OAK_PLANKS, 4));
        patterns.add(processing(List.of(new GenericStack(AEItemKey.of(Items.DARK_OAK_LOG), 1)), Items.DARK_OAK_PLANKS, 4));
        var plan = calculate(patterns, Map.of(Items.OAK_LOG, 8L), Items.CLOCK, 1);
        assertFalse(plan.simulation());
        assertTrue(plan.missingItems().isEmpty());
        assertEquals(8L, plan.usedItems().get(AEItemKey.of(Items.OAK_LOG)));
        assertEquals(0L, plan.usedItems().get(AEItemKey.of(Items.DARK_OAK_LOG)));
    }
    @Test
    void primaryWoodVariantsAllowCraftingFromStockedOakLogs(MinecraftServer server) throws Exception {
        var darkChest = processing(List.of(new GenericStack(AEItemKey.of(Items.DARK_OAK_PLANKS), 8)), Items.CHEST, 1);
        var oakChest = processing(List.of(new GenericStack(AEItemKey.of(Items.OAK_PLANKS), 8)), Items.CHEST, 1);
        var darkPlanks = processing(List.of(new GenericStack(AEItemKey.of(Items.DARK_OAK_LOG), 1)), Items.DARK_OAK_PLANKS, 4);
        var oakPlanks = processing(List.of(new GenericStack(AEItemKey.of(Items.OAK_LOG), 1)), Items.OAK_PLANKS, 4);
        var flexibleChest = flexibleWood(darkChest);
        // A craftable primary output pins the child crafting branch even though
        // the input accepts stocked oak planks. Oak logs alone don't satisfy it.
        var withoutVariant = calculate(List.of(flexibleChest, darkPlanks, oakPlanks), Map.of(Items.OAK_LOG, 2L), Items.CHEST, 1);
        assertTrue(withoutVariant.simulation());
        var withVariant = calculate(List.of(flexibleChest, oakChest, darkPlanks, oakPlanks), Map.of(Items.OAK_LOG, 2L), Items.CHEST, 1);
        assertFalse(withVariant.simulation());
        assertTrue(withVariant.patternTimes().containsKey(oakChest));
        assertFalse(withVariant.patternTimes().containsKey(darkPlanks));
    }

    private IPatternDetails processing(List<GenericStack> inputs, Item output, int count) {
        return new AEProcessingPattern(AEItemKey.of(PatternDetailsHelper.encodeProcessingPattern(
                inputs, List.of(new GenericStack(AEItemKey.of(output), count)))));
    }

    private IPatternDetails flexibleWood(IPatternDetails primary) {
        var original = primary.getInputs()[0];
        var flexible = new IPatternDetails.IInput() {
            public GenericStack[] getPossibleInputs() { return new GenericStack[]{
                    new GenericStack(AEItemKey.of(Items.DARK_OAK_PLANKS), 1),
                    new GenericStack(AEItemKey.of(Items.OAK_PLANKS), 1)}; }
            public long getMultiplier() { return original.getMultiplier(); }
            public boolean isValid(AEKey key, net.minecraft.world.level.Level level) {
                return key.equals(AEItemKey.of(Items.DARK_OAK_PLANKS)) || key.equals(AEItemKey.of(Items.OAK_PLANKS));
            }
            public AEKey getRemainingKey(AEKey key) { return null; }
        };
        return (IPatternDetails) Proxy.newProxyInstance(IPatternDetails.class.getClassLoader(), new Class<?>[]{IPatternDetails.class},
                (proxy, method, args) -> method.getName().equals("getInputs")
                        ? new IPatternDetails.IInput[]{flexible} : method.invoke(primary, args));
    }
    @Test
    void storedCopperBlocksSupplyBothBlockAndIngotDependencies(MinecraftServer server) throws Exception {
        var root = new AEProcessingPattern(AEItemKey.of(PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(AEItemKey.of(Items.COPPER_BLOCK), 1), new GenericStack(AEItemKey.of(Items.COPPER_INGOT), 1)),
                List.of(new GenericStack(AEItemKey.of(Items.CLOCK), 1)))));
        var unpack = new AEProcessingPattern(AEItemKey.of(PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(AEItemKey.of(Items.COPPER_BLOCK), 1)),
                List.of(new GenericStack(AEItemKey.of(Items.COPPER_INGOT), 9)))));
        var pack = new AEProcessingPattern(AEItemKey.of(PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(AEItemKey.of(Items.COPPER_INGOT), 9)),
                List.of(new GenericStack(AEItemKey.of(Items.COPPER_BLOCK), 1)))));
        var plan = calculate(List.of(root, pack, unpack), Map.of(Items.COPPER_BLOCK, 2L), Items.CLOCK, 1);
        assertFalse(plan.simulation());
        assertEquals(1L, plan.patternTimes().get(unpack));
        assertFalse(plan.patternTimes().containsKey(pack));
    }

    @Test
    void inadequateStockStillProducesAPartialPlan(MinecraftServer server) throws Exception {
        var blocks = pattern(Items.REDSTONE_BLOCK, 1, 9);
        var hats = pattern(Items.LEATHER_HELMET, 3, 6);
        var plan = calculate(List.of(blocks, hats), Map.of(Items.REDSTONE_BLOCK, 1L), 100);
        assertTrue(plan.simulation());
        assertFalse(plan.missingItems().isEmpty());
    }
    @Test
    void availableBlocksWorkEvenWhenHatPatternIsFirst(MinecraftServer server) throws Exception {
        var hats = pattern(Items.LEATHER_HELMET, 3, 6);
        var blocks = pattern(Items.REDSTONE_BLOCK, 1, 9);
        var plan = calculate(List.of(hats, blocks), Map.of(Items.REDSTONE_BLOCK, 1L), 9);
        assertFalse(plan.simulation());
        assertEquals(1L, plan.patternTimes().get(blocks));
        assertFalse(plan.patternTimes().containsKey(hats));
    }

    @Test
    void changedStockUsesOtherAlternativeWithoutRegeneratingPatterns(MinecraftServer server) throws Exception {
        var hats = pattern(Items.LEATHER_HELMET, 3, 6);
        var blocks = pattern(Items.REDSTONE_BLOCK, 1, 9);
        var patterns = List.<IPatternDetails>of(blocks, hats);
        var first = calculate(patterns, Map.of(Items.REDSTONE_BLOCK, 1L), 6);
        var second = calculate(patterns, Map.of(Items.LEATHER_HELMET, 3L), 6);
        assertFalse(first.simulation());
        assertFalse(second.simulation());
        assertTrue(first.patternTimes().containsKey(blocks));
        assertTrue(second.patternTimes().containsKey(hats));
        assertFalse(second.patternTimes().containsKey(blocks));
    }

    @Test
    void combinesAvailableSourcesWhenOneIsInsufficient(MinecraftServer server) throws Exception {
        var hats = pattern(Items.LEATHER_HELMET, 3, 6);
        var blocks = pattern(Items.REDSTONE_BLOCK, 1, 9);
        var plan = calculate(List.of(blocks, hats), Map.of(Items.REDSTONE_BLOCK, 1L, Items.LEATHER_HELMET, 3L), 15);
        assertFalse(plan.simulation());
        assertEquals(1L, plan.patternTimes().get(blocks));
        assertEquals(1L, plan.patternTimes().get(hats));
    }

    private IPatternDetails pattern(Item source, int input, int output) {
        var stack = PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(AEItemKey.of(source), input)),
                List.of(new GenericStack(AEItemKey.of(Items.REDSTONE), output)));
        return new AEProcessingPattern(AEItemKey.of(stack));
    }

    private ICraftingPlan calculate(List<IPatternDetails> patterns, Map<Item, Long> stock, long amount) throws Exception {
        return calculate(patterns, stock, Items.REDSTONE, amount);
    }

    private ICraftingPlan calculate(List<IPatternDetails> patterns, Map<Item, Long> stock, Item output, long amount) throws Exception {
        var inventory = new KeyCounter();
        stock.forEach((item, count) -> inventory.add(AEItemKey.of(item), count));
        var storage = stub(IStorageService.class, (name, args) -> switch (name) {
            case "getCachedInventory" -> inventory;
            default -> throw new AssertionError(name);
        });
        var crafting = stub(ICraftingService.class, (name, args) -> switch (name) {
            case "getCraftingFor" -> patterns.stream().filter(p -> p.getOutputs().stream()
                    .anyMatch(o -> o.what().equals(args[0]))).toList();
            case "canEmitFor" -> false;
            case "getFuzzyCraftable" -> null;
            default -> throw new AssertionError(name);
        });
        var grid = stub(IGrid.class, (name, args) -> switch (name) {
            case "getStorageService" -> storage;
            case "getCraftingService" -> crafting;
            default -> throw new AssertionError(name);
        });
        var node = stub(IGridNode.class, (name, args) -> switch (name) {
            case "getGrid" -> grid;
            default -> throw new AssertionError(name);
        });
        var requester = stub(ICraftingSimulationRequester.class, (name, args) -> switch (name) {
            case "getGridNode" -> node;
            case "getActionSource" -> IActionSource.empty();
            default -> throw new AssertionError(name);
        });
        var calculation = new CraftingCalculation(null, grid, requester,
                new GenericStack(AEItemKey.of(output), amount), CalculationStrategy.REPORT_MISSING_ITEMS);
        // Run the synchronous calculation directly; bypass the tick scheduler's
        // initial pause in this small fixture, not any crafting decisions.
        var incTime = CraftingCalculation.class.getDeclaredField("incTime");
        incTime.setAccessible(true);
        incTime.setInt(calculation, 0);
        var compute = CraftingCalculation.class.getDeclaredMethod("computePlan");
        compute.setAccessible(true);
        return (ICraftingPlan) compute.invoke(calculation);
    }

    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> type, BiFunction<String, Object[], Object> calls) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> calls.apply(method.getName(), args));
    }
}
