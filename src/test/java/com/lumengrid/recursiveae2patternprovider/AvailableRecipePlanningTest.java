package com.lumengrid.recursiveae2patternprovider;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.*;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.*;
import appeng.crafting.CraftingCalculation;
import appeng.crafting.pattern.AEProcessingPattern;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

/** Exercise AE2's real calculation/backtracking with a small in-memory grid. */
@ExtendWith(EphemeralTestServerProvider.class)
class AvailableRecipePlanningTest {
    @Test
    void availableBlocksWorkEvenWhenHatPatternIsFirst(MinecraftServer server) throws Exception {
        var hats = pattern(Items.LEATHER_HELMET, 3, 6);
        var blocks = pattern(Items.REDSTONE_BLOCK, 1, 9);
        var plan = calculate(List.of(hats, blocks), Map.of(Items.REDSTONE_BLOCK, 1L), 9);
        assertFalse(plan.simulation());
        assertEquals(1L, plan.patternTimes().get(blocks));
        assertFalse(plan.patternTimes().containsKey(hats));
    }

    @Test
    void changedStockUsesOtherAlternativeWithoutRegeneratingPatterns(MinecraftServer server) throws Exception {
        var hats = pattern(Items.LEATHER_HELMET, 3, 6);
        var blocks = pattern(Items.REDSTONE_BLOCK, 1, 9);
        var patterns = List.<IPatternDetails>of(blocks, hats);
        var first = calculate(patterns, Map.of(Items.REDSTONE_BLOCK, 1L), 6);
        var second = calculate(patterns, Map.of(Items.LEATHER_HELMET, 3L), 6);
        assertFalse(first.simulation());
        assertFalse(second.simulation());
        assertTrue(first.patternTimes().containsKey(blocks));
        assertTrue(second.patternTimes().containsKey(hats));
        assertFalse(second.patternTimes().containsKey(blocks));
    }

    @Test
    void combinesAvailableSourcesWhenOneIsInsufficient(MinecraftServer server) throws Exception {
        var hats = pattern(Items.LEATHER_HELMET, 3, 6);
        var blocks = pattern(Items.REDSTONE_BLOCK, 1, 9);
        var plan = calculate(List.of(blocks, hats), Map.of(Items.REDSTONE_BLOCK, 1L, Items.LEATHER_HELMET, 3L), 15);
        assertFalse(plan.simulation());
        assertEquals(1L, plan.patternTimes().get(blocks));
        assertEquals(1L, plan.patternTimes().get(hats));
    }

    private IPatternDetails pattern(Item source, int input, int output) {
        var stack = PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(AEItemKey.of(source), input)),
                List.of(new GenericStack(AEItemKey.of(Items.REDSTONE), output)));
        return new AEProcessingPattern(AEItemKey.of(stack));
    }

    private ICraftingPlan calculate(List<IPatternDetails> patterns, Map<Item, Long> stock, long amount) throws Exception {
        var inventory = new KeyCounter();
        stock.forEach((item, count) -> inventory.add(AEItemKey.of(item), count));
        var storage = stub(IStorageService.class, (name, args) -> switch (name) {
            case "getCachedInventory" -> inventory;
            default -> throw new AssertionError(name);
        });
        var crafting = stub(ICraftingService.class, (name, args) -> switch (name) {
            case "getCraftingFor" -> AEItemKey.of(Items.REDSTONE).equals(args[0]) ? patterns : List.of();
            case "canEmitFor" -> false;
            case "getFuzzyCraftable" -> null;
            default -> throw new AssertionError(name);
        });
        var grid = stub(IGrid.class, (name, args) -> switch (name) {
            case "getStorageService" -> storage;
            case "getCraftingService" -> crafting;
            default -> throw new AssertionError(name);
        });
        var node = stub(IGridNode.class, (name, args) -> switch (name) {
            case "getGrid" -> grid;
            default -> throw new AssertionError(name);
        });
        var requester = stub(ICraftingSimulationRequester.class, (name, args) -> switch (name) {
            case "getGridNode" -> node;
            case "getActionSource" -> IActionSource.empty();
            default -> throw new AssertionError(name);
        });
        var calculation = new CraftingCalculation(null, grid, requester,
                new GenericStack(AEItemKey.of(Items.REDSTONE), amount), CalculationStrategy.REPORT_MISSING_ITEMS);
        // Run the synchronous calculation directly; bypass the tick scheduler's
        // initial pause in this small fixture, not any crafting decisions.
        var incTime = CraftingCalculation.class.getDeclaredField("incTime");
        incTime.setAccessible(true);
        incTime.setInt(calculation, 0);
        var compute = CraftingCalculation.class.getDeclaredMethod("computePlan");
        compute.setAccessible(true);
        return (ICraftingPlan) compute.invoke(calculation);
    }

    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> type, BiFunction<String, Object[], Object> calls) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> calls.apply(method.getName(), args));
    }
}
