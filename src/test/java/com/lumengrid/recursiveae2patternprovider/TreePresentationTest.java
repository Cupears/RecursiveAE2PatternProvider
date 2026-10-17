package com.lumengrid.recursiveae2patternprovider;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.*;
import net.minecraft.world.item.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class TreePresentationTest {
    @Test void colorAndWoodGroupsRetainCountsComponentsAndRecipeShape(MinecraftServer server) {
        assertEquals(TreePresentation.groupKey(item(Items.WHITE_WOOL)), TreePresentation.groupKey(item(Items.RED_WOOL)));
        assertEquals(TreePresentation.groupKey(item(Items.OAK_PLANKS)), TreePresentation.groupKey(item(Items.BIRCH_PLANKS)));
        assertNotEquals(TreePresentation.groupKey(item(Items.WHITE_WOOL)), TreePresentation.groupKey(item(Items.WHITE_CONCRETE)));
        var named = new ItemStack(Items.RED_WOOL); named.set(DataComponents.CUSTOM_NAME, Component.literal("special"));
        assertNotEquals(TreePresentation.groupKey(item(Items.RED_WOOL)), TreePresentation.groupKey(new RecipeTree.Node(0, false, named, "", List.of(), 0)));
        var white = recipe("test:white", Items.WHITE_WOOL, Items.CHEST);
        var red = recipe("test:red", Items.RED_WOOL, Items.CHEST);
        assertEquals(TreePresentation.groupKey(white), TreePresentation.groupKey(red));
        assertNotEquals(TreePresentation.groupKey(white), TreePresentation.groupKey(recipe("test:other", Items.RED_WOOL, Items.BARREL)));
        var shifted = new ArrayList<>(red.grid()); Collections.swap(shifted, 0, 1);
        assertNotEquals(TreePresentation.groupKey(white), TreePresentation.groupKey(new RecipeTree.Node(0, true, red.item(), red.recipeId(), shifted, 0)));
        var doubled = new ItemStack(Items.RED_WOOL, 2);
        assertNotEquals(TreePresentation.groupKey(item(Items.WHITE_WOOL)), TreePresentation.groupKey(new RecipeTree.Node(0, false, doubled, "", List.of(), 0)));
        assertNull(TreePresentation.groupKey(recipe("test:dirt", Items.DIRT, Items.CHEST)));
    }
    @Test void compressedDetectionKeepsOrdinaryStorageBlocksOpen(MinecraftServer server) {
        assertTrue(TreePresentation.compressedId("compressium:cobblestone_1"));
        assertTrue(TreePresentation.compressedId("pack:double_compressed_copper"));
        assertFalse(TreePresentation.compressed(item(Items.COPPER_BLOCK)));
        assertFalse(TreePresentation.compressed(item(Items.GOLD_BLOCK)));
        assertTrue(TreePresentation.compressed(recipe("test:compressed_copper", Items.COPPER_BLOCK, Items.COPPER_INGOT)));
    }
    @Test void wheelZoomKeepsThePointUnderTheCursorAndClamps(MinecraftServer server) {
        var view = TreePresentation.zoomAt(1, 16, 12, 250, 100, 1);
        assertTrue(view.zoom() > 1);
        assertEquals((250 - 16) / 1.0, (250 - view.panX()) / view.zoom(), .00001);
        assertEquals((100 - 12) / 1.0, (100 - view.panY()) / view.zoom(), .00001);
        var back = TreePresentation.zoomAt(view.zoom(), view.panX(), view.panY(), 250, 100, -1);
        assertEquals(1, back.zoom(), .00001); assertEquals(16, back.panX(), .00001);
        assertEquals(.15, TreePresentation.zoomAt(1, 0, 0, 100, 100, -1000).zoom());
        assertEquals(1.5, TreePresentation.zoomAt(1, 0, 0, 100, 100, 1000).zoom());
    }
    @Test void personalDefaultsPersistAndInvalidEditsCannotOverwriteThem(MinecraftServer server, @TempDir Path dir) throws Exception {
        var file = dir.resolve("defaults.json"); assertTrue(RecipeDefaults.load(file).isEmpty());
        RecipeDefaults.save(file, Set.of("test:hats", "test:essence"));
        assertEquals(Set.of("test:hats", "test:essence"), RecipeDefaults.load(file));
        var previous = Files.readString(file);
        assertThrows(java.io.IOException.class, () -> RecipeDefaults.save(file, Set.of("bad id")));
        assertEquals(previous, Files.readString(file));
        RecipeDefaults.save(file, Set.of()); assertTrue(RecipeDefaults.load(file).isEmpty());
        Files.writeString(file, "{broken"); assertThrows(java.io.IOException.class, () -> RecipeDefaults.load(file));
    }
    @Test void newPatternsInheritDefaultsAndReencodingRetainsOverrides(MinecraftServer server) {
        var pattern = PatternDetailsHelper.encodeProcessingPattern(List.of(new GenericStack(AEItemKey.of(Items.DIRT), 1)),
                List.of(new GenericStack(AEItemKey.of(Items.CHEST), 1)));
        PatternRecipeRules.copyOrDefault(ItemStack.EMPTY, pattern, Set.of("test:hats"));
        assertEquals(Set.of("test:hats"), PatternRecipeRules.read(pattern));
        var old = pattern.copy(); PatternRecipeRules.write(old, Set.of());
        PatternRecipeRules.copyOrDefault(old, pattern, Set.of("test:hats"));
        assertTrue(PatternRecipeRules.read(pattern).isEmpty());
        PatternRecipeRules.write(old, Set.of("test:essence"));
        PatternRecipeRules.copyOrDefault(old, pattern, Set.of("test:hats"));
        assertEquals(Set.of("test:essence"), PatternRecipeRules.read(pattern));
    }
    private static RecipeTree.Node item(Item item) { return new RecipeTree.Node(0, false, new ItemStack(item), "", List.of(), 0); }
    private static RecipeTree.Node recipe(String id, Item input, Item output) {
        var grid = new ArrayList<ItemStack>(Collections.nCopies(9, ItemStack.EMPTY)); grid.set(0, new ItemStack(input));
        return new RecipeTree.Node(0, true, new ItemStack(output), id, grid, 0);
    }
}
