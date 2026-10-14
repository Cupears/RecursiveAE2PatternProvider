package com.lumengrid.recursiveae2patternprovider;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Items;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class StalePatternKeysTest {
    @Test
    void freshPatternsAreLeftAlone(MinecraftServer server) {
        assertFalse(StalePatternKeys.containsStaleKeys(List.of(pattern())));
        assertFalse(StalePatternKeys.containsStaleKeys(List.of()));
    }

    @Test
    void detectsInputWhoseCachedHashRejectsAnIdenticalStack(MinecraftServer server) {
        var pattern = pattern();
        var key = (AEItemKey) pattern.getInputs()[0].getPossibleInputs()[0].what();
        makeStale(key);
        var fresh = AEItemKey.of(key.getReadOnlyStack());
        assertTrue(key.matches(fresh.getReadOnlyStack()));
        assertNotEquals(key, fresh, "AE2 rejects matching stacks when the cached hash has changed");
        assertTrue(StalePatternKeys.containsStaleKeys(List.of(pattern)));
    }

    @Test
    void detectsStaleOutputsToo(MinecraftServer server) {
        var pattern = pattern();
        makeStale((AEItemKey) pattern.getOutputs().getFirst().what());
        assertTrue(StalePatternKeys.containsStaleKeys(List.of(pattern)));
    }

    @Test
    void detectionDoesNotMutateKeysAlreadyUsedInMaps(MinecraftServer server) {
        var pattern = pattern();
        var key = (AEItemKey) pattern.getInputs()[0].getPossibleInputs()[0].what();
        int originalHash = key.hashCode();
        makeStale(key);
        assertTrue(StalePatternKeys.containsStaleKeys(List.of(pattern)));
        assertEquals(originalHash, key.hashCode());
    }

    private static void makeStale(AEItemKey key) {
        // Test-owned stack only: simulate components changing after AE2 captured the hash.
        key.getReadOnlyStack().set(DataComponents.CUSTOM_NAME, Component.literal("Changed after decoding"));
    }

    private static IPatternDetails pattern() {
        return new AEProcessingPattern(AEItemKey.of(PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(AEItemKey.of(Items.CHERRY_LOG), 1)),
                List.of(new GenericStack(AEItemKey.of(Items.CHERRY_PLANKS), 4)))));
    }
}
