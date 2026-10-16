package com.lumengrid.recursiveae2patternprovider.client;

import appeng.core.definitions.AEItems;
import com.lumengrid.recursiveae2patternprovider.Config;
import com.lumengrid.recursiveae2patternprovider.PatternUtil;
import com.lumengrid.recursiveae2patternprovider.RecursiveAE2PatternProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.List;

@EventBusSubscriber(modid = RecursiveAE2PatternProvider.MODID, value = Dist.CLIENT)
public class TooltipHandler {

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();

        if (!Config.ENABLE.get() || Config.RECURSION_DEPTH.get() == 0 || stack.isEmpty()) {
            return;
        }

        if (!PatternUtil.isRecursive(stack)) {
            return;
        }

        try {
            List<Component> tooltip = event.getToolTip();
            Component recursiveComponent = Component.translatable("tooltip.recursiveae2patternprovider.recursive_pattern")
                    .withStyle(ChatFormatting.GREEN);

            int insertIndex = tooltip.size();
            for (int i = 0; i < tooltip.size(); i++) {
                Component component = tooltip.get(i);

                if (component.getContents() instanceof TranslatableContents translatable) {
                    String key = translatable.getKey().toLowerCase();
                    if (key.contains("encoded")) {
                        insertIndex = i;
                        break;
                    }
                } else {
                    String visibleText = component.getString().toLowerCase();
                    if (visibleText.contains("encoded") || visibleText.contains("codificato")) {
                        insertIndex = i;
                        break;
                    }
                }
            }
            tooltip.add(insertIndex, recursiveComponent);
            tooltip.add(insertIndex + 1, Component.translatable("gui.recursiveae2patternprovider.tree.tooltip").withColor(0xFF9A32));
            int exclusions = com.lumengrid.recursiveae2patternprovider.PatternRecipeRules.read(stack).size();
            if (exclusions > 0) tooltip.add(insertIndex + 2, Component.translatable(
                    "gui.recursiveae2patternprovider.tree.tooltip_count", exclusions).withColor(0xFF9A32));

        } catch (Exception e) {
            RecursiveAE2PatternProvider.LOGGER.debug("Errore nell'aggiunta del tooltip al pattern: {}", e.getMessage());
        }
    }
}