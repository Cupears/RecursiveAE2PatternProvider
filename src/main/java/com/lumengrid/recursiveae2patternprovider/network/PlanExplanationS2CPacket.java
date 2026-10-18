package com.lumengrid.recursiveae2patternprovider.network;

import appeng.api.stacks.AEKey;
import com.lumengrid.recursiveae2patternprovider.PlanExplanation;
import com.lumengrid.recursiveae2patternprovider.RecursiveAE2PatternProvider;
import com.lumengrid.recursiveae2patternprovider.menu.IPlanExplanationMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.*;

public record PlanExplanationS2CPacket(int menuId, Map<AEKey, List<AEKey>> sources, Map<AEKey, List<AEKey>> origins) implements CustomPacketPayload {
    public PlanExplanationS2CPacket(int menuId, Map<AEKey, List<AEKey>> sources) {
        this(menuId, sources, Map.of());
    }
    public static final Type<PlanExplanationS2CPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(RecursiveAE2PatternProvider.MODID, "plan_explanation"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PlanExplanationS2CPacket> STREAM_CODEC = new StreamCodec<>() {
        public PlanExplanationS2CPacket decode(RegistryFriendlyByteBuf buf) {
            int menuId = buf.readVarInt();
            return new PlanExplanationS2CPacket(menuId, readMap(buf), readMap(buf));
        }
        public void encode(RegistryFriendlyByteBuf buf, PlanExplanationS2CPacket packet) {
            buf.writeVarInt(packet.menuId());
            writeMap(buf, packet.sources());
            writeMap(buf, packet.origins());
        }
    };
    private static Map<AEKey, List<AEKey>> readMap(RegistryFriendlyByteBuf buf) {
        int count = bounded(buf.readVarInt(), PlanExplanation.MAX_SOURCES);
        Map<AEKey, List<AEKey>> sources = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            AEKey source = Objects.requireNonNull(AEKey.readKey(buf));
            int products = bounded(buf.readVarInt(), PlanExplanation.MAX_PRODUCTS);
            List<AEKey> outputs = new ArrayList<>();
            for (int j = 0; j < products; j++) outputs.add(Objects.requireNonNull(AEKey.readKey(buf)));
            sources.put(source, List.copyOf(outputs));
        }
        return Collections.unmodifiableMap(sources);
    }
    private static void writeMap(RegistryFriendlyByteBuf buf, Map<AEKey, List<AEKey>> sources) {
        buf.writeVarInt(bounded(sources.size(), PlanExplanation.MAX_SOURCES));
        sources.forEach((source, products) -> {
            AEKey.writeKey(buf, source);
            buf.writeVarInt(bounded(products.size(), PlanExplanation.MAX_PRODUCTS));
            products.forEach(product -> AEKey.writeKey(buf, product));
        });
    }
    private static int bounded(int count, int maximum) {
        if (count < 0 || count > maximum) throw new IllegalArgumentException("Invalid plan explanation size: " + count);
        return count;
    }
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(PlanExplanationS2CPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            var menu = context.player().containerMenu;
            if (menu.containerId == packet.menuId() && menu instanceof IPlanExplanationMenu explanations) {
                explanations.recursiveAE2$setExplanation(packet.sources());
                explanations.recursiveAE2$setOrigins(packet.origins());
            }
        });
    }
}
