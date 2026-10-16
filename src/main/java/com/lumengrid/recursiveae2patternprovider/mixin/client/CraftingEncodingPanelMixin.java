package com.lumengrid.recursiveae2patternprovider.mixin.client;

import appeng.util.Icon;
import appeng.client.gui.WidgetContainer;
import appeng.client.gui.me.items.CraftingEncodingPanel;
import appeng.client.gui.me.items.PatternEncodingTermScreen;
import appeng.client.gui.widgets.ToggleButton;
import com.lumengrid.recursiveae2patternprovider.Config;
import com.lumengrid.recursiveae2patternprovider.client.CraftingTreeScreen;
import com.lumengrid.recursiveae2patternprovider.client.InspectButton;
import com.lumengrid.recursiveae2patternprovider.menu.IInspectablePatternMenu;
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

    @Unique
    private InspectButton inspectBtn;

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

        this.inspectBtn = new InspectButton(btn -> {
            if (screen.getMenu() instanceof IInspectablePatternMenu) {
                CraftingTreeScreen.requestAndOpen(screen.getMenu().containerId);
            }
        });
        this.inspectBtn.setDisableBackground(true);
    }

    @Inject(method = "updateBeforeRender", at = @At("TAIL"))
    private void onUpdateBeforeRender(CallbackInfo ci) {
        if (!Config.ENABLE.get() || !Config.ENABLE_ENCODING_GUI_TOGGLE.get()) {
            if (this.recursiveBtn != null) {
                this.recursiveBtn.setVisibility(false);
            }
            if (this.inspectBtn != null) {
                this.inspectBtn.setVisibility(false);
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

        if (this.inspectBtn != null && this.recursiveBtn != null) {
            // Shift the icon up and left so it lines up with the button's hit area.
            this.inspectBtn.setX(this.recursiveBtn.getX() + 8);
            this.inspectBtn.setY(this.recursiveBtn.getY() - 2);
            // Only offer inspection once there is a recursive pattern in the encoded slot to walk.
            boolean hasPattern = this.panelScreen.getMenu() instanceof IInspectablePatternMenu menu
                    && !menu.recursiveAE2$getInspectPattern().isEmpty();
            this.inspectBtn.setVisibility(hasPattern);

            if (this.panelScreen != null) {
                ScreenAccessor accessor = (ScreenAccessor) this.panelScreen;
                if (!accessor.getRenderables().contains(this.inspectBtn)) {
                    accessor.getRenderables().add(this.inspectBtn);
                }
                if (!accessor.getChildren().contains(this.inspectBtn)) {
                    accessor.getChildren().add(this.inspectBtn);
                }
            }
        }
    }

    @Inject(method = "setVisible", at = @At("TAIL"))
    private void onSetVisible(boolean visible, CallbackInfo ci) {
        boolean enabled = Config.ENABLE.get() && Config.ENABLE_ENCODING_GUI_TOGGLE.get();
        if (this.recursiveBtn != null) {
            this.recursiveBtn.setVisibility(visible && enabled);
        }
        if (this.inspectBtn != null && !(visible && enabled)) {
            this.inspectBtn.setVisibility(false);
        }
    }
}