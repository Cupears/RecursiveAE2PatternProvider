package com.lumengrid.recursiveae2patternprovider.network;

import com.lumengrid.recursiveae2patternprovider.RecursiveAE2PatternProvider;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public class ModNetwork {
    public static void registerPayloadHandlers(final RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("2");
        registrar.playToClient(RecipeTreePackets.Open.TYPE, RecipeTreePackets.Open.CODEC, RecipeTreePackets.Open::handle);
        registrar.playToClient(RecipeTreePackets.Result.TYPE, RecipeTreePackets.Result.CODEC, RecipeTreePackets.Result::handle);
        registrar.playToServer(RecipeTreePackets.Save.TYPE, RecipeTreePackets.Save.CODEC, RecipeTreePackets.Save::handle);
        registrar.playToClient(PlanExplanationS2CPacket.TYPE, PlanExplanationS2CPacket.STREAM_CODEC, PlanExplanationS2CPacket::handle);

        registrar.playToServer(
                ToggleRecursiveModeC2SPacket.TYPE,
                ToggleRecursiveModeC2SPacket.STREAM_CODEC,
                ToggleRecursiveModeC2SPacket::handle
        );
    }
}
