package com.lumengrid.recursiveae2patternprovider.mixin;

import appeng.menu.guisync.GuiSync;
import com.lumengrid.recursiveae2patternprovider.Config;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.parts.encoding.EncodingMode;
import com.lumengrid.recursiveae2patternprovider.PatternUtil;
import com.lumengrid.recursiveae2patternprovider.RecursiveAE2PatternProvider;
import com.lumengrid.recursiveae2patternprovider.menu.IRecursivePatternMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(value = PatternEncodingTermMenu.class, remap = false)
public abstract class PatternEncodingTermMenuMixin implements IRecursivePatternMenu {

    @Shadow
    public abstract EncodingMode getMode();

    @Unique
    @GuiSync(30000)
    public boolean recursiveModeActive = Config.ENABLE.get() && Config.DEFAULT_RECURSIVE_MODE.get();

    @Override
    public void setRecursiveMode(boolean active) {
        this.recursiveModeActive = active;
    }

    @Override
    public boolean isRecursiveMode() {
        return this.recursiveModeActive;
    }

    @Inject(method = "encode", at = @At(value = "INVOKE", target = "Lappeng/menu/slot/RestrictedInputSlot;set(Lnet/minecraft/world/item/ItemStack;)V"), locals = LocalCapture.CAPTURE_FAILHARD)
    private void preserveOrApplyRecursiveFlag(CallbackInfo ci, ItemStack encodedPattern) {
        try {
            if (encodedPattern == null || encodedPattern.isEmpty()) {
                return;
            }

            if (this.getMode() != EncodingMode.CRAFTING) {
                return;
            }

            PatternUtil.setRecursive(encodedPattern, Config.ENABLE.get() && this.recursiveModeActive);

        } catch (Exception e) {
            RecursiveAE2PatternProvider.LOGGER.error("Failed to apply recursive flag: {}", e.getMessage(), e);
        }
    }
}