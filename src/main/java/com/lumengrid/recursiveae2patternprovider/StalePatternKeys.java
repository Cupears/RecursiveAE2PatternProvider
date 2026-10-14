package com.lumengrid.recursiveae2patternprovider;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Detect keys whose cached identity no longer agrees with their stack's components. */
public final class StalePatternKeys {
    private StalePatternKeys() {}

    public static boolean containsStaleKeys(Collection<IPatternDetails> patterns) {
        Set<AEKey> checked = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var pattern : patterns) {
            for (var input : pattern.getInputs()) {
                for (var stack : input.getPossibleInputs()) {
                    if (isStale(stack.what(), checked)) return true;
                }
            }
            for (var stack : pattern.getOutputs()) {
                if (isStale(stack.what(), checked)) return true;
            }
        }
        return false;
    }

    private static boolean isStale(AEKey key, Set<AEKey> checked) {
        return key instanceof AEItemKey itemKey && checked.add(key)
                && key.hashCode() != ItemStack.hashItemAndComponents(itemKey.getReadOnlyStack());
    }
}
