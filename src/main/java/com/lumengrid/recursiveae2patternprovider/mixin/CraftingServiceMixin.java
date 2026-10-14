package com.lumengrid.recursiveae2patternprovider.mixin;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.AEKey;
import appeng.me.service.CraftingService;
import com.lumengrid.recursiveae2patternprovider.RefreshingRecursiveProvider;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.Future;

@Mixin(value = CraftingService.class, remap = false)
public abstract class CraftingServiceMixin {
    @Shadow @Final private IGrid grid;

    @Inject(method = "beginCraftingCalculation", at = @At("HEAD"))
    private void refreshStaleRecursivePatterns(Level level, ICraftingSimulationRequester requester,
            AEKey what, long amount, CalculationStrategy strategy,
            CallbackInfoReturnable<Future<ICraftingPlan>> cir) {
        var server = level.getServer();
        if (server == null || !server.isSameThread()) return;
        var service = (CraftingService) (Object) this;
        // Existing jobs retain pattern/key references. Never replace them underneath a running job.
        if (service.getCpus().stream().anyMatch(cpu -> cpu.isBusy())) return;
        for (var node : grid.getNodes()) {
            var provider = node.getService(ICraftingProvider.class);
            if (provider instanceof RefreshingRecursiveProvider recursive
                    && recursive.recursiveAE2$refreshStalePatterns()) {
                // requestUpdate alone is queued; the calculation needs the new index immediately.
                service.refreshNodeCraftingProvider(node);
            }
        }
    }
}
