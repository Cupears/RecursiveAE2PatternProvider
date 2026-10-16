package com.lumengrid.recursiveae2patternprovider.client;

import com.lumengrid.recursiveae2patternprovider.CraftingTreeData;
import net.minecraft.client.Minecraft;

/**
 * Client-side glue between the {@link com.lumengrid.recursiveae2patternprovider.network.CraftingTreeS2CPacket}
 * and the {@link CraftingTreeScreen}.
 *
 * <p>The server owns the tree: pressing Inspect (or toggling a ban) sends a request, the server
 * builds a fresh {@link CraftingTreeData} and ships it back here. If the inspector screen is already
 * open for the same menu we just refresh it in place; otherwise we open it. This keeps the AE2
 * encoding container menu untouched (opening a plain {@link net.minecraft.client.gui.screens.Screen}
 * via {@code setScreen} does not close the container).
 */
public final class CraftingTreeClient {

    private CraftingTreeClient() {}

    public static void acceptTree(int menuId, CraftingTreeData tree) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.containerMenu == null || mc.player.containerMenu.containerId != menuId) {
            return;
        }

        if (mc.screen instanceof CraftingTreeScreen screen && screen.getMenuId() == menuId) {
            screen.updateTree(tree);
            return;
        }

        if (tree.isEmpty()) {
            return;
        }

        mc.setScreen(new CraftingTreeScreen(menuId, tree, mc.screen));
    }
}
