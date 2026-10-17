package com.lumengrid.recursiveae2patternprovider.mixin;

import appeng.crafting.pattern.AECraftingPattern;
import appeng.api.stacks.GenericStack;
import com.lumengrid.recursiveae2patternprovider.ShapelessIngredients;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.*;

/** AE2 26.1.13's shapeless lookup drops alternatives and assumes transfer order. */
@Mixin(value = AECraftingPattern.class, remap = false)
public abstract class ShapelessPatternIngredientsMixin {
    @Shadow @Final private CraftingRecipe recipe;
    @Shadow @Final private List<GenericStack> sparseInputs;
    @Inject(method = "getShapelessRecipeIngredient", at = @At("HEAD"), cancellable = true)
    private void recursiveAE2$ingredientRole(int slot, CallbackInfoReturnable<Optional<Ingredient>> cir) {
        cir.setReturnValue(ShapelessIngredients.at(recipe, sparseInputs, slot));
    }
}
