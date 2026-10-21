package com.lumengrid.recursiveae2patternprovider.mixin;

import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.crafting.IPatternDetails;
import appeng.crafting.CraftBranchFailure;
import appeng.crafting.inv.CraftingSimulationState;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.CraftingTreeProcess;
import com.lumengrid.recursiveae2patternprovider.StoredConversionInputs;
import java.util.ArrayList;
import java.util.Collection;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CraftingTreeNode.class, remap = false)
public abstract class StoredConversionInputMixin {
    @Shadow @Final private CraftingTreeProcess parent;
    @Shadow @Final private AEKey what;
    @Shadow private ArrayList<CraftingTreeProcess> nodes;
    @Unique private CraftingSimulationState recursiveAE2$currentInventory;

    @WrapMethod(method = "request")
    private void recursiveAE2$withCurrentInventory(CraftingSimulationState inventory, long requested,
            KeyCounter containers, Operation<Void> original) throws CraftBranchFailure, InterruptedException {
        var previous = recursiveAE2$currentInventory;
        recursiveAE2$currentInventory = inventory;
        try {
            original.call(inventory, requested, containers);
        } finally {
            recursiveAE2$currentInventory = previous;
        }
    }

    @Redirect(method = "buildChildPatterns", at = @At(value = "INVOKE", target =
            "Lappeng/api/networking/crafting/ICraftingService;getCraftingFor(Lappeng/api/stacks/AEKey;)Ljava/util/Collection;"))
    private Collection<IPatternDetails> recursiveAE2$onlyAvailableRecoveries(ICraftingService service, AEKey key) {
        var choices = service.getCraftingFor(key);
        if (recursiveAE2$currentInventory == null) return choices;
        return choices.stream().filter(pattern -> StoredConversionInputs.available(pattern, recursiveAE2$currentInventory, 1)).toList();
    }

    @WrapOperation(method = "request", at = @At(value = "INVOKE", target =
            "Lappeng/crafting/CraftingTreeProcess;request(Lappeng/crafting/inv/CraftingSimulationState;J)V"))
    private void recursiveAE2$tryOtherRouteWhenRecoveryRunsOut(CraftingTreeProcess process,
            CraftingSimulationState inventory, long crafts, Operation<Void> original)
            throws CraftBranchFailure, InterruptedException {
        // AE2's partial-plan pass normally invents missing inputs. Recovery may
        // only spend real stock, so let the existing branch rollback try the next route.
        if (nodes.size() > 1 && !StoredConversionInputs.available(
                ((CraftingTreeProcessAccessor) process).recursiveAE2$details(), inventory, crafts)) {
            throw new CraftBranchFailure(what, crafts);
        }
        original.call(process, inventory, crafts);
    }

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
