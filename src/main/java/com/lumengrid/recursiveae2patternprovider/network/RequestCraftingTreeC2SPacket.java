package com.lumengrid.recursiveae2patternprovider.network;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import com.lumengrid.recursiveae2patternprovider.CraftingTreeData;
import com.lumengrid.recursiveae2patternprovider.CraftingTreeInspector;
import com.lumengrid.recursiveae2patternprovider.PatternUtil;
import com.lumengrid.recursiveae2patternprovider.RecursiveAE2PatternProvider;
import com.lumengrid.recursiveae2patternprovider.menu.IInspectablePatternMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client -> server: build (or rebuild) the crafting tree for the pattern in the terminal. */
public record RequestCraftingTreeC2SPacket(int menuId) implements CustomPacketPayload {

    public static final Type<RequestCraftingTreeC2SPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(RecursiveAE2PatternProvider.MODID, "request_crafting_tree"));

    public static final StreamCodec<ByteBuf, RequestCraftingTreeC2SPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RequestCraftingTreeC2SPacket::menuId,
            RequestCraftingTreeC2SPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RequestCraftingTreeC2SPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                sendTreeTo(player, packet.menuId());
            }
        });
    }

    /** Build the inspector tree for the player's open encoding menu and send it back. */
    public static void sendTreeTo(ServerPlayer player, int menuId) {
        var menu = player.containerMenu;
        if (menu == null || menu.containerId != menuId || !(menu instanceof IInspectablePatternMenu inspectable)) {
            return;
        }
        ItemStack pattern = inspectable.recursiveAE2$getInspectPattern();
        CraftingTreeData data = CraftingTreeData.EMPTY;
        if (!pattern.isEmpty()) {
            IPatternDetails details = PatternDetailsHelper.decodePattern(pattern, player.level());
            if (details != null) {
                data = CraftingTreeInspector.build(details, player.level(), PatternUtil.getBans(pattern));
            }
        }
        PacketDistributor.sendToPlayer(player, new CraftingTreeS2CPacket(menuId, data));
    }
}
