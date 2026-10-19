package com.lumengrid.recursiveae2patternprovider;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Collection;
import java.util.Arrays;
import java.util.stream.Collectors;

/** Reverse conversions may consume stored inputs, but must not manufacture them. */
public final class StoredConversionInputs {
    private static final ReferenceQueue<IPatternDetails> COLLECTED = new ReferenceQueue<>();
    private static final Map<Identity, Set<AEKey>> PATTERNS = new HashMap<>();
    private static final class Identity extends WeakReference<IPatternDetails> {
        private final int hash;
        Identity(IPatternDetails pattern) { super(pattern, COLLECTED); hash = System.identityHashCode(pattern); }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) {
            return this == other || other instanceof Identity key && get() != null && get() == key.get();
        }
    }
    private static void clean() {
        for (var key = COLLECTED.poll(); key != null; key = COLLECTED.poll()) PATTERNS.remove(key);
    }
    private StoredConversionInputs() {}
    public static void register(IPatternDetails pattern) {
        register(pattern, Arrays.stream(pattern.getInputs()).flatMap(input -> Arrays.stream(input.getPossibleInputs()))
                .map(stack -> stack.what()).collect(Collectors.toSet()));
    }
    public static synchronized void register(IPatternDetails pattern, Collection<AEKey> inputs) {
        clean();
        if (!inputs.isEmpty()) PATTERNS.put(new Identity(pattern), Set.copyOf(inputs));
    }
    public static synchronized boolean contains(IPatternDetails pattern) { clean(); return PATTERNS.containsKey(new Identity(pattern)); }
    public static synchronized boolean contains(IPatternDetails pattern, AEKey input) {
        clean(); return PATTERNS.getOrDefault(new Identity(pattern), Set.of()).contains(input);
    }
}
