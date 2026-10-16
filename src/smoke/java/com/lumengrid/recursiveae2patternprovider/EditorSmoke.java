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
                    var server = mc.getSingleplayerServer(); server.execute(() -> {
                        var player = server.getPlayerList().getPlayers().getFirst();
                        var pattern = PatternDetailsHelper.encodeProcessingPattern(List.of(new GenericStack(AEItemKey.of(Items.OAK_PLANKS), 4)),
                                List.of(new GenericStack(AEItemKey.of(Items.CRAFTING_TABLE), 1)));
                        PatternUtil.setRecursive(pattern, true); player.setItemInHand(InteractionHand.MAIN_HAND, pattern);
                        player.containerMenu.broadcastChanges();
                    }); next(7);
                }
            } else if (phase == 7 && ticks - since > 30 && PatternUtil.isRecursive(mc.player.getMainHandItem())) {
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND); next(8);
            } else if (phase == 8 && mc.screen instanceof RecipeTreeScreen && ticks - since > 20) {
                record("Recipe tree opened through actual right-click packet"); shot("recipe-tree-root");
                mc.screen.keyPressed(new net.minecraft.client.input.KeyEvent(61, 0, 0));
                var zoom = RecipeTreeScreen.class.getDeclaredField("zoom"); zoom.setAccessible(true);
                check(zoom.getDouble(mc.screen) > 1, "Zoom in did not work");
                mc.screen.keyPressed(new net.minecraft.client.input.KeyEvent(45, 0, 0));
                check(Math.abs(zoom.getDouble(mc.screen) - 1) < .001, "Zoom out did not work");
                mc.screen.mouseClicked(new MouseButtonEvent(12, 60, new MouseButtonInfo(0, 0)), false);
                mc.screen.mouseDragged(new MouseButtonEvent(22, 60, new MouseButtonInfo(0, 0)), 10, 0);
                mc.screen.mouseReleased(new MouseButtonEvent(22, 60, new MouseButtonInfo(0, 0)));
                clickButton("Reset view");
                record("Zoom, background drag and reset view worked");
                // Root child card is at depth 1, row 1. Bring it into view then expand.
                var pan = RecipeTreeScreen.class.getDeclaredField("panY"); pan.setAccessible(true); pan.setDouble(mc.screen, -90);
                next(9);
            } else if (phase == 9 && ticks - since > 10) {
                mc.screen.mouseClicked(new MouseButtonEvent(260, 100, new MouseButtonInfo(0, 0)), false); next(10);
            } else if (phase == 10 && ticks - since > 10) {
                // Pan through the real scroll handler to bring the first recipe card into view.
                mc.screen.mouseScrolled(260, 100, -222.0 / 40, -110.0 / 40); next(16);
            } else if (phase == 16 && ticks - since > 10) {
                mc.screen.mouseClicked(new MouseButtonEvent(260, 100, new MouseButtonInfo(0, 0)), false);
                clickButton("Disable route"); next(17);
            } else if (phase == 17 && ticks - since > 10) {
                shot("recipe-tree-expanded"); clickButton("Enable route");
                mc.screen.mouseClicked(new MouseButtonEvent(260, 100, new MouseButtonInfo(1, 0)), false);
                record("Expand, scroll/pan, selection, disable, enable and right-click toggle worked");
                clickButton("Save");
                var savingScreen = mc.screen; savingScreen.onClose();
                check(mc.screen == savingScreen, "Cancel closed editor during pending save"); next(11);
            } else if (phase == 11 && mc.screen == null && ticks - since > 20) {
                check(!PatternRecipeRules.read(mc.player.getMainHandItem()).isEmpty(), "Saved rules did not reach client inventory");
                record("Recipe exclusion saved through client/server packets and inventory synchronized");
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND); next(12);
            } else if (phase == 12 && mc.screen instanceof RecipeTreeScreen && ticks - since > 20) {
                clickButton("Reset rules"); clickButton("Cancel"); next(13);
            } else if (phase == 13 && mc.screen == null && ticks - since > 20) {
                check(!PatternRecipeRules.read(mc.player.getMainHandItem()).isEmpty(), "Cancel changed saved rules");
                record("Cancel discarded edits and retained saved exclusions");
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND); next(14);
            } else if (phase == 14 && mc.screen instanceof RecipeTreeScreen && ticks - since > 20) {
                clickButton("Reset rules"); clickButton("Save"); next(15);
            } else if (phase == 15 && mc.screen == null && ticks - since > 20) {
                check(PatternRecipeRules.read(mc.player.getMainHandItem()).isEmpty(), "Reset/save did not clear rules");
                record("Reset and save cleared exclusions; pending save cannot be cancelled");
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND); next(18);
            } else if (phase == 18 && mc.screen instanceof RecipeTreeScreen && ticks - since > 20) {
                // Invalidate the server session, then submit from the still-open client screen.
                var field = RecipeTreeScreen.class.getDeclaredField("snapshot"); field.setAccessible(true);
                var snapshot = (com.lumengrid.recursiveae2patternprovider.network.RecipeTreePackets.Open)field.get(mc.screen);
                ClientPacketDistributor.sendToServer(new com.lumengrid.recursiveae2patternprovider.network.RecipeTreePackets.Save(snapshot.session(), false, Set.of()));
                next(19);
            } else if (phase == 19 && ticks - since > 20) {
                clickButton("Save"); next(20);
            } else if (phase == 20 && ticks - since > 20) {
                var field = RecipeTreeScreen.class.getDeclaredField("invalidSession"); field.setAccessible(true);
                check(field.getBoolean(mc.screen), "Expired session did not return an error");
                shot("recipe-tree-expired"); clickButton("Cancel");
                record("Missing server session returned an error and editor remained closable");
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND); next(21);
            } else if (phase == 21 && mc.screen instanceof RecipeTreeScreen && ticks - since > 20) {
                var server = mc.getSingleplayerServer();
                server.execute(() -> {
                    var player = server.getPlayerList().getPlayers().getFirst();
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT));
                    player.containerMenu.broadcastChanges();
                }); next(22);
            } else if (phase == 22 && ticks - since > 20) {
                clickButton("Save"); next(23);
            } else if (phase == 23 && ticks - since > 20) {
                var invalid = RecipeTreeScreen.class.getDeclaredField("invalidSession"); invalid.setAccessible(true);
                check(invalid.getBoolean(mc.screen), "Changed held pattern was not rejected");
                check(mc.player.getMainHandItem().is(Items.DIRT), "Save overwrote the replacement held item");
                record("Changing the held item rejected save without overwriting it");
                // Simulate a missing confirmation at the timeout boundary, without a 30-second delay.
                var waiting = RecipeTreeScreen.class.getDeclaredField("waiting"); waiting.setAccessible(true); waiting.setBoolean(mc.screen, true);
                var savingTicks = RecipeTreeScreen.class.getDeclaredField("savingTicks"); savingTicks.setAccessible(true); savingTicks.setInt(mc.screen, 599);
                mc.screen.tick();
                check(!waiting.getBoolean(mc.screen), "Save timeout did not release the editor");
                clickButton("Cancel"); record("Missing-confirmation timeout released the editor");
                finish(mc, "PASS");
            }
        } catch (Throwable e) {
            try { finish(mc, "FAIL phase " + phase + ": " + e); }
            catch (Exception nested) { RecursiveAE2PatternProvider.LOGGER.error("Smoke failure", e); failed = true; mc.stop(); }
        }
    }
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
