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
    void partialPlanUsesPlanksInsteadOfMissingPortableTable(MinecraftServer server) throws Exception {
        var recover = processing(List.of(new GenericStack(AEItemKey.of(Items.CRAFTING_TABLE), 1)), Items.CHEST, 1);
        StoredConversionInputs.register(recover);
        var fresh = processing(List.of(new GenericStack(AEItemKey.of(Items.BAMBOO_PLANKS), 4)), Items.CHEST, 1);
        var root = processing(List.of(new GenericStack(AEItemKey.of(Items.CHEST), 3),
                new GenericStack(AEItemKey.of(Items.DIAMOND), 1)), Items.CLOCK, 1);
        var plan = calculate(List.of(root, recover, fresh), Map.of(Items.BAMBOO_PLANKS, 340L), Items.CLOCK, 1);
        assertTrue(plan.simulation());
        assertEquals(3L, plan.patternTimes().get(fresh));
        assertFalse(plan.patternTimes().containsKey(recover));
        assertEquals(0L, plan.missingItems().get(AEItemKey.of(Items.CRAFTING_TABLE)));
        assertEquals(1L, plan.missingItems().get(AEItemKey.of(Items.DIAMOND)));
    }

    @Test
    void partialPlanUsesFreshCableRouteAfterStoredVariantRunsOut(MinecraftServer server) throws Exception {
        var recover = processing(List.of(new GenericStack(AEItemKey.of(Items.MAGENTA_STAINED_GLASS), 1),
                new GenericStack(AEItemKey.of(Items.SNOWBALL), 1)), Items.GLASS, 1);
        StoredConversionInputs.register(recover, List.of(AEItemKey.of(Items.MAGENTA_STAINED_GLASS)));
        var fresh = processing(List.of(new GenericStack(AEItemKey.of(Items.SAND), 1)), Items.GLASS, 1);
        var root = processing(List.of(new GenericStack(AEItemKey.of(Items.GLASS), 5),
                new GenericStack(AEItemKey.of(Items.DIAMOND), 1)), Items.CLOCK, 1);
        var plan = calculate(List.of(root, recover, fresh),
                Map.of(Items.MAGENTA_STAINED_GLASS, 1L, Items.SNOWBALL, 5L, Items.SAND, 4L), Items.CLOCK, 1);
        assertTrue(plan.simulation());
        assertEquals(1L, plan.patternTimes().get(recover));
        assertEquals(4L, plan.patternTimes().get(fresh));
        assertEquals(0L, plan.missingItems().get(AEItemKey.of(Items.MAGENTA_STAINED_GLASS)));
        assertEquals(1L, plan.missingItems().get(AEItemKey.of(Items.DIAMOND)));
    }

    @Test
    void missingRecoveryWithoutManufacturingReportsBaseItem(MinecraftServer server) throws Exception {
        var recover = processing(List.of(new GenericStack(AEItemKey.of(Items.MAGENTA_STAINED_GLASS), 1)), Items.GLASS, 1);
        StoredConversionInputs.register(recover);
        var root = processing(List.of(new GenericStack(AEItemKey.of(Items.GLASS), 5)), Items.CLOCK, 1);
        var plan = calculate(List.of(root, recover), Map.of(), Items.CLOCK, 1);
        assertTrue(plan.simulation());
        assertEquals(5L, plan.missingItems().get(AEItemKey.of(Items.GLASS)));
        assertEquals(0L, plan.missingItems().get(AEItemKey.of(Items.MAGENTA_STAINED_GLASS)));
    }

    @Test
    void storedVariantCanCraftAuxiliaryConversionIngredients(MinecraftServer server) throws Exception {
        var recover = processing(List.of(new GenericStack(AEItemKey.of(Items.YELLOW_STAINED_GLASS), 8),
                new GenericStack(AEItemKey.of(Items.REDSTONE_TORCH), 1)), Items.GLASS, 8);
        StoredConversionInputs.register(recover, List.of(AEItemKey.of(Items.YELLOW_STAINED_GLASS)));
        var invert = processing(List.of(new GenericStack(AEItemKey.of(Items.GLASS), 8),
                new GenericStack(AEItemKey.of(Items.REDSTONE_TORCH), 1)), Items.YELLOW_STAINED_GLASS, 8);
        var torch = processing(List.of(new GenericStack(AEItemKey.of(Items.REDSTONE), 1),
                new GenericStack(AEItemKey.of(Items.STICK), 1)), Items.REDSTONE_TORCH, 1);
        var root = processing(List.of(new GenericStack(AEItemKey.of(Items.GLASS), 8)), Items.CLOCK, 1);
        var plan = calculate(List.of(root, recover, invert, torch),
                Map.of(Items.YELLOW_STAINED_GLASS, 8L, Items.REDSTONE, 1L, Items.STICK, 1L), Items.CLOCK, 1);
        assertFalse(plan.simulation());
        assertEquals(1L, plan.patternTimes().get(recover));
        assertEquals(1L, plan.patternTimes().get(torch));
        assertFalse(plan.patternTimes().containsKey(invert));
    }

    @Test
    void copperRecoveryDoesNotPackAndWaxFreshIngots(MinecraftServer server) throws Exception {
        var recover = processing(List.of(new GenericStack(AEItemKey.of(Items.WAXED_COPPER_BLOCK), 1)), Items.COPPER_INGOT, 9);
        StoredConversionInputs.register(recover);
        var block = processing(List.of(new GenericStack(AEItemKey.of(Items.COPPER_INGOT), 9)), Items.COPPER_BLOCK, 1);
        var wax = processing(List.of(new GenericStack(AEItemKey.of(Items.COPPER_BLOCK), 1),
                new GenericStack(AEItemKey.of(Items.HONEYCOMB), 1)), Items.WAXED_COPPER_BLOCK, 1);
        var fresh = processing(List.of(new GenericStack(AEItemKey.of(Items.RAW_COPPER), 1)), Items.COPPER_INGOT, 1);
        var root = processing(List.of(new GenericStack(AEItemKey.of(Items.COPPER_INGOT), 9)), Items.CLOCK, 1);
        var patterns = List.of(root, recover, wax, block, fresh);
        var plan = calculate(patterns, Map.of(Items.COPPER_INGOT, 8L, Items.RAW_COPPER, 1L, Items.HONEYCOMB, 1L), Items.CLOCK, 1);
        assertFalse(plan.simulation());
        assertEquals(1L, plan.patternTimes().get(fresh));
        assertFalse(plan.patternTimes().containsKey(wax));
        assertFalse(plan.patternTimes().containsKey(recover));
        var stored = calculate(patterns, Map.of(Items.WAXED_COPPER_BLOCK, 1L), Items.CLOCK, 1);
        assertFalse(stored.simulation());
        assertEquals(1L, stored.patternTimes().get(recover));
        var packed = calculate(List.of(block, recover, fresh), Map.of(Items.RAW_COPPER, 9L), Items.COPPER_BLOCK, 1);
        assertFalse(packed.simulation());
        assertEquals(1L, packed.patternTimes().get(block));
        assertEquals(9L, packed.patternTimes().get(fresh));
    }

    @Test
    void conversionRestrictionDoesNotLeakToEqualManualPattern(MinecraftServer server) {
        var inputs = List.of(new GenericStack(AEItemKey.of(Items.RED_STAINED_GLASS), 8));
        var generated = processing(inputs, Items.GLASS, 8);
        var manual = processing(inputs, Items.GLASS, 8);
        StoredConversionInputs.register(generated);
        assertTrue(StoredConversionInputs.contains(generated));
        assertFalse(StoredConversionInputs.contains(manual));
    }

    @Test
    void glassRecoveryDoesNotCraftItsOwnColouredInput(MinecraftServer server) throws Exception {
        var recover = processing(List.of(new GenericStack(AEItemKey.of(Items.BROWN_STAINED_GLASS), 8)), Items.GLASS, 8);
        StoredConversionInputs.register(recover);
        var dye = processing(List.of(new GenericStack(AEItemKey.of(Items.GLASS), 8),
                new GenericStack(AEItemKey.of(Items.BROWN_DYE), 1)), Items.BROWN_STAINED_GLASS, 8);
        var fresh = processing(List.of(new GenericStack(AEItemKey.of(Items.SAND), 1)), Items.GLASS, 1);
        var root = processing(List.of(new GenericStack(AEItemKey.of(Items.GLASS), 8)), Items.CLOCK, 1);
        var plan = calculate(List.of(root, recover, dye, fresh), Map.of(Items.GLASS, 7L, Items.SAND, 1L, Items.BROWN_DYE, 1L), Items.CLOCK, 1);
        assertFalse(plan.simulation());
        assertFalse(plan.patternTimes().containsKey(dye));
        assertFalse(plan.patternTimes().containsKey(recover));
        assertEquals(1L, plan.patternTimes().get(fresh));
        assertEquals(0L, plan.usedItems().get(AEItemKey.of(Items.BROWN_DYE)));
    }

    @Test
    void glassRecoveryCanStillUseExistingColouredGlass(MinecraftServer server) throws Exception {
        var recover = processing(List.of(new GenericStack(AEItemKey.of(Items.BLUE_STAINED_GLASS), 8)), Items.GLASS, 8);
        StoredConversionInputs.register(recover);
        var dye = processing(List.of(new GenericStack(AEItemKey.of(Items.GLASS), 8),
                new GenericStack(AEItemKey.of(Items.BLUE_DYE), 1)), Items.BLUE_STAINED_GLASS, 8);
        var root = processing(List.of(new GenericStack(AEItemKey.of(Items.GLASS), 8)), Items.CLOCK, 1);
        var plan = calculate(List.of(root, recover, dye), Map.of(Items.BLUE_STAINED_GLASS, 8L), Items.CLOCK, 1);
        assertFalse(plan.simulation());
        assertEquals(1L, plan.patternTimes().get(recover));
        assertFalse(plan.patternTimes().containsKey(dye));
    }

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
        assertFalse(PlanExplanation.analyze(supplied).containsKey(AEItemKey.of(Items.LEATHER_HELMET)));
    }

    @Test
    void successfulPlanExplainsWoodAndCraftedIntermediate(MinecraftServer server) throws Exception {
        var planks = processing(List.of(new GenericStack(AEItemKey.of(Items.OAK_LOG), 1)), Items.OAK_PLANKS, 4);
        var chest = processing(List.of(new GenericStack(AEItemKey.of(Items.OAK_PLANKS), 8)), Items.CHEST, 1);
        PlanExplanation.registerGenerated(planks);
        PlanExplanation.registerGenerated(chest);
        var plan = calculate(List.of(planks, chest), Map.of(Items.OAK_LOG, 2L), Items.CHEST, 1);
        assertFalse(plan.simulation());
        var explanation = PlanExplanation.analyze(plan);
        assertEquals(List.of(AEItemKey.of(Items.OAK_PLANKS)), explanation.get(AEItemKey.of(Items.OAK_LOG)));
        assertEquals(List.of(AEItemKey.of(Items.CHEST)), explanation.get(AEItemKey.of(Items.OAK_PLANKS)));
        assertFalse(explanation.containsKey(AEItemKey.of(Items.DARK_OAK_LOG)));
    }

    @Test
    void flexibleInputsDoNotClaimAnUnprovenWoodChoice(MinecraftServer server) throws Exception {
        var chest = flexibleWood(processing(List.of(new GenericStack(AEItemKey.of(Items.DARK_OAK_PLANKS), 8)), Items.CHEST, 1));
        PlanExplanation.registerGenerated(chest);
        var plan = calculate(List.of(chest), Map.of(Items.OAK_PLANKS, 8L), Items.CHEST, 1);
        assertFalse(plan.simulation());
        assertTrue(PlanExplanation.analyze(plan).isEmpty());
    }

    @Test
    void manuallyEncodedPatternExplainsItsSelectedMissingSource(MinecraftServer server) throws Exception {
        var manual = pattern(Items.DIRT, 1, 1);
        var plan = calculate(List.of(manual), Map.of(), 1);
        assertTrue(plan.simulation());
        assertEquals(List.of(AEItemKey.of(Items.REDSTONE)), PlanExplanation.analyze(plan).get(AEItemKey.of(Items.DIRT)));
    }

    @Test
    void manualWaterBucketDependencyExplainsSourceAndRequestedPattern(MinecraftServer server) throws Exception {
        var fill = processing(List.of(new GenericStack(AEItemKey.of(Items.LEATHER_HELMET), 1),
                new GenericStack(AEItemKey.of(Items.BUCKET), 1)), Items.WATER_BUCKET, 1);
        var wash = processing(List.of(new GenericStack(AEItemKey.of(Items.MAGENTA_WOOL), 1),
                new GenericStack(AEItemKey.of(Items.WATER_BUCKET), 1)), Items.STRING, 1);
        var root = processing(List.of(new GenericStack(AEItemKey.of(Items.STRING), 1)), Items.CLOCK, 1);
        var plan = calculate(List.of(root, wash, fill), Map.of(Items.BUCKET, 1L), Items.CLOCK, 1);
        var explanation = PlanExplanation.analyze(plan);
        assertTrue(plan.simulation());
        assertEquals(List.of(AEItemKey.of(Items.WATER_BUCKET)), explanation.get(AEItemKey.of(Items.LEATHER_HELMET)));
        assertEquals(List.of(AEItemKey.of(Items.CLOCK)), PlanExplanation.origins(plan, explanation).get(AEItemKey.of(Items.LEATHER_HELMET)));
    }

    @Test
    void recursiveRootExplainsDirectMissingIngredient(MinecraftServer server) throws Exception {
        var stack = PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(AEItemKey.of(Items.GOLD_BLOCK), 1)),
                List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 9)));
        PatternUtil.setRecursive(stack, true);
        var root = new AEProcessingPattern(AEItemKey.of(stack));
        var plan = calculate(List.of(root), Map.of(), Items.GOLD_INGOT, 9);
        assertTrue(plan.simulation());
        assertEquals(List.of(AEItemKey.of(Items.GOLD_INGOT)),
                PlanExplanation.analyze(plan).get(AEItemKey.of(Items.GOLD_BLOCK)));
        assertEquals(List.of(AEItemKey.of(Items.GOLD_INGOT)),
                PlanExplanation.origins(plan, PlanExplanation.analyze(plan)).get(AEItemKey.of(Items.GOLD_BLOCK)));
    }

    @Test
    void sourceOriginFollowsSelectedRootRatherThanFirstGenerator(MinecraftServer server) throws Exception {
        var ingots = processing(List.of(new GenericStack(AEItemKey.of(Items.GOLD_BLOCK), 1)), Items.GOLD_INGOT, 9);
        PlanExplanation.registerGenerated(ingots, List.of(AEItemKey.of(Items.COMPASS)));
        var stack = PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 9)),
                List.of(new GenericStack(AEItemKey.of(Items.CLOCK), 1)));
        PatternUtil.setRecursive(stack, true);
        var root = new AEProcessingPattern(AEItemKey.of(stack));
        var plan = calculate(List.of(root, ingots), Map.of(), Items.CLOCK, 1);
        var sources = PlanExplanation.analyze(plan);
        assertEquals(List.of(AEItemKey.of(Items.GOLD_INGOT)), sources.get(AEItemKey.of(Items.GOLD_BLOCK)));
        assertEquals(List.of(AEItemKey.of(Items.CLOCK)), PlanExplanation.origins(plan, sources).get(AEItemKey.of(Items.GOLD_BLOCK)));
    }

    @Test
    void directlyRequestedDependencyRetainsStoredPatternOrigin(MinecraftServer server) throws Exception {
        var ingots = processing(List.of(new GenericStack(AEItemKey.of(Items.COPPER_BLOCK), 1)), Items.COPPER_INGOT, 9);
        PlanExplanation.registerGenerated(ingots, List.of(AEItemKey.of(Items.CLOCK)));
        var plan = calculate(List.of(ingots), Map.of(), Items.COPPER_INGOT, 9);
        assertEquals(List.of(AEItemKey.of(Items.CLOCK)),
                PlanExplanation.origins(plan, PlanExplanation.analyze(plan)).get(AEItemKey.of(Items.COPPER_BLOCK)));
    }

    @Test
    void flexibleMissingInputExplainsOnlyUnambiguousSource(MinecraftServer server) throws Exception {
        var chest = flexibleWood(processing(List.of(new GenericStack(AEItemKey.of(Items.DARK_OAK_PLANKS), 8)), Items.CHEST, 1));
        PlanExplanation.registerGenerated(chest);
        var plan = calculate(List.of(chest), Map.of(), Items.CHEST, 1);
        assertTrue(plan.simulation());
        assertEquals(List.of(AEItemKey.of(Items.CHEST)),
                PlanExplanation.analyze(plan).get(AEItemKey.of(Items.DARK_OAK_PLANKS)));
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

    ICraftingPlan calculate(List<IPatternDetails> patterns, Map<Item, Long> stock, Item output, long amount) throws Exception {
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
