package com.lumengrid.recursiveae2patternprovider;

import appeng.api.ids.AEComponents;
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
class GeneratedIngredientAlternativesTest {
    @Test
    void generatedVariantsBindExecutionToValidatedIngredients(MinecraftServer server) {
        var pattern = ShapedRecipePattern.of(Map.of('X', Ingredient.of(Items.OAK_LOG, Items.OAK_WOOD)), "X");
        CraftingRecipe recipe = new ShapedRecipe(null, null, pattern, new ItemStackTemplate(Items.OAK_PLANKS, 4));
        var holder = new RecipeHolder<>(ResourceKey.create(Registries.RECIPE, Identifier.parse("test:oak_planks")), recipe);
        var inputs = new ItemStack[9];
        Arrays.fill(inputs, ItemStack.EMPTY);
        inputs[0] = new ItemStack(Items.OAK_LOG);
        var encoded = RecursivePatternGenerator.encodePatternStack(holder, inputs, new ItemStack(Items.OAK_PLANKS, 4), null);
        assertFalse(encoded.get(AEComponents.ENCODED_CRAFTING_PATTERN).canSubstitute());
        assertTrue(recipe.matches(CraftingInput.of(1, 1, List.of(new ItemStack(Items.OAK_WOOD))), null));
        assertTrue(ItemStack.matches(recipe.assemble(CraftingInput.of(1, 1, List.of(new ItemStack(Items.OAK_LOG)))),
                recipe.assemble(CraftingInput.of(1, 1, List.of(new ItemStack(Items.OAK_WOOD))))));
        assertFalse(recipe.matches(CraftingInput.of(1, 1, List.of(new ItemStack(Items.BIRCH_LOG))), null));
    }

    @Test
    void inheritedSettingsCanStillBeRequested(MinecraftServer server) {
        boolean previous = Config.GENERATED_INGREDIENT_ALTERNATIVES.get();
        boolean previousDefault = Config.DEFAULT_ALLOW_SUBSTITUTES.get();
        try {
            Config.GENERATED_INGREDIENT_ALTERNATIVES.set(false);
            Config.DEFAULT_ALLOW_SUBSTITUTES.set(false);
            var pattern = ShapedRecipePattern.of(Map.of('X', Ingredient.of(Items.OAK_LOG, Items.OAK_WOOD)), "X");
            CraftingRecipe recipe = new ShapedRecipe(null, null, pattern, new ItemStackTemplate(Items.OAK_PLANKS, 4));
            var holder = new RecipeHolder<>(ResourceKey.create(Registries.RECIPE, Identifier.parse("test:strict_planks")), recipe);
            var inputs = new ItemStack[9];
            Arrays.fill(inputs, ItemStack.EMPTY);
            inputs[0] = new ItemStack(Items.OAK_LOG);
            var encoded = RecursivePatternGenerator.encodePatternStack(holder, inputs, new ItemStack(Items.OAK_PLANKS, 4), null);
            assertFalse(encoded.get(AEComponents.ENCODED_CRAFTING_PATTERN).canSubstitute());
        } finally {
            Config.GENERATED_INGREDIENT_ALTERNATIVES.set(previous);
            Config.DEFAULT_ALLOW_SUBSTITUTES.set(previousDefault);
        }
    }

    @Test
    void ancestorConversionIsPublishedButSelfConsumptionIsNeverPublished(MinecraftServer server) {
        ItemStack[] inputs = {new ItemStack(Items.COPPER_BLOCK)};
        assertFalse(RecursivePatternGenerator.shouldSkipDependency(inputs, Items.COPPER_INGOT, Set.of(Items.COPPER_BLOCK), true));
        assertTrue(RecursivePatternGenerator.shouldSkipDependency(inputs, Items.COPPER_INGOT, Set.of(Items.COPPER_BLOCK), false));
        assertTrue(RecursivePatternGenerator.shouldSkipDependency(inputs, Items.COPPER_BLOCK, Set.of(), true));
    }
}
