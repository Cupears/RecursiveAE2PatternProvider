package com.lumengrid.recursiveae2patternprovider;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.lumengrid.recursiveae2patternprovider.network.PlanExplanationS2CPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Items;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class PlanExplanationPacketTest {
    @Test
    void serverMenuMixinApplies(MinecraftServer server) {
        assertTrue(com.lumengrid.recursiveae2patternprovider.menu.IPlanExplanationMenu.class
                .isAssignableFrom(appeng.menu.me.crafting.CraftConfirmMenu.class));
    }
    @Test
    void explanationsAndClearPacketRoundTrip(MinecraftServer server) {
        Map<AEKey, List<AEKey>> sources = Map.of(AEItemKey.of(Items.LEATHER_HELMET), List.of(AEItemKey.of(Items.GLOWSTONE_DUST)));
        for (var expected : List.of(new PlanExplanationS2CPacket(7, sources), new PlanExplanationS2CPacket(7, Map.of()))) {
            var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
            try {
                PlanExplanationS2CPacket.STREAM_CODEC.encode(buf, expected);
                assertEquals(expected, PlanExplanationS2CPacket.STREAM_CODEC.decode(buf));
                assertEquals(0, buf.readableBytes());
            } finally { buf.release(); }
        }
    }

    @Test
    void rejectsOversizedSourceCount(MinecraftServer server) {
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
        try {
            buf.writeVarInt(7);
            buf.writeVarInt(PlanExplanation.MAX_SOURCES + 1);
            assertThrows(IllegalArgumentException.class, () -> PlanExplanationS2CPacket.STREAM_CODEC.decode(buf));
        } finally { buf.release(); }
    }
}
