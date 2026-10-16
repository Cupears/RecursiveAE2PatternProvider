package com.lumengrid.recursiveae2patternprovider.client;

import com.lumengrid.recursiveae2patternprovider.*;
import com.lumengrid.recursiveae2patternprovider.network.RecipeTreePackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import java.util.*;

/** Server-authored recipe alternatives with explicit per-item save/cancel. */
public final class RecipeTreeScreen extends Screen {
    private static final int ORANGE = 0xFFFF9A32, CARD_WIDTH = 132, CARD_HEIGHT = 76;
    private final RecipeTreePackets.Open snapshot;
    private final Set<String> excluded;
    private final List<RecipeTree.Node> nodes;
    private final Set<Integer> complete = new HashSet<>();
    private final Map<Integer, Integer> offsets = new HashMap<>();
    private final Map<Integer, Integer> pending = new HashMap<>();
    private int ticks;
    private final Set<Integer> expanded = new HashSet<>();
    private final List<Box> boxes = new ArrayList<>();
    private final Map<Integer, List<Integer>> children = new HashMap<>();
    private int selected = -1;
    private double panX = 16, panY = 12, zoom = 1;
    private boolean dragging, waiting, closed, invalidSession;
    private Component status = Component.empty();
    private Button routeButton, saveButton, cancelButton;
    private int savingTicks;
    private record Box(int id, int x, int y) {}

