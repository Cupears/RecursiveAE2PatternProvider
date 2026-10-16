package com.lumengrid.recursiveae2patternprovider;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;
import com.lumengrid.recursiveae2patternprovider.network.RecipeTreePackets;
import io.netty.buffer.Unpooled;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.*;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class RecipeTreeEditorTest {
    @Test void exclusionsArePerPatternAndPreserveUnrelatedData(MinecraftServer server) {
        var a = pattern(Items.REDSTONE, Items.CLOCK, Set.of());
        var b = a.copy();
        var tag = new net.minecraft.nbt.CompoundTag(); tag.putString("owner", "Cupears");
        a.set(DataComponents.CUSTOM_DATA, CustomData.of(tag)); PatternUtil.setRecursive(a, true);
        PatternRecipeRules.write(a, Set.of("test:hats"));
        assertEquals(Set.of("test:hats"), PatternRecipeRules.read(a));
        assertTrue(PatternRecipeRules.read(b).isEmpty());
        assertTrue(PatternUtil.isRecursive(a));
        assertEquals("Cupears", a.get(DataComponents.CUSTOM_DATA).copyTag().getString("owner").orElseThrow());
        PatternRecipeRules.write(a, Set.of());
        assertTrue(PatternRecipeRules.read(a).isEmpty());
        assertTrue(PatternUtil.isRecursive(a));
    }
    @Test void reencodingKeepsRulesAndOffStillWins(MinecraftServer server) {
        var old = pattern(Items.REDSTONE, Items.CLOCK, Set.of("test:hats"));
        var encoded = pattern(Items.GOLD_INGOT, Items.CLOCK, Set.of());
        PatternRecipeRules.copy(old, encoded); PatternUtil.setRecursive(encoded, false);
        assertEquals(Set.of("test:hats"), PatternRecipeRules.read(encoded));
        assertFalse(PatternUtil.isRecursive(encoded));
        assertTrue(PatternUtil.isRecursive(old));
    }
    @Test void saveRejectsInventedIdsButKeepsPreviouslyHiddenExclusions(MinecraftServer server) {
        assertTrue(PatternRecipeRules.maySave(Set.of("test:hidden", "test:shown"), Set.of("test:hidden"), Set.of("test:shown")));
        assertTrue(PatternRecipeRules.maySave(Set.of(), Set.of("test:hidden"), Set.of("test:shown")));
        assertFalse(PatternRecipeRules.maySave(Set.of("test:forged"), Set.of(), Set.of("test:shown")));
        assertFalse(PatternRecipeRules.maySave(Set.of("bad recipe"), Set.of(), Set.of("bad recipe")));
        var tooMany = new HashSet<String>();
        for (int i = 0; i <= PatternRecipeRules.MAX_EXCLUSIONS; i++) tooMany.add("test:r" + i);
        assertFalse(PatternRecipeRules.maySave(tooMany, Set.of(), tooMany));
    }
    @Test void changedHeldItemSlotOrExpiredSessionCannotSave(MinecraftServer server) {
        var original = pattern(Items.REDSTONE, Items.CLOCK, Set.of());
        assertTrue(RecipeTreeSessions.matchesPattern(original.copy(), original, true, 20));
        assertFalse(RecipeTreeSessions.matchesPattern(original, original, false, 20));
        assertFalse(RecipeTreeSessions.matchesPattern(original, original, true, 20 * 60 * 16));
        var changed = original.copy(); PatternRecipeRules.write(changed, Set.of("test:hats"));
        assertFalse(RecipeTreeSessions.matchesPattern(changed, original, true, 20));
        assertFalse(RecipeTreeSessions.matchesPattern(new ItemStack(Items.DIRT), original, true, 20));
    }
    @Test void bannedRecipeIsAbsentFromGeneratedDependencies(MinecraftServer server) throws Exception {
        withRecipes(server, fixtures(), () -> {
            var root = details(pattern(Items.REDSTONE, Items.CLOCK, Set.of("test:hats")));
            var generated = RecursivePatternGenerator.generate(List.of(root), recipeLevel(server), -1);
            assertTrue(consumes(generated, Items.REDSTONE_BLOCK));
            assertFalse(consumes(generated, Items.LEATHER_HELMET));
        });
    }
    @Test void allRoutesBannedLeavesMaterialForStorageOrManualPatterns(MinecraftServer server) throws Exception {
        withRecipes(server, fixtures(), () -> {
            var root = details(pattern(Items.REDSTONE, Items.CLOCK, Set.of("test:hats", "test:blocks")));
            assertTrue(RecursivePatternGenerator.generate(List.of(root), recipeLevel(server), -1).isEmpty());
        });
    }
    @Test void exclusionsDoNotLeakIntoAnotherRoot(MinecraftServer server) throws Exception {
        withRecipes(server, fixtures(), () -> {
            var blocked = details(pattern(Items.REDSTONE, Items.CLOCK, Set.of("test:hats")));
            var free = details(pattern(Items.REDSTONE, Items.COMPASS, Set.of()));
            for (var order : List.of(List.of(blocked, free), List.of(free, blocked))) {
                var generated = RecursivePatternGenerator.generate(order, recipeLevel(server), -1);
                assertTrue(consumes(generated, Items.LEATHER_HELMET));
                assertTrue(consumes(generated, Items.REDSTONE_BLOCK));
            }
        });
    }
    @Test void nestedDependenciesInheritTheRootExclusions(MinecraftServer server) throws Exception {
        var recipes = new ArrayList<RecipeHolder<?>>(fixtures());
        recipes.add(recipe("test:chest", Items.REDSTONE, Items.CHEST, 1));
        withRecipes(server, recipes, () -> {
            var root = details(pattern(Items.CHEST, Items.CLOCK, Set.of("test:hats")));
            var generated = RecursivePatternGenerator.generate(List.of(root), recipeLevel(server), -1);
            assertTrue(consumes(generated, Items.REDSTONE));
            assertTrue(consumes(generated, Items.REDSTONE_BLOCK));
            assertFalse(consumes(generated, Items.LEATHER_HELMET));
        });
    }
    @Test void previewShowsDisabledRecipesSoTheyCanBeEnabledAgain(MinecraftServer server) throws Exception {
        withRecipes(server, fixtures(), () -> {
            var root = details(pattern(Items.REDSTONE, Items.CLOCK, Set.of("test:hats")));
            var tree = expandedTree(root, recipeLevel(server));
            assertEquals(Set.of("test:hats", "test:blocks"), tree.recipeIds());
            assertTrue(tree.nodes().size() <= RecipeTree.MAX_NODES);
            for (int i = 1; i < tree.nodes().size(); i++) assertTrue(tree.nodes().get(i).parent() < i);
            for (var node : tree.nodes()) if (node.recipe()) assertEquals(9, node.grid().size());
        });
    }
    @Test void treeAndSavePacketsRoundTrip(MinecraftServer server) throws Exception {
        withRecipes(server, fixtures(), () -> {
            var root = details(pattern(Items.REDSTONE, Items.CLOCK, Set.of("test:hats")));
            var tree = expandedTree(root, recipeLevel(server));
            var expected = new RecipeTreePackets.Open(UUID.randomUUID(), tree, Set.of("test:hats"));
            var buf = buffer(server);
            try {
                RecipeTreePackets.Open.CODEC.encode(buf, expected);
                assertTrue(buf.readableBytes() < 100000);
                var decoded = RecipeTreePackets.Open.CODEC.decode(buf);
                assertEquals(expected.session(), decoded.session()); assertEquals(expected.excluded(), decoded.excluded());
                assertEquals(tree.recipeIds(), decoded.tree().recipeIds());
                assertEquals(tree.nodes().size(), decoded.tree().nodes().size());
                for (int i = 0; i < tree.nodes().size(); i++) {
                    var a = tree.nodes().get(i); var b = decoded.tree().nodes().get(i);
                    assertEquals(a.parent(), b.parent()); assertEquals(a.recipeId(), b.recipeId());
                    assertTrue(ItemStack.matches(a.item(), b.item())); assertTrue(ItemStack.listMatches(a.grid(), b.grid()));
                }
                assertEquals(0, buf.readableBytes());
                var save = new RecipeTreePackets.Save(expected.session(), true, expected.excluded());
                RecipeTreePackets.Save.CODEC.encode(buf, save);
                assertEquals(save, RecipeTreePackets.Save.CODEC.decode(buf));
                assertEquals(0, buf.readableBytes());
            } finally { buf.release(); }
        });
    }
    @Test void allAlternativesAreReachableAcrossPagesWithoutATreeCap(MinecraftServer server) throws Exception {
        var recipes = new ArrayList<RecipeHolder<?>>();
        for (int i = 0; i < 200; i++) recipes.add(recipe("test:alternative_" + i, Items.DIRT, Items.REDSTONE, 1));
        withRecipes(server, recipes, () -> {
            var initial = RecipeTree.build(details(pattern(Items.REDSTONE, Items.CLOCK, Set.of())), recipeLevel(server));
            assertEquals(2, initial.nodes().size());
            var nodes = new ArrayList<>(initial.nodes());
            int offset = 0; boolean done;
            do {
                var page = RecipeTree.expand(nodes, 1, offset, recipeLevel(server));
                assertTrue(page.nodes().size() <= RecipeTree.MAX_NODES);
                var packet = new RecipeTreePackets.Branch(UUID.randomUUID(), 1, nodes.size(), page);
                var buf = buffer(server);
                try {
                    RecipeTreePackets.Branch.CODEC.encode(buf, packet);
                    var decoded = RecipeTreePackets.Branch.CODEC.decode(buf);
                    assertEquals(page.nextOffset(), decoded.page().nextOffset());
                    assertEquals(page.done(), decoded.page().done());
                    assertEquals(page.nodes().size(), decoded.page().nodes().size());
                    for (int i = 0; i < page.nodes().size(); i++) {
                        assertEquals(page.nodes().get(i).parent(), decoded.page().nodes().get(i).parent());
                        assertTrue(ItemStack.listMatches(page.nodes().get(i).grid(), decoded.page().nodes().get(i).grid()));
                    }
                    assertEquals(0, buf.readableBytes());
                } finally { buf.release(); }
                nodes.addAll(page.nodes()); offset = page.nextOffset(); done = page.done();
            } while (!done);
            assertEquals(200, new RecipeTree(nodes, false).recipeIds().size());
            assertTrue(nodes.size() > RecipeTree.MAX_NODES);
        });
    }
    @Test void previewHasNoDepthCutoffAndStopsCycles(MinecraftServer server) throws Exception {
        Item[] chain = {Items.REDSTONE, Items.DIRT, Items.STONE, Items.COBBLESTONE, Items.GRAVEL,
                Items.SAND, Items.CLAY, Items.BRICK, Items.IRON_INGOT};
        var recipes = new ArrayList<RecipeHolder<?>>();
        for (int i = 0; i < chain.length - 1; i++) recipes.add(recipe("test:step_" + i, chain[i + 1], chain[i], 1));
        recipes.add(recipe("test:back", Items.REDSTONE, Items.IRON_INGOT, 1));
        withRecipes(server, recipes, () -> {
            var tree = expandedTree(details(pattern(Items.REDSTONE, Items.CLOCK, Set.of())), recipeLevel(server));
            assertFalse(tree.truncated());
            assertEquals(9, tree.recipeIds().size());
            assertTrue(tree.nodes().stream().anyMatch(n -> n.note() == 2));
            assertTrue(tree.nodes().stream().noneMatch(n -> n.note() == 3));
        });
    }
    @Test void furnaceBanRemainsLocalAndAnotherPatternCanUseEssence(MinecraftServer server) throws Exception {
        withRecipes(server, List.of(recipe("test:stone_essence", Items.SLIME_BALL, Items.COBBLESTONE, 8),
                recipe("test:stone", Items.STONE, Items.COBBLESTONE, 1)), () -> {
            var furnace = details(pattern(Items.COBBLESTONE, Items.FURNACE, Set.of("test:stone_essence")));
            var other = details(pattern(Items.COBBLESTONE, Items.DISPENSER, Set.of()));
            var blocked = RecursivePatternGenerator.generate(List.of(furnace), recipeLevel(server), -1);
            var allowed = RecursivePatternGenerator.generate(List.of(other), recipeLevel(server), -1);
            assertFalse(consumes(blocked, Items.SLIME_BALL));
            assertTrue(consumes(blocked, Items.STONE));
            assertTrue(consumes(allowed, Items.SLIME_BALL));
            var combined = RecursivePatternGenerator.generate(List.of(furnace, other), recipeLevel(server), -1);
            assertTrue(consumes(combined, Items.SLIME_BALL)); // Shared AE2 recipe pool; no global ban.
            assertEquals(Set.of("test:stone_essence"), PatternRecipeRules.read(furnace.getDefinition().toStack()));
            assertTrue(PatternRecipeRules.read(other.getDefinition().toStack()).isEmpty());
        });
    }
    private static RecipeTree expandedTree(IPatternDetails root, net.minecraft.world.level.Level level) {
        var initial = RecipeTree.build(root, level); var nodes = new ArrayList<>(initial.nodes());
        for (int id = 1; id < nodes.size(); id++) {
            if (nodes.get(id).recipe() || nodes.get(id).note() == 2) continue;
            int offset = 0; boolean done;
            do { var page = RecipeTree.expand(nodes, id, offset, level); nodes.addAll(page.nodes()); offset = page.nextOffset(); done = page.done(); }
            while (!done);
        }
        return new RecipeTree(List.copyOf(nodes), false);
    }
    @Test void exclusionsSurviveItemSerialization(MinecraftServer server) {
        var original = pattern(Items.REDSTONE, Items.CLOCK, Set.of("test:hats", "test:blocks"));
        var buf = buffer(server);
        try {
            ItemStack.STREAM_CODEC.encode(buf, original);
            var restored = ItemStack.STREAM_CODEC.decode(buf);
            assertTrue(ItemStack.matches(original, restored));
            assertEquals(PatternRecipeRules.read(original), PatternRecipeRules.read(restored));
        } finally { buf.release(); }
    }
    @Test void oversizedOrCyclicTreePacketsAreRejected(MinecraftServer server) {
        var buf = buffer(server);
        try {
            buf.writeUUID(UUID.randomUUID()); buf.writeBoolean(false); buf.writeVarInt(RecipeTree.MAX_NODES + 1);
            assertThrows(IllegalArgumentException.class, () -> RecipeTreePackets.Open.CODEC.decode(buf));
            buf.clear(); buf.writeUUID(UUID.randomUUID()); buf.writeBoolean(false); buf.writeVarInt(1); buf.writeVarInt(0);
            assertThrows(IllegalArgumentException.class, () -> RecipeTreePackets.Open.CODEC.decode(buf));
            buf.clear(); buf.writeUUID(UUID.randomUUID()); buf.writeBoolean(true); buf.writeVarInt(PatternRecipeRules.MAX_EXCLUSIONS + 1);
            assertThrows(IllegalArgumentException.class, () -> RecipeTreePackets.Save.CODEC.decode(buf));
        } finally { buf.release(); }
    }
    private static net.minecraft.server.level.ServerLevel recipeLevel(MinecraftServer server) throws Exception {
        // The ephemeral server has no loaded dimensions. Only its real recipe manager is needed here.
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) field.get(null);
        var level = (net.minecraft.server.level.ServerLevel) unsafe.allocateInstance(net.minecraft.server.level.ServerLevel.class);
        var serverField = net.minecraft.server.level.ServerLevel.class.getDeclaredField("server"); serverField.setAccessible(true); serverField.set(level, server);
        return level;
    }
    private static RegistryFriendlyByteBuf buffer(MinecraftServer server) { return new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess()); }
    private static ItemStack pattern(Item input, Item output, Set<String> rules) {
        var stack = PatternDetailsHelper.encodeProcessingPattern(List.of(new GenericStack(AEItemKey.of(input), 1)),
                List.of(new GenericStack(AEItemKey.of(output), 1)));
        PatternUtil.setRecursive(stack, true); PatternRecipeRules.write(stack, rules); return stack;
    }
    private static IPatternDetails details(ItemStack stack) { return new AEProcessingPattern(AEItemKey.of(stack)); }
    private static boolean consumes(List<IPatternDetails> patterns, Item item) {
        return patterns.stream().flatMap(p -> Arrays.stream(p.getInputs())).flatMap(i -> Arrays.stream(i.getPossibleInputs()))
                .anyMatch(s -> s.what() instanceof AEItemKey key && key.getItem() == item);
    }
    private static List<RecipeHolder<?>> fixtures() { return List.of(recipe("test:hats", Items.LEATHER_HELMET, Items.REDSTONE, 2), recipe("test:blocks", Items.REDSTONE_BLOCK, Items.REDSTONE, 9)); }
    private static RecipeHolder<CraftingRecipe> recipe(String id, Item input, Item output, int count) {
        var recipe = new ShapedRecipe(null, null, ShapedRecipePattern.of(Map.of('I', Ingredient.of(input)), "I"), new ItemStackTemplate(output, count));
        return new RecipeHolder<>(ResourceKey.create(Registries.RECIPE, Identifier.parse(id)), recipe);
    }
    private static void withRecipes(MinecraftServer server, List<RecipeHolder<?>> recipes, CheckedRunnable action) throws Exception {
        var manager = server.getRecipeManager(); var field = RecipeManager.class.getDeclaredField("recipes"); field.setAccessible(true);
        var old = field.get(manager); var cache = RecursivePatternGenerator.class.getDeclaredField("cached"); cache.setAccessible(true);
        try { field.set(manager, RecipeMap.create(recipes)); cache.set(null, null); action.run(); }
        finally { field.set(manager, old); cache.set(null, null); }
    }
    private interface CheckedRunnable { void run() throws Exception; }
}
