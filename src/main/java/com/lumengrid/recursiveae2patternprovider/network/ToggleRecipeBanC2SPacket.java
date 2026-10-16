package com.lumengrid.recursiveae2patternprovider.network;

import com.lumengrid.recursiveae2patternprovider.PatternUtil;
import com.lumengrid.recursiveae2patternprovider.RecipeBans;
import com.lumengrid.recursiveae2patternprovider.RecursiveAE2PatternProvider;
import com.lumengrid.recursiveae2patternprovider.RecursivePatternGenerator;
import com.lumengrid.recursiveae2patternprovider.menu.IInspectablePatternMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client -> server: toggle a recipe ban on the pattern being inspected, then resend the tree. */
public record ToggleRecipeBanC2SPacket(int menuId, String itemId, String recipeId, boolean everywhere)
        implements CustomPacketPayload {

    public static final Type<ToggleRecipeBanC2SPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(RecursiveAE2PatternProvider.MODID, "toggle_recipe_ban"));

    public static final StreamCodec<ByteBuf, ToggleRecipeBanC2SPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ToggleRecipeBanC2SPacket::menuId,
            ByteBufCodecs.STRING_UTF8, ToggleRecipeBanC2SPacket::itemId,
            ByteBufCodecs.STRING_UTF8, ToggleRecipeBanC2SPacket::recipeId,
            ByteBufCodecs.BOOL, ToggleRecipeBanC2SPacket::everywhere,
            ToggleRecipeBanC2SPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ToggleRecipeBanC2SPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            var menu = player.containerMenu;
            if (menu == null || menu.containerId != packet.menuId()
                    || !(menu instanceof IInspectablePatternMenu inspectable)) {
                return;
            }
            ItemStack pattern = inspectable.recursiveAE2$getInspectPattern();
            if (pattern.isEmpty() || packet.recipeId().isEmpty()) {
                return;
            }

            RecipeBans current = PatternUtil.getBans(pattern);
            boolean wasBanned = current.isBanned(packet.itemId(), packet.recipeId());
            RecipeBans updated = packet.everywhere()
                    ? current.toggledRecipe(packet.recipeId())
                    : current.toggledEdge(packet.itemId(), packet.recipeId());

            // "An unbanned option may be selected": never let the player ban the last
            // remaining recipe for an item. Only guard when a ban is being turned on.
            boolean nowBanned = updated.isBanned(packet.itemId(), packet.recipeId());
            if (nowBanned && !wasBanned && !leavesAnUnbannedRecipe(player, packet.itemId(), updated)) {
                // Reject the toggle; resend the unchanged tree so the client re-syncs.
                RequestCraftingTreeC2SPacket.sendTreeTo(player, packet.menuId());
                return;
            }

            ItemStack edited = pattern.copy();
            PatternUtil.setBans(edited, updated);
            inspectable.recursiveAE2$setInspectPattern(edited);

            RequestCraftingTreeC2SPacket.sendTreeTo(player, packet.menuId());
        });
    }

    private static boolean leavesAnUnbannedRecipe(ServerPlayer player, String itemId, RecipeBans bans) {
        try {
            Item item = BuiltInRegistries.ITEM.get(Identifier.parse(itemId)).map(Holder::value).orElse(null);
            if (item == null) {
                return true;
            }
            var candidates = RecursivePatternGenerator.recipeIndex(player.level()).getOrDefault(item, java.util.List.of());
            for (var holder : candidates) {
                if (!RecursivePatternGenerator.isSelectableRecipe(holder, item)) {
                    continue;
                }
                if (!bans.isBanned(itemId, holder.id().identifier().toString())) {
                    return true;
                }
            }
            return candidates.isEmpty();
        } catch (Exception e) {
            RecursiveAE2PatternProvider.LOGGER.debug("Ban guard failed for {}: {}", itemId, e.getMessage());
            return true;
        }
    }
}
