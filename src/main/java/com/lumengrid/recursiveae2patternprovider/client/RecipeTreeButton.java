package com.lumengrid.recursiveae2patternprovider.client;

import appeng.client.gui.widgets.IconButton;
import appeng.util.Icon;
import com.lumengrid.recursiveae2patternprovider.PatternUtil;
import com.lumengrid.recursiveae2patternprovider.menu.IRecipeTreeMenu;
import com.lumengrid.recursiveae2patternprovider.network.RecipeTreePackets;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import java.util.List;

public final class RecipeTreeButton extends IconButton {
    public RecipeTreeButton() {
        super(button -> {
            var player = Minecraft.getInstance().player;
            if (player != null && canEdit(player.containerMenu))
                ClientPacketDistributor.sendToServer(new RecipeTreePackets.RequestOpen(player.containerMenu.containerId));
        });
        setHalfSize(true);
        setDisableBackground(true);
    }
    public static boolean canEdit(AbstractContainerMenu menu) {
        return menu instanceof IRecipeTreeMenu terminal && terminal.recursiveAE2$treeSlot() != null
                && PatternUtil.isRecursive(terminal.recursiveAE2$treeSlot().getItem());
    }
    @Override protected Icon getIcon() { return Icon.S_CRAFT; }
    @Override public List<Component> getTooltipMessage() {
        return List.of(Component.translatable("gui.recursiveae2patternprovider.tree.button").withColor(0xFFC98A));
    }
}
