package com.lumengrid.recursiveae2patternprovider;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import java.util.*;

/** Describes the selected recipe path, not a minimum-materials solution. */
public final class PlanExplanation {
    public static final int MAX_SOURCES = 256;
    public static final int MAX_PRODUCTS = 4;
    private static final Map<IPatternDetails, Set<AEKey>> GENERATED = Collections.synchronizedMap(new WeakHashMap<>());

    private PlanExplanation() {}

    public static void registerGenerated(IPatternDetails pattern) {
        registerGenerated(pattern, List.of());
    }

    public static void registerGenerated(IPatternDetails pattern, Collection<AEKey> roots) {
        synchronized (GENERATED) {
            var owners = new LinkedHashSet<>(GENERATED.getOrDefault(pattern, Set.of()));
            for (var root : roots) if (owners.size() < MAX_PRODUCTS) owners.add(root);
            GENERATED.put(pattern, Collections.unmodifiableSet(owners));
        }
    }

    /** Stored pattern outputs that generated each source's selected dependency. */
    public static Map<AEKey, List<AEKey>> origins(ICraftingPlan plan, Map<AEKey, List<AEKey>> sources) {
        // Follow the selected plan to its stored recursive roots first. A shared
        // generated recipe can be used by a different root than created it first.
        Map<AEKey, Set<AEKey>> consumers = new HashMap<>();
        Set<AEKey> selectedRoots = new HashSet<>();
        for (var use : plan.patternTimes().entrySet()) {
            if (use.getValue() <= 0) continue;
            var pattern = use.getKey();
            for (var output : pattern.getOutputs()) {
                if (PatternUtil.isRecursive(pattern.getDefinition().toStack())
                        || !GENERATED.containsKey(pattern) && output.what().equals(plan.finalOutput().what())) selectedRoots.add(output.what());
                for (var input : pattern.getInputs()) {
                    if (input.getPossibleInputs().length == 1) {
                        consumers.computeIfAbsent(input.getPossibleInputs()[0].what(), ignored -> new LinkedHashSet<>()).add(output.what());
                    }
                }
            }
        }
        Map<AEKey, LinkedHashSet<AEKey>> owners = new LinkedHashMap<>();
        for (var use : plan.patternTimes().entrySet()) {
            if (use.getValue() <= 0) continue;
            var pattern = use.getKey();
            boolean storedPattern = !GENERATED.containsKey(pattern);
            Set<AEKey> roots = GENERATED.getOrDefault(pattern, Set.of());
            if (storedPattern || PatternUtil.isRecursive(pattern.getDefinition().toStack())) {
                roots = new LinkedHashSet<>(roots);
                for (var output : pattern.getOutputs()) roots.add(output.what());
            }
            if (!storedPattern && !selectedRoots.isEmpty()) {
                roots = new LinkedHashSet<>(roots);
                roots.retainAll(selectedRoots);
            }
            if (roots.isEmpty()) continue;
            for (var input : pattern.getInputs()) for (var possible : input.getPossibleInputs()) {
                var source = possible.what();
                var products = sources.get(source);
                if (products == null || pattern.getOutputs().stream().noneMatch(output -> products.contains(output.what()))) continue;
                var result = owners.computeIfAbsent(source, ignored -> new LinkedHashSet<>());
                for (var root : roots) if (result.size() < MAX_PRODUCTS) result.add(root);
            }
        }
        for (var source : sources.keySet()) {
            Set<AEKey> visited = new HashSet<>();
            var pending = new ArrayDeque<AEKey>();
            pending.add(source);
            LinkedHashSet<AEKey> roots = new LinkedHashSet<>();
            while (!pending.isEmpty()) {
                var key = pending.removeFirst();
                if (!visited.add(key)) continue;
                if (selectedRoots.contains(key) && !source.equals(key) && roots.size() < MAX_PRODUCTS) roots.add(key);
                pending.addAll(consumers.getOrDefault(key, Set.of()));
            }
            if (!roots.isEmpty()) owners.put(source, roots);
        }
        Map<AEKey, List<AEKey>> result = new LinkedHashMap<>();
        owners.forEach((source, roots) -> result.put(source, List.copyOf(roots)));
        return Collections.unmodifiableMap(result);
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
                if (use.getValue() <= 0) continue;
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
