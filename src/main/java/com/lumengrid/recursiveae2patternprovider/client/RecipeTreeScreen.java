package com.lumengrid.recursiveae2patternprovider.client;

import com.lumengrid.recursiveae2patternprovider.*;
import com.lumengrid.recursiveae2patternprovider.network.RecipeTreePackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import java.util.*;

/** Server-authored recipe alternatives with explicit per-item save/cancel. */
public final class RecipeTreeScreen extends Screen {
    private static java.lang.ref.WeakReference<RecipeTreeScreen> activeEditor = new java.lang.ref.WeakReference<>(null);
    private static final int ORANGE = 0xFFFFC98A, CARD_WIDTH = 148, CARD_HEIGHT = 94;
    private final RecipeTreePackets.Open snapshot;
    private final Screen terminalScreen;
    private final net.minecraft.world.inventory.AbstractContainerMenu terminalMenu;
    private final Map<appeng.api.stacks.AEItemKey, Integer> firstBranch = new HashMap<>();
    private final Map<String, net.minecraft.world.item.ItemStack> recipeExamples = new HashMap<>();
    private final Map<Integer, Integer> sharedBranches = new HashMap<>();
    private final ArrayDeque<Integer> loadQueue = new ArrayDeque<>();
    private boolean layoutDirty = true, centerOnRoot = true;
    private final Set<String> excluded;
    private final Map<String, String> ingredientLocks;
    private final Set<String> originalDefaults;
    private final Set<String> personalDefaults = new HashSet<>();
    private boolean defaultsWritable = true, managingDefaults;
    private int defaultsScroll;
    private final List<AbstractWidget> defaultsWidgets = new ArrayList<>();
    private final List<Button> defaultRemoveButtons = new ArrayList<>();
    private EditBox defaultsSearch;
    private boolean defaultsDragging;
    private int choosingGroup = -1, choicesScroll;
    private EditBox choicesSearch;
    private int previewItem = -1, previewIndex, previewGeneration = -1;
    private final Map<appeng.api.stacks.AEItemKey, List<RecipePreviewBridge.Preview>> recipePreviews = new HashMap<>();
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
        this.ingredientLocks = new TreeMap<>(snapshot.locks());
        activeEditor = new java.lang.ref.WeakReference<>(this);
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
        if (node.recipe()) recipeExamples.putIfAbsent(node.recipeId(), node.item());
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
        if (locked(node)) variantChoice.put(head, groupMembers.get(head).indexOf(id));
        if (!TreePresentation.compressed(node) && !(node.recipe() && excluded.contains(node.recipeId()))) expanded.add(id);
        if (id == 0 || node.recipe() || node.note() == 2 || node.item().isEmpty()) return;
        var previous = firstBranch.putIfAbsent(appeng.api.stacks.AEItemKey.of(node.item()), id);
        if (previous == null) loadQueue.add(id);
        else sharedBranches.put(id, previous);
    }
    private int head(int id) { return groupHeads.getOrDefault(id, id); }
    private boolean locked(RecipeTree.Node node) {
        return !node.recipe() && node.choice() >= 0 && !node.item().isEmpty() && Objects.equals(ingredientLocks.get(PatternIngredientLocks.scope(nodes, node)),
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(node.item().getItem()).toString());
    }
    private static RecipeTreeScreen currentEditor() {
        var mc = Minecraft.getInstance(); var editor = mc.screen instanceof RecipeTreeScreen screen ? screen : activeEditor.get();
        return editor != null && !editor.closed && mc.player != null && mc.player.containerMenu == editor.terminalMenu ? editor : null;
    }
    private int displayed(int id) {
        int head = head(id);
        var members = groupMembers.getOrDefault(head, List.of(id));
        int choice = variantChoice.getOrDefault(head, head == hoveredHead ? hoveredVariant
                : !nodes.get(head).recipe() || expanded.contains(head) ? 0 : ticks / 40);
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
        var screen = currentEditor();
        if (screen == null || !screen.snapshot.session().equals(packet.session())) return;
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
        screen.nodes.set(packet.parent(), new RecipeTree.Node(old.parent(), false, old.item(), "", List.of(), packet.page().note(), old.choice()));
        screen.offsets.put(packet.parent(), packet.page().nextOffset());
        if (packet.page().done()) screen.complete.add(packet.parent());
        else screen.loadQueue.addFirst(packet.parent());
        if (screen.managingDefaults) screen.rebuildDefaultRows();
    }
    private static Component tr(String key, Object... args) {
        return Component.translatable("gui.recursiveae2patternprovider.tree." + key, args);
    }
    public static void open(RecipeTreePackets.Open snapshot) {
        Minecraft.getInstance().setScreen(new RecipeTreeScreen(snapshot));
    }
    public static void result(RecipeTreePackets.Result result) {
        var mc = Minecraft.getInstance();
        var screen = currentEditor();
        if (screen == null || !screen.snapshot.session().equals(result.session())) return;
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
        defaultsWidgets.clear(); defaultRemoveButtons.clear();
        defaultsButton = addRenderableWidget(Button.builder(tr("defaults", personalDefaults.size()), b -> openDefaults())
                .bounds(width - 112, 10, 100, 20).build(MenuButton::new));
        defaultsButton.active = defaultsWritable && !waiting;
        addRenderableWidget(Button.builder(tr("reset_rules"), b -> { if (!waiting) { excluded.clear(); ingredientLocks.clear(); updateButtons(); } })
                .bounds(16 + w * 2, height - 27, w, 20).build(MenuButton::new));
        addRenderableWidget(Button.builder(tr("reset_view"), b -> { panY = 12; zoom = 1; centerOnRoot = true; layoutDirty = true; })
                .bounds(20 + w * 3, height - 27, w, 20).build(MenuButton::new));
        saveButton = addRenderableWidget(Button.builder(tr("save"), b -> save())
                .bounds(24 + w * 4, height - 27, w, 20).build(MenuButton::new));
        cancelButton = addRenderableWidget(Button.builder(tr("cancel"), b -> onClose())
                .bounds(28 + w * 5, height - 27, w, 20).build(MenuButton::new));
        saveButton.active = !waiting && !invalidSession;
        updateButtons();
        if (managingDefaults) openDefaults();
        else if (choosingGroup >= 0) openChoices(choosingGroup);
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
        ClientPacketDistributor.sendToServer(new RecipeTreePackets.Save(snapshot.session(), true, Set.copyOf(excluded), Map.copyOf(ingredientLocks)));
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
        private boolean automaticTooltip = true;
        private MenuButton(Button.Builder builder) { super(builder); }
        @Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float partial) {
            int fill = !active ? 0xFFC1B7AA : isHoveredOrFocused() ? 0xFFFFD59F : 0xFFE9C398;
            panel(g, getX(), getY(), getWidth(), getHeight(), fill);
            var font = Minecraft.getInstance().font;
            String label = font.plainSubstrByWidth(getMessage().getString(), Math.max(1, getWidth() - 6));
            if (automaticTooltip && !getMessage().equals(tooltipMessage)) {
                tooltipMessage = getMessage(); setTooltip(net.minecraft.client.gui.components.Tooltip.create(tooltipMessage));
            }
            g.text(font, label, getX() + (getWidth() - font.width(label)) / 2,
                    getY() + (getHeight() - font.lineHeight) / 2, active ? 0xFF493729 : 0xFF857C72, false);
        }
    }
    private void wrapped(GuiGraphicsExtractor g, Component text, int x, int y, int width, int lines, int color) {
        var split = font.split(text, width);
        for (int i = 0; i < Math.min(lines, split.size()); i++) {
            var line = split.get(i);
            if (i == lines - 1 && split.size() > lines) {
                var plain = new StringBuilder(); line.accept((index, style, codepoint) -> { plain.appendCodePoint(codepoint); return true; });
                g.text(font, font.plainSubstrByWidth(plain.toString(), width - font.width("...")) + "...", x, y + i * font.lineHeight, color, false);
            } else g.text(font, line, x, y + i * font.lineHeight, color, false);
        }
    }
    private Component description(int id) {
        var node = nodes.get(id);
        if (locked(node)) return tr("locked_item", node.item().getHoverName());
        int count = groupMembers.get(head(id)).size();
        if (node.choice() >= 0 && count > 1) return tr("ingredient_choice", count);
        return routeDescription(id);
    }
    private Component routeDescription(int id) {
        var node = nodes.get(id);
        boolean hasRecipes = children.getOrDefault(id, List.of()).stream().anyMatch(i -> nodes.get(i).recipe());
        boolean allDisabled = children.getOrDefault(id, List.of()).stream().filter(i -> nodes.get(i).recipe())
                .allMatch(i -> excluded.contains(nodes.get(i).recipeId()));
        return sharedBranches.containsKey(id) ? tr("shared") : pending.containsKey(id) || loadQueue.contains(id) ? tr("loading")
                : id == 0 ? tr("root") : hasRecipes && allDisabled ? tr("all_disabled") : tr("note." + node.note());
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
            wrapped(g, node.item().getHoverName(), box.x() + 24, box.y() + 4, CARD_WIDTH - 29, 2, 0xFF392B20);
            if (node.recipe()) {
                for (int slot = 0; slot < 9; slot++) {
                    int sx = box.x() + 5 + slot % 3 * 18, sy = box.y() + 26 + slot / 3 * 18;
                    g.fill(sx - 1, sy - 1, sx + 16, sy + 16, 0xFF8D8173);
                    g.horizontalLine(sx - 1, sx + 16, sy - 1, 0xFF685E54);
                    g.verticalLine(sx - 1, sy - 1, sy + 16, 0xFF685E54);
                    g.horizontalLine(sx - 1, sx + 16, sy + 16, 0xFFFFE9CE);
                    g.verticalLine(sx + 16, sy - 1, sy + 16, 0xFFFFE9CE);
                    g.item(node.grid().get(slot), sx, sy);
                }
                g.text(font, tr(banned ? "disabled" : "allowed"), box.x() + 63, box.y() + 29, banned ? 0xFF863D25 : 0xFF61401E, false);
                wrapped(g, Component.literal(node.recipeId()), box.x() + 63, box.y() + 42, CARD_WIDTH - 68, 2, 0xFF574535);
            } else {
                wrapped(g, description(actual), box.x() + 8, box.y() + 28, CARD_WIDTH - 16, 5, 0xFF61401E);
            }
            if (children.containsKey(actual) || (!node.recipe() && box.id() != 0 && node.note() == 0 && !sharedBranches.containsKey(actual) && !complete.contains(actual))) g.text(font, expanded.contains(box.id()) ? "[-]" : "[+]", box.x() + CARD_WIDTH - 24, box.y() + CARD_HEIGHT - 12, ORANGE, false);
            var variants = groupMembers.get(box.id());
            if (variants != null && variants.size() > 1) g.text(font, node.recipe() ? "< " + (variants.indexOf(actual) + 1) + "/" + variants.size() + " >" : tr("choices_button", variants.size()).getString(),
                    box.x() + (node.recipe() ? 63 : 5), box.y() + (node.recipe() ? 68 : CARD_HEIGHT - 12), 0xFF61401E, false);
            if (inCanvas(mx, my) && x >= box.x() && x < box.x() + CARD_WIDTH && y >= box.y() && y < box.y() + CARD_HEIGHT) hovered = box;
        }
        g.pose().popMatrix(); g.disableScissor();
        g.text(font, tr("count", excluded.size(), snapshot.excluded().size()), 12, height - 61, 0xFF493729, false);
        g.textWithWordWrap(font, status.getString().isEmpty() ? tr("help") : status, 12, height - 46, width - 24, 0xFF493729, false);
        if (managingDefaults) { hoveredHead = -1; renderDefaults(g, mx, my); super.extractRenderState(g, mx, my, partial); return; }
        if (choosingGroup >= 0) { hoveredHead = -1; renderChoices(g, mx, my); super.extractRenderState(g, mx, my, partial); return; }
        super.extractRenderState(g, mx, my, partial);
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
            else { lines.add(description(displayed(hovered.id()))); lines.add(tr("item_help")); }
            if (!node.recipe() && node.choice() >= 0 && groupMembers.get(hovered.id()).size() > 1)
                lines.add(routeDescription(displayed(hovered.id())));
            if (TreePresentation.compressed(node)) lines.add(tr("compressed"));
            if (groupMembers.getOrDefault(hovered.id(), List.of()).size() > 1) lines.add(tr("group_help"));
            if (node.recipe() && personalDefaults.contains(node.recipeId())) lines.add(tr("always_hint"));
            g.setComponentTooltipForNextFrame(font, lines, mx, my);
        }
    }
    private int defaultsWidth() { return Math.min(460, width - 24); }
    private int defaultsHeight() { return Math.min(310, height - 24); }
    private int defaultsRows() { return Math.max(1, (defaultsHeight() - 110) / 26); }
    private int defaultsX() { return (width - defaultsWidth()) / 2; }
    private int defaultsY() { return (height - defaultsHeight()) / 2; }
    private net.minecraft.world.item.ItemStack defaultItem(String id) {
        return recipeExamples.getOrDefault(id, net.minecraft.world.item.ItemStack.EMPTY);
    }
    private String defaultName(String id) {
        var item = defaultItem(id);
        return item.isEmpty() ? id.substring(id.indexOf(':') + 1).replace('_', ' ').replace('/', ' ') : item.getHoverName().getString();
    }
    private List<String> filteredDefaults() {
        String query = defaultsSearch == null ? "" : defaultsSearch.getValue().strip().toLowerCase(Locale.ROOT);
        return personalDefaults.stream().sorted().filter(id -> id.toLowerCase(Locale.ROOT).contains(query)
                || defaultName(id).toLowerCase(Locale.ROOT).contains(query)).toList();
    }
    private void openDefaults() {
        managingDefaults = true; dragging = false; defaultsScroll = 0;
        children().stream().filter(w -> w instanceof AbstractWidget).forEach(w -> ((AbstractWidget)w).visible = false);
        int x = defaultsX(), y = defaultsY(), w = defaultsWidth();
        defaultsSearch = new EditBox(font, x + 58, y + 43, w - 68, 18, tr("defaults_search"));
        defaultsSearch.setMaxLength(256); defaultsSearch.setHint(tr("defaults_search"));
        defaultsSearch.setResponder(value -> { defaultsScroll = 0; rebuildDefaultRows(); });
        defaultsWidgets.add(addRenderableWidget(defaultsSearch));
        var done = Button.builder(tr("defaults_done"), b -> closeDefaults())
                .bounds(x + w - 90, y + defaultsHeight() - 28, 80, 20).build(MenuButton::new);
        defaultsWidgets.add(addRenderableWidget(done));
        rebuildDefaultRows(); setFocused(defaultsSearch);
    }
    private void closeDefaults() {
        managingDefaults = false; defaultsDragging = false; setFocused(null);
        for (var widget : defaultsWidgets) removeWidget(widget);
        defaultsWidgets.clear(); defaultRemoveButtons.clear(); defaultsSearch = null;
        children().stream().filter(w -> w instanceof AbstractWidget).forEach(w -> ((AbstractWidget)w).visible = true);
        updateButtons();
    }
    private void rebuildDefaultRows() {
        for (var button : defaultRemoveButtons) { removeWidget(button); defaultsWidgets.remove(button); }
        defaultRemoveButtons.clear();
        var ids = filteredDefaults();
        defaultsScroll = Math.clamp(defaultsScroll, 0, Math.max(0, ids.size() - defaultsRows()));
        for (int row = 0; row < defaultsRows() && row + defaultsScroll < ids.size(); row++) {
            String id = ids.get(row + defaultsScroll);
            var button = Button.builder(Component.literal("x"), b -> {
                personalDefaults.remove(id); updateButtons(); rebuildDefaultRows();
            }).bounds(defaultsX() + defaultsWidth() - 40, defaultsY() + 70 + row * 26 + 3, 18, 18).build(MenuButton::new);
            button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(tr("defaults_remove", id)));
            ((MenuButton)button).automaticTooltip = false;
            defaultsWidgets.add(addRenderableWidget(button)); defaultRemoveButtons.add(button);
        }
    }
    private String shortened(String value, int width) {
        return font.width(value) <= width ? value : font.plainSubstrByWidth(value, Math.max(0, width - font.width("..."))) + "...";
    }
    private void renderDefaults(GuiGraphicsExtractor g, int mx, int my) {
        int w = defaultsWidth(), h = defaultsHeight(), x = (width - w) / 2, y = (height - h) / 2;
        g.fill(0, 0, width, height, 0xA018120D); panel(g, x, y, w, h, 0xFFF0D7BA);
        g.fill(x + 1, y + 1, x + w - 1, y + 3, 0xFFFFAA44);
        g.text(font, tr("defaults_title"), x + 10, y + 10, 0xFF392B20, false);
        g.text(font, shortened(tr("defaults_help").getString(), w - 20), x + 10, y + 26, 0xFF614B35, false);
        g.text(font, tr("defaults_search_label"), x + 10, y + 48, 0xFF614B35, false);
        int listY = y + 70, listHeight = defaultsRows() * 26;
        g.fill(x + 9, listY - 1, x + w - 9, listY + listHeight, 0xFFB69D81);
        var ids = filteredDefaults();
        for (int row = 0; row < defaultsRows() && row + defaultsScroll < ids.size(); row++) {
            String id = ids.get(row + defaultsScroll); int rowY = listY + row * 26;
            boolean hover = mx >= x + 10 && mx < x + w - 44 && my >= rowY && my < rowY + 25;
            g.fill(x + 10, rowY, x + w - 20, rowY + 25, hover ? 0xFFFFE4C3 : row % 2 == 0 ? 0xFFF9E8D2 : 0xFFF3DFC4);
            var item = defaultItem(id); int labelX = x + (item.isEmpty() ? 16 : 34);
            if (!item.isEmpty()) g.item(item, x + 14, rowY + 4);
            g.text(font, shortened(defaultName(id), x + w - 46 - labelX), labelX, rowY + 4, 0xFF392B20, false);
            g.text(font, shortened(id, x + w - 46 - labelX), labelX, rowY + 14, 0xFF78614B, false);
            if (hover) g.setComponentTooltipForNextFrame(font, List.of(Component.literal(defaultName(id)), Component.literal(id)), mx, my);
        }
        if (ids.isEmpty()) g.textWithWordWrap(font, tr(personalDefaults.isEmpty() ? "defaults_empty" : "defaults_no_results"), x + 18, listY + 12, w - 40, 0xFF61401E, false);
        int end = Math.min(ids.size(), defaultsScroll + defaultsRows());
        g.text(font, tr("defaults_count", ids.isEmpty() ? 0 : defaultsScroll + 1, end, ids.size()), x + 10, y + h - 21, 0xFF614B35, false);
        if (ids.size() > defaultsRows()) {
            int thumb = Math.max(12, listHeight * defaultsRows() / ids.size());
            int top = listY + (listHeight - thumb) * defaultsScroll / (ids.size() - defaultsRows());
            panel(g, x + w - 17, top, 6, thumb, 0xFFFFBA6C);
        }
    }
    private void dragDefaults(double my) {
        int max = Math.max(0, filteredDefaults().size() - defaultsRows());
        defaultsScroll = (int)Math.round(Math.clamp((my - defaultsY() - 70) / (defaultsRows() * 26.0), 0, 1) * max);
        rebuildDefaultRows();
    }
    private int choicesWidth() { return Math.min(460, width - 24); }
    private int choicesHeight() {
        int count = groupMembers.getOrDefault(choosingGroup, List.of()).size();
        int rows = Math.max(1, (count + choicesColumns() - 1) / choicesColumns());
        return Math.min(Math.min(360, height - 24), 214 + rows * 26);
    }
    private int choicesX() { return (width - choicesWidth()) / 2; }
    private int choicesY() { return (height - choicesHeight()) / 2; }
    private int choicesColumns() { return Math.max(1, (choicesWidth() - 20) / 26); }
    private int choicesRows() { return Math.max(1, (choicesHeight() - 214) / 26); }
    private List<Integer> filteredChoices() {
        String query = choicesSearch == null ? "" : choicesSearch.getValue().strip().toLowerCase(Locale.ROOT);
        return groupMembers.getOrDefault(choosingGroup, List.of()).stream().filter(id -> {
            var item = nodes.get(id).item();
            return item.getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)
                    || net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.getItem()).toString().contains(query);
        }).toList();
    }
    private void openChoices(int group) {
        choosingGroup = head(group); choicesScroll = 0; dragging = false; previewItem = displayed(choosingGroup); previewIndex = 0;
        children().stream().filter(w -> w instanceof AbstractWidget).forEach(w -> ((AbstractWidget)w).visible = false);
        choicesSearch = new EditBox(font, choicesX() + 58, choicesY() + 43, choicesWidth() - 68, 18, tr("defaults_search"));
        choicesSearch.setMaxLength(256); choicesSearch.setResponder(value -> choicesScroll = 0);
        defaultsWidgets.add(addRenderableWidget(choicesSearch));
        defaultsWidgets.add(addRenderableWidget(Button.builder(tr("defaults_done"), b -> closeChoices())
                .bounds(choicesX() + choicesWidth() - 90, choicesY() + choicesHeight() - 28, 80, 20).build(MenuButton::new)));
        defaultsWidgets.add(addRenderableWidget(Button.builder(tr("choices_any"), b -> {
            ingredientLocks.remove(PatternIngredientLocks.scope(nodes, nodes.get(displayed(choosingGroup)))); closeChoices();
        }).bounds(choicesX() + 10, choicesY() + choicesHeight() - 28, 126, 20).build(MenuButton::new)));
        var recipes = Button.builder(tr("choices_recipes"), b -> { if (previewItem >= 0) RecipePreviewBridge.show(nodes.get(previewItem).item()); })
                .bounds(choicesX() + 142, choicesY() + choicesHeight() - 28, 78, 20).build(MenuButton::new);
        recipes.active = RecipePreviewBridge.available(); recipes.visible = choicesWidth() >= 340; defaultsWidgets.add(addRenderableWidget(recipes));
        int previewY = choicesY() + 70 + choicesRows() * 26 + 4;
        defaultsWidgets.add(addRenderableWidget(Button.builder(Component.literal("<"), b -> previewIndex--)
                .bounds(choicesX() + choicesWidth() - 49, previewY, 18, 16).build(MenuButton::new)));
        defaultsWidgets.add(addRenderableWidget(Button.builder(Component.literal(">"), b -> previewIndex++)
                .bounds(choicesX() + choicesWidth() - 29, previewY, 18, 16).build(MenuButton::new)));
        setFocused(choicesSearch);
    }
    private void closeChoices() {
        choosingGroup = -1; choicesSearch = null; setFocused(null);
        for (var widget : defaultsWidgets) removeWidget(widget);
        defaultsWidgets.clear();
        children().stream().filter(w -> w instanceof AbstractWidget).forEach(w -> ((AbstractWidget)w).visible = true);
        updateButtons();
    }
    private int choicesMaxScroll() { return Math.max(0, (filteredChoices().size() + choicesColumns() - 1) / choicesColumns() - choicesRows()); }
    private int choiceAt(double mx, double my) {
        int x = (int)Math.floor(mx - choicesX() - 10), y = (int)Math.floor(my - choicesY() - 70);
        if (x < 0 || y < 0 || x >= choicesColumns() * 26 || y >= choicesRows() * 26) return -1;
        int index = (choicesScroll + y / 26) * choicesColumns() + x / 26;
        var ids = filteredChoices(); return index < ids.size() ? ids.get(index) : -1;
    }
    private void renderChoices(GuiGraphicsExtractor g, int mx, int my) {
        int x = choicesX(), y = choicesY(), w = choicesWidth(), h = choicesHeight();
        g.fill(0, 0, width, height, 0xA018120D); panel(g, x, y, w, h, 0xFFF0D7BA);
        g.fill(x + 1, y + 1, x + w - 1, y + 3, 0xFFFFAA44);
        g.text(font, tr("choices_title"), x + 10, y + 10, 0xFF392B20, false);
        g.text(font, shortened(tr("choices_help").getString(), w - 20), x + 10, y + 26, 0xFF614B35, false);
        g.text(font, tr("defaults_search_label"), x + 10, y + 48, 0xFF614B35, false);
        var ids = filteredChoices(); choicesScroll = Math.clamp(choicesScroll, 0, choicesMaxScroll());
        int start = choicesScroll * choicesColumns(), end = Math.min(ids.size(), start + choicesRows() * choicesColumns());
        int hover = choiceAt(mx, my), current = displayed(choosingGroup);
        if (hover >= 0 && hover != previewItem) { previewItem = hover; previewIndex = 0; }
        for (int i = start; i < end; i++) {
            int id = ids.get(i), cellX = x + 10 + (i - start) % choicesColumns() * 26, cellY = y + 70 + (i - start) / choicesColumns() * 26;
            var node = nodes.get(id);
            panel(g, cellX, cellY, 24, 24, node.note() == 2 ? 0xFFC2ADA0 : id == hover ? 0xFFFFE4C3 : 0xFFF9E8D2);
            if (locked(node)) g.outline(cellX, cellY, 24, 24, 0xFFFF962E);
            g.item(node.item(), cellX + 4, cellY + 4); g.itemDecorations(font, node.item(), cellX + 4, cellY + 4);
            if (node.note() == 2) g.text(font, "x", cellX + 17, cellY + 1, 0xFF9B3B26, false);
        }
        if (ids.isEmpty()) g.text(font, tr("defaults_no_results"), x + 10, y + 76, 0xFF614B35, false);
        var countLabel = tr("choices_count", ids.size());
        g.text(font, countLabel, x + w - 10 - font.width(countLabel), y + 10, 0xFF614B35, false);
        if (choicesMaxScroll() > 0) {
            int track = choicesRows() * 26, thumb = Math.max(12, track * choicesRows() / (choicesMaxScroll() + choicesRows()));
            panel(g, x + w - 8, y + 70 + (track - thumb) * choicesScroll / choicesMaxScroll(), 5, thumb, 0xFFFFBA6C);
        }
        renderChoicePreview(g, mx, my);
        if (hover >= 0) {
            var node = nodes.get(hover); var lines = new ArrayList<Component>();
            lines.add(node.item().getHoverName());
            lines.add(Component.literal(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(node.item().getItem()).toString()));
            lines.add(tr(node.note() == 2 ? "choices_cycle" : "choices_select"));
            g.setComponentTooltipForNextFrame(font, lines, mx, my);
        }
    }
    private void renderChoicePreview(GuiGraphicsExtractor g, int mx, int my) {
        int x = choicesX() + 10, y = choicesY() + 70 + choicesRows() * 26 + 4;
        int w = choicesWidth() - 20, h = choicesY() + choicesHeight() - 35 - y;
        panel(g, x, y, w, h, 0xFFF9E8D2);
        if (previewItem < 0) return;
        var item = nodes.get(previewItem).item();
        if (previewGeneration != RecipePreviewBridge.generation) { recipePreviews.clear(); previewGeneration = RecipePreviewBridge.generation; }
        var previews = recipePreviews.computeIfAbsent(appeng.api.stacks.AEItemKey.of(item), key -> RecipePreviewBridge.previews(item));
        g.text(font, shortened(item.getHoverName().getString(), w - 60), x + 6, y + 5, 0xFF392B20, false);
        if (!previews.isEmpty()) {
            var preview = previews.get(Math.floorMod(previewIndex, previews.size()));
            var label = tr(preview.processing() ? "preview_processing" : "preview_crafting", preview.title(), Math.floorMod(previewIndex, previews.size()) + 1, previews.size());
            g.text(font, shortened(label.getString(), w - 12), x + 6, y + 18, 0xFF614B35, false);
            try { preview.draw().draw(g, x + 6, y + 31, w - 12, Math.max(1, h - 35), mx, my); }
            catch (RuntimeException e) { g.text(font, tr("preview_failed"), x + 6, y + 31, 0xFF614B35, false); }
        } else {
            var recipes = children.getOrDefault(previewItem, List.of()).stream().filter(id -> nodes.get(id).recipe()).toList();
            if (!recipes.isEmpty()) {
                var recipe = nodes.get(recipes.get(Math.floorMod(previewIndex, recipes.size())));
                g.text(font, shortened(recipe.recipeId(), w - 12), x + 6, y + 18, 0xFF614B35, false);
                for (int i = 0; i < 9; i++) g.item(recipe.grid().get(i), x + 6 + i % 3 * 18, y + 31 + i / 3 * 18);
            } else g.textWithWordWrap(font, tr(RecipePreviewBridge.available() ? "preview_none" : "preview_no_jei"), x + 6, y + 22, w - 12, 0xFF614B35, false);
        }
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (choosingGroup >= 0) {
            int choice = choiceAt(event.x(), event.y());
            if (event.button() == 0 && choice >= 0 && nodes.get(choice).note() != 2) {
                String scope = PatternIngredientLocks.scope(nodes, nodes.get(choice));
                if (!ingredientLocks.containsKey(scope) && ingredientLocks.size() >= PatternIngredientLocks.MAX_LOCKS) { status = tr("rule_limit"); return true; }
                ingredientLocks.put(scope, net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(nodes.get(choice).item().getItem()).toString());
                variantChoice.put(choosingGroup, groupMembers.get(choosingGroup).indexOf(choice));
                selected = choice; expanded.add(choosingGroup); layoutDirty = true; closeChoices();
            } else if (event.button() == 1 && choice >= 0) RecipePreviewBridge.show(nodes.get(choice).item());
            else super.mouseClicked(event, doubleClick);
            return true;
        }
        if (managingDefaults) {
            if (event.button() == 0 && event.x() >= defaultsX() + defaultsWidth() - 19 && event.x() < defaultsX() + defaultsWidth() - 9
                    && event.y() >= defaultsY() + 70 && event.y() < defaultsY() + 70 + defaultsRows() * 26) {
                defaultsDragging = true; dragDefaults(event.y()); return true;
            }
            super.mouseClicked(event, doubleClick); return true;
        }
        if (!inCanvas(event.x(), event.y())) return super.mouseClicked(event, doubleClick);
        double x = (event.x() - panX) / zoom, y = (event.y() - 57 - panY) / zoom;
        for (var box : boxes) if (x >= box.x() && x < box.x() + CARD_WIDTH && y >= box.y() && y < box.y() + CARD_HEIGHT) {
            int actual = displayed(box.id());
            var variants = groupMembers.getOrDefault(box.id(), List.of(actual));
            boolean recipeCard = nodes.get(actual).recipe();
            boolean overTypes = recipeCard ? x >= box.x() + 63 && y >= box.y() + 63 && y < box.y() + 79
                    : y >= box.y() + CARD_HEIGHT - 18 && x < box.x() + 85;
            if (variants.size() > 1 && overTypes) {
                if (!recipeCard) { openChoices(box.id()); return true; }
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
        if (choosingGroup >= 0) { super.mouseDragged(event, dx, dy); return true; }
        if (managingDefaults) { if (defaultsDragging) dragDefaults(event.y()); else super.mouseDragged(event, dx, dy); return true; }
        if (dragging) { panX += dx; panY += dy; return true; }
        return super.mouseDragged(event, dx, dy);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) { dragging = false; defaultsDragging = false; return super.mouseReleased(event); }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (choosingGroup >= 0) { choicesScroll = Math.clamp(choicesScroll - (int)Math.signum(vertical), 0, choicesMaxScroll()); return true; }
        if (managingDefaults) {
            defaultsScroll -= (int)Math.signum(vertical) * 3; rebuildDefaultRows(); return true;
        }
        if (!inCanvas(x, y)) return super.mouseScrolled(x, y, horizontal, vertical);
        var view = TreePresentation.zoomAt(zoom, panX, panY, x, y - 57, vertical);
        zoom = view.zoom(); panX = view.panX(); panY = view.panY();
        panX += horizontal * 40; return true;
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (choosingGroup >= 0) { if (event.isEscape()) closeChoices(); else super.keyPressed(event); return true; }
        if (managingDefaults) { if (event.isEscape()) closeDefaults(); else super.keyPressed(event); return true; }
        if (event.key() == 61 || event.key() == 334) { zoom = Math.min(1.5, zoom + .1); return true; }
        if (event.key() == 45 || event.key() == 333) { zoom = Math.max(.15, zoom - .1); return true; }
        if (event.key() == 32 && selected >= 0 && nodes.get(selected).recipe()) { toggleSelected(); return true; }
        return super.keyPressed(event);
    }
}
