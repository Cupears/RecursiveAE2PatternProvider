package com.lumengrid.recursiveae2patternprovider.network;

import com.lumengrid.recursiveae2patternprovider.RecursiveAE2PatternProvider;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public class ModNetwork {
    public static void registerPayloadHandlers(final RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("4");
        registrar.playToServer(RecipeTreePackets.RequestOpen.TYPE, RecipeTreePackets.RequestOpen.CODEC, RecipeTreePackets.RequestOpen::handle);
        registrar.playToClient(RecipeTreePackets.Branch.TYPE, RecipeTreePackets.Branch.CODEC, RecipeTreePackets.Branch::handle);
        registrar.playToServer(RecipeTreePackets.Expand.TYPE, RecipeTreePackets.Expand.CODEC, RecipeTreePackets.Expand::handle);
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
