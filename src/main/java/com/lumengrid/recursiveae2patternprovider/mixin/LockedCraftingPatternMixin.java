package com.lumengrid.recursiveae2patternprovider.mixin;

import appeng.crafting.pattern.AECraftingPattern;
import appeng.api.stacks.AEItemKey;
import com.lumengrid.recursiveae2patternprovider.PatternIngredientLocks;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AECraftingPattern.class, remap = false)
public abstract class LockedCraftingPatternMixin implements PatternIngredientLocks.Slots {
    @Shadow @Final private int[] sparseToCompressed;
    @Shadow @Final private AEItemKey definition;
    @Unique private java.util.Map<String, String> recursiveAE2$locks;
    @Override public int recursiveAE2$inputForSlot(int slot) { return sparseToCompressed[slot]; }
    @Inject(method = "isItemValid", at = @At("HEAD"), cancellable = true)
    private void recursiveAE2$lockedExecution(int slot, AEItemKey key, Level level, CallbackInfoReturnable<Boolean> cir) {
        if (recursiveAE2$locks == null) recursiveAE2$locks = PatternIngredientLocks.read(definition.toStack());
        String item = recursiveAE2$locks.get("root/" + slot);
        if (item != null && (key == null || !net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(key.getItem()).toString().equals(item)))
            cir.setReturnValue(false);
    }
}
