package com.lumengrid.recursiveae2patternprovider.mixin;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import appeng.menu.me.crafting.CraftConfirmMenu;
import appeng.menu.me.crafting.CraftingPlanSummary;
import com.lumengrid.recursiveae2patternprovider.PlanExplanation;
import com.lumengrid.recursiveae2patternprovider.menu.IPlanExplanationMenu;
import com.lumengrid.recursiveae2patternprovider.network.PlanExplanationS2CPacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.*;

@Mixin(value = CraftConfirmMenu.class, remap = false)
public abstract class CraftConfirmMenuMixin implements IPlanExplanationMenu {
    @Shadow private ICraftingPlan result;
    @Shadow private CraftingPlanSummary plan;
    @Unique private CraftingPlanSummary recursiveAE2$lastPlan;
    @Unique private Map<AEKey, List<AEKey>> recursiveAE2$explanation = Map.of();

    public Map<AEKey, List<AEKey>> recursiveAE2$getExplanation() { return recursiveAE2$explanation; }
    public void recursiveAE2$setExplanation(Map<AEKey, List<AEKey>> explanation) {
        recursiveAE2$explanation = explanation;
    }

    @Inject(method = "broadcastChanges", at = @At("TAIL"))
    private void recursiveAE2$sendExplanation(CallbackInfo ci) {
        var menu = (CraftConfirmMenu) (Object) this;
        if (menu.getPlayer() instanceof ServerPlayer player && plan != recursiveAE2$lastPlan) {
            recursiveAE2$lastPlan = plan;
            var explanation = result != null && plan != null ? PlanExplanation.analyze(result) : Map.<AEKey, List<AEKey>>of();
            PacketDistributor.sendToPlayer(player, new PlanExplanationS2CPacket(menu.containerId, explanation));
        }
    }

    // A replan may reuse the same menu. Discard the previous explanation immediately.
    @Inject(method = "setPlan", at = @At("HEAD"))
    private void recursiveAE2$clearExplanation(CraftingPlanSummary newPlan, CallbackInfo ci) {
        recursiveAE2$explanation = Map.of();
    }
}
