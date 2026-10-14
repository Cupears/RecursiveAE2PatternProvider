package com.lumengrid.recursiveae2patternprovider.mixin.client;

import appeng.client.gui.me.crafting.CraftConfirmTableRenderer;
import appeng.menu.me.crafting.CraftingPlanSummaryEntry;
import com.lumengrid.recursiveae2patternprovider.menu.IPlanExplanationMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.*;

@Mixin(value = CraftConfirmTableRenderer.class, remap = false)
public abstract class CraftConfirmTableRendererMixin {
    @Inject(method = "getEntryTooltip(Lappeng/menu/me/crafting/CraftingPlanSummaryEntry;)Ljava/util/List;", at = @At("RETURN"), cancellable = true)
    private void recursiveAE2$explainSource(CraftingPlanSummaryEntry entry, CallbackInfoReturnable<List<Component>> cir) {
        var player = Minecraft.getInstance().player;
        if (entry.getMissingAmount() <= 0 || player == null || !(player.containerMenu instanceof IPlanExplanationMenu menu)) return;
        var products = menu.recursiveAE2$getExplanation().get(entry.getWhat());
        if (products == null || products.isEmpty()) return;
        var lines = new ArrayList<>(cir.getReturnValue());
        lines.add(Component.translatable("gui.recursiveae2patternprovider.plan.source").withColor(0xFF9A32));
        for (var product : products) {
            lines.add(Component.translatable("gui.recursiveae2patternprovider.plan.produces", product.getDisplayName()).withColor(0xFF9A32));
        }
        cir.setReturnValue(lines);
    }
}
