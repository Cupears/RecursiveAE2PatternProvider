package com.lumengrid.recursiveae2patternprovider.network;

import com.lumengrid.recursiveae2patternprovider.RecursiveAE2PatternProvider;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public class ModNetwork {
    public static void registerPayloadHandlers(final RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(RecursiveAE2PatternProvider.MODID);
        registrar.playToClient(PlanExplanationS2CPacket.TYPE, PlanExplanationS2CPacket.STREAM_CODEC, PlanExplanationS2CPacket::handle);

        registrar.playToServer(
                ToggleRecursiveModeC2SPacket.TYPE,
                ToggleRecursiveModeC2SPacket.STREAM_CODEC,
                ToggleRecursiveModeC2SPacket::handle
        );

        registrar.playToClient(
                CraftingTreeS2CPacket.TYPE,
                CraftingTreeS2CPacket.STREAM_CODEC,
                CraftingTreeS2CPacket::handle
        );

        registrar.playToServer(
                RequestCraftingTreeC2SPacket.TYPE,
                RequestCraftingTreeC2SPacket.STREAM_CODEC,
                RequestCraftingTreeC2SPacket::handle
        );

        registrar.playToServer(
                ToggleRecipeBanC2SPacket.TYPE,
                ToggleRecipeBanC2SPacket.STREAM_CODEC,
                ToggleRecipeBanC2SPacket::handle
        );
    }
}
