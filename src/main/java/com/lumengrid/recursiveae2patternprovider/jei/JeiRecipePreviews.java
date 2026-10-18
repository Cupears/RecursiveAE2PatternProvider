package com.lumengrid.recursiveae2patternprovider.jei;

import com.lumengrid.recursiveae2patternprovider.client.RecipePreviewBridge;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.recipe.*;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.constants.*;
import net.minecraft.world.item.ItemStack;
import java.util.*;

final class JeiRecipePreviews implements RecipePreviewBridge.Provider {
    private final IJeiRuntime runtime;
    JeiRecipePreviews(IJeiRuntime runtime) { this.runtime = runtime; }
    private IFocus<ItemStack> focus(ItemStack output) {
        return runtime.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, output);
    }
    @Override public List<RecipePreviewBridge.Preview> previews(ItemStack output) {
        var focus = focus(output); var result = new ArrayList<RecipePreviewBridge.Preview>();
        var categories = runtime.getRecipeManager().createRecipeCategoryLookup().limitFocus(List.of(focus)).get().toList();
        for (var category : categories) add(category, focus, result);
        return List.copyOf(result);
    }
    private <T> void add(IRecipeCategory<T> category, IFocus<ItemStack> focus, List<RecipePreviewBridge.Preview> target) {
        var manager = runtime.getRecipeManager();
        var focuses = runtime.getJeiHelpers().getFocusFactory().createFocusGroup(List.of(focus));
        // The full viewer remains available for categories with many near-identical recipes.
        for (var recipe : manager.createRecipeLookup(category.getRecipeType()).limitFocus(List.of(focus)).get().limit(6).toList()) {
            try {
                var layout = manager.createRecipeLayoutDrawable(category, recipe, focuses).orElse(null);
                if (layout == null) continue;
                target.add(new RecipePreviewBridge.Preview(category.getTitle(), !category.getRecipeType().equals(RecipeTypes.CRAFTING),
                        (g, x, y, w, h, mx, my) -> {
                            var rect = layout.getRectWithBorder();
                            float scale = Math.min(1f, Math.min((float)w / Math.max(1, rect.getWidth()), (float)h / Math.max(1, rect.getHeight())));
                            g.enableScissor(x, y, x + w, y + h); g.pose().pushMatrix();
                            g.pose().translate(x - rect.getX() * scale, y - rect.getY() * scale); g.pose().scale(scale);
                            try { layout.drawRecipe(g, (int)((mx - x) / scale) + rect.getX(), (int)((my - y) / scale) + rect.getY()); }
                            finally { g.pose().popMatrix(); g.disableScissor(); }
                        }));
            } catch (RuntimeException e) {
                com.lumengrid.recursiveae2patternprovider.RecursiveAE2PatternProvider.LOGGER.debug("JEI preview unavailable for {}", category.getTitle().getString(), e);
            }
        }
    }
    @Override public void show(ItemStack output) { runtime.getRecipesGui().show(focus(output)); }
}
