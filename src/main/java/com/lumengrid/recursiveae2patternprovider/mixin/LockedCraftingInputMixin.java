package com.lumengrid.recursiveae2patternprovider.mixin;

import appeng.crafting.pattern.AECraftingPattern;
import appeng.api.stacks.*;
import com.lumengrid.recursiveae2patternprovider.PatternIngredientLocks;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(targets = "appeng.crafting.pattern.AECraftingPattern$Input", remap = false)
public abstract class LockedCraftingInputMixin {
    @Shadow @Final private int slot;
    @Shadow @Final private AECraftingPattern this$0;
    @Shadow @Final @Mutable private GenericStack[] possibleInputs;
    @Unique private String recursiveAE2$lockedItem;
    @Inject(method = "<init>", at = @At("RETURN"))
    private void recursiveAE2$lockPlanning(CallbackInfo ci) {
        recursiveAE2$lockedItem = PatternIngredientLocks.read(this$0.getDefinition().toStack()).get("root/" + slot);
        if (recursiveAE2$lockedItem == null) return;
        possibleInputs = java.util.Arrays.stream(possibleInputs).filter(c -> c.what() instanceof AEItemKey key
                && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(key.getItem()).toString().equals(recursiveAE2$lockedItem)).distinct().toArray(GenericStack[]::new);
        if (possibleInputs.length == 0) throw new IllegalArgumentException("Locked ingredient is no longer accepted by this recipe");
    }
    @Inject(method = "isValid", at = @At("HEAD"), cancellable = true)
    private void recursiveAE2$lockInput(AEKey key, net.minecraft.world.level.Level level, CallbackInfoReturnable<Boolean> cir) {
        if (recursiveAE2$lockedItem != null && (!(key instanceof AEItemKey item)
                || !net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.getItem()).toString().equals(recursiveAE2$lockedItem))) cir.setReturnValue(false);
    }
}
