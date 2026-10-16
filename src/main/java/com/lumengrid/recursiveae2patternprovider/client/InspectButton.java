package com.lumengrid.recursiveae2patternprovider.client;

import appeng.client.gui.widgets.IconButton;
import appeng.util.Icon;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The "Inspect crafting tree" button shown next to the recursive toggle in the pattern encoding
 * panel. A plain one-shot {@link IconButton} (unlike the adjacent toggle), it opens the
 * {@link CraftingTreeScreen} for the recursive pattern currently in the encoded slot.
 */
public class InspectButton extends IconButton {

    private static final List<Component> TOOLTIP = List.of(
            Component.literal("Inspect crafting tree"),
            Component.literal("Review the recipes this recursive pattern generates and ban the ones you don't want.")
    );

    public InspectButton(Button.OnPress onPress) {
        super(onPress);
    }

    @Override
    protected Icon getIcon() {
        return Icon.VIEW_MODE_CRAFTING;
    }

    @Override
    public List<Component> getTooltipMessage() {
        return TOOLTIP;
    }
}
