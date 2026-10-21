package com.lumengrid.recursiveae2patternprovider.mixin;

import appeng.crafting.CraftingCalculation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = CraftingCalculation.class, remap = false)
public interface CraftingCalculationAccessor {
    @Accessor("simulate") void recursiveAE2$setSimulation(boolean simulation);
}
