package com.lumengrid.recursiveae2patternprovider.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.inventories.InternalInventory;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import com.extendedae_plus.content.matrix.supermatrix.SuperAssemblerMatrixCluster;
import com.extendedae_plus.util.crafting.StrictMolecularAssemblerPattern;
import com.lumengrid.recursiveae2patternprovider.Config;
import com.lumengrid.recursiveae2patternprovider.RecursiveAE2PatternProvider;
import com.lumengrid.recursiveae2patternprovider.RecursivePatternGenerator;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * ExtendedAE Plus - Super Assembler Matrix integration.
 *
 * Unlike the other providers, the Super Assembler Matrix does not keep a cached
 * pattern list: the core block entity (an ICraftingProvider) delegates
 * getAvailablePatterns() to the cluster, which decodes the hybrid cores'
 * pattern inventories on every call. We therefore append the generated
 * dependency patterns to the returned list.
 *
 * Only molecular-assembler-compatible patterns are relevant here, because the
 * cluster can only execute those (see SuperAssemblerMatrixCluster#pushPattern).
 * Generated patterns are wrapped with StrictMolecularAssemblerPattern exactly
 * like EAP does for the patterns it decodes, so EAP's own logic (power
 * calculation, scaling) treats them the same way.
 */
@Mixin(value = SuperAssemblerMatrixCluster.class, remap = false)
public abstract class SuperAssemblerMatrixClusterMixin {

    @Inject(method = "getAvailablePatterns", at = @At("RETURN"), cancellable = true)
    private void recursiveae2$appendDependencyPatterns(CallbackInfoReturnable<List<IPatternDetails>> cir) {
        try {
            if (!Config.ENABLE.get()) {
                return;
            }

            int maxDepth = Config.RECURSION_DEPTH.get();
            if (maxDepth == 0) {
                return;
            }

            List<IPatternDetails> original = cir.getReturnValue();
            // Empty = destroyed cluster, no core, no pattern cores or no patterns: nothing to expand.
            if (original == null || original.isEmpty()) {
                return;
            }

            SuperAssemblerMatrixCluster self = (SuperAssemblerMatrixCluster) (Object) this;
            var core = self.getCore();
            if (core == null) {
                return;
            }
            Level level = core.getLevel();
            if (level == null || level.isClientSide()) {
                return;
            }

            // Recursive patterns from every hybrid core, limited to what the matrix can execute.
            List<IPatternDetails> recursivePatterns = new ArrayList<>();
            List<com.lumengrid.recursiveae2patternprovider.RecipeBans> banParts = new ArrayList<>();
            for (InternalInventory inventory : self.getPatternInventories()) {
                for (IPatternDetails details : RecursivePatternGenerator.collectRecursivePatterns(inventory, level)) {
                    if (details instanceof IMolecularAssemblerSupportedPattern) {
                        recursivePatterns.add(details);
                    }
                }
                banParts.add(RecursivePatternGenerator.collectBans(inventory));
            }
            if (recursivePatterns.isEmpty()) {
                return;
            }

            // The original list holds all patterns the user put in the matrix:
            // the generator will not create alternatives for items already covered.
            List<IPatternDetails> generated =
                    RecursivePatternGenerator.generate(recursivePatterns, original, level, maxDepth,
                            com.lumengrid.recursiveae2patternprovider.RecipeBans.merge(banParts));
            if (generated.isEmpty()) {
                return;
            }

            // The original may be immutable (List.of()), always return a new list.
            List<IPatternDetails> result = new ArrayList<>(original.size() + generated.size());
            result.addAll(original);
            for (IPatternDetails pattern : generated) {
                if (pattern instanceof IMolecularAssemblerSupportedPattern supported) {
                    result.add(StrictMolecularAssemblerPattern.wrap(supported));
                }
            }
            cir.setReturnValue(result);

            RecursiveAE2PatternProvider.LOGGER.debug("Generated {} dependency patterns for Super Assembler Matrix",
                    result.size() - original.size());

        } catch (Exception e) {
            RecursiveAE2PatternProvider.LOGGER.error("Failed to inject dependency patterns for Super Assembler Matrix: {}",
                    e.getMessage(), e);
        }
    }
}
