package com.lumengrid.recursiveae2patternprovider.mixin;

import appeng.menu.guisync.GuiSync;
import com.lumengrid.recursiveae2patternprovider.Config;
import com.lhy.wcwt.menu.WirelessComprehensiveWorkTerminalMenu;
import com.lumengrid.recursiveae2patternprovider.PatternUtil;
import com.lumengrid.recursiveae2patternprovider.RecursiveAE2PatternProvider;
import com.lumengrid.recursiveae2patternprovider.menu.IRecursivePatternMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = WirelessComprehensiveWorkTerminalMenu.class, remap = false)
public abstract class WirelessComprehensiveWorkTerminalMenuMixin implements IRecursivePatternMenu {

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

            PatternUtil.setRecursive(encodedPattern, Config.ENABLE.get() && this.recursiveModeActive);

        } catch (Exception e) {
            RecursiveAE2PatternProvider.LOGGER.error("Failed to apply recursive flag in WCWT: {}", e.getMessage(), e);
        }
    }
}