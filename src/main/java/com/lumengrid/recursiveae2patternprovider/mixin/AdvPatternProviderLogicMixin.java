package com.lumengrid.recursiveae2patternprovider.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.util.inv.AppEngInternalInventory;
import com.lumengrid.recursiveae2patternprovider.Config;
import com.lumengrid.recursiveae2patternprovider.RefreshingRecursiveProvider;
import com.lumengrid.recursiveae2patternprovider.StalePatternKeys;
import com.lumengrid.recursiveae2patternprovider.RecursiveAE2PatternProvider;
import com.lumengrid.recursiveae2patternprovider.RecursivePatternGenerator;
import net.minecraft.world.level.Level;
import net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogic;
import net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogicHost;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Set;

@Mixin(AdvPatternProviderLogic.class)
public abstract class AdvPatternProviderLogicMixin implements RefreshingRecursiveProvider {

    @Shadow
    private AdvPatternProviderLogicHost host;

    @Shadow
    @Final
    private IManagedGridNode mainNode;

    @Shadow
    @Final
    private AppEngInternalInventory patternInventory;

    @Shadow
    @Final
    private List<IPatternDetails> patterns;

    @Shadow
    @Final
    private Set<AEKey> patternInputs;

    @Shadow
    public abstract void updatePatterns();

    @Unique
    private boolean recursiveAE2$hasRecursivePatterns;

    @Override
    public boolean recursiveAE2$refreshStalePatterns() {
        if (!Config.ENABLE.get() || !recursiveAE2$hasRecursivePatterns
                || !StalePatternKeys.containsStaleKeys(patterns)) return false;
        updatePatterns();
        RecursiveAE2PatternProvider.LOGGER.info("Refreshed recursive patterns after an item identity changed");
        return true;
    }

    /**
     * Inject dependency pattern generation after the original updatePatterns() completes.
     * All heavy lifting is delegated to RecursivePatternGenerator (shared cached index).
     */
    @Inject(method = "updatePatterns", at = @At("RETURN"), remap = false)
    private void injectDependencyPatterns(CallbackInfo ci) {
        recursiveAE2$hasRecursivePatterns = false;
        try {
            if (!Config.ENABLE.get()) {
                return;
            }

            int maxDepth = Config.RECURSION_DEPTH.get();
            if (maxDepth == 0) {
                return;
            }

            Level level = host.getBlockEntity().getLevel();
            if (level == null) {
                return;
            }

            List<IPatternDetails> recursivePatterns =
                    RecursivePatternGenerator.collectRecursivePatterns(this.patternInventory, level);

            recursiveAE2$hasRecursivePatterns = !recursivePatterns.isEmpty();

            List<IPatternDetails> generated =
                    RecursivePatternGenerator.generate(recursivePatterns, this.patterns, level, maxDepth);

            RecursivePatternGenerator.appendGenerated(generated, this.patterns, this.patternInputs);

            if (!generated.isEmpty()) {
                RecursiveAE2PatternProvider.LOGGER.info("Generated {} dependency patterns for AdvancedAE Pattern Provider",
                        generated.size());
                ICraftingProvider.requestUpdate(this.mainNode);
            }

        } catch (Exception e) {
            RecursiveAE2PatternProvider.LOGGER.error("Failed to inject dependency patterns for AdvancedAE: {}", e.getMessage(), e);
        }
    }
}
