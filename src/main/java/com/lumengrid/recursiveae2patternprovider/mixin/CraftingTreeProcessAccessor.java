package com.lumengrid.recursiveae2patternprovider.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.crafting.CraftingTreeProcess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = CraftingTreeProcess.class, remap = false)
public interface CraftingTreeProcessAccessor {
    @Accessor("details") IPatternDetails recursiveAE2$details();
}
