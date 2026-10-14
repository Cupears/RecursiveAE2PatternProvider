package com.lumengrid.recursiveae2patternprovider;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class RecipeInputVariantsTest {
    @Test
    void cherryDrawerShapeKeepsCherryPlanksWhileAllowingChestChoices(MinecraftServer server) {
        // The installed Functional Storage cherry_4 recipe uses this shape;
        // a vanilla barrel stands in for its output in the isolated fixture.
        var recipe = new ShapedRecipe(null, null, ShapedRecipePattern.of(Map.of(
                'C', Ingredient.of(Items.CHEST, Items.TRAPPED_CHEST),
                'P', Ingredient.of(Items.CHERRY_PLANKS)), "CPC", "PPP", "CPC"), new ItemStackTemplate(Items.BARREL, 4));
        var original = new ItemStack[9];
        Arrays.setAll(original, i -> new ItemStack(Items.CHERRY_PLANKS));
        for (int slot : new int[]{0, 2, 6, 8}) original[slot] = new ItemStack(Items.TRAPPED_CHEST);
        var variants = RecipeInputVariants.generate(recipe, original, new ItemStack(Items.BARREL, 4), null, 64);
        assertEquals(2, variants.size());
        for (var variant : variants) {
            for (int slot : new int[]{1, 3, 4, 5, 7}) assertTrue(variant[slot].is(Items.CHERRY_PLANKS));
            assertTrue(recipe.matches(CraftingInput.of(3, 3, Arrays.asList(variant)), null));
            variant[1] = new ItemStack(Items.OAK_PLANKS);
            assertFalse(recipe.matches(CraftingInput.of(3, 3, Arrays.asList(variant)), null));
        }
    }

    @Test
    void genericParentGetsOakPrimaryEvenWhenAnotherWoodIsFirst(MinecraftServer server) {
        var ingredient = Ingredient.of(Items.DARK_OAK_PLANKS, Items.OAK_PLANKS);
        var pattern = ShapedRecipePattern.of(Map.of('P', ingredient), "PPP", "P P", "PPP");
        var recipe = new ShapedRecipe(null, null, pattern, new ItemStackTemplate(Items.CHEST));
        var original = grid(Items.DARK_OAK_PLANKS);
        original[4] = ItemStack.EMPTY;
        var variants = RecipeInputVariants.generate(recipe, original, new ItemStack(Items.CHEST), null, 64);
        assertEquals(2, variants.size());
        assertTrue(variants.stream().anyMatch(v -> v[0].is(Items.OAK_PLANKS)));
        for (var variant : variants) {
            assertTrue(variant[4].isEmpty());
            for (int slot : new int[]{0, 1, 2, 3, 5, 6, 7, 8}) assertTrue(variant[slot].is(variant[0].getItem()));
            assertTrue(recipe.matches(CraftingInput.of(3, 3, Arrays.asList(variant)), null));
        }
    }

    @Test
    void shapeOutputAndVariantLimitArePreserved(MinecraftServer server) {
        var recipe = new ShapedRecipe(null, null, ShapedRecipePattern.of(
                Map.of('P', Ingredient.of(Items.DARK_OAK_PLANKS, Items.OAK_PLANKS)), "P ", "PP"), new ItemStackTemplate(Items.CHEST));
        var original = new ItemStack[9];
        Arrays.fill(original, ItemStack.EMPTY);
        for (int slot : new int[]{0, 3, 4}) original[slot] = new ItemStack(Items.DARK_OAK_PLANKS);
        var variants = RecipeInputVariants.generate(recipe, original, new ItemStack(Items.CHEST), null, 1);
        assertEquals(1, variants.size());
        assertTrue(variants.getFirst()[1].isEmpty());
        assertTrue(variants.getFirst()[3].is(Items.DARK_OAK_PLANKS));
        assertTrue(RecipeInputVariants.generate(recipe, original, new ItemStack(Items.DIAMOND), null, 64).isEmpty());
    }

    @Test
    void outputChangingIngredientIsRejected(MinecraftServer server) {
        var recipe = new ShapedRecipe(null, null, ShapedRecipePattern.of(
                Map.of('L', Ingredient.of(Items.OAK_LOG, Items.BIRCH_LOG)), "L"), new ItemStackTemplate(Items.OAK_PLANKS, 4)) {
            @Override
            public ItemStack assemble(CraftingInput input) {
                return new ItemStack(input.getItem(0).is(Items.BIRCH_LOG) ? Items.BIRCH_PLANKS : Items.OAK_PLANKS, 4);
            }
        };
        var original = new ItemStack[9];
        Arrays.fill(original, ItemStack.EMPTY);
        original[0] = new ItemStack(Items.OAK_LOG);
        var variants = RecipeInputVariants.generate(recipe, original, new ItemStack(Items.OAK_PLANKS, 4), null, 64);
        assertEquals(1, variants.size());
        assertTrue(variants.getFirst()[0].is(Items.OAK_LOG));
    }

    private ItemStack[] grid(Item item) {
        var grid = new ItemStack[9];
        Arrays.setAll(grid, i -> new ItemStack(item));
        return grid;
    }
}
