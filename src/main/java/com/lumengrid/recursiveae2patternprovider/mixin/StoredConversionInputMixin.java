package com.lumengrid.recursiveae2patternprovider.mixin;

import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.CraftingTreeProcess;
import com.lumengrid.recursiveae2patternprovider.StoredConversionInputs;
import java.util.ArrayList;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CraftingTreeNode.class, remap = false)
public abstract class StoredConversionInputMixin {
    @Shadow @Final private CraftingTreeProcess parent;
    @Shadow @Final private AEKey what;
    @Shadow private ArrayList<CraftingTreeProcess> nodes;

    @Unique private boolean recursiveAE2$storedOnly(AEKey key) {
        return parent != null && StoredConversionInputs.contains(((CraftingTreeProcessAccessor) parent).recursiveAE2$details(), key);
    }

    @Inject(method = "findCraftedStack", at = @At("HEAD"), cancellable = true)
    private void recursiveAE2$keepStoredIdentity(ICraftingService service, AEKey key, CallbackInfoReturnable<AEKey> cir) {
        if (recursiveAE2$storedOnly(key)) cir.setReturnValue(key);
    }

    @Inject(method = "buildChildPatterns", at = @At("HEAD"), cancellable = true)
    private void recursiveAE2$avoidReverseDetour(CallbackInfo ci) {
        if (recursiveAE2$storedOnly(what)) {
            if (nodes == null) nodes = new ArrayList<>();
            ci.cancel();
        }
    }
}
