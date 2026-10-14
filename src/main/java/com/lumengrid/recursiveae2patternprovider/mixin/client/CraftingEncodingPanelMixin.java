package com.lumengrid.recursiveae2patternprovider.mixin.client;

import appeng.util.Icon;
import appeng.client.gui.WidgetContainer;
import appeng.client.gui.me.items.CraftingEncodingPanel;
import appeng.client.gui.me.items.PatternEncodingTermScreen;
import appeng.client.gui.widgets.ToggleButton;
import com.lumengrid.recursiveae2patternprovider.Config;
import com.lumengrid.recursiveae2patternprovider.menu.IRecursivePatternMenu;
import com.lumengrid.recursiveae2patternprovider.network.ToggleRecursiveModeC2SPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = CraftingEncodingPanel.class, remap = false)
public abstract class CraftingEncodingPanelMixin {

    @Shadow
    private ToggleButton fluidSubstitutionsBtn;

    @Unique
    private PatternEncodingTermScreen<?> panelScreen;

    @Unique
    private ToggleButton recursiveBtn;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void addRecursiveButton(PatternEncodingTermScreen<?> screen, WidgetContainer widgets, CallbackInfo ci) {
        this.panelScreen = screen;

        this.recursiveBtn = new ToggleButton(
                Icon.S_SUBSTITUTION_ENABLED,
                Icon.S_SUBSTITUTION_DISABLED,
                state -> {
                    if (screen.getMenu() instanceof IRecursivePatternMenu menu) menu.setRecursiveMode(state);
                    if (Minecraft.getInstance().getConnection() != null) {
                        Minecraft.getInstance().getConnection().send(new ToggleRecursiveModeC2SPacket(state));
                    }
                }
        );

        this.recursiveBtn.setHalfSize(true);
        this.recursiveBtn.setDisableBackground(true);

        this.recursiveBtn.setTooltipOn(List.of(
                Component.translatable("gui.recursiveae2patternprovider.button.recursive_on"),
                Component.translatable("gui.recursiveae2patternprovider.button.recursive_desc_on")
        ));
        this.recursiveBtn.setTooltipOff(List.of(
                Component.translatable("gui.recursiveae2patternprovider.button.recursive_off"),
                Component.translatable("gui.recursiveae2patternprovider.button.recursive_desc_off")
        ));
    }

    @Inject(method = "updateBeforeRender", at = @At("TAIL"))
    private void onUpdateBeforeRender(CallbackInfo ci) {
        if (!Config.ENABLE.get() || !Config.ENABLE_ENCODING_GUI_TOGGLE.get()) {
            if (this.recursiveBtn != null) {
                this.recursiveBtn.setVisibility(false);
            }
            return;
        }

        if (this.recursiveBtn != null && this.fluidSubstitutionsBtn != null) {
            this.recursiveBtn.setX(this.fluidSubstitutionsBtn.getX() + 10);
            this.recursiveBtn.setY(this.fluidSubstitutionsBtn.getY());
            this.recursiveBtn.setState(this.panelScreen.getMenu() instanceof IRecursivePatternMenu menu && menu.isRecursiveMode());

            if (this.panelScreen != null) {
                ScreenAccessor accessor = (ScreenAccessor) this.panelScreen;
                if (!accessor.getRenderables().contains(this.recursiveBtn)) {
                    accessor.getRenderables().add(this.recursiveBtn);
                }
                if (!accessor.getChildren().contains(this.recursiveBtn)) {
                    accessor.getChildren().add(this.recursiveBtn);
                }
            }
        }
    }

    @Inject(method = "setVisible", at = @At("TAIL"))
    private void onSetVisible(boolean visible, CallbackInfo ci) {
        if (this.recursiveBtn != null) {
            boolean shouldBeVisible = visible && Config.ENABLE.get() && Config.ENABLE_ENCODING_GUI_TOGGLE.get();
            this.recursiveBtn.setVisibility(shouldBeVisible);
        }
    }
}