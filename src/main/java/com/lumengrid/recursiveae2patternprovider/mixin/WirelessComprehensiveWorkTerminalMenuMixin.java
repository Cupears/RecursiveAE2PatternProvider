package com.lumengrid.recursiveae2patternprovider.mixin;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.menu.guisync.GuiSync;
import com.lumengrid.recursiveae2patternprovider.Config;
import appeng.menu.slot.RestrictedInputSlot;
import com.lhy.wcwt.menu.WirelessComprehensiveWorkTerminalMenu;
import com.lumengrid.recursiveae2patternprovider.PatternUtil;
import com.lumengrid.recursiveae2patternprovider.RecursiveAE2PatternProvider;
import com.lumengrid.recursiveae2patternprovider.menu.IRecursivePatternMenu;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = WirelessComprehensiveWorkTerminalMenu.class, remap = false)
public abstract class WirelessComprehensiveWorkTerminalMenuMixin implements IRecursivePatternMenu {

    @Shadow
    private RestrictedInputSlot encodedPatternSlot;

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

    @Inject(method = "encodeCraftingPattern", at = @At("RETURN"))
    private void applyRecursiveFlagOnCraftingPattern(CallbackInfoReturnable<ItemStack> cir) {
        try {
            ItemStack encodedPattern = cir.getReturnValue();
            if (encodedPattern == null || encodedPattern.isEmpty()) {
                return;
            }

            boolean shouldBeRecursive = this.recursiveModeActive;

            // Preserva la flag ricorsiva se il pattern già inserito nel terminale era ricorsivo
            if (!shouldBeRecursive && this.encodedPatternSlot != null && !this.encodedPatternSlot.getItem().isEmpty()) {
                ItemStack existingPattern = this.encodedPatternSlot.getItem();
                if (PatternDetailsHelper.isEncodedPattern(existingPattern) && PatternUtil.isRecursive(existingPattern)) {
                    shouldBeRecursive = true;
                }
            }

            if (shouldBeRecursive) {
                var existingData = encodedPattern.get(DataComponents.CUSTOM_DATA);
                CompoundTag customData = existingData != null ? existingData.copyTag() : new CompoundTag();
                customData.putBoolean("recursive", true);
                encodedPattern.set(DataComponents.CUSTOM_DATA, CustomData.of(customData));
                RecursiveAE2PatternProvider.LOGGER.debug("Applied recursive flag in WCWT crafting pattern encoding");
            }

        } catch (Exception e) {
            RecursiveAE2PatternProvider.LOGGER.error("Failed to apply recursive flag in WCWT: {}", e.getMessage(), e);
        }
    }
}