package com.lumengrid.recursiveae2patternprovider.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import com.lumengrid.recursiveae2patternprovider.Config;
import com.lumengrid.recursiveae2patternprovider.RefreshingRecursiveProvider;
import com.lumengrid.recursiveae2patternprovider.StalePatternKeys;
import com.lumengrid.recursiveae2patternprovider.RecursiveAE2PatternProvider;
import com.lumengrid.recursiveae2patternprovider.RecursivePatternGenerator;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Set;

@Mixin(PatternProviderLogic.class)
public abstract class PatternProviderLogicMixin implements RefreshingRecursiveProvider {

    @Shadow
    protected PatternProviderLogicHost host;

    @Shadow
    @Final
    private IManagedGridNode mainNode;

    @Shadow
    @Final
    private List<IPatternDetails> patterns;

    @Shadow
    @Final
    private Set<AEKey> patternInputs;

    @Shadow
    public abstract InternalInventory getPatternInv();

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
     * All heavy lifting is delegated to RecursivePatternGenerator, which uses a
     * shared, cached output-item -> recipes index instead of scanning the whole
     * recipe book per item per provider.
     */
    @Inject(method = "updatePatterns", at = @At("RETURN"))
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
                    RecursivePatternGenerator.collectRecursivePatterns(getPatternInv(), level);

            recursiveAE2$hasRecursivePatterns = !recursivePatterns.isEmpty();

            List<IPatternDetails> generated =
                    RecursivePatternGenerator.generate(recursivePatterns, this.patterns, level, maxDepth);

            RecursivePatternGenerator.appendGenerated(generated, this.patterns, this.patternInputs);

            if (!generated.isEmpty()) {
                RecursiveAE2PatternProvider.LOGGER.info("Generated {} dependency patterns for Pattern Provider",
                        generated.size());
                ICraftingProvider.requestUpdate(this.mainNode);
            }

        } catch (Exception e) {
            RecursiveAE2PatternProvider.LOGGER.error("Failed to inject dependency patterns: {}", e.getMessage(), e);
        }
    }
}
