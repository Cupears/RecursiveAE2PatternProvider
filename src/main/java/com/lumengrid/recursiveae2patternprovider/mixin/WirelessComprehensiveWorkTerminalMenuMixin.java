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
public abstract class WirelessComprehensiveWorkTerminalMenuMixin implements IRecursivePatternMenu, com.lumengrid.recursiveae2patternprovider.menu.IRecipeTreeMenu {
    @org.spongepowered.asm.mixin.Shadow
    private appeng.menu.slot.RestrictedInputSlot encodedPatternSlot;


    @Override
    public net.minecraft.world.inventory.Slot recursiveAE2$treeSlot() { return encodedPatternSlot; }

    @Unique
    private java.util.Set<String> recursiveAE2$personalDefaults = java.util.Set.of();
    @Override public void recursiveAE2$setDefaults(java.util.Set<String> defaults) { recursiveAE2$personalDefaults = java.util.Set.copyOf(defaults); }
    @Override public java.util.Set<String> recursiveAE2$defaults() { return recursiveAE2$personalDefaults; }

    @Unique
    @GuiSync(com.lumengrid.recursiveae2patternprovider.menu.RecursiveMenuSync.RECURSIVE_MODE)
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

            if (encodedPatternSlot != null) com.lumengrid.recursiveae2patternprovider.PatternRecipeRules.copyOrDefault(
                    encodedPatternSlot.getItem(), encodedPattern, recursiveAE2$personalDefaults);
            PatternUtil.setRecursive(encodedPattern, Config.ENABLE.get() && this.recursiveModeActive);

        } catch (Exception e) {
            RecursiveAE2PatternProvider.LOGGER.error("Failed to apply recursive flag in WCWT: {}", e.getMessage(), e);
        }
    }
}