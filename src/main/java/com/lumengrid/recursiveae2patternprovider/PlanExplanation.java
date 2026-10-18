package com.lumengrid.recursiveae2patternprovider;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import java.util.*;

/** Describes the selected recipe path, not a minimum-materials solution. */
public final class PlanExplanation {
    public static final int MAX_SOURCES = 256;
    public static final int MAX_PRODUCTS = 4;
    private static final Map<IPatternDetails, Boolean> GENERATED = Collections.synchronizedMap(new WeakHashMap<>());

    private PlanExplanation() {}

    public static void registerGenerated(IPatternDetails pattern) {
        GENERATED.put(pattern, Boolean.TRUE);
    }

    public static Map<AEKey, List<AEKey>> analyze(ICraftingPlan plan) {
        Map<AEKey, LinkedHashSet<AEKey>> sources = new LinkedHashMap<>();
        Set<AEKey> crafted = new HashSet<>();
        for (var use : plan.patternTimes().entrySet()) {
            if (use.getValue() > 0) for (var output : use.getKey().getOutputs()) crafted.add(output.what());
        }
        // Keep missing-source explanations first when the packet reaches its bound.
        for (boolean missingPass : new boolean[]{true, false}) {
            for (var use : plan.patternTimes().entrySet()) {
                var pattern = use.getKey();
                if (use.getValue() <= 0 || (!GENERATED.containsKey(pattern)
                        && !PatternUtil.isRecursive(pattern.getDefinition().toStack()))) continue;
                for (var input : pattern.getInputs()) {
                    // A single missing alternative is identifiable only when no other
                    // accepted alternative occurs anywhere in the selected plan.
                    if (input.getPossibleInputs().length != 1) {
                        Set<AEKey> observed = new HashSet<>();
                        for (var possible : input.getPossibleInputs()) {
                            var key = possible.what();
                            if (plan.missingItems().get(key) > 0 || plan.usedItems().get(key) > 0
                                    || crafted.contains(key) || plan.emittedItems().get(key) > 0) observed.add(key);
                        }
                        if (observed.size() != 1 || plan.missingItems().get(observed.iterator().next()) <= 0) continue;
                    }
                    for (var possible : input.getPossibleInputs()) {
                        var source = possible.what();
                        boolean missing = plan.missingItems().get(source) > 0;
                        if (missing != missingPass) continue;
                        if (!missing && plan.usedItems().get(source) <= 0 && !crafted.contains(source)
                                && plan.emittedItems().get(source) <= 0) continue;
                        if (!sources.containsKey(source) && sources.size() >= MAX_SOURCES) continue;
                        var products = sources.computeIfAbsent(source, ignored -> new LinkedHashSet<>());
                        for (var output : pattern.getOutputs()) {
                            if (!source.equals(output.what()) && products.size() < MAX_PRODUCTS) products.add(output.what());
                        }
                    }
                }
            }
        }
        Map<AEKey, List<AEKey>> result = new LinkedHashMap<>();
        sources.forEach((key, products) -> { if (!products.isEmpty()) result.put(key, List.copyOf(products)); });
        return Collections.unmodifiableMap(result);
    }
}
