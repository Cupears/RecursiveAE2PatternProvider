package com.lumengrid.recursiveae2patternprovider.mixin;

import appeng.menu.guisync.GuiSync;
import appeng.menu.slot.RestrictedInputSlot;
import com.lumengrid.recursiveae2patternprovider.Config;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.parts.encoding.EncodingMode;
import com.lumengrid.recursiveae2patternprovider.PatternUtil;
import com.lumengrid.recursiveae2patternprovider.RecipeBans;
import com.lumengrid.recursiveae2patternprovider.RecursiveAE2PatternProvider;
import com.lumengrid.recursiveae2patternprovider.menu.IInspectablePatternMenu;
import com.lumengrid.recursiveae2patternprovider.menu.IRecursivePatternMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(value = PatternEncodingTermMenu.class, remap = false)
public abstract class PatternEncodingTermMenuMixin implements IRecursivePatternMenu, IInspectablePatternMenu {

    @Shadow
    public abstract EncodingMode getMode();

    @Shadow
    @Final
    private RestrictedInputSlot encodedPatternSlot;

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

    @Override
    public ItemStack recursiveAE2$getInspectPattern() {
        ItemStack stack = this.encodedPatternSlot.getItem();
        return PatternUtil.isRecursive(stack) ? stack : ItemStack.EMPTY;
    }

    @Override
    public void recursiveAE2$setInspectPattern(ItemStack stack) {
        this.encodedPatternSlot.set(stack);
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

            // Re-encoding rebuilds the pattern from the grid; carry over any bans the
            // player set on the pattern previously in the slot (mirrors the recursive flag).
            RecipeBans previousBans = PatternUtil.getBans(this.encodedPatternSlot.getItem());

            PatternUtil.setRecursive(encodedPattern, Config.ENABLE.get() && this.recursiveModeActive);

            if (Config.ENABLE.get() && this.recursiveModeActive && !previousBans.isEmpty()) {
                PatternUtil.setBans(encodedPattern, previousBans);
            }

        } catch (Exception e) {
            RecursiveAE2PatternProvider.LOGGER.error("Failed to apply recursive flag: {}", e.getMessage(), e);
        }
    }
}