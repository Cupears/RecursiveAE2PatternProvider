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
    private static final int ORANGE = 0xFFFFC98A, CARD_WIDTH = 132, CARD_HEIGHT = 82;
    private final RecipeTreePackets.Open snapshot;
    private final Screen terminalScreen;
    private final net.minecraft.world.inventory.AbstractContainerMenu terminalMenu;
    private final Map<appeng.api.stacks.AEItemKey, Integer> firstBranch = new HashMap<>();
    private final Map<Integer, Integer> sharedBranches = new HashMap<>();
    private final ArrayDeque<Integer> loadQueue = new ArrayDeque<>();
    private boolean layoutDirty = true, centerOnRoot = true;
    private final Set<String> excluded;
    private final Set<String> originalDefaults;
    private final Set<String> personalDefaults = new HashSet<>();
    private boolean defaultsWritable = true, managingDefaults;
    private int defaultsScroll;
    private final Map<Integer, Integer> groupHeads = new HashMap<>();
    private final Map<Integer, List<Integer>> groupMembers = new HashMap<>();
    private final Map<Integer, Map<Object, Integer>> siblingGroups = new HashMap<>();
    private final Map<Integer, List<Integer>> groupedChildren = new HashMap<>();
    private final Map<Integer, Integer> variantChoice = new HashMap<>();
    private final List<RecipeTree.Node> nodes;
    private final Set<Integer> complete = new HashSet<>();
    private final Map<Integer, Integer> offsets = new HashMap<>();
    private final Map<Integer, Integer> pending = new HashMap<>();
    private int ticks;
    private final Set<Integer> expanded = new HashSet<>();
    private final List<Box> boxes = new ArrayList<>();
    private final Map<Integer, List<Integer>> children = new HashMap<>();
    private int selected = -1, hoveredHead = -1, hoveredVariant;
    private double panX = 16, panY = 12, zoom = 1;
    private boolean dragging, waiting, closed, invalidSession;
    private Component status = Component.empty();
    private Button routeButton, alwaysButton, defaultsButton, saveButton, cancelButton;
    private int savingTicks;
    private record Box(int id, int x, int y) {}

    private RecipeTreeScreen(RecipeTreePackets.Open snapshot) {
        super(tr("title"));
        this.snapshot = snapshot;
        this.terminalScreen = Minecraft.getInstance().screen;
        this.terminalMenu = Minecraft.getInstance().player.containerMenu;
        this.nodes = new ArrayList<>(snapshot.tree().nodes());
        this.excluded = new HashSet<>(snapshot.excluded());
        try { personalDefaults.addAll(RecipeDefaults.load(ClientRecipeDefaults.path())); }
        catch (java.io.IOException e) { defaultsWritable = false; status = tr("defaults_error");
            RecursiveAE2PatternProvider.LOGGER.warn("Could not read personal recipe defaults", e); }
        originalDefaults = Set.copyOf(personalDefaults);
        if (snapshot.tree().truncated()) loadQueue.add(0);
        else complete.add(0);
        expanded.add(0);
        for (int i = 0; i < nodes.size(); i++) {
            if (i > 0) children.computeIfAbsent(nodes.get(i).parent(), k -> new ArrayList<>()).add(i);
            registerNode(i);
        }
    }
    private void registerNode(int id) {
        var node = nodes.get(id);
        Object key = TreePresentation.groupKey(node);
        var siblings = siblingGroups.computeIfAbsent(node.parent(), ignored -> new HashMap<>());
        Integer existing = key == null ? null : siblings.get(key);
        int head = existing == null ? id : existing;
        groupHeads.put(id, head);
        groupMembers.computeIfAbsent(head, ignored -> new ArrayList<>()).add(id);
        if (existing == null) {
            groupedChildren.computeIfAbsent(node.parent(), ignored -> new ArrayList<>()).add(id);
            if (key != null) siblings.put(key, id);
        } else if (groupMembers.get(head).size() == 2 && !variantChoice.containsKey(head)) expanded.remove(head);
        if (!TreePresentation.compressed(node) && !(node.recipe() && excluded.contains(node.recipeId()))) expanded.add(id);
        if (id == 0 || node.recipe() || node.note() == 2 || node.item().isEmpty()) return;
        var previous = firstBranch.putIfAbsent(appeng.api.stacks.AEItemKey.of(node.item()), id);
        if (previous == null) loadQueue.add(id);
        else sharedBranches.put(id, previous);
    }
    private int head(int id) { return groupHeads.getOrDefault(id, id); }
    private int displayed(int id) {
        int head = head(id);
        var members = groupMembers.getOrDefault(head, List.of(id));
        int choice = variantChoice.getOrDefault(head, head == hoveredHead ? hoveredVariant : expanded.contains(head) ? 0 : ticks / 40);
        return members.get(Math.floorMod(choice, members.size()));
    }
    private List<Integer> visibleChildren(int id) { return groupedChildren.getOrDefault(displayed(id), List.of()); }
    private void collapseRecipe(String recipeId) {
        for (int i = 0; i < nodes.size(); i++) if (nodes.get(i).recipe() && nodes.get(i).recipeId().equals(recipeId)) expanded.remove(head(i));
        layoutDirty = true;
    }
    private void toggleAlways() {
        if (waiting || invalidSession || !defaultsWritable || selected < 0 || !nodes.get(selected).recipe()) return;
        String id = nodes.get(selected).recipeId();
        if (personalDefaults.contains(id)) { personalDefaults.remove(id); excluded.remove(id); }
        else {
            if (personalDefaults.size() >= PatternRecipeRules.MAX_EXCLUSIONS || !excluded.contains(id) && excluded.size() >= PatternRecipeRules.MAX_EXCLUSIONS) {
                status = tr("rule_limit"); return;
            }
            personalDefaults.add(id); excluded.add(id); collapseRecipe(id);
        }
        status = Component.empty(); updateButtons();
    }
    private void returnToTerminal() {
        var mc = Minecraft.getInstance();
        mc.setScreen(mc.player != null && mc.player.containerMenu == terminalMenu ? terminalScreen : null);
    }
    private void requestBranch(int id) {
        if (nodes.get(id).recipe() || nodes.get(id).note() == 2 || complete.contains(id)
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
        screen.layoutDirty = true;
        for (var node : packet.page().nodes()) {
            int id = screen.nodes.size(); screen.nodes.add(node);
            screen.children.computeIfAbsent(node.parent(), k -> new ArrayList<>()).add(id);
            screen.registerNode(id);
        }
        var old = screen.nodes.get(packet.parent());
        screen.nodes.set(packet.parent(), new RecipeTree.Node(old.parent(), false, old.item(), "", List.of(), packet.page().note()));
        screen.offsets.put(packet.parent(), packet.page().nextOffset());
        if (packet.page().done()) screen.complete.add(packet.parent());
        else screen.loadQueue.addFirst(packet.parent());
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
            boolean defaultsSaved = true;
            if (screen.defaultsWritable) try {
                RecipeDefaults.saveChanges(ClientRecipeDefaults.path(), screen.originalDefaults, screen.personalDefaults);
                ClientRecipeDefaults.send(Set.copyOf(screen.personalDefaults));
            } catch (java.io.IOException e) {
                defaultsSaved = false; RecursiveAE2PatternProvider.LOGGER.warn("Could not save personal recipe defaults", e);
            }
            screen.returnToTerminal();
            if (mc.player != null) mc.gui.setOverlayMessage(tr(defaultsSaved ? "saved" : "defaults_save_error"), false);
        } else {
            screen.status = tr(Set.of("stale", "invalid").contains(result.message()) ? result.message() : "error");
            screen.invalidSession = true;
            screen.saveButton.active = false;
            screen.updateButtons();
        }
    }
    @Override protected void init() {
        layoutDirty = true; centerOnRoot = true;
        int w = Math.max(32, (width - 36) / 6);
        routeButton = addRenderableWidget(Button.builder(tr("disable"), b -> toggleSelected())
                .bounds(8, height - 27, w, 20).build(MenuButton::new));
        alwaysButton = addRenderableWidget(Button.builder(tr("always"), b -> toggleAlways())
                .bounds(12 + w, height - 27, w, 20).build(MenuButton::new));
        defaultsButton = addRenderableWidget(Button.builder(tr("defaults", personalDefaults.size()), b -> { managingDefaults = true; defaultsScroll = 0; })
                .bounds(width - 112, 10, 100, 20).build(MenuButton::new));
        defaultsButton.active = defaultsWritable && !waiting;
        addRenderableWidget(Button.builder(tr("reset_rules"), b -> { if (!waiting) { excluded.clear(); updateButtons(); } })
                .bounds(16 + w * 2, height - 27, w, 20).build(MenuButton::new));
        addRenderableWidget(Button.builder(tr("reset_view"), b -> { panY = 12; zoom = 1; centerOnRoot = true; layoutDirty = true; })
                .bounds(20 + w * 3, height - 27, w, 20).build(MenuButton::new));
        saveButton = addRenderableWidget(Button.builder(tr("save"), b -> save())
                .bounds(24 + w * 4, height - 27, w, 20).build(MenuButton::new));
        cancelButton = addRenderableWidget(Button.builder(tr("cancel"), b -> onClose())
                .bounds(28 + w * 5, height - 27, w, 20).build(MenuButton::new));
        saveButton.active = !waiting && !invalidSession;
        updateButtons();
    }
    private void updateButtons() {
        cancelButton.active = !waiting;
        defaultsButton.active = defaultsWritable && !waiting;
        defaultsButton.setMessage(tr("defaults", personalDefaults.size()));
        boolean recipe = selected >= 0 && nodes.get(selected).recipe();
        routeButton.active = recipe && !waiting && !invalidSession;
        alwaysButton.active = routeButton.active && defaultsWritable;
        alwaysButton.setMessage(tr(recipe && personalDefaults.contains(nodes.get(selected).recipeId()) ? "forget" : "always"));
        routeButton.setMessage(tr(recipe && excluded.contains(nodes.get(selected).recipeId()) ? "enable" : "disable"));
    }
    private void toggleSelected() {
        if (waiting || invalidSession || selected < 0) return;
        var node = nodes.get(selected);
        if (!node.recipe()) return;
        if (!excluded.remove(node.recipeId())) {
            if (excluded.size() >= PatternRecipeRules.MAX_EXCLUSIONS) { status = tr("rule_limit"); return; }
            excluded.add(node.recipeId()); collapseRecipe(node.recipeId());
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
        closed = true; returnToTerminal();
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void tick() {
        super.tick(); ticks++;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.containerMenu != terminalMenu) { closed = true; mc.setScreen(null); return; }
        if (!waiting && !invalidSession && pending.isEmpty() && !loadQueue.isEmpty()) requestBranch(loadQueue.removeFirst());
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

    // Dependencies run left to right, with each first child on its parent's row.
    private void layout() {
        record Pending(int id, int depth) {}
        var stack = new ArrayDeque<Pending>();
        stack.push(new Pending(0, 0));
        int row = 0;
        boxes.clear();
        while (!stack.isEmpty()) {
            var next = stack.pop();
            boxes.add(new Box(next.id(), next.depth() * (CARD_WIDTH + 16), row * (CARD_HEIGHT + 8)));
            var nested = expanded.contains(next.id()) ? visibleChildren(next.id()) : List.<Integer>of();
            if (!nested.isEmpty()) {
                for (int i = nested.size() - 1; i >= 0; i--) stack.push(new Pending(nested.get(i), next.depth() + 1));
            } else row++;
        }
        layoutDirty = false;
        if (centerOnRoot) { panX = 16; panY = 12; centerOnRoot = false; }
    }
    private static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill) {
        g.fill(x, y, x + w, y + h, fill);
        g.horizontalLine(x, x + w - 1, y, 0xFFFFE4C4);
        g.verticalLine(x, y, y + h - 1, 0xFFFFE4C4);
        g.horizontalLine(x, x + w - 1, y + h - 1, 0xFF705C48);
        g.verticalLine(x + w - 1, y, y + h - 1, 0xFF705C48);
    }
    private static final class MenuButton extends Button {
        private Component tooltipMessage;
        private MenuButton(Button.Builder builder) { super(builder); }
        @Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float partial) {
            int fill = !active ? 0xFFC1B7AA : isHoveredOrFocused() ? 0xFFFFD59F : 0xFFE9C398;
            panel(g, getX(), getY(), getWidth(), getHeight(), fill);
            var font = Minecraft.getInstance().font;
            String label = font.plainSubstrByWidth(getMessage().getString(), Math.max(1, getWidth() - 6));
            if (!getMessage().equals(tooltipMessage)) {
                tooltipMessage = getMessage(); setTooltip(net.minecraft.client.gui.components.Tooltip.create(tooltipMessage));
            }
            g.text(font, label, getX() + (getWidth() - font.width(label)) / 2,
                    getY() + (getHeight() - font.lineHeight) / 2, active ? 0xFF493729 : 0xFF857C72, false);
        }
    }
    private boolean inCanvas(double x, double y) { return x >= 8 && x < width - 8 && y >= 57 && y < height - 70; }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
        g.fill(0, 0, width, height, 0x3818120D);
        panel(g, 5, 5, width - 10, 46, 0xEFE8CAA8);
        panel(g, 5, height - 65, width - 10, 61, 0xEFE8CAA8);
        g.fill(0, 0, width, 3, ORANGE);
        g.text(font, title, 12, 14, 0xFF493729, false);
        g.textWithWordWrap(font, tr("scope"), 12, 32, width - 24, 0xFF574535, false);
        g.fill(8, 57, width - 8, height - 70, 0x18241A12);
        g.outline(8, 57, width - 16, height - 127, 0xFF9B5625);
        if (!nodes.isEmpty() && layoutDirty) layout();
        Box hovered = null;
        g.enableScissor(9, 58, width - 9, height - 71);
        g.pose().pushMatrix(); g.pose().translate((float)panX, (float)(57 + panY)); g.pose().scale((float)zoom);
        Map<Integer, Box> byId = new HashMap<>();
        for (var box : boxes) byId.put(box.id(), box);
        for (var box : boxes) {
            int parent = head(nodes.get(box.id()).parent());
            var p = byId.get(parent);
            if (p == null) continue;
            int x1 = p.x() + CARD_WIDTH, y1 = p.y() + CARD_HEIGHT / 2;
            int x2 = box.x(), y2 = box.y() + CARD_HEIGHT / 2, bend = x1 + 8;
            g.horizontalLine(x1, bend, y1, ORANGE);
            g.verticalLine(bend, Math.min(y1, y2), Math.max(y1, y2), ORANGE);
            g.horizontalLine(bend, x2, y2, ORANGE);
        }
        double x = (mx - panX) / zoom, y = (my - 57 - panY) / zoom;
        for (var box : boxes) {
            if (box.x() + CARD_WIDTH < (9 - panX) / zoom || box.x() > (width - 9 - panX) / zoom
                    || box.y() + CARD_HEIGHT < (58 - 57 - panY) / zoom || box.y() > (height - 71 - 57 - panY) / zoom) continue;
            int actual = displayed(box.id());
            var node = nodes.get(actual);
            boolean banned = node.recipe() && excluded.contains(node.recipeId());
            panel(g, box.x(), box.y(), CARD_WIDTH, CARD_HEIGHT, banned ? 0xE9BA9583 : 0xEFF3D4B1);
            g.outline(box.x(), box.y(), CARD_WIDTH, CARD_HEIGHT, head(selected) == box.id() ? 0xFFFFAA44 : 0xFFB78F65);
            if (!node.item().isEmpty()) { g.item(node.item(), box.x() + 5, box.y() + 5); g.itemDecorations(font, node.item(), box.x() + 5, box.y() + 5); }
            g.text(font, font.plainSubstrByWidth(node.item().getHoverName().getString(), CARD_WIDTH - 28), box.x() + 24, box.y() + 8, 0xFF392B20, false);
            if (node.recipe()) {
                for (int slot = 0; slot < 9; slot++) {
                    int sx = box.x() + 5 + slot % 3 * 18, sy = box.y() + 23 + slot / 3 * 18;
                    g.fill(sx - 1, sy - 1, sx + 16, sy + 16, 0xFF8D8173);
                    g.horizontalLine(sx - 1, sx + 16, sy - 1, 0xFF685E54);
                    g.verticalLine(sx - 1, sy - 1, sy + 16, 0xFF685E54);
                    g.horizontalLine(sx - 1, sx + 16, sy + 16, 0xFFFFE9CE);
                    g.verticalLine(sx + 16, sy - 1, sy + 16, 0xFFFFE9CE);
                    g.item(node.grid().get(slot), sx, sy);
                }
                g.text(font, tr(banned ? "disabled" : "allowed"), box.x() + 63, box.y() + 26, banned ? 0xFF863D25 : 0xFF61401E, false);
                g.text(font, font.plainSubstrByWidth(node.recipeId(), CARD_WIDTH - 67), box.x() + 63, box.y() + 39, 0xFF574535, false);
            } else {
                boolean allDisabled = children.getOrDefault(actual, List.of()).stream()
                        .filter(i -> nodes.get(i).recipe()).allMatch(i -> excluded.contains(nodes.get(i).recipeId()));
                boolean hasRecipes = children.getOrDefault(actual, List.of()).stream().anyMatch(i -> nodes.get(i).recipe());
                var line = sharedBranches.containsKey(actual) ? tr("shared") : pending.containsKey(actual) || loadQueue.contains(actual) ? tr("loading") : box.id() == 0 ? tr("root") : hasRecipes && allDisabled ? tr("all_disabled") : tr("note." + node.note());
                g.textWithWordWrap(font, line, box.x() + 8, box.y() + 27, CARD_WIDTH - 16, 0xFF61401E, false);
            }
            if (children.containsKey(actual) || (!node.recipe() && box.id() != 0 && node.note() == 0 && !sharedBranches.containsKey(actual) && !complete.contains(actual))) g.text(font, expanded.contains(box.id()) ? "[-]" : "[+]", box.x() + CARD_WIDTH - 24, box.y() + CARD_HEIGHT - 12, ORANGE, false);
            var variants = groupMembers.get(box.id());
            if (variants != null && variants.size() > 1) g.text(font, "< " + (variants.indexOf(actual) + 1) + "/" + variants.size() + " >",
                    box.x() + (node.recipe() ? 63 : 5), box.y() + (node.recipe() ? 56 : CARD_HEIGHT - 12), 0xFF61401E, false);
            if (inCanvas(mx, my) && x >= box.x() && x < box.x() + CARD_WIDTH && y >= box.y() && y < box.y() + CARD_HEIGHT) hovered = box;
        }
        g.pose().popMatrix(); g.disableScissor();
        g.text(font, tr("count", excluded.size(), snapshot.excluded().size()), 12, height - 61, 0xFF493729, false);
        g.textWithWordWrap(font, status.getString().isEmpty() ? tr("help") : status, 12, height - 46, width - 24, 0xFF493729, false);
        super.extractRenderState(g, mx, my, partial);
        if (managingDefaults) { hoveredHead = -1; renderDefaults(g); return; }
        if (hovered == null) hoveredHead = -1;
        else {
            int shown = displayed(hovered.id());
            hoveredHead = hovered.id(); hoveredVariant = groupMembers.get(hoveredHead).indexOf(shown);
        }
        if (hovered != null) {
            var node = nodes.get(displayed(hovered.id()));
            var lines = new ArrayList<Component>();
            lines.add(node.item().getHoverName());
            if (node.recipe()) { lines.add(Component.literal(node.recipeId())); lines.add(tr("recipe_help")); lines.add(tr("variants")); }
            else lines.add(tr("item_help"));
            if (TreePresentation.compressed(node)) lines.add(tr("compressed"));
            if (groupMembers.getOrDefault(hovered.id(), List.of()).size() > 1) lines.add(tr("group_help"));
            if (node.recipe() && personalDefaults.contains(node.recipeId())) lines.add(tr("always_hint"));
            g.setComponentTooltipForNextFrame(font, lines, mx, my);
        }
    }
    private int defaultsWidth() { return Math.min(420, width - 32); }
    private int defaultsHeight() { return Math.min(260, height - 32); }
    private int defaultsRows() { return Math.max(1, (defaultsHeight() - 70) / 16); }
    private void renderDefaults(GuiGraphicsExtractor g) {
        int w = defaultsWidth(), h = defaultsHeight(), x = (width - w) / 2, y = (height - h) / 2;
        g.fill(0, 0, width, height, 0x88000000); panel(g, x, y, w, h, 0xFFF3D4B1);
        g.text(font, tr("defaults_title"), x + 8, y + 8, 0xFF493729, false);
        g.textWithWordWrap(font, tr("defaults_help"), x + 8, y + 24, w - 16, 0xFF493729, false);
        var ids = new ArrayList<>(new TreeSet<>(personalDefaults));
        defaultsScroll = Math.clamp(defaultsScroll, 0, Math.max(0, ids.size() - defaultsRows()));
        for (int row = 0; row < defaultsRows() && row + defaultsScroll < ids.size(); row++)
            g.text(font, font.plainSubstrByWidth(ids.get(row + defaultsScroll), w - 32) + "  x", x + 8, y + 48 + row * 16, 0xFF61401E, false);
        if (ids.isEmpty()) g.text(font, tr("defaults_empty"), x + 8, y + 48, 0xFF61401E, false);
        g.text(font, tr("defaults_done"), x + 8, y + h - 16, 0xFF493729, false);
    }
    private void clickDefaults(double mx, double my) {
        int w = defaultsWidth(), h = defaultsHeight(), x = (width - w) / 2, y = (height - h) / 2;
        if (mx < x || mx >= x + w || my < y || my >= y + h || my >= y + h - 22) { managingDefaults = false; return; }
        int row = ((int)my - y - 48) / 16;
        var ids = new ArrayList<>(new TreeSet<>(personalDefaults));
        if (my >= y + 48 && row >= 0 && row < defaultsRows() && row + defaultsScroll < ids.size()) {
            personalDefaults.remove(ids.get(row + defaultsScroll)); updateButtons();
        }
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (managingDefaults) { clickDefaults(event.x(), event.y()); return true; }
        if (!inCanvas(event.x(), event.y())) return super.mouseClicked(event, doubleClick);
        double x = (event.x() - panX) / zoom, y = (event.y() - 57 - panY) / zoom;
        for (var box : boxes) if (x >= box.x() && x < box.x() + CARD_WIDTH && y >= box.y() && y < box.y() + CARD_HEIGHT) {
            int actual = displayed(box.id());
            var variants = groupMembers.getOrDefault(box.id(), List.of(actual));
            boolean recipeCard = nodes.get(actual).recipe();
            boolean overTypes = recipeCard ? x >= box.x() + 63 && y >= box.y() + 52 && y < box.y() + 68
                    : y >= box.y() + CARD_HEIGHT - 18 && x < box.x() + 85;
            if (variants.size() > 1 && overTypes) {
                int direction = x < box.x() + (recipeCard ? 80 : 25) ? -1 : 1;
                variantChoice.put(box.id(), Math.floorMod(variants.indexOf(actual) + direction, variants.size()));
                selected = displayed(box.id()); layoutDirty = true; updateButtons(); return true;
            }
            selected = actual;
            variantChoice.put(box.id(), variants.indexOf(actual));
            updateButtons();
            var node = nodes.get(selected);
            if ((event.button() == 1 || event.hasShiftDown()) && node.recipe()) toggleSelected();
            else if (sharedBranches.containsKey(selected)) {
                int target = sharedBranches.get(selected);
                for (int ancestor = target; ancestor >= 0; ancestor = nodes.get(ancestor).parent()) {
                    expanded.add(head(ancestor)); variantChoice.put(head(ancestor), groupMembers.get(head(ancestor)).indexOf(ancestor));
                }
                layout();
                for (var destination : boxes) if (destination.id() == head(target)) {
                    panX = width / 2.0 - (destination.x() + CARD_WIDTH / 2.0) * zoom;
                    panY = 12 - destination.y() * zoom; selected = target; break;
                }
            }
            else if (!node.recipe() || doubleClick || y >= box.y() + CARD_HEIGHT - 18) {
                if (!expanded.remove(box.id())) expanded.add(box.id());
                layoutDirty = true;
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
        if (managingDefaults) {
            defaultsScroll = Math.clamp(defaultsScroll - (int)Math.signum(vertical), 0, Math.max(0, personalDefaults.size() - defaultsRows())); return true;
        }
        if (!inCanvas(x, y)) return super.mouseScrolled(x, y, horizontal, vertical);
        var view = TreePresentation.zoomAt(zoom, panX, panY, x, y - 57, vertical);
        zoom = view.zoom(); panX = view.panX(); panY = view.panY();
        panX += horizontal * 40; return true;
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (managingDefaults) { if (event.isEscape()) managingDefaults = false; return true; }
        if (event.key() == 61 || event.key() == 334) { zoom = Math.min(1.5, zoom + .1); return true; }
        if (event.key() == 45 || event.key() == 333) { zoom = Math.max(.15, zoom - .1); return true; }
        if (event.key() == 32 && selected >= 0 && nodes.get(selected).recipe()) { toggleSelected(); return true; }
        return super.keyPressed(event);
    }
}
