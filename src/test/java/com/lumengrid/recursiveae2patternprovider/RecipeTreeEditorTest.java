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
    @Test void shapelessOverlappingIngredientsUseDistinctRoles(MinecraftServer server) {
        var recipe = new ShapelessRecipe(null, null, new ItemStackTemplate(Items.CLOCK), List.of(
                Ingredient.of(Items.IRON_INGOT, Items.COPPER_INGOT), Ingredient.of(Items.COPPER_INGOT)));
        var slots = Arrays.asList(new GenericStack(AEItemKey.of(Items.COPPER_INGOT), 1), null,
                new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1));
        var copper = ShapelessIngredients.at(recipe, slots, 0).orElseThrow();
        var iron = ShapelessIngredients.at(recipe, slots, 2).orElseThrow();
        assertEquals(1, copper.items().count()); assertTrue(copper.test(new ItemStack(Items.COPPER_INGOT)));
        assertEquals(2, iron.items().count()); assertTrue(iron.test(new ItemStack(Items.IRON_INGOT)));
        assertTrue(ShapelessIngredients.at(recipe, slots, 1).isEmpty());
    }
    @Test void orangeWoolShapelessFindsOtherColorsEvenWithDyeTransferredFirst(MinecraftServer server) throws Exception {
        var wool = Ingredient.of(net.minecraft.core.registries.BuiltInRegistries.ITEM.getOrThrow(net.minecraft.tags.ItemTags.WOOL));
        var recipe = new ShapelessRecipe(null, null, new ItemStackTemplate(Items.ORANGE_WOOL), List.of(wool, Ingredient.of(Items.ORANGE_DYE)));
        var holder = new RecipeHolder<CraftingRecipe>(ResourceKey.create(Registries.RECIPE, Identifier.parse("test:orange_wool")), recipe);
        withRecipes(server, List.of(holder, recipe("test:white_wool", Items.STRING, Items.WHITE_WOOL, 1)), () -> {
            var level = recipeLevel(server);
            var input = new ItemStack[9]; Arrays.fill(input, ItemStack.EMPTY);
            input[0] = new ItemStack(Items.ORANGE_DYE); input[1] = new ItemStack(Items.ORANGE_WOOL);
            assertTrue(recipe.matches(CraftingInput.of(3, 3, Arrays.asList(input)), level));
            var encoded = PatternDetailsHelper.encodeCraftingPattern(holder, input, new ItemStack(Items.ORANGE_WOOL), true, false);
            PatternUtil.setRecursive(encoded, true);
            var root = new appeng.crafting.pattern.AECraftingPattern(AEItemKey.of(encoded), level);
            assertTrue(root.isItemValid(1, AEItemKey.of(Items.WHITE_WOOL), level));
            assertFalse(root.isItemValid(1, AEItemKey.of(Items.ORANGE_DYE), level));
            var tree = RecipeTree.build(root, level);
            var woolNodes = tree.nodes().stream().filter(n -> n.choice() == 1).toList();
            assertTrue(woolNodes.size() >= 15, "Shapeless lookup dropped wool substitutes");
            assertFalse(woolNodes.getFirst().item().is(Items.ORANGE_WOOL));
            assertTrue(woolNodes.stream().anyMatch(n -> n.item().is(Items.WHITE_WOOL) && n.note() == 0));
            assertTrue(woolNodes.stream().filter(n -> n.item().is(Items.ORANGE_WOOL)).allMatch(n -> n.note() == 2));
            assertTrue(tree.nodes().stream().filter(n -> n.choice() == 0).allMatch(n -> n.item().is(Items.ORANGE_DYE)));
            var generated = RecursivePatternGenerator.generate(List.of(root), level, -1);
            assertTrue(consumes(generated, Items.STRING), "Recursive planning did not discover the white wool route");
            var exactEncoded = PatternDetailsHelper.encodeCraftingPattern(holder, input, new ItemStack(Items.ORANGE_WOOL), false, false);
            var exact = RecipeTree.build(new appeng.crafting.pattern.AECraftingPattern(AEItemKey.of(exactEncoded), level), level);
            assertEquals(3, exact.nodes().size()); // Exact mode must not silently become substitutable.
        });
    }
    @Test void nativeWoolTagChoicesStayGroupedAcrossPrimaryAndNetworkChanges(MinecraftServer server) throws Exception {
        var wool = Ingredient.of(net.minecraft.core.registries.BuiltInRegistries.ITEM.getOrThrow(net.minecraft.tags.ItemTags.WOOL));
        int accepted = (int)wool.items().count(); assertTrue(accepted > 1);
        var recipe = new ShapedRecipe(null, null, ShapedRecipePattern.of(Map.of('W', wool), "WW"), new ItemStackTemplate(Items.CHEST));
        var holder = new RecipeHolder<CraftingRecipe>(ResourceKey.create(Registries.RECIPE, Identifier.parse("test:wool_choices")), recipe);
        withRecipes(server, List.of(holder), () -> {
            var level = recipeLevel(server);
            Set<Item> previous = null;
            for (var primary : List.of(Items.RED_WOOL, Items.WHITE_WOOL)) {
                var input = new ItemStack[9]; Arrays.fill(input, ItemStack.EMPTY);
                input[0] = new ItemStack(primary); input[1] = new ItemStack(primary);
                var encoded = PatternDetailsHelper.encodeCraftingPattern(holder, input, new ItemStack(Items.CHEST), true, false);
                PatternUtil.setRecursive(encoded, true);
                var root = new appeng.crafting.pattern.AECraftingPattern(AEItemKey.of(encoded), level);
                var tree = RecipeTree.build(root, level);
                var choices = tree.nodes().subList(1, tree.nodes().size());
                assertEquals(accepted, choices.size());
                assertEquals(1, choices.stream().map(TreePresentation::groupKey).distinct().count());
                assertTrue(choices.stream().allMatch(n -> n.item().getCount() == 2));
                var items = choices.stream().map(n -> n.item().getItem()).collect(java.util.stream.Collectors.toSet());
                if (previous != null) assertEquals(previous, items); previous = items;
                var manual = details(pattern(Items.STRING, Items.WHITE_WOOL, Set.of()));
                RecursivePatternGenerator.generate(List.of(root, manual), level, -1);
                assertEquals(items, RecipeTree.build(root, level).nodes().stream().skip(1).map(n -> n.item().getItem()).collect(java.util.stream.Collectors.toSet()));
                RecursivePatternGenerator.generate(List.of(root), level, -1);
                assertEquals(accepted + 1, RecipeTree.build(root, level).nodes().size());
                var exactEncoded = PatternDetailsHelper.encodeCraftingPattern(holder, input, new ItemStack(Items.CHEST), false, false);
                var exact = RecipeTree.build(new appeng.crafting.pattern.AECraftingPattern(AEItemKey.of(exactEncoded), level), level);
                assertEquals(2, exact.nodes().size()); assertTrue(exact.nodes().get(1).item().is(primary));
            }
        });
    }
    @Test void hiddenTagChoicesAreFilteredInTreeGridAndGeneratedVariants(MinecraftServer server) throws Exception {
        var holder = net.minecraft.core.registries.BuiltInRegistries.ITEM.wrapAsHolder(Items.GLASS);
        var previousTags = holder.tags().toList();
        var bind = holder.getClass().getDeclaredMethod("bindTags", Collection.class);
        bind.setAccessible(true);
        try {
            var tags = new ArrayList<>(previousTags);
            tags.add(net.minecraft.tags.TagKey.create(Registries.ITEM, Identifier.parse("c:hidden_from_recipe_viewers")));
            bind.invoke(holder, tags);
            var recipe = new ShapedRecipe(null, null, ShapedRecipePattern.of(Map.of(
                    'G', Ingredient.of(Items.GLASS, Items.WHITE_STAINED_GLASS), 'I', Ingredient.of(Items.IRON_INGOT)), "GGI"),
                    new ItemStackTemplate(Items.CHEST));
            var recipeHolder = new RecipeHolder<CraftingRecipe>(ResourceKey.create(Registries.RECIPE, Identifier.parse("test:hidden_choice")), recipe);
            withRecipes(server, List.of(recipeHolder), () -> {
                var initial = RecipeTree.build(details(pattern(Items.CHEST, Items.CLOCK, Set.of())), recipeLevel(server));
                var page = RecipeTree.expand(new ArrayList<>(initial.nodes()), 1, 0, recipeLevel(server));
                assertFalse(page.nodes().stream().anyMatch(n -> n.item().is(Items.GLASS)));
                assertTrue(page.nodes().stream().anyMatch(n -> n.item().is(Items.WHITE_STAINED_GLASS) && n.item().getCount() == 2));
                var grid = RecursivePatternGenerator.getRecipeInputsAs3x3Grid(recipe);
                assertTrue(grid[0].is(Items.WHITE_STAINED_GLASS));
                var stale = grid.clone(); stale[0] = new ItemStack(Items.GLASS); stale[1] = new ItemStack(Items.GLASS);
                var variants = RecipeInputVariants.generate(recipe, stale, new ItemStack(Items.CHEST), null, 64);
                assertEquals(1, variants.size());
                assertTrue(variants.getFirst()[0].is(Items.WHITE_STAINED_GLASS));
                var generated = RecursivePatternGenerator.generate(List.of(details(pattern(Items.CHEST, Items.CLOCK, Set.of()))), recipeLevel(server), -1);
                assertFalse(consumes(generated, Items.GLASS));
                assertTrue(consumes(generated, Items.WHITE_STAINED_GLASS));
                // An exact hidden ingredient must not disappear or become a different item.
                var exact = RecipeTree.build(details(pattern(Items.GLASS, Items.CLOCK, Set.of())), recipeLevel(server));
                assertTrue(exact.nodes().get(1).item().is(Items.GLASS));
            });
        } finally { bind.invoke(holder, previousTags); }
    }
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
                    assertEquals(a.parent(), b.parent()); assertEquals(a.recipeId(), b.recipeId()); assertEquals(a.choice(), b.choice());
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
    @Test void overlappingRootChoicesRemainSeparateRequirements(MinecraftServer server) throws Exception {
        var base = details(pattern(Items.GLASS, Items.CLOCK, Set.of()));
        java.util.function.BiFunction<GenericStack[], Long, IPatternDetails.IInput> input = (choices, multiplier) -> new IPatternDetails.IInput() {
            public GenericStack[] getPossibleInputs() { return choices; }
            public long getMultiplier() { return multiplier; }
            public boolean isValid(appeng.api.stacks.AEKey key, net.minecraft.world.level.Level level) { return true; }
            public appeng.api.stacks.AEKey getRemainingKey(appeng.api.stacks.AEKey key) { return null; }
        };
        var first = input.apply(new GenericStack[]{new GenericStack(AEItemKey.of(Items.GLASS), 1), new GenericStack(AEItemKey.of(Items.TINTED_GLASS), 1)}, 2L);
        var second = input.apply(new GenericStack[]{new GenericStack(AEItemKey.of(Items.GLASS), 1), new GenericStack(AEItemKey.of(Items.WHITE_STAINED_GLASS), 1)}, 1L);
        IPatternDetails root = new IPatternDetails() {
            public AEItemKey getDefinition() { return base.getDefinition(); }
            public List<GenericStack> getOutputs() { return base.getOutputs(); }
            public IInput[] getInputs() { return new IInput[]{first, second}; }
        };
        var nodes = RecipeTree.build(root, recipeLevel(server)).nodes();
        assertEquals(5, nodes.size());
        assertEquals(2, nodes.stream().filter(n -> n.choice() == 0).count());
        assertEquals(2, nodes.stream().filter(n -> n.choice() == 1).count());
        var glass = nodes.stream().filter(n -> !n.recipe() && n.item().is(Items.GLASS)).toList();
        assertEquals(2, glass.size());
        assertNotEquals(TreePresentation.groupKey(glass.get(0)), TreePresentation.groupKey(glass.get(1)));
        assertEquals(2, glass.get(0).item().getCount()); assertEquals(1, glass.get(1).item().getCount());
    }
    @Test void nestedGlassAlternativesAreOneRequirementWithCorrectCount(MinecraftServer server) throws Exception {
        var recipe = new ShapedRecipe(null, null, ShapedRecipePattern.of(Map.of(
                'G', Ingredient.of(Items.GLASS, Items.TINTED_GLASS, Items.WHITE_STAINED_GLASS), 'I', Ingredient.of(Items.IRON_INGOT)), "GGI"),
                new ItemStackTemplate(Items.CHEST, 1));
        var holder = new RecipeHolder<CraftingRecipe>(ResourceKey.create(Registries.RECIPE, Identifier.parse("test:glass_choices")), recipe);
        withRecipes(server, List.of(holder), () -> {
            var initial = RecipeTree.build(details(pattern(Items.CHEST, Items.CLOCK, Set.of())), recipeLevel(server));
            var page = RecipeTree.expand(new ArrayList<>(initial.nodes()), 1, 0, recipeLevel(server));
            var glass = page.nodes().stream().filter(n -> !n.recipe() && n.choice() == 0).toList();
            assertEquals(3, glass.size()); assertTrue(glass.stream().allMatch(n -> n.item().getCount() == 2));
            assertEquals(1, glass.stream().map(TreePresentation::groupKey).distinct().count());
            var iron = page.nodes().stream().filter(n -> !n.recipe() && n.choice() == 1).findFirst().orElseThrow();
            assertTrue(iron.item().is(Items.IRON_INGOT)); assertEquals(1, iron.item().getCount());
            assertNotEquals(TreePresentation.groupKey(glass.getFirst()), TreePresentation.groupKey(iron));
        });
    }
    @Test void largeNestedChoiceGroupsCanCrossPagesAndInterleave(MinecraftServer server) throws Exception {
        var options = net.minecraft.core.registries.BuiltInRegistries.ITEM.stream().filter(i -> i != Items.AIR).limit(700).toArray(Item[]::new);
        var recipe = new ShapedRecipe(null, null, ShapedRecipePattern.of(Map.of('I', Ingredient.of(options)), "I"), new ItemStackTemplate(Items.NETHER_STAR, 1));
        var holder = new RecipeHolder<CraftingRecipe>(ResourceKey.create(Registries.RECIPE, Identifier.parse("test:wide_choices")), recipe);
        withRecipes(server, List.of(holder), () -> {
            var initial = RecipeTree.build(details(pattern(Items.NETHER_STAR, Items.CLOCK, Set.of())), recipeLevel(server));
            var branch = RecipeTree.branch(initial.nodes(), 1, recipeLevel(server));
            var nodes = new ArrayList<>(initial.nodes());
            var first = branch.page(1, nodes.size(), 0); nodes.addAll(first.nodes());
            assertFalse(first.done());
            int recipeId = 2;
            nodes.add(new RecipeTree.Node(0, false, new ItemStack(Items.DIRT), "", List.of(), 0));
            var next = branch.page(1, nodes.size(), first.nextOffset());
            assertTrue(next.nodes().stream().allMatch(n -> n.parent() == recipeId));
            var buf = buffer(server);
            try {
                RecipeTreePackets.Branch.CODEC.encode(buf, new RecipeTreePackets.Branch(UUID.randomUUID(), 1, nodes.size(), next));
                var decoded = RecipeTreePackets.Branch.CODEC.decode(buf);
                assertEquals(next.nodes().size(), decoded.page().nodes().size());
                assertTrue(decoded.page().nodes().stream().allMatch(n -> n.parent() == recipeId && n.choice() == 0));
            } finally { buf.release(); }
        });
    }
    @Test void largeInitialIngredientSetsFitPagedPackets(MinecraftServer server) throws Exception {
        var base = details(pattern(Items.REDSTONE, Items.CLOCK, Set.of()));
        var candidates = net.minecraft.core.registries.BuiltInRegistries.ITEM.stream().filter(i -> i != Items.AIR)
                .limit(700).map(i -> new GenericStack(AEItemKey.of(i), Long.MAX_VALUE)).toArray(GenericStack[]::new);
        IPatternDetails root = new IPatternDetails() {
            public AEItemKey getDefinition() { return base.getDefinition(); }
            public List<GenericStack> getOutputs() { return base.getOutputs(); }
            public IInput[] getInputs() { return new IInput[]{new IInput() {
                public GenericStack[] getPossibleInputs() { return candidates; }
                public long getMultiplier() { return Long.MAX_VALUE; }
                public boolean isValid(appeng.api.stacks.AEKey key, net.minecraft.world.level.Level level) { return true; }
                public appeng.api.stacks.AEKey getRemainingKey(appeng.api.stacks.AEKey key) { return null; }
            }}; }
        };
        var tree = RecipeTree.build(root, recipeLevel(server));
        assertEquals(candidates.length + 1, tree.nodes().size());
        assertTrue(tree.nodes().stream().skip(1).allMatch(n -> n.item().getCount() == 64));
        var opening = tree.opening(); assertTrue(opening.truncated()); assertEquals(RecipeTree.MAX_NODES, opening.nodes().size());
        var buf = buffer(server);
        try {
            var id = UUID.randomUUID();
            RecipeTreePackets.Open.CODEC.encode(buf, new RecipeTreePackets.Open(id, opening, Set.of()));
            var decoded = RecipeTreePackets.Open.CODEC.decode(buf);
            var combined = new ArrayList<>(decoded.tree().nodes());
            int offset = 0; boolean done;
            do {
                var page = tree.rootPage(offset);
                assertTrue(page.nodes().size() <= RecipeTree.MAX_NODES);
                RecipeTreePackets.Branch.CODEC.encode(buf, new RecipeTreePackets.Branch(id, 0, combined.size(), page));
                var branch = RecipeTreePackets.Branch.CODEC.decode(buf);
                assertEquals(0, branch.parent()); combined.addAll(branch.page().nodes());
                offset = page.nextOffset(); done = page.done();
            } while (!done);
            assertEquals(tree.nodes().size(), combined.size());
            for (int i = 0; i < combined.size(); i++) assertTrue(ItemStack.matches(tree.nodes().get(i).item(), combined.get(i).item()));
            assertEquals(0, buf.readableBytes());
            assertThrows(IllegalArgumentException.class, () -> tree.rootPage(-1));
            assertThrows(IllegalArgumentException.class, () -> tree.rootPage(tree.nodes().size()));
        } finally { buf.release(); }
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
