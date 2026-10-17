package com.lumengrid.recursiveae2patternprovider;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.parts.AEBasePart;
import appeng.menu.me.items.PatternEncodingTermMenu;
import com.lumengrid.recursiveae2patternprovider.client.RecipeTreeScreen;
import com.lumengrid.recursiveae2patternprovider.menu.IRecursivePatternMenu;
import com.lumengrid.recursiveae2patternprovider.network.ToggleRecursiveModeC2SPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Runs only with -PeditorSmoke in an isolated dev game directory. */
@EventBusSubscriber(modid = RecursiveAE2PatternProvider.MODID, value = Dist.CLIENT)
public final class EditorSmoke {
    private static int ticks, phase, since, terminal;
    private static final StringBuilder report = new StringBuilder();
    private static final AtomicBoolean prepared = new AtomicBoolean();
    private static final List<AEBasePart> terminals = new ArrayList<>();
    private static boolean failed;
    private static volatile Throwable serverFailure;
    private static long start = System.nanoTime();
    private static void record(String text) { report.append(text).append('\n'); RecursiveAE2PatternProvider.LOGGER.info("EDITOR_SMOKE: {}", text); }
    private static void next(int value) { phase = value; since = ticks; }
    private static void check(boolean value, String reason) { if (!value) throw new IllegalStateException(reason); }
    private static void shot(String name) {
        var mc = Minecraft.getInstance();
        Screenshot.takeScreenshot(mc.getMainRenderTarget(), image -> {
            try { var out = Path.of(System.getProperty("otter.smoke.output")); Files.createDirectories(out); image.writeToFile(out.resolve(name + ".png")); }
            catch (Exception e) { throw new RuntimeException(e); }
            finally { image.close(); }
        });
    }
    private static void clickButton(String text) {
        var mc = Minecraft.getInstance();
        var button = mc.screen.children().stream().filter(w -> w instanceof Button b && b.getMessage().getString().equals(text))
                .map(w -> (Button)w).findFirst().orElseThrow();
        check(button.active, "Button is disabled: " + text);
        button.onPress(new MouseButtonInfo(0, 0));
    }
    private static void finish(Minecraft mc, String result) throws Exception {
        record(result);
        Path out = Path.of(System.getProperty("otter.smoke.output")); Files.createDirectories(out);
        Files.writeString(out.resolve("report.txt"), report);
        failed = true; mc.stop();
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("otter.editorSmoke") || failed) return;
        var mc = Minecraft.getInstance(); ticks++;
        try {
            if (serverFailure != null) throw new IllegalStateException("Server action failed", serverFailure);
            if ((System.nanoTime() - start) / 1e9 > 300) throw new IllegalStateException("Smoke timeout at phase " + phase + ", screen " + mc.screen);
            if (phase == 0 && ticks > 60 && mc.getOverlay() == null) {
                RecipeDefaults.save(com.lumengrid.recursiveae2patternprovider.client.ClientRecipeDefaults.path(), Set.of());
                mc.options.guiScale().set(2);
                mc.getWindow().setWindowed(1280, 720);
                mc.options.renderDistance().set(3); mc.options.simulationDistance().set(5);
                var settings = new LevelSettings("Otter isolated smoke", GameType.CREATIVE,
                        new LevelSettings.DifficultySettings(net.minecraft.world.Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
                record("Creating isolated flat world with AE2 and development mod"); next(1);
                mc.createWorldOpenFlows().createFreshLevel("otter-smoke-" + System.currentTimeMillis(), settings,
                        new WorldOptions(42, false, false), WorldPresets::createFlatWorldDimensions, new TitleScreen());
            } else if (phase == 1 && mc.player != null && mc.level != null && ticks - since > 30) {
                var server = mc.getSingleplayerServer(); check(server != null, "Missing integrated server");
                server.execute(() -> {
                    try {
                        var player = server.getPlayerList().getPlayers().getFirst();
                        var level = player.level(); var base = player.blockPosition().offset(1, 0, 0);
                        var items = List.of(AEParts.PATTERN_ENCODING_TERMINAL.asItem(), AEParts.CRAFTING_TERMINAL.asItem(),
                                AEParts.PATTERN_ACCESS_TERMINAL.asItem(), AEParts.TERMINAL.asItem());
                        for (int i = 0; i < items.size(); i++) {
                            var pos = base.offset(i % 2, 0, i / 2);
                            level.setBlockAndUpdate(pos, AEBlocks.CABLE_BUS.block().defaultBlockState());
                            var host = (CableBusBlockEntity)level.getBlockEntity(pos);
                            terminals.add((AEBasePart)host.addPart(items.get(i), Direction.NORTH, player));
                        }
                        prepared.set(true);
                    } catch (Throwable e) { serverFailure = e; }
                }); next(2);
            } else if (phase == 2 && prepared.get() && ticks - since > 40) {
                requestTerminal(mc); next(3);
            } else if (phase == 3 && mc.player != null && mc.player.containerMenu instanceof appeng.menu.AEBaseMenu && ticks - since > 20) {
                record("OPENED " + mc.player.containerMenu.getClass().getSimpleName() + " screen=" + mc.screen.getClass().getSimpleName());
                if (terminal == 0) {
                    check(mc.player.containerMenu instanceof PatternEncodingTermMenu, "Wrong encoding menu");
                    check(((IRecursivePatternMenu)mc.player.containerMenu).isRecursiveMode(), "Recursive default missing");
                    shot("encoding-terminal");
                    ClientPacketDistributor.sendToServer(new ToggleRecursiveModeC2SPacket(false)); next(4);
                } else { mc.screen.onClose(); terminal++; next(6); }
            } else if (phase == 4 && ticks - since > 20) {
                check(!((IRecursivePatternMenu)mc.player.containerMenu).isRecursiveMode(), "Off toggle did not sync");
                ClientPacketDistributor.sendToServer(new ToggleRecursiveModeC2SPacket(true)); next(5);
            } else if (phase == 5 && ticks - since > 20) {
                check(((IRecursivePatternMenu)mc.player.containerMenu).isRecursiveMode(), "On toggle did not sync");
                record("Recursive toggle synchronized off and on"); mc.screen.onClose(); terminal++; next(6);
            } else if (phase == 6 && ticks - since > 20) {
                if (terminal < terminals.size()) { requestTerminal(mc); next(3); }
                else {
                    terminal = 0; requestTerminal(mc); next(7);
                }
            } else if (phase == 7 && mc.player.containerMenu instanceof PatternEncodingTermMenu && ticks - since > 20) {
                check(!com.lumengrid.recursiveae2patternprovider.client.RecipeTreeButton.canEdit(mc.player.containerMenu), "Empty terminal offers editor");
                var server = mc.getSingleplayerServer();
                server.execute(() -> {
                    try {
                        var player = server.getPlayerList().getPlayers().getFirst();
                        var manager = server.getRecipeManager();
                        var recipes = new ArrayList<net.minecraft.world.item.crafting.RecipeHolder<?>>(manager.getRecipes());
                        for (int i = 0; i < 1000; i++) {
                            var recipe = new net.minecraft.world.item.crafting.ShapedRecipe(null, null,
                                    net.minecraft.world.item.crafting.ShapedRecipePattern.of(Map.of('I', net.minecraft.world.item.crafting.Ingredient.of(Items.DIRT)), "I"),
                                    new ItemStackTemplate(Items.OAK_PLANKS, 4));
                            recipes.add(new net.minecraft.world.item.crafting.RecipeHolder<>(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE,
                                    net.minecraft.resources.Identifier.parse("smoke:planks_" + i)), recipe));
                        }
                        for (var entry : Map.of("white_variant", Items.WHITE_WOOL, "red_variant", Items.RED_WOOL, "compressed_planks", Items.DIRT, "zz_oak_from_birch", Items.BIRCH_LOG).entrySet()) {
                            var recipe = new net.minecraft.world.item.crafting.ShapedRecipe(null, null,
                                    net.minecraft.world.item.crafting.ShapedRecipePattern.of(Map.of('I', net.minecraft.world.item.crafting.Ingredient.of(entry.getValue())), "I"),
                                    new ItemStackTemplate(Items.OAK_PLANKS, 4));
                            recipes.add(new net.minecraft.world.item.crafting.RecipeHolder<>(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE,
                                    net.minecraft.resources.Identifier.parse("smoke:" + entry.getKey())), recipe));
                        }
                        var field = manager.getClass().getDeclaredField("recipes"); field.setAccessible(true);
                        field.set(manager, net.minecraft.world.item.crafting.RecipeMap.create(recipes));
                        var holder = (net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe>)(Object)manager.getRecipes().stream()
                                .filter(r -> r.id().identifier().toString().equals("minecraft:crafting_table")).findFirst().orElseThrow();
                        ItemStack[] input = new ItemStack[9]; Arrays.fill(input, ItemStack.EMPTY);
                        for (int index : new int[]{0, 1, 3, 4}) input[index] = new ItemStack(Items.OAK_PLANKS);
                        var pattern = PatternDetailsHelper.encodeCraftingPattern(holder, input, new ItemStack(Items.CRAFTING_TABLE), false, false);
                        PatternUtil.setRecursive(pattern, true);
                        ((com.lumengrid.recursiveae2patternprovider.menu.IRecipeTreeMenu)player.containerMenu).recursiveAE2$treeSlot().set(pattern);
                        player.containerMenu.broadcastChanges();
                    } catch (Throwable e) { serverFailure = e; }
                }); next(8);
            } else if (phase == 8 && ticks - since > 30) {
                shot("encoding-terminal-tree-button");
                clickTreeButton(); next(27);
            } else if (phase == 27 && mc.screen instanceof RecipeTreeScreen) {
                var earlyNodes = (List<RecipeTree.Node>)field("nodes");
                for (int i = 0; i < earlyNodes.size(); i++) if (earlyNodes.get(i).recipe()) {
                    earlyRecipe = i; focusNode(i); next(31); break;
                }
            } else if (phase == 31 && ticks - since > 0) {
                check(((Map<Integer, List<Integer>>)field("groupMembers")).get(earlyRecipe).size() == 1, "Late type arrived before selection check");
                mc.screen.mouseClicked(new MouseButtonEvent(40, 80, new MouseButtonInfo(0, 0)), false); next(9);
            } else if (phase == 9 && mc.screen instanceof RecipeTreeScreen && ticks - since > 80 && treeIdle()) {
                record("Terminal button opened tree; all branches loaded automatically");
                shot("recipe-tree-root");
                var nodes = (List<RecipeTree.Node>)field("nodes");
                check(nodes.size() > 320, "Tree stopped at former node cap");
                check(((Map<Integer, List<Integer>>)field("groupMembers")).get(earlyRecipe).size() == 2, "Late wood type was not grouped");
                check(((Set<Integer>)field("expanded")).contains(earlyRecipe), "Late type collapsed a selected branch");
                record("Late-arriving variants preserved the selected expanded branch");
                var expanded = (Set<Integer>)field("expanded");
                var heads = (Map<Integer, Integer>)field("groupHeads");
                int compressed = -1, variant = -1;
                for (int i = 0; i < nodes.size(); i++) {
                    if (nodes.get(i).recipeId().equals("smoke:compressed_planks")) compressed = i;
                    if (nodes.get(i).recipeId().equals("smoke:white_variant")) variant = i;
                }
                check(compressed > 0 && !expanded.contains(heads.get(compressed)), "Compressed route did not start collapsed");
                check(variant > 0 && !expanded.contains(heads.get(variant)), "Color variants did not group/collapse");
                var groups = (Map<Integer, List<Integer>>)field("groupMembers");
                check(groups.get(heads.get(variant)).size() == 2, "Color recipes were not grouped together");
                record("Compressed route folded and color variants grouped");
                check(!((Map<?, ?>)field("sharedBranches")).isEmpty(), "Repeated materials were not shared");
                mc.screen.keyPressed(new net.minecraft.client.input.KeyEvent(61, 0, 0));
                check((double)field("zoom") > 1, "Zoom failed");
                mc.screen.mouseScrolled(200, 100, 0, -1);
                double beforeWheel = (double)field("zoom");
                mc.screen.mouseScrolled(200, 100, 0, 1);
                check((double)field("zoom") > beforeWheel, "Wheel did not zoom");
                record("Mouse wheel zoomed in and out");
                clickButton("Reset view");
                selectedRecipe = -1;
                for (int i = 0; i < nodes.size(); i++) if (nodes.get(i).recipe()) { selectedRecipe = i; break; }
                check(selectedRecipe > 0, "No recipes loaded");
                selectedVariant = heads.get(variant);
                int preview = displayNode(selectedVariant);
                setField("ticks", (int)field("ticks") + 40);
                check(displayNode(selectedVariant) != preview, "Collapsed preview did not cycle");
                var members = groups.get(selectedVariant);
                setField("hoveredHead", selectedVariant); setField("hoveredVariant", members.indexOf(preview));
                check(displayNode(selectedVariant) == preview, "Hover did not freeze preview");
                setField("ticks", (int)field("ticks") + 40);
                check(displayNode(selectedVariant) == preview, "Hovered preview changed");
                setField("hoveredHead", -1);
                focusNode(selectedVariant); next(25);
            } else if (phase == 25 && ticks - since > 10) {
                int preview = displayNode(selectedVariant);
                mc.screen.mouseClicked(new MouseButtonEvent(130, 138, new MouseButtonInfo(0, 0)), false);
                check(displayNode(selectedVariant) != preview, "Variant arrow did not change type");
                int chosen = displayNode(selectedVariant);
                mc.screen.mouseClicked(new MouseButtonEvent(40, 80, new MouseButtonInfo(0, 0)), true);
                check(((Set<Integer>)field("expanded")).contains(selectedVariant), "Variant branch did not expand");
                setField("ticks", (int)field("ticks") + 40);
                check(displayNode(selectedVariant) == chosen, "Expanded type changed unexpectedly");
                clickButton("Disable route");
                check(!((Set<Integer>)field("expanded")).contains(selectedVariant), "Variant ban did not collapse path");
                clickButton("Enable route");
                record("Variant previews cycle, pause on hover, and support type selection and path collapse");
                next(26);
            } else if (phase == 26 && ticks - since > 10) {
                shot("recipe-tree-variants"); focusNode(selectedRecipe); next(10);
            } else if (phase == 10 && ticks - since > 10) {
                shiftClickSelected();
                var nodes = (List<RecipeTree.Node>)field("nodes");
                var excluded = (Set<String>)field("excluded");
                check(excluded.equals(Set.of(nodes.get(selectedRecipe).recipeId())), "Shift-click banned unrelated alternatives");
                check(!((Set<Integer>)field("expanded")).contains(((Map<Integer, Integer>)field("groupHeads")).get(selectedRecipe)), "Blocked recipe did not collapse");
                clickButton("Always block");
                check(((Set<String>)field("personalDefaults")).equals(excluded), "Always block did not remember exact recipe");
                record("Shift-click collapsed the blocked route; Always block queued a personal default");
                next(22);
            } else if (phase == 22 && ticks - since > 10) {
                shot("recipe-tree-expanded"); clickButton("Save");
                var screen = mc.screen; screen.onClose(); check(mc.screen == screen, "Pending save could be cancelled"); next(11);
            } else if (phase == 11 && !(mc.screen instanceof RecipeTreeScreen) && ticks - since > 20) {
                check(mc.player.containerMenu instanceof PatternEncodingTermMenu, "Save closed terminal menu");
                check(!PatternRecipeRules.read(terminalPattern()).isEmpty(), "Terminal pattern did not receive saved rules");
                record("Save updated encoded slot and returned to terminal");
                check(!RecipeDefaults.load(com.lumengrid.recursiveae2patternprovider.client.ClientRecipeDefaults.path()).isEmpty(), "Personal defaults did not persist");
                var server = mc.getSingleplayerServer(); server.execute(() -> {
                    try {
                        var player = server.getPlayerList().getPlayers().getFirst();
                        var menu = (PatternEncodingTermMenu)player.containerMenu;
                        var treeMenu = (com.lumengrid.recursiveae2patternprovider.menu.IRecipeTreeMenu)menu;
                        check(!treeMenu.recursiveAE2$defaults().isEmpty(), "Client defaults did not reach encoding menu");
                        treeMenu.recursiveAE2$treeSlot().set(ItemStack.EMPTY);
                        menu.setMode(appeng.parts.encoding.EncodingMode.CRAFTING);
                        var slots = menu.getCraftingGridSlots();
                        for (int i = 0; i < slots.length; i++) slots[i].set(Set.of(0, 1, 3, 4).contains(i) ? new ItemStack(Items.OAK_PLANKS) : ItemStack.EMPTY);
                        var blank = PatternEncodingTermMenu.class.getDeclaredField("blankPatternSlot"); blank.setAccessible(true);
                        ((net.minecraft.world.inventory.Slot)blank.get(menu)).set(appeng.core.definitions.AEItems.BLANK_PATTERN.stack());
                        menu.encode();
                        check(PatternRecipeRules.read(treeMenu.recursiveAE2$treeSlot().getItem()).equals(treeMenu.recursiveAE2$defaults()), "New encoded pattern did not inherit defaults");
                        menu.broadcastChanges();
                    } catch (Throwable e) { serverFailure = e; }
                }); next(24);
            } else if (phase == 24 && ticks - since > 30) {
                record("New pattern encoded through AE2 inherited the client blacklist");
                clickTreeButton(); next(12);
            } else if (phase == 12 && mc.screen instanceof RecipeTreeScreen && ticks - since > 20) {
                clickButton("Reset rules"); removeDefaultInManager(); clickButton("Cancel"); next(13);
            } else if (phase == 13 && !(mc.screen instanceof RecipeTreeScreen) && ticks - since > 20) {
                check(!PatternRecipeRules.read(terminalPattern()).isEmpty(), "Cancel changed saved rules");
                check(!RecipeDefaults.load(com.lumengrid.recursiveae2patternprovider.client.ClientRecipeDefaults.path()).isEmpty(), "Cancel changed personal defaults");
                record("Cancel returned to terminal and preserved pattern rules and personal defaults");
                clickTreeButton(); next(14);
            } else if (phase == 14 && mc.screen instanceof RecipeTreeScreen && ticks - since > 20) {
                clickButton("Reset rules"); removeDefaultInManager(); clickButton("Save"); next(15);
            } else if (phase == 15 && !(mc.screen instanceof RecipeTreeScreen) && ticks - since > 20) {
                check(PatternRecipeRules.read(terminalPattern()).isEmpty(), "Reset did not save");
                check(RecipeDefaults.load(com.lumengrid.recursiveae2patternprovider.client.ClientRecipeDefaults.path()).isEmpty(), "Removing default did not persist");
                clickTreeButton(); next(16);
            } else if (phase == 16 && mc.screen instanceof RecipeTreeScreen && ticks - since > 20) {
                var server = mc.getSingleplayerServer(); server.execute(() -> {
                    var player = server.getPlayerList().getPlayers().getFirst();
                    ((com.lumengrid.recursiveae2patternprovider.menu.IRecipeTreeMenu)player.containerMenu).recursiveAE2$treeSlot().set(new ItemStack(Items.DIRT));
                    player.containerMenu.broadcastChanges();
                }); next(17);
            } else if (phase == 17 && ticks - since > 20) {
                // The auto-loader can already have rejected the changed slot.
                if (!(boolean)field("invalidSession")) clickButton("Save"); next(18);
            } else if (phase == 18 && ticks - since > 20) {
                check((boolean)field("invalidSession"), "Changed terminal pattern was accepted");
                check(terminalPattern().is(Items.DIRT), "Save overwrote replacement item");
                clickButton("Cancel"); record("Changed encoded slot rejected without overwriting replacement");
                mc.screen.onClose(); next(19);
            } else if (phase == 19 && mc.screen == null && ticks - since > 20) {
                var server = mc.getSingleplayerServer(); server.execute(() -> {
                    var player = server.getPlayerList().getPlayers().getFirst();
                    var pattern = PatternDetailsHelper.encodeProcessingPattern(List.of(new GenericStack(AEItemKey.of(Items.OAK_PLANKS), 4)),
                            List.of(new GenericStack(AEItemKey.of(Items.CRAFTING_TABLE), 1)));
                    PatternUtil.setRecursive(pattern, true); player.setItemInHand(InteractionHand.MAIN_HAND, pattern);
                    player.containerMenu.broadcastChanges();
                }); next(20);
            } else if (phase == 20 && ticks - since > 20) {
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                ClientPacketDistributor.sendToServer(new com.lumengrid.recursiveae2patternprovider.network.RecipeTreePackets.RequestOpen(999));
                next(21);
            } else if (phase == 21 && ticks - since > 20) {
                check(!(mc.screen instanceof RecipeTreeScreen), "Held pattern or forged menu opened editor");
                record("Right-click no longer opens editor; invalid menu request rejected");
                terminal = 0; requestTerminal(mc); next(28);
            } else if (phase == 28 && mc.player.containerMenu instanceof PatternEncodingTermMenu && ticks - since > 20) {
                var server = mc.getSingleplayerServer(); server.execute(() -> {
                    try {
                        var player = server.getPlayerList().getPlayers().getFirst();
                        var manager = server.getRecipeManager();
                        var candidates = net.minecraft.core.registries.BuiltInRegistries.ITEM.stream().filter(i -> i != Items.AIR).limit(700).toArray(Item[]::new);
                        var recipe = new net.minecraft.world.item.crafting.ShapedRecipe(null, null,
                                net.minecraft.world.item.crafting.ShapedRecipePattern.of(Map.of('I', net.minecraft.world.item.crafting.Ingredient.of(candidates)), "I"),
                                new ItemStackTemplate(Items.CLOCK, 1));
                        var holder = new net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe>(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE,
                                net.minecraft.resources.Identifier.parse("smoke:wide_inputs")), recipe);
                        var recipes = new ArrayList<net.minecraft.world.item.crafting.RecipeHolder<?>>(manager.getRecipes()); recipes.add(holder);
                        var field = manager.getClass().getDeclaredField("recipes"); field.setAccessible(true);
                        field.set(manager, net.minecraft.world.item.crafting.RecipeMap.create(recipes));
                        ItemStack[] input = new ItemStack[9]; Arrays.fill(input, ItemStack.EMPTY); input[0] = new ItemStack(Items.DIRT);
                        var pattern = PatternDetailsHelper.encodeCraftingPattern(holder, input, new ItemStack(Items.CLOCK), true, false);
                        PatternUtil.setRecursive(pattern, true);
                        var details = PatternDetailsHelper.decodePattern(pattern, player.level());
                        check(RecipeTree.build(details, player.level()).nodes().size() > 320, "Wide pattern did not expose enough alternatives");
                        ((com.lumengrid.recursiveae2patternprovider.menu.IRecipeTreeMenu)player.containerMenu).recursiveAE2$treeSlot().set(pattern);
                        player.containerMenu.broadcastChanges();
                    } catch (Throwable e) { serverFailure = e; }
                }); next(29);
            } else if (phase == 29 && ticks - since > 30) {
                clickTreeButton(); next(30);
            } else if (phase == 30 && mc.screen instanceof RecipeTreeScreen && ((Set<Integer>)field("complete")).contains(0)) {
                var nodes = (List<RecipeTree.Node>)field("nodes");
                check(nodes.stream().filter(n -> n.parent() == 0).count() == 700, "Initial ingredient pages lost alternatives");
                var roots = ((Map<Integer, List<Integer>>)field("groupedChildren")).get(0);
                check(roots.size() == 1, "Accepted root alternatives were shown as separate requirements");
                check(((Map<Integer, List<Integer>>)field("groupMembers")).get(roots.getFirst()).size() == 700, "Root choices were lost");
                record("700 initial alternatives loaded as ONE accepted ingredient group");
                clickButton("Cancel"); next(32);
            } else if (phase == 32 && ticks - since > 20) {
                var server = mc.getSingleplayerServer(); server.execute(() -> {
                    try {
                        var player = server.getPlayerList().getPlayers().getFirst(); var manager = server.getRecipeManager();
                        var recipe = new net.minecraft.world.item.crafting.ShapedRecipe(null, null,
                                net.minecraft.world.item.crafting.ShapedRecipePattern.of(Map.of('G', net.minecraft.world.item.crafting.Ingredient.of(Items.GLASS, Items.WHITE_STAINED_GLASS, Items.TINTED_GLASS),
                                        'I', net.minecraft.world.item.crafting.Ingredient.of(Items.IRON_INGOT)), "GGI"), new ItemStackTemplate(Items.CHEST, 1));
                        var holder = new net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe>(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE,
                                net.minecraft.resources.Identifier.parse("smoke:glass_choices")), recipe);
                        var recipes = new ArrayList<net.minecraft.world.item.crafting.RecipeHolder<?>>(manager.getRecipes()); recipes.add(holder);
                        var field = manager.getClass().getDeclaredField("recipes"); field.setAccessible(true); field.set(manager, net.minecraft.world.item.crafting.RecipeMap.create(recipes));
                        ItemStack[] input = new ItemStack[9]; Arrays.fill(input, ItemStack.EMPTY);
                        input[0] = new ItemStack(Items.GLASS); input[1] = new ItemStack(Items.GLASS); input[2] = new ItemStack(Items.IRON_INGOT);
                        var pattern = PatternDetailsHelper.encodeCraftingPattern(holder, input, new ItemStack(Items.CHEST), true, false); PatternUtil.setRecursive(pattern, true);
                        ((com.lumengrid.recursiveae2patternprovider.menu.IRecipeTreeMenu)player.containerMenu).recursiveAE2$treeSlot().set(pattern); player.containerMenu.broadcastChanges();
                    } catch (Throwable e) { serverFailure = e; }
                }); next(33);
            } else if (phase == 33 && ticks - since > 30) {
                clickTreeButton(); next(34);
            } else if (phase == 34 && mc.screen instanceof RecipeTreeScreen && ticks - since > 20) {
                var roots = ((Map<Integer, List<Integer>>)field("groupedChildren")).get(0);
                check(roots.size() == 2, "Glass alternatives were shown as three separate requirements");
                var groups = (Map<Integer, List<Integer>>)field("groupMembers");
                int glass = roots.stream().filter(id -> groups.get(id).size() == 3).findFirst().orElseThrow();
                var nodes = (List<RecipeTree.Node>)field("nodes");
                check(groups.get(glass).stream().allMatch(id -> nodes.get(id).item().getCount() == 2), "Glass quantity was not preserved");
                record("Glass choices displayed as one ingredient requiring two items, plus one iron requirement");
                next(35);
            } else if (phase == 35 && ticks - since > 10) {
                shot("recipe-tree-glass-choices"); clickButton("Cancel"); finish(mc, "PASS");
            }
        } catch (Throwable e) {
            try { finish(mc, "FAIL phase " + phase + ": " + e); }
            catch (Exception nested) { RecursiveAE2PatternProvider.LOGGER.error("Smoke failure", e); failed = true; mc.stop(); }
        }
    }
    private static int selectedRecipe, selectedVariant, earlyRecipe;
    private static void setField(String name, int value) throws Exception {
        var f = RecipeTreeScreen.class.getDeclaredField(name); f.setAccessible(true); f.setInt(Minecraft.getInstance().screen, value);
    }
    private static int displayNode(int head) throws Exception {
        var method = RecipeTreeScreen.class.getDeclaredMethod("displayed", int.class); method.setAccessible(true);
        return (int)method.invoke(Minecraft.getInstance().screen, head);
    }
    private static Object field(String name) throws Exception {
        var f = RecipeTreeScreen.class.getDeclaredField(name); f.setAccessible(true); return f.get(Minecraft.getInstance().screen);
    }
    private static boolean treeIdle() throws Exception { return ((Map<?, ?>)field("pending")).isEmpty() && ((Collection<?>)field("loadQueue")).isEmpty(); }
    private static ItemStack terminalPattern() {
        return ((com.lumengrid.recursiveae2patternprovider.menu.IRecipeTreeMenu)Minecraft.getInstance().player.containerMenu).recursiveAE2$treeSlot().getItem();
    }
    private static void clickTreeButton() {
        var screen = Minecraft.getInstance().screen;
        var button = screen.children().stream().filter(w -> w instanceof com.lumengrid.recursiveae2patternprovider.client.RecipeTreeButton)
                .map(w -> (Button)w).findFirst().orElseThrow();
        check(button.visible && button.active, "Tree button hidden/disabled");
        screen.mouseClicked(new MouseButtonEvent(button.getX() + 3, button.getY() + 3, new MouseButtonInfo(0, 0)), false);
    }
    private static void focusNode(int id) throws Exception {
        var center = RecipeTreeScreen.class.getDeclaredField("centerOnRoot"); center.setAccessible(true);
        center.setBoolean(Minecraft.getInstance().screen, false);
        for (var box : (List<?>)field("boxes")) {
            var getId = box.getClass().getDeclaredMethod("id"); getId.setAccessible(true);
            if ((int)getId.invoke(box) != id) continue;
            var x = box.getClass().getDeclaredMethod("x"); x.setAccessible(true);
            var y = box.getClass().getDeclaredMethod("y"); y.setAccessible(true);
            var px = RecipeTreeScreen.class.getDeclaredField("panX"); px.setAccessible(true);
            var py = RecipeTreeScreen.class.getDeclaredField("panY"); py.setAccessible(true);
            px.setDouble(Minecraft.getInstance().screen, 30 - (int)x.invoke(box));
            py.setDouble(Minecraft.getInstance().screen, 12 - (int)y.invoke(box));
        }
    }
    private static void removeDefaultInManager() {
        var mc = Minecraft.getInstance(); clickButton("Defaults: 1");
        int w = Math.min(420, mc.screen.width - 32), h = Math.min(260, mc.screen.height - 32);
        mc.screen.mouseClicked(new MouseButtonEvent((mc.screen.width - w) / 2 + 12, (mc.screen.height - h) / 2 + 52, new MouseButtonInfo(0, 0)), false);
        mc.screen.keyPressed(new net.minecraft.client.input.KeyEvent(256, 0, 0));
    }
    private static void shiftClickSelected() { Minecraft.getInstance().screen.mouseClicked(new MouseButtonEvent(40, 80, new MouseButtonInfo(0, 1)), false); }
    private static void requestTerminal(Minecraft mc) {
        var server = mc.getSingleplayerServer(); var part = terminals.get(terminal);
        server.execute(() -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            try { check(MenuOpener.open(part instanceof appeng.parts.reporting.PatternAccessTerminalPart
                    ? appeng.menu.implementations.PatternAccessTermMenu.TYPE
                    : ((appeng.parts.reporting.AbstractTerminalPart)part).getMenuType(player), player, MenuLocators.forPart(part)), "Menu open returned false"); }
            catch (Throwable e) { serverFailure = e; }
        });
    }
}
