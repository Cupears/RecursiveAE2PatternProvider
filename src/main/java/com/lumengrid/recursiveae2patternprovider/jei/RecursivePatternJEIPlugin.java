package com.lumengrid.recursiveae2patternprovider.jei;

import com.lumengrid.recursiveae2patternprovider.Config;
import com.lumengrid.recursiveae2patternprovider.PatternUtil;
import com.lumengrid.recursiveae2patternprovider.RecursiveAE2PatternProvider;
import appeng.core.definitions.AEItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Arrays;
import java.util.List;

@JeiPlugin
public class RecursivePatternJEIPlugin implements IModPlugin {
    @Override public void onRuntimeAvailable(mezz.jei.api.runtime.IJeiRuntime runtime) {
        com.lumengrid.recursiveae2patternprovider.client.RecipePreviewBridge.set(new JeiRecipePreviews(runtime));
    }
    @Override public void onRuntimeUnavailable() {
        com.lumengrid.recursiveae2patternprovider.client.RecipePreviewBridge.set(null);
    }
    public static final Identifier UID = Identifier.fromNamespaceAndPath(
            RecursiveAE2PatternProvider.MODID, "recursive_pattern_recipes");

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    private ItemStack getConfiguredRecipeItemStack() {
        try {
            String itemName = Config.RECIPE_ITEM.get();
            Identifier itemId = Identifier.parse(itemName);
            Item item = BuiltInRegistries.ITEM.get(itemId).map(Holder::value).orElse(Items.IRON_INGOT);
            return new ItemStack(item);
        } catch (Exception e) {
            RecursiveAE2PatternProvider.LOGGER.warn("Invalid recipe item configured for JEI: '{}', falling back to iron ingot", Config.RECIPE_ITEM.get());
            return new ItemStack(Items.IRON_INGOT);
        }
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new RecursivePatternRecipeCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        ItemStack configuredRecipeItem = getConfiguredRecipeItemStack();

        List<RecursivePatternRecipe> recipes = Arrays.asList(
                new RecursivePatternRecipe(
                        Arrays.asList(AEItems.CRAFTING_PATTERN.stack(), configuredRecipeItem),
                        createRecursivePattern(AEItems.CRAFTING_PATTERN.stack()),
                        "Add Recursion"
                ),
                new RecursivePatternRecipe(
                        Arrays.asList(AEItems.PROCESSING_PATTERN.stack(), configuredRecipeItem.copy()),
                        createRecursivePattern(AEItems.PROCESSING_PATTERN.stack()),
                        "Add Recursion"
                ),
                new RecursivePatternRecipe(
                        Arrays.asList(AEItems.SMITHING_TABLE_PATTERN.stack(), configuredRecipeItem.copy()),
                        createRecursivePattern(AEItems.SMITHING_TABLE_PATTERN.stack()),
                        "Add Recursion"
                ),
                new RecursivePatternRecipe(
                        Arrays.asList(createRecursivePattern(AEItems.CRAFTING_PATTERN.stack())),
                        AEItems.CRAFTING_PATTERN.stack(),
                        "Remove Recursion"
                ),
                new RecursivePatternRecipe(
                        Arrays.asList(createRecursivePattern(AEItems.PROCESSING_PATTERN.stack())),
                        AEItems.PROCESSING_PATTERN.stack(),
                        "Remove Recursion"
                ),
                new RecursivePatternRecipe(
                        Arrays.asList(createRecursivePattern(AEItems.SMITHING_TABLE_PATTERN.stack())),
                        AEItems.SMITHING_TABLE_PATTERN.stack(),
                        "Remove Recursion"
                )
        );

        registration.addRecipes(RecursivePatternRecipeCategory.RECIPE_TYPE, recipes);
    }

    private ItemStack createRecursivePattern(ItemStack originalPattern) {
        ItemStack recursivePattern = originalPattern.copy();
        recursivePattern = PatternUtil.createRecursivePattern(recursivePattern);
        return recursivePattern;
    }

    public static class RecursivePatternRecipe {
        private final List<ItemStack> inputs;
        private final ItemStack output;
        private final String recipeType;

        public RecursivePatternRecipe(List<ItemStack> inputs, ItemStack output, String recipeType) {
            this.inputs = inputs;
            this.output = output;
            this.recipeType = recipeType;
        }

        public List<ItemStack> getInputs() {
            return inputs;
        }

        public ItemStack getOutput() {
            return output;
        }

        public String getRecipeType() {
            return recipeType;
        }
    }
}
