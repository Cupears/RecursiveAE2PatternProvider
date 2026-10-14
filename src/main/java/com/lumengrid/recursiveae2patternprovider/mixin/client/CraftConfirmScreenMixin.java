package com.lumengrid.recursiveae2patternprovider.mixin.client;

import appeng.client.gui.me.crafting.CraftConfirmScreen;
import com.lumengrid.recursiveae2patternprovider.menu.IPlanExplanationMenu;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = CraftConfirmScreen.class, remap = false)
public abstract class CraftConfirmScreenMixin {
    @ModifyArg(method = "updateBeforeRender", at = @At(value = "INVOKE",
            target = "Lappeng/client/gui/me/crafting/CraftConfirmScreen;setTextContent(Ljava/lang/String;Lnet/minecraft/network/chat/Component;)V", ordinal = 1), index = 1)
    private Component recursiveAE2$showHoverHint(Component original) {
        var menu = ((CraftConfirmScreen) (Object) this).getMenu();
        if (menu.getPlan() != null && menu.getPlan().isSimulation()
                && menu instanceof IPlanExplanationMenu explanations && !explanations.recursiveAE2$getExplanation().isEmpty()) {
            return Component.translatable("gui.recursiveae2patternprovider.plan.hover_hint").withColor(0xFF9A32);
        }
        return original;
    }
}