    private RecipeTreeScreen(RecipeTreePackets.Open snapshot) {
        super(tr("title"));
        this.snapshot = snapshot;
        this.nodes = new ArrayList<>(snapshot.tree().nodes());
        this.excluded = new HashSet<>(snapshot.excluded());
        expanded.add(0);
        for (int i = 1; i < nodes.size(); i++)
            children.computeIfAbsent(nodes.get(i).parent(), k -> new ArrayList<>()).add(i);
    }
    private void requestBranch(int id) {
        if (id == 0 || nodes.get(id).recipe() || nodes.get(id).note() == 2 || complete.contains(id)
                || pending.containsKey(id) || invalidSession || waiting) return;
        pending.put(id, ticks);
        ClientPacketDistributor.sendToServer(new RecipeTreePackets.Expand(snapshot.session(), id, offsets.getOrDefault(id, 0)));
    }
    public static void branch(RecipeTreePackets.Branch packet) {
        var mc = Minecraft.getInstance();
        if (!(mc.screen instanceof RecipeTreeScreen screen) || !screen.snapshot.session().equals(packet.session())) return;
        if (screen.invalidSession) return;
        if (packet.start() != screen.nodes.size() || !screen.pending.containsKey(packet.parent())) {
            screen.invalidSession = true; screen.status = tr("error"); screen.saveButton.active = false; screen.updateButtons(); return;
        }
        screen.pending.remove(packet.parent());
        for (var node : packet.page().nodes()) {
            int id = screen.nodes.size(); screen.nodes.add(node);
            screen.children.computeIfAbsent(node.parent(), k -> new ArrayList<>()).add(id);
        }
        var old = screen.nodes.get(packet.parent());
        screen.nodes.set(packet.parent(), new RecipeTree.Node(old.parent(), false, old.item(), "", List.of(), packet.page().note()));
        screen.offsets.put(packet.parent(), packet.page().nextOffset());
        if (packet.page().done()) screen.complete.add(packet.parent());
        else if (screen.expanded.contains(packet.parent())) screen.requestBranch(packet.parent());
    }
    private static Component tr(String key, Object... args) {
        return Component.translatable("gui.recursiveae2patternprovider.tree." + key, args);
    }
    public static void open(RecipeTreePackets.Open snapshot) {
        Minecraft.getInstance().setScreen(new RecipeTreeScreen(snapshot));
    }
    public static void result(RecipeTreePackets.Result result) {
        var mc = Minecraft.getInstance();
        if (!(mc.screen instanceof RecipeTreeScreen screen) || !screen.snapshot.session().equals(result.session())) return;
        screen.waiting = false;
        if (result.saved()) {
            screen.closed = true;
            mc.setScreen(null);
            if (mc.player != null) mc.gui.setOverlayMessage(tr("saved"), false);
        } else {
            screen.status = tr(Set.of("stale", "invalid").contains(result.message()) ? result.message() : "error");
            screen.invalidSession = true;
            screen.saveButton.active = false;
            screen.updateButtons();
        }
    }
    @Override protected void init() {
        int w = Math.max(32, (width - 32) / 5);
        routeButton = addRenderableWidget(Button.builder(tr("disable"), b -> toggleSelected())
                .bounds(8, height - 27, w, 20).build());
        addRenderableWidget(Button.builder(tr("reset_rules"), b -> { if (!waiting) { excluded.clear(); updateButtons(); } })
                .bounds(12 + w, height - 27, w, 20).build());
        addRenderableWidget(Button.builder(tr("reset_view"), b -> { panX = 16; panY = 12; zoom = 1; expanded.clear(); expanded.add(0); })
                .bounds(16 + w * 2, height - 27, w, 20).build());
        saveButton = addRenderableWidget(Button.builder(tr("save"), b -> save())
                .bounds(20 + w * 3, height - 27, w, 20).build());
        cancelButton = addRenderableWidget(Button.builder(tr("cancel"), b -> onClose())
                .bounds(24 + w * 4, height - 27, w, 20).build());
        saveButton.active = !waiting && !invalidSession;
        updateButtons();
    }
    private void updateButtons() {
        cancelButton.active = !waiting;
        boolean recipe = selected >= 0 && nodes.get(selected).recipe();
        routeButton.active = recipe && !waiting && !invalidSession;
        routeButton.setMessage(tr(recipe && excluded.contains(nodes.get(selected).recipeId()) ? "enable" : "disable"));
    }
    private void toggleSelected() {
        if (waiting || invalidSession || selected < 0) return;
        var node = nodes.get(selected);
        if (!node.recipe()) return;
        if (!excluded.remove(node.recipeId())) {
            if (excluded.size() >= PatternRecipeRules.MAX_EXCLUSIONS) { status = tr("rule_limit"); return; }
            excluded.add(node.recipeId());
        }
        status = Component.empty(); updateButtons();
    }
    private void save() {
        if (waiting || invalidSession) return;
        waiting = true; savingTicks = 0; saveButton.active = false; updateButtons(); status = tr("saving");
        ClientPacketDistributor.sendToServer(new RecipeTreePackets.Save(snapshot.session(), true, Set.copyOf(excluded)));
    }
    @Override public void onClose() {
        if (waiting) return;
        if (!closed) ClientPacketDistributor.sendToServer(new RecipeTreePackets.Save(snapshot.session(), false, Set.of()));
        closed = true; super.onClose();
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void tick() {
        super.tick(); ticks++;
        if (pending.values().stream().anyMatch(start -> ticks - start >= 20 * 30)) {
            pending.clear(); invalidSession = true; saveButton.active = false; status = tr("branch_timeout"); updateButtons();
        }
        if (waiting && ++savingTicks >= 20 * 30) {
            waiting = false;
            invalidSession = true;
            status = tr("save_timeout");
            updateButtons();
        }
    }

    // Iterative layout supports arbitrarily deep expanded branches without recursion.
    private void layout() {
        record Pending(int id, int depth) {}
        var stack = new ArrayDeque<Pending>(); stack.push(new Pending(0, 0)); int row = 0;
        while (!stack.isEmpty()) {
            var next = stack.pop();
            boxes.add(new Box(next.id(), next.depth() * (CARD_WIDTH + 16), row * (CARD_HEIGHT + 8)));
            var nested = expanded.contains(next.id()) ? children.getOrDefault(next.id(), List.of()) : List.<Integer>of();
            if (!nested.isEmpty()) {
                for (int i = nested.size() - 1; i >= 0; i--) stack.push(new Pending(nested.get(i), next.depth() + 1));
            } else row++;
        }
    }
    private boolean inCanvas(double x, double y) { return x >= 8 && x < width - 8 && y >= 57 && y < height - 70; }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
        g.fill(0, 0, width, height, 0x4818120D);
        g.fill(0, 0, width, 3, ORANGE);
        g.text(font, title, 12, 14, ORANGE);
        g.textWithWordWrap(font, tr("scope"), 12, 32, width - 24, 0xFFD6BDA6);
        g.fill(8, 57, width - 8, height - 70, 0x18241A12);
        g.outline(8, 57, width - 16, height - 127, 0xFF9B5625);
        boxes.clear();
        if (!nodes.isEmpty()) layout();
        Box hovered = null;
        g.enableScissor(9, 58, width - 9, height - 71);
        g.pose().pushMatrix(); g.pose().translate((float)panX, (float)(57 + panY)); g.pose().scale((float)zoom);
        Map<Integer, Box> byId = new HashMap<>();
        for (var box : boxes) byId.put(box.id(), box);
        for (var box : boxes) {
            int parent = nodes.get(box.id()).parent();
            var p = byId.get(parent);
            if (p == null) continue;
            int x1 = p.x() + CARD_WIDTH, y1 = p.y() + CARD_HEIGHT / 2;
            int x2 = box.x(), y2 = box.y() + CARD_HEIGHT / 2, bend = x1 + 8;
            g.horizontalLine(x1, bend, y1, 0xFFB86C2D);
            g.verticalLine(bend, Math.min(y1, y2), Math.max(y1, y2), 0xFFB86C2D);
            g.horizontalLine(bend, x2, y2, 0xFFB86C2D);
        }
        double x = (mx - panX) / zoom, y = (my - 57 - panY) / zoom;
        for (var box : boxes) {
            if (box.x() + CARD_WIDTH < (9 - panX) / zoom || box.x() > (width - 9 - panX) / zoom
                    || box.y() + CARD_HEIGHT < (58 - 57 - panY) / zoom || box.y() > (height - 71 - 57 - panY) / zoom) continue;
            var node = nodes.get(box.id());
            boolean banned = node.recipe() && excluded.contains(node.recipeId());
            g.fill(box.x(), box.y(), box.x() + CARD_WIDTH, box.y() + CARD_HEIGHT, banned ? 0xBB412A22 : 0xAA37271A);
            g.outline(box.x(), box.y(), CARD_WIDTH, CARD_HEIGHT, box.id() == selected ? 0xFFFFCA8A : ORANGE);
            if (!node.item().isEmpty()) { g.item(node.item(), box.x() + 5, box.y() + 5); g.itemDecorations(font, node.item(), box.x() + 5, box.y() + 5); }
            g.text(font, font.plainSubstrByWidth(node.item().getHoverName().getString(), CARD_WIDTH - 28), box.x() + 24, box.y() + 8, 0xFFFFE8D0);
            if (node.recipe()) {
                for (int slot = 0; slot < 9; slot++) {
                    int sx = box.x() + 5 + slot % 3 * 16, sy = box.y() + 23 + slot / 3 * 16;
                    g.fill(sx - 1, sy - 1, sx + 16, sy + 16, 0xFF21170F);
                    g.item(node.grid().get(slot), sx, sy);
                }
                g.text(font, tr(banned ? "disabled" : "allowed"), box.x() + 58, box.y() + 26, banned ? 0xFFFFAA88 : ORANGE);
                g.text(font, font.plainSubstrByWidth(node.recipeId(), CARD_WIDTH - 62), box.x() + 58, box.y() + 39, 0xFFD6BDA6);
            } else {
                boolean allDisabled = children.getOrDefault(box.id(), List.of()).stream()
                        .filter(i -> nodes.get(i).recipe()).allMatch(i -> excluded.contains(nodes.get(i).recipeId()));
                boolean hasRecipes = children.getOrDefault(box.id(), List.of()).stream().anyMatch(i -> nodes.get(i).recipe());
                var line = pending.containsKey(box.id()) ? tr("loading") : box.id() == 0 ? tr("root") : hasRecipes && allDisabled ? tr("all_disabled") : tr("note." + node.note());
                g.textWithWordWrap(font, line, box.x() + 8, box.y() + 27, CARD_WIDTH - 16, ORANGE);
            }
            if (children.containsKey(box.id()) || (!node.recipe() && box.id() != 0 && node.note() == 0)) g.text(font, expanded.contains(box.id()) ? "[-]" : "[+]", box.x() + CARD_WIDTH - 24, box.y() + CARD_HEIGHT - 12, ORANGE);
            if (inCanvas(mx, my) && x >= box.x() && x < box.x() + CARD_WIDTH && y >= box.y() && y < box.y() + CARD_HEIGHT) hovered = box;
        }
        g.pose().popMatrix(); g.disableScissor();
        g.text(font, tr("count", excluded.size(), snapshot.excluded().size()), 12, height - 61, ORANGE);
        g.textWithWordWrap(font, status.getString().isEmpty() ? tr("help") : status, 12, height - 46, width - 24, ORANGE);
        super.extractRenderState(g, mx, my, partial);
        if (hovered != null) {
            var node = nodes.get(hovered.id());
            var lines = new ArrayList<Component>();
            lines.add(node.item().getHoverName());
            if (node.recipe()) { lines.add(Component.literal(node.recipeId())); lines.add(tr("recipe_help")); lines.add(tr("variants")); }
            else lines.add(tr("item_help"));
            g.setComponentTooltipForNextFrame(font, lines, mx, my);
        }
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!inCanvas(event.x(), event.y())) return super.mouseClicked(event, doubleClick);
        double x = (event.x() - panX) / zoom, y = (event.y() - 57 - panY) / zoom;
        for (var box : boxes) if (x >= box.x() && x < box.x() + CARD_WIDTH && y >= box.y() && y < box.y() + CARD_HEIGHT) {
            selected = box.id(); updateButtons();
            var node = nodes.get(selected);
            if (event.button() == 1 && node.recipe()) toggleSelected();
            else if (!node.recipe() || doubleClick || y >= box.y() + CARD_HEIGHT - 18) {
                if (!expanded.remove(selected)) { expanded.add(selected); requestBranch(selected); }
            }
            return true;
        }
        dragging = event.button() == 0; return true;
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging) { panX += dx; panY += dy; return true; }
        return super.mouseDragged(event, dx, dy);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) { dragging = false; return super.mouseReleased(event); }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (!inCanvas(x, y)) return super.mouseScrolled(x, y, horizontal, vertical);
        panY += vertical * 40; panX += horizontal * 40; return true;
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (event.key() == 61 || event.key() == 334) { zoom = Math.min(1.5, zoom + .1); return true; }
        if (event.key() == 45 || event.key() == 333) { zoom = Math.max(.5, zoom - .1); return true; }
        if (event.key() == 32 && selected >= 0 && nodes.get(selected).recipe()) { toggleSelected(); return true; }
        return super.keyPressed(event);
    }
}
