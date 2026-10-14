package com.lumengrid.recursiveae2patternprovider.mixin.client;

import appeng.util.Icon;
import appeng.client.gui.widgets.ToggleButton;
import appeng.parts.encoding.EncodingMode;
import com.lhy.wcwt.client.WirelessComprehensiveWorkTerminalScreen;
import com.lumengrid.recursiveae2patternprovider.Config;
import com.lumengrid.recursiveae2patternprovider.menu.IRecursivePatternMenu;
import com.lumengrid.recursiveae2patternprovider.network.ToggleRecursiveModeC2SPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = WirelessComprehensiveWorkTerminalScreen.class, remap = false)
public abstract class WirelessComprehensiveWorkTerminalScreenMixin extends Screen {

    protected WirelessComprehensiveWorkTerminalScreenMixin(Component title) {
        super(title);
    }

    @Shadow
    private ToggleButton patternFluidSubstitutionButton;

    @Shadow
    private EncodingMode patternEncodingMode;

    @Unique
    private ToggleButton recursiveBtn;

    @Inject(method = "init", at = @At("TAIL"))
    private void addRecursiveButtonWCWT(CallbackInfo ci) {
        this.recursiveBtn = new ToggleButton(
                Icon.S_SUBSTITUTION_ENABLED,
                Icon.S_SUBSTITUTION_DISABLED,
                state -> {
                    var player = Minecraft.getInstance().player;
                    if (player != null && player.containerMenu instanceof IRecursivePatternMenu menu) menu.setRecursiveMode(state);
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

        this.addRenderableWidget(this.recursiveBtn);
    }

    @Inject(method = "updatePatternEncodingOptionButtons", at = @At("TAIL"))
    private void onUpdatePatternEncodingOptionButtonsWCWT(CallbackInfo ci) {
        if (!Config.ENABLE.get() || !Config.ENABLE_ENCODING_GUI_TOGGLE.get()) {
            if (this.recursiveBtn != null) {
                this.recursiveBtn.setVisibility(false);
            }
            return;
        }

        if (this.recursiveBtn != null && this.patternFluidSubstitutionButton != null) {
            boolean showRecursive = this.patternEncodingMode == EncodingMode.CRAFTING;
            this.recursiveBtn.visible = showRecursive;
            this.recursiveBtn.active = showRecursive;

            this.recursiveBtn.setX(this.patternFluidSubstitutionButton.getX() + 10);
            this.recursiveBtn.setY(this.patternFluidSubstitutionButton.getY());
            var player = Minecraft.getInstance().player;
            this.recursiveBtn.setState(player != null && player.containerMenu instanceof IRecursivePatternMenu menu && menu.isRecursiveMode());
        }
    }
}