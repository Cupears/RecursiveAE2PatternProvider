package com.lumengrid.recursiveae2patternprovider;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import java.util.*;

/** Describes the selected partial plan, not a minimum-materials solution. */
public final class PlanExplanation {
    public static final int MAX_SOURCES = 256;
    public static final int MAX_PRODUCTS = 4;
    private static final Map<IPatternDetails, Boolean> GENERATED = Collections.synchronizedMap(new WeakHashMap<>());

    private PlanExplanation() {}

    public static void registerGenerated(IPatternDetails pattern) {
        GENERATED.put(pattern, Boolean.TRUE);
    }

    public static Map<AEKey, List<AEKey>> analyze(ICraftingPlan plan) {
        if (!plan.simulation()) return Map.of();
        Map<AEKey, LinkedHashSet<AEKey>> sources = new LinkedHashMap<>();
        for (var use : plan.patternTimes().entrySet()) {
            var pattern = use.getKey();
            if (use.getValue() <= 0 || !GENERATED.containsKey(pattern)) continue;
            for (var input : pattern.getInputs()) {
                for (var possible : input.getPossibleInputs()) {
                    var source = possible.what();
                    if (plan.missingItems().get(source) <= 0) continue;
                    if (!sources.containsKey(source) && sources.size() >= MAX_SOURCES) continue;
                    var products = sources.computeIfAbsent(source, ignored -> new LinkedHashSet<>());
                    for (var output : pattern.getOutputs()) {
                        if (!source.equals(output.what()) && products.size() < MAX_PRODUCTS) products.add(output.what());
                    }
                }
            }
        }
        Map<AEKey, List<AEKey>> result = new LinkedHashMap<>();
        sources.forEach((key, products) -> { if (!products.isEmpty()) result.put(key, List.copyOf(products)); });
        return Collections.unmodifiableMap(result);
    }
}
