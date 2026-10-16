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
    private static final int ORANGE = 0xFFFF9A32, CARD_WIDTH = 184, CARD_HEIGHT = 96;
    private final RecipeTreePackets.Open snapshot;
    private final Set<String> excluded;
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
        this.excluded = new HashSet<>(snapshot.excluded());
        expanded.add(0);
        for (int i = 1; i < snapshot.tree().nodes().size(); i++)
            children.computeIfAbsent(snapshot.tree().nodes().get(i).parent(), k -> new ArrayList<>()).add(i);
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
        boolean recipe = selected >= 0 && snapshot.tree().nodes().get(selected).recipe();
        routeButton.active = recipe && !waiting && !invalidSession;
        routeButton.setMessage(tr(recipe && excluded.contains(snapshot.tree().nodes().get(selected).recipeId()) ? "enable" : "disable"));
    }
    private void toggleSelected() {
        if (waiting || invalidSession || selected < 0) return;
        var node = snapshot.tree().nodes().get(selected);
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
        super.tick();
        if (waiting && ++savingTicks >= 20 * 30) {
            waiting = false;
            invalidSession = true;
            status = tr("save_timeout");
            updateButtons();
        }
    }

    // Preorder rows keep siblings separated; branches expand without overlapping cards.
    private int layout(int id, int depth, int row) {
        boxes.add(new Box(id, depth * (CARD_WIDTH + 38), row * (CARD_HEIGHT + 14)));
        int next = row + 1;
        if (expanded.contains(id)) for (int child : children.getOrDefault(id, List.of())) next = layout(child, depth + 1, next);
        return next;
    }
    private boolean inCanvas(double x, double y) { return x >= 8 && x < width - 8 && y >= 57 && y < height - 105; }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
        g.fill(0, 0, width, height, 0xFF18120D);
        g.fill(0, 0, width, 3, ORANGE);
        g.text(font, title, 12, 14, ORANGE);
        g.textWithWordWrap(font, tr("scope"), 12, 32, width - 24, 0xFFD6BDA6);
        g.fill(8, 57, width - 8, height - 105, 0xFF241A12);
        g.outline(8, 57, width - 16, height - 162, 0xFF9B5625);
        boxes.clear();
        if (!snapshot.tree().nodes().isEmpty()) layout(0, 0, 0);
        Box hovered = null;
        g.enableScissor(9, 58, width - 9, height - 106);
        g.pose().pushMatrix(); g.pose().translate((float)panX, (float)(57 + panY)); g.pose().scale((float)zoom);
        Map<Integer, Box> byId = new HashMap<>();
        for (var box : boxes) byId.put(box.id(), box);
        for (var box : boxes) {
            int parent = snapshot.tree().nodes().get(box.id()).parent();
            var p = byId.get(parent);
            if (p == null) continue;
            int x1 = p.x() + CARD_WIDTH, y1 = p.y() + CARD_HEIGHT / 2;
            int x2 = box.x(), y2 = box.y() + CARD_HEIGHT / 2, bend = x1 + 18;
            g.horizontalLine(x1, bend, y1, 0xFFB86C2D);
            g.verticalLine(bend, Math.min(y1, y2), Math.max(y1, y2), 0xFFB86C2D);
            g.horizontalLine(bend, x2, y2, 0xFFB86C2D);
        }
        double x = (mx - panX) / zoom, y = (my - 57 - panY) / zoom;
        for (var box : boxes) {
            var node = snapshot.tree().nodes().get(box.id());
            boolean banned = node.recipe() && excluded.contains(node.recipeId());
            g.fill(box.x(), box.y(), box.x() + CARD_WIDTH, box.y() + CARD_HEIGHT, banned ? 0xFF412A22 : 0xFF37271A);
            g.outline(box.x(), box.y(), CARD_WIDTH, CARD_HEIGHT, box.id() == selected ? 0xFFFFCA8A : ORANGE);
            if (!node.item().isEmpty()) { g.item(node.item(), box.x() + 8, box.y() + 8); g.itemDecorations(font, node.item(), box.x() + 8, box.y() + 8); }
            g.text(font, font.plainSubstrByWidth(node.item().getHoverName().getString(), CARD_WIDTH - 38), box.x() + 30, box.y() + 12, 0xFFFFE8D0);
            if (node.recipe()) {
                for (int slot = 0; slot < 9; slot++) {
                    int sx = box.x() + 8 + slot % 3 * 18, sy = box.y() + 30 + slot / 3 * 18;
                    g.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF21170F);
                    g.item(node.grid().get(slot), sx, sy);
                }
                g.text(font, tr(banned ? "disabled" : "allowed"), box.x() + 70, box.y() + 34, banned ? 0xFFFFAA88 : ORANGE);
                g.text(font, font.plainSubstrByWidth(node.recipeId(), CARD_WIDTH - 76), box.x() + 70, box.y() + 49, 0xFFD6BDA6);
                g.text(font, tr("example"), box.x() + 70, box.y() + 64, 0xFFBDA58E);
            } else {
                boolean allDisabled = children.getOrDefault(box.id(), List.of()).stream()
                        .filter(i -> snapshot.tree().nodes().get(i).recipe()).allMatch(i -> excluded.contains(snapshot.tree().nodes().get(i).recipeId()));
                boolean hasRecipes = children.getOrDefault(box.id(), List.of()).stream().anyMatch(i -> snapshot.tree().nodes().get(i).recipe());
                var line = box.id() == 0 ? tr("root") : hasRecipes && allDisabled ? tr("all_disabled") : tr("note." + node.note());
                g.textWithWordWrap(font, line, box.x() + 8, box.y() + 34, CARD_WIDTH - 16, ORANGE);
            }
            if (children.containsKey(box.id())) g.text(font, expanded.contains(box.id()) ? "[-]" : "[+]", box.x() + CARD_WIDTH - 24, box.y() + CARD_HEIGHT - 12, ORANGE);
            if (inCanvas(mx, my) && x >= box.x() && x < box.x() + CARD_WIDTH && y >= box.y() && y < box.y() + CARD_HEIGHT) hovered = box;
        }
        g.pose().popMatrix(); g.disableScissor();
        g.text(font, tr("count", excluded.size(), snapshot.excluded().size()), 12, height - 96, ORANGE);
        if (snapshot.tree().truncated()) g.textWithWordWrap(font, tr("limited"), 12, height - 81, width - 24, 0xFFD6BDA6);
        g.textWithWordWrap(font, status.getString().isEmpty() ? tr("help") : status, 12, height - 56, width - 24, ORANGE);
        super.extractRenderState(g, mx, my, partial);
        if (hovered != null) {
            var node = snapshot.tree().nodes().get(hovered.id());
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
            var node = snapshot.tree().nodes().get(selected);
            if (event.button() == 1 && node.recipe()) toggleSelected();
            else if (!node.recipe() || doubleClick || y >= box.y() + CARD_HEIGHT - 18) {
                if (!expanded.remove(selected)) expanded.add(selected);
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
        if (event.key() == 32 && selected >= 0 && snapshot.tree().nodes().get(selected).recipe()) { toggleSelected(); return true; }
        return super.keyPressed(event);
    }
}
