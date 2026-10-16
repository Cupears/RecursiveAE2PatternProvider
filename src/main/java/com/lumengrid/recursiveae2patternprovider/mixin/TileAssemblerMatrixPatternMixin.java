package com.lumengrid.recursiveae2patternprovider.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixPattern;
import com.lumengrid.recursiveae2patternprovider.Config;
import com.lumengrid.recursiveae2patternprovider.RecursiveAE2PatternProvider;
import com.lumengrid.recursiveae2patternprovider.RecursivePatternGenerator;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = TileAssemblerMatrixPattern.class, remap = false)
public class TileAssemblerMatrixPatternMixin {

    @Shadow
    @Final
    private List<IPatternDetails> patterns;

    /**
     * Inject dependency pattern generation after the original updatePatterns() completes.
     * All heavy lifting is delegated to RecursivePatternGenerator (shared cached index).
     */
    @Inject(method = "updatePatterns", at = @At("RETURN"), remap = false)
    private void injectDependencyPatterns(CallbackInfo ci) {
        try {
            if (!Config.ENABLE.get()) {
                return;
            }

            int maxDepth = Config.RECURSION_DEPTH.get();
            if (maxDepth == 0) {
                return;
            }

            TileAssemblerMatrixPattern self = (TileAssemblerMatrixPattern) (Object) this;

            Level level = self.getLevel();
            if (level == null) {
                return;
            }

            List<IPatternDetails> recursivePatterns =
                    RecursivePatternGenerator.collectRecursivePatterns(self.getPatternInventory(), level);

            var bans = RecursivePatternGenerator.collectBans(self.getPatternInventory());
            List<IPatternDetails> generated =
                    RecursivePatternGenerator.generate(recursivePatterns, this.patterns, level, maxDepth, bans);

            RecursivePatternGenerator.appendGenerated(generated, this.patterns, null);

            if (!generated.isEmpty()) {
                RecursiveAE2PatternProvider.LOGGER.info("Generated {} dependency patterns for Assembler Matrix Pattern",
                        generated.size());
                ICraftingProvider.requestUpdate(self.getMainNode());
            }

        } catch (Exception e) {
            RecursiveAE2PatternProvider.LOGGER.error("Failed to inject dependency patterns for Assembler Matrix: {}", e.getMessage(), e);
        }
    }
}
