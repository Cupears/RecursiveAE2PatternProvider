package com.lumengrid.recursiveae2patternprovider.client;

import com.lumengrid.recursiveae2patternprovider.CraftingTreeData;
import com.lumengrid.recursiveae2patternprovider.RecursiveAE2PatternProvider;
import com.lumengrid.recursiveae2patternprovider.network.RequestCraftingTreeC2SPacket;
import com.lumengrid.recursiveae2patternprovider.network.ToggleRecipeBanC2SPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Standalone node-graph inspector for a recursive pattern's crafting tree.
 *
 * <p>Opening this screen via {@code Minecraft#setScreen} keeps the AE2 encoding container menu
 * alive (only {@code onClose}/{@code closeContainer} would end it), so the ban packets can keep
 * referencing that menu. The server owns the tree data; clicking a recipe card asks the server to
 * toggle a ban and resend the (recomputed) tree, which lands back in {@link #updateTree}.
 *
 * <p>Interaction: left-click a recipe card to ban that {@code (item, recipe)} edge; shift-left-click
 * to ban the recipe everywhere. Drag empty space (or use the mouse wheel) to pan. Hold control and
 * scroll to zoom toward the cursor. The root pattern's own card is shown for context but cannot be
 * banned.
 */
public class CraftingTreeScreen extends Screen {

    // Card / layout geometry (world units, at zoom 1.0).
    private static final int SLOT = 9;
    private static final int GRID = SLOT * 3;
    private static final int CARD_W = 82;
    private static final int CARD_H = 40;
    private static final int HEADER_H = 20;
    private static final int NODE_PAD = 4;
    private static final int COL_GAP = 58;
    private static final int ROW_GAP = 16;

    // Zoom.
    private static final double ZOOM_MIN = 0.5;
    private static final double ZOOM_MAX = 2.0;
    private static final double ZOOM_STEP_SCROLL = 0.10;

    // Colors (ARGB).
    private static final int COL_PANEL = 0xC0101014;
    private static final int COL_NODE_BG = 0xFF26262B;
    private static final int COL_CARD_BG = 0xFF333338;
    private static final int COL_BORDER = 0xFF555560;
    private static final int COL_ACTIVE = 0xFF55DD55;
    private static final int COL_BANNED = 0xFFDD5555;
    private static final int COL_BANNED_FILL = 0x66DD5555;
    private static final int COL_ROOT = 0xFF5599FF;
    private static final int COL_CONNECTOR = 0xFF7A7A82;
    private static final int COL_SLOT_BG = 0xFF1C1C20;

    private final int menuId;
    @Nullable
    private final Screen parent;
    private CraftingTreeData tree;

    // Layout, recomputed whenever the tree changes (world units).
    private final Map<Integer, int[]> itemRects = new HashMap<>();   // itemIndex -> {x, y, w, h}
    private final List<CardHit> cardHits = new ArrayList<>();        // recipe cards, world units

    // Pan in screen pixels; applied as a pose translation.
    private double panX;
    private double panY;
    private boolean dragging;
    private boolean panInitialised;

    // Zoom factor (1.0 = 100%). Zooms around an anchor point.
    private double zoom = 1.0;

    private record CardHit(int recipeIndex, int x, int y, int w, int h) {}

    public CraftingTreeScreen(int menuId, CraftingTreeData tree, @Nullable Screen parent) {
        super(Component.literal("Crafting Tree"));
        this.menuId = menuId;
        this.tree = tree;
        this.parent = parent;
    }

    public int getMenuId() {
        return menuId;
    }

    public void updateTree(CraftingTreeData newTree) {
        this.tree = newTree;
        layout();
    }

    @Override
    protected void init() {
        layout();
        if (!panInitialised) {
            panX = 48;
            panY = Math.max(40, this.height / 3.0);
            panInitialised = true;
        }
        int w = 60;
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(this.width - w - 8, this.height - 28, w, 20)
                .build());
    }

    // ----- zoom -----------------------------------------------------------------------------------

    private void zoomAt(double delta, int anchorX, int anchorY) {
        setZoom(Math.clamp(zoom + delta, ZOOM_MIN, ZOOM_MAX), anchorX, anchorY);
    }

    /** Set zoom while keeping the screen point {@code (anchorX, anchorY)} fixed on the same world point. */
    private void setZoom(double next, int anchorX, int anchorY) {
        double prev = this.zoom;
        this.zoom = Math.clamp(next, ZOOM_MIN, ZOOM_MAX);
        if (prev != this.zoom) {
            double cx = this.width / 2.0;
            double cy = this.height / 2.0;
            // World point currently under the anchor.
            double worldX = cx + (anchorX - cx - panX) / prev;
            double worldY = cy + (anchorY - cy - panY) / prev;
            // Re-anchor pan so that world point stays at the same screen anchor.
            panX = anchorX - cx - (worldX - cx) * this.zoom;
            panY = anchorY - cy - (worldY - cy) * this.zoom;
        }
    }

    // ----- layout ---------------------------------------------------------------------------------

    private void layout() {
        itemRects.clear();
        cardHits.clear();
        if (tree == null || tree.isEmpty()) {
            return;
        }

        // Column = inspector-assigned depth (push-deeper ensures every edge runs strictly right).
        Map<Integer, List<Integer>> columns = new java.util.TreeMap<>();
        int maxDepth = 0;
        for (int i = 0; i < tree.items().size(); i++) {
            int d = tree.items().get(i).depth();
            columns.computeIfAbsent(d, k -> new ArrayList<>()).add(i);
            maxDepth = Math.max(maxDepth, d);
        }

        // Layout columns left to right. Within each column, sort items by their parents'
        // average vertical position so children cluster near the items that need them.
        // Dynamic whitespace is inserted between items whose ideal positions are far apart,
        // so children can align with their parents without cramping unrelated items together.
        Map<Integer, Integer> itemY = new HashMap<>();
        for (int col = 0; col <= maxDepth; col++) {
            List<Integer> items = new ArrayList<>(columns.getOrDefault(col, List.of()));
            if (col > 0) {
                items.sort(Comparator.comparingInt(i -> parentAvgY(i, itemY)));
            }
            int x = col * (CARD_W + COL_GAP);
            int y = 0;
            for (int item : items) {
                int recipeCount = tree.items().get(item).recipeIndices().size();
                int h = HEADER_H + recipeCount * CARD_H + NODE_PAD * 2;
                int w = CARD_W + NODE_PAD * 2;

                // Try to center this item on its ideal Y (average parent position). If the
                // ideal Y is far below the current cursor, dynamic whitespace opens up
                // between this item and the previous one.
                int idealY = parentAvgY(item, itemY);
                int targetY;
                if (idealY != Integer.MAX_VALUE) {
                    targetY = Math.max(y, idealY - h / 2);
                } else {
                    targetY = y;
                }

                itemRects.put(item, new int[]{x, targetY, w, h});
                // Store center Y so children align with parent centers, not parent tops.
                itemY.put(item, targetY + h / 2);
                y = targetY + h + ROW_GAP;
            }
        }
    }

    /** Average Y of this item's producers (items whose recipes use it as an ingredient), or {@code Integer.MAX_VALUE} if unknown. */
    private int parentAvgY(int item, Map<Integer, Integer> itemY) {
        int totalY = 0;
        int count = 0;
        for (int recipeIdx = 0; recipeIdx < tree.recipes().size(); recipeIdx++) {
            var recipe = tree.recipes().get(recipeIdx);
            if (!recipe.active() && !recipe.banned() && !recipe.root()) {
                continue;
            }
            if (!recipe.ingredientItemIndices().contains(item)) {
                continue;
            }
            int producerItem = findProducerItem(recipeIdx);
            if (producerItem != -1 && itemY.containsKey(producerItem)) {
                totalY += itemY.get(producerItem);
                count++;
            }
        }
        return count > 0 ? totalY / count : Integer.MAX_VALUE;
    }

    /** Find the item node that produces the recipe at {@code recipeIndex}. */
    private int findProducerItem(int recipeIndex) {
        for (int i = 0; i < tree.items().size(); i++) {
            if (tree.items().get(i).recipeIndices().contains(recipeIndex)) {
                return i;
            }
        }
        return -1;
    }

    /** Column index (depth) of the item node at {@code itemIndex}. */
    private int itemDepth(int itemIndex) {
        return tree.items().get(itemIndex).depth();
    }

    // ----- per-frame render state -----------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        if (tree == null || tree.isEmpty()) {
            g.centeredText(this.font, Component.literal("No crafting tree to inspect."),
                    this.width / 2, this.height / 2, 0xFFFFFFFF);
            super.extractRenderState(g, mouseX, mouseY, partial);
            return;
        }

        int cx = this.width / 2;
        int cy = this.height / 2;

        // Chrome (unscaled screen space).
        g.fill(0, 0, this.width, 22, COL_PANEL);
        g.text(this.font, Component.literal("Crafting Tree Inspector").withStyle(ChatFormatting.WHITE), 8, 7, 0xFFFFFFFF);
        drawLegend(g);

        // Convert screen mouse to world coords for hit testing.
        double wmx = cx + (mouseX - cx - panX) / zoom;
        double wmy = cy + (mouseY - cy - panY) / zoom;

        // Clip the graph to the viewport in screen space, before applying the pose transform.
        int top = 24;
        int bottom = this.height - 32;
        g.enableScissor(0, top, this.width, bottom);

        // Pose transform: pan (screen pixels), then zoom around the viewport centre.
        g.pose().pushMatrix();
        g.pose().translate((float) panX, (float) panY);
        g.pose().translate(cx, cy);
        g.pose().scale((float) zoom, (float) zoom);
        g.pose().translate(-cx, -cy);

        cardHits.clear();
        drawConnectors(g);
        drawProducerLines(g);

        for (var entry : itemRects.entrySet()) {
            int item = entry.getKey();
            int[] r = entry.getValue();
            drawItemNode(g, item, r[0], r[1], r[2], r[3], wmx, wmy);
        }

        g.pose().popMatrix();
        g.disableScissor();

        super.extractRenderState(g, mouseX, mouseY, partial);

        // Tooltips drawn last (deferred internally).
        drawHoverTooltip(g, mouseX, mouseY);
    }

    private void drawLegend(GuiGraphicsExtractor g) {
        int y = this.height - 26;
        g.fill(0, y - 2, this.width, this.height, COL_PANEL);
        String legend = "Left-click: ban this recipe here   §7•   §rShift-click: ban everywhere"
                + "   §7•   §rDrag/scroll: pan   §7•   §rCtrl-scroll / buttons: zoom";
        g.text(this.font, Component.literal(legend), 8, y + 4, 0xFFBBBBBB);
    }

    // ----- rendering ------------------------------------------------------------------------------

    private void drawConnectors(GuiGraphicsExtractor g) {
        // Only draw connector lines for meaningful edges: the active (chosen) recipe per item
        // and banned (disabled) edges. Inactive alternatives keep their card (so they can be
        // banned/switched) but no line, which keeps the tree from turning into a spiderweb of
        // crossing connectors between vertically neighbouring or deeply nested elements.

        // Count edges per column pair so we can spread elbows evenly across the gap.
        Map<String, Integer> totalPerColumnPair = new HashMap<>();
        for (int recipeIdx = 0; recipeIdx < tree.recipes().size(); recipeIdx++) {
            var recipe = tree.recipes().get(recipeIdx);
            if (!recipe.active() && !recipe.banned() && !recipe.root()) {
                continue;
            }
            int[] card = cardRect(recipeIdx);
            if (card == null) {
                continue;
            }
            int parentItem = findProducerItem(recipeIdx);
            if (parentItem == -1) {
                continue;
            }
            int parentCol = itemDepth(parentItem);
            for (int ing : recipe.ingredientItemIndices()) {
                int[] r = itemRects.get(ing);
                if (r == null) {
                    continue;
                }
                int childCol = itemDepth(ing);
                if (childCol <= parentCol) {
                    continue;
                }
                String key = parentCol + "," + childCol;
                totalPerColumnPair.merge(key, 1, Integer::sum);
            }
        }

        Map<String, Integer> drawnPerColumnPair = new HashMap<>();
        for (int recipeIdx = 0; recipeIdx < tree.recipes().size(); recipeIdx++) {
            var recipe = tree.recipes().get(recipeIdx);
            if (!recipe.active() && !recipe.banned() && !recipe.root()) {
                continue;
            }
            int[] card = cardRect(recipeIdx);
            if (card == null) {
                continue;
            }
            int parentItem = findProducerItem(recipeIdx);
            if (parentItem == -1) {
                continue;
            }
            int parentCol = itemDepth(parentItem);
            int startX = card[0] + card[2];
            int startY = card[1] + card[3] / 2;
            for (int ing : recipe.ingredientItemIndices()) {
                int[] r = itemRects.get(ing);
                if (r == null) {
                    continue;
                }
                int childCol = itemDepth(ing);
                if (childCol <= parentCol) {
                    continue;
                }
                int endX = r[0];
                int endY = r[1] + HEADER_H / 2;
                int color = recipe.banned() ? COL_BANNED : COL_ACTIVE;

                // Spread elbows evenly across the gap between columns. Multiple edges between
                // the same two columns get distinct horizontal positions so they don't stack
                // into a thick wall.
                String key = parentCol + "," + childCol;
                int total = totalPerColumnPair.get(key);
                int drawn = drawnPerColumnPair.merge(key, 1, Integer::sum);
                int gap = endX - startX;
                int spread = Math.min(gap / 3, 30);
                int offset = total <= 1 ? 0 : -spread + (drawn - 1) * (2 * spread) / (total - 1);
                int elbowX = startX + gap / 2 + offset;
                drawElbow(g, startX, startY, endX, endY, elbowX, color);
            }
        }
    }

    /** Cards are laid out inside their owning item node (world units). */
    @Nullable
    private int[] cardRect(int recipeIndex) {
        for (var entry : itemRects.entrySet()) {
            var recipeIndices = tree.items().get(entry.getKey()).recipeIndices();
            int slot = recipeIndices.indexOf(recipeIndex);
            if (slot >= 0) {
                int[] r = entry.getValue();
                int cx = r[0] + NODE_PAD;
                int cy = r[1] + HEADER_H + NODE_PAD + slot * CARD_H;
                return new int[]{cx, cy, CARD_W, CARD_H - 2};
            }
        }
        return null;
    }

    private void drawElbow(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int elbowX, int color) {
        // Clamp the elbow into the gap between parent and child so it never touches a node.
        elbowX = Math.min(Math.max(elbowX, x0 + 1), x1 - 1);
        g.fill(Math.min(x0, elbowX), y0, Math.max(x0, elbowX) + 1, y0 + 1, color);
        g.fill(elbowX, Math.min(y0, y1), elbowX + 1, Math.max(y0, y1) + 1, color);
        g.fill(Math.min(elbowX, x1), y1, Math.max(elbowX, x1) + 1, y1 + 1, color);
    }

    /** Draw a short line from each item node to its producing recipe card, so produced items
     *  always have a visible connection even when nothing consumes them as an ingredient. */
    private void drawProducerLines(GuiGraphicsExtractor g) {
        for (int itemIdx = 0; itemIdx < tree.items().size(); itemIdx++) {
            int[] r = itemRects.get(itemIdx);
            if (r == null) {
                continue;
            }
            var recipeIndices = tree.items().get(itemIdx).recipeIndices();
            if (recipeIndices.isEmpty()) {
                continue; // raw material with no crafting recipe
            }
            int recipeIdx = recipeIndices.get(0);
            var recipe = tree.recipes().get(recipeIdx);
            if (!recipe.active() && !recipe.banned() && !recipe.root()) {
                continue;
            }
            int cy = r[1] + HEADER_H + NODE_PAD + CARD_H / 2;
            int color = recipe.banned() ? COL_BANNED : (recipe.active() ? COL_ACTIVE : COL_ROOT);
            // Short line from item's left edge into the node at the recipe's vertical center.
            g.fill(r[0], cy - 1, r[0] + 22, cy + 1, color);
        }
    }

    private boolean drawItemNode(GuiGraphicsExtractor g, int item, int x, int y, int w, int h,
                                 double worldMouseX, double worldMouseY) {
        var node = tree.items().get(item);
        g.fill(x, y, x + w, y + h, COL_NODE_BG);
        g.outline(x, y, w, h, COL_BORDER);

        // Header: item icon + name, truncated to fit the node width.
        g.item(node.icon(), x + NODE_PAD, y + 2);
        String name = node.icon().getHoverName().getString();
        int maxNameW = w - NODE_PAD * 2 - 20; // 20 = icon width (16) + gap (4)
        name = fitWidth(this.font, name, Math.max(0, maxNameW));
        g.text(this.font, Component.literal(name), x + NODE_PAD + 20, y + 6, 0xFFE0E0E0);

        boolean hoveredHeader = worldMouseX >= x && worldMouseX < x + w
                && worldMouseY >= y && worldMouseY < y + HEADER_H;

        // Recipe cards.
        var recipeIndices = node.recipeIndices();
        for (int slot = 0; slot < recipeIndices.size(); slot++) {
            int recipeIdx = recipeIndices.get(slot);
            int cx = x + NODE_PAD;
            int cy = y + HEADER_H + NODE_PAD + slot * CARD_H;
            drawCard(g, recipeIdx, cx, cy, CARD_W, CARD_H - 2, worldMouseX, worldMouseY);
            cardHits.add(new CardHit(recipeIdx, cx, cy, CARD_W, CARD_H - 2));
        }
        return hoveredHeader;
    }

    /** Truncate {@code name} so it fits within {@code maxWidth} (unscaled world units). */
    private static String fitWidth(Font font, String name, int maxWidth) {
        if (maxWidth <= 0) {
            return "…";
        }
        if (font.width(name) <= maxWidth) {
            return name;
        }
        StringBuilder sb = new StringBuilder(name);
        while (sb.length() > 0 && font.width(sb + "…") > maxWidth) {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.append("…").toString();
    }

    private void drawCard(GuiGraphicsExtractor g, int recipeIdx, int x, int y, int w, int h,
                          double worldMouseX, double worldMouseY) {
        var recipe = tree.recipes().get(recipeIdx);
        boolean hovered = worldMouseX >= x && worldMouseX < x + w
                && worldMouseY >= y && worldMouseY < y + h;

        g.fill(x, y, x + w, y + h, COL_CARD_BG);

        // 3x3 grid.
        int gx = x + 3;
        int gy = y + (h - GRID) / 2;
        g.fill(gx - 1, gy - 1, gx + GRID + 1, gy + GRID + 1, COL_SLOT_BG);
        for (int i = 0; i < 9; i++) {
            ItemStack cell = i < recipe.grid().size() ? recipe.grid().get(i) : ItemStack.EMPTY;
            int sxx = gx + (i % 3) * SLOT;
            int syy = gy + (i / 3) * SLOT;
            if (!cell.isEmpty()) {
                drawSmallItem(g, cell, sxx, syy);
            }
        }

        // Arrow + output.
        int arrowX = gx + GRID + 3;
        int outY = y + (h - 16) / 2;
        g.text(this.font, Component.literal("→"), arrowX, outY + 4, 0xFFAAAAAA);
        int outX = arrowX + 9;
        g.item(recipe.output(), outX, outY);
        if (recipe.output().getCount() > 1) {
            g.itemDecorations(this.font, recipe.output(), outX, outY);
        }

        // State overlays and borders.
        if (recipe.banned()) {
            g.fill(x, y, x + w, y + h, COL_BANNED_FILL);
            g.outline(x, y, w, h, COL_BANNED);
        } else if (recipe.active()) {
            g.outline(x, y, w, h, COL_ACTIVE);
        } else if (recipe.root()) {
            g.outline(x, y, w, h, COL_ROOT);
        } else {
            g.outline(x, y, w, h, COL_BORDER);
        }
        if (hovered && isBannable(recipe)) {
            g.outline(x - 1, y - 1, w + 2, h + 2, 0xFFFFFFFF);
        }
    }

    /** Render an item at roughly 2/3 scale into a 9px slot by translating the pose and scaling. */
    private void drawSmallItem(GuiGraphicsExtractor g, ItemStack stack, int x, int y) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x + SLOT / 2f, y + SLOT / 2f);
        pose.scale(0.55f, 0.55f);
        pose.translate(-8f, -8f);
        g.item(stack, 0, 0);
        pose.popMatrix();
    }

    private void drawHoverTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        double wmx = this.width / 2.0 + (mouseX - this.width / 2.0 - panX) / zoom;
        double wmy = this.height / 2.0 + (mouseY - this.height / 2.0 - panY) / zoom;
        for (int i = cardHits.size() - 1; i >= 0; i--) {
            CardHit hit = cardHits.get(i);
            if (wmx >= hit.x() && wmx < hit.x() + hit.w() && wmy >= hit.y() && wmy < hit.y() + hit.h()) {
                var recipe = tree.recipes().get(hit.recipeIndex());
                List<Component> lines = new ArrayList<>();
                if (recipe.root()) {
                    lines.add(Component.literal("Encoded pattern").withStyle(ChatFormatting.AQUA));
                    lines.add(Component.literal("The pattern you are editing (not bannable).")
                            .withStyle(ChatFormatting.GRAY));
                } else {
                    lines.add(Component.literal(recipe.output().getHoverName().getString())
                            .withStyle(ChatFormatting.WHITE));
                    lines.add(Component.literal(recipe.recipeId()).withStyle(ChatFormatting.DARK_GRAY));
                    if (recipe.bannedEverywhere()) {
                        lines.add(Component.literal("Banned everywhere").withStyle(ChatFormatting.RED));
                    } else if (recipe.banned()) {
                        lines.add(Component.literal("Banned here").withStyle(ChatFormatting.RED));
                    } else if (recipe.active()) {
                        lines.add(Component.literal("Currently chosen").withStyle(ChatFormatting.GREEN));
                    }
                    lines.add(Component.literal("Left-click: toggle ban here").withStyle(ChatFormatting.GRAY));
                    lines.add(Component.literal("Shift-click: toggle ban everywhere").withStyle(ChatFormatting.GRAY));
                }
                g.setComponentTooltipForNextFrame(this.font, lines, mouseX, mouseY);
                return;
            }
        }
    }

    private boolean isBannable(CraftingTreeData.RecipeNode recipe) {
        return !recipe.root() && !recipe.recipeId().isEmpty();
    }

    // ----- input --------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (event.button() == 0) {
            double wmx = this.width / 2.0 + (event.x() - this.width / 2.0 - panX) / zoom;
            double wmy = this.height / 2.0 + (event.y() - this.height / 2.0 - panY) / zoom;
            for (int i = cardHits.size() - 1; i >= 0; i--) {
                CardHit hit = cardHits.get(i);
                if (wmx >= hit.x() && wmx < hit.x() + hit.w() && wmy >= hit.y() && wmy < hit.y() + hit.h()) {
                    var recipe = tree.recipes().get(hit.recipeIndex());
                    if (isBannable(recipe)) {
                        toggleBan(recipe, event.hasShiftDown());
                    }
                    return true;
                }
            }
        }
        dragging = true;
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragging) {
            panX += dragX;
            panY += dragY;
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (hasControlDown()) {
            zoomAt(scrollY * ZOOM_STEP_SCROLL, (int) mouseX, (int) mouseY);
            return true;
        }
        if (scrollX != 0) {
            panX += scrollX * 16;
        }
        panY += scrollY * 16;
        return true;
    }

    private boolean hasControlDown() {
        try {
            long window = org.lwjgl.glfw.GLFW.glfwGetCurrentContext();
            if (window == 0L) {
                return false;
            }
            int left = org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL);
            int right = org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL);
            return left == org.lwjgl.glfw.GLFW.GLFW_PRESS || right == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        } catch (Throwable t) {
            return false;
        }
    }

    private void toggleBan(CraftingTreeData.RecipeNode recipe, boolean everywhere) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return;
        }
        connection.send(new ToggleRecipeBanC2SPacket(menuId, recipe.outputItemId(), recipe.recipeId(),
                recipe.producingItemIndex(), everywhere));
        // The server resends the recomputed tree after applying the toggle.
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Ask the server to (re)build the tree for the pattern currently in the terminal. */
    public static void requestAndOpen(int menuId) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            connection.send(new RequestCraftingTreeC2SPacket(menuId));
        }
    }
}
