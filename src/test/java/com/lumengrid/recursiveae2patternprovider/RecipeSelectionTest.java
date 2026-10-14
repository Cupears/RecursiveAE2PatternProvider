package com.lumengrid.recursiveae2patternprovider;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class RecipeSelectionTest {
    private final Map<Item, List<RecipeHolder<CraftingRecipe>>> index = new HashMap<>();

    @Test
    void redstoneBlocksAndHatRecipeAreBothPublished(MinecraftServer server) {
        add("minecraft:redstone_from_block", Items.REDSTONE, 9, Items.REDSTONE_BLOCK, 1);
        add("minecraft:redstone_block", Items.REDSTONE_BLOCK, 1, Items.REDSTONE, 9);
        // A leather helmet stands in for the modded hat's item identity.
        add("reliquary:uncrafting/redstone", Items.REDSTONE, 6, Items.LEATHER_HELMET, 3);
        var selection = selector(Set.of(Items.REDSTONE));
        var choices = selection.alternatives(Items.REDSTONE, Set.of());
        assertEquals(2, choices.size());
        assertEquals("minecraft:redstone_from_block", choices.getFirst().recipe().id().identifier().toString());
        assertTrue(selection.isConversion(choices.getFirst().recipe().value(), Items.REDSTONE));
        assertFalse(selection.isConversion(choices.getLast().recipe().value(), Items.REDSTONE));
    }

    @Test
    void stockConversionsAreRetainedEvenWithoutACraftingSource(MinecraftServer server) {
        conversions(Items.IRON_INGOT, Items.IRON_BLOCK, Items.IRON_NUGGET);
        var choices = selector(Set.of(Items.IRON_INGOT)).alternatives(Items.IRON_INGOT, Set.of());
        assertEquals(2, choices.size());
        assertTrue(choices.stream().allMatch(c -> selector(Set.of()).isConversion(c.recipe().value(), Items.IRON_INGOT)));
    }

    @Test
    void firstVisitAncestorsDoNotPermanentlyHideStockConversions(MinecraftServer server) {
        conversions(Items.IRON_INGOT, Items.IRON_BLOCK, Items.IRON_NUGGET);
        var choices = selector(Set.of(Items.IRON_INGOT)).alternatives(Items.IRON_INGOT, Set.of(Items.IRON_BLOCK));
        assertEquals(2, choices.size());
        assertTrue(choices.stream().anyMatch(c -> c.recipe().id().identifier().toString().equals("minecraft:ingot_from_block")));
        var selection = new RecipeSelection(index, Set.of(), r -> false, this::grid);
        assertTrue(selection.alternatives(Items.IRON_INGOT, Set.of()).isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void vanillaRecipeIndexRecognizesSmeltedMetals(MinecraftServer server) throws Exception {
        var indexMethod = RecursivePatternGenerator.class.getDeclaredMethod("getRecipeIndex", RecipeManager.class);
        var basesMethod = RecursivePatternGenerator.class.getDeclaredMethod("getFurnaceOutputs", RecipeManager.class);
        indexMethod.setAccessible(true);
        basesMethod.setAccessible(true);
        var recipes = (Map<Item, List<RecipeHolder<CraftingRecipe>>>) indexMethod.invoke(null, server.getRecipeManager());
        var bases = (Set<Item>) basesMethod.invoke(null, server.getRecipeManager());
        assertTrue(bases.containsAll(Set.of(Items.IRON_INGOT, Items.COPPER_INGOT, Items.GOLD_INGOT)));
        var selection = new RecipeSelection(recipes, bases, r -> true, this::grid);
        assertTrue(selection.rank(Items.IRON_INGOT, Set.of()).isEmpty());
        assertTrue(selection.rank(Items.COPPER_INGOT, Set.of()).isEmpty());
        assertFalse(selection.rank(Items.IRON_BLOCK, Set.of()).isEmpty());
        assertFalse(selection.rank(Items.CLOCK, Set.of()).isEmpty());
        assertFalse(selection.rank(Items.OAK_PLANKS, Set.of()).isEmpty());
    }

    @Test
    void indirectThreeItemCycleIsRejected(MinecraftServer server) {
        add("test:a", Items.IRON_INGOT, 1, Items.COPPER_INGOT, 1);
        add("test:b", Items.COPPER_INGOT, 1, Items.GOLD_INGOT, 1);
        add("test:c", Items.GOLD_INGOT, 1, Items.IRON_INGOT, 1);
        assertTrue(selector(Set.of()).rank(Items.IRON_INGOT, Set.of()).isEmpty());
    }

    @Test
    void ironConversionsAreNotASourceOfIron(MinecraftServer server) {
        conversions(Items.IRON_INGOT, Items.IRON_BLOCK, Items.IRON_NUGGET);
        assertTrue(selector(Set.of(Items.IRON_INGOT)).rank(Items.IRON_INGOT, Set.of()).isEmpty());
    }

    @Test
    void copperDoesNotClimbCompressedBlocks(MinecraftServer server) {
        // Diamond blocks stand in for a second compression tier; selection uses
        // the recipe graph, not hardcoded Compressium names.
        add("minecraft:ingot", Items.COPPER_INGOT, 9, Items.COPPER_BLOCK, 1);
        add("minecraft:block", Items.COPPER_BLOCK, 1, Items.COPPER_INGOT, 9);
        add("compressium:unpack", Items.COPPER_BLOCK, 9, Items.DIAMOND_BLOCK, 1);
        add("compressium:pack", Items.DIAMOND_BLOCK, 1, Items.COPPER_BLOCK, 9);
        var selection = selector(Set.of(Items.COPPER_INGOT));
        assertTrue(selection.rank(Items.COPPER_INGOT, Set.of()).isEmpty());
        assertEquals("minecraft:block", selection.rank(Items.COPPER_BLOCK, Set.of()).getFirst().recipe().id().identifier().toString());
    }

    @Test
    void blockAndNuggetRecipesStillWorkFromBaseIngots(MinecraftServer server) {
        conversions(Items.IRON_INGOT, Items.IRON_BLOCK, Items.IRON_NUGGET);
        var selection = selector(Set.of(Items.IRON_INGOT));
        assertEquals(1, selection.rank(Items.IRON_BLOCK, Set.of()).size());
        assertEquals(1, selection.rank(Items.IRON_NUGGET, Set.of()).size());
    }

    @Test
    void closedConversionLoopWithoutFurnaceSourceIsRejected(MinecraftServer server) {
        add("test:forward", Items.IRON_BLOCK, 1, Items.IRON_INGOT, 9);
        add("test:backward", Items.IRON_INGOT, 9, Items.IRON_BLOCK, 1);
        assertTrue(selector(Set.of()).rank(Items.IRON_BLOCK, Set.of()).isEmpty());
    }

    @Test
    void cheaperRouteWinsOverAlphabeticalId(MinecraftServer server) {
        add("test:a_expensive", Items.CLOCK, 1, Items.DIAMOND, 9);
        add("test:z_simple", Items.CLOCK, 1, Items.REDSTONE, 1);
        assertEquals("test:z_simple", selector(Set.of()).rank(Items.CLOCK, Set.of()).getFirst().recipe().id().identifier().toString());
    }

    @Test
    void productiveAlternativeForFurnaceOutputIsKept(MinecraftServer server) {
        conversions(Items.IRON_INGOT, Items.IRON_BLOCK, Items.IRON_NUGGET);
        add("test:essence", Items.IRON_INGOT, 4, Items.REDSTONE, 1);
        var choices = selector(Set.of(Items.IRON_INGOT)).rank(Items.IRON_INGOT, Set.of());
        assertEquals(1, choices.size());
        assertEquals("test:essence", choices.getFirst().recipe().id().identifier().toString());
    }

    @Test
    void ancestorAndBlacklistAreRespected(MinecraftServer server) {
        add("test:blocked", Items.CLOCK, 1, Items.REDSTONE, 1);
        assertTrue(selector(Set.of()).rank(Items.CLOCK, Set.of(Items.REDSTONE)).isEmpty());
        var selection = new RecipeSelection(index, Set.of(), r -> false, this::grid);
        assertTrue(selection.rank(Items.CLOCK, Set.of()).isEmpty());
    }

    private void conversions(Item ingot, Item block, Item nugget) {
        add("minecraft:ingot_from_block", ingot, 9, block, 1);
        add("minecraft:ingot_from_nuggets", ingot, 1, nugget, 9);
        add("minecraft:block", block, 1, ingot, 9);
        add("minecraft:nuggets", nugget, 9, ingot, 1);
    }

    private void add(String id, Item output, int count, Item input, int inputCount) {
        String[] rows = inputCount == 9 ? new String[]{"XXX", "XXX", "XXX"} : new String[]{"X".repeat(inputCount)};
        var pattern = ShapedRecipePattern.of(Map.of('X', Ingredient.of(input)), rows);
        CraftingRecipe recipe = new ShapedRecipe(null, null, pattern, new ItemStackTemplate(output, count));
        var holder = new RecipeHolder<>(ResourceKey.create(Registries.RECIPE, Identifier.parse(id)), recipe);
        index.computeIfAbsent(output, k -> new ArrayList<>()).add(holder);
    }

    private RecipeSelection selector(Set<Item> bases) {
        return new RecipeSelection(index, bases, r -> true, this::grid);
    }

    private ItemStack[] grid(CraftingRecipe recipe) {
        try {
            var method = RecursivePatternGenerator.class.getDeclaredMethod("getRecipeInputsAs3x3Grid", CraftingRecipe.class);
            method.setAccessible(true);
            return (ItemStack[]) method.invoke(null, recipe);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
