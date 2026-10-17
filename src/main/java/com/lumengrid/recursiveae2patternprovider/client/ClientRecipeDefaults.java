package com.lumengrid.recursiveae2patternprovider.client;

import com.lumengrid.recursiveae2patternprovider.*;
import com.lumengrid.recursiveae2patternprovider.menu.IRecipeTreeMenu;
import com.lumengrid.recursiveae2patternprovider.network.RecipeTreePackets;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import java.nio.file.Path;
import java.util.Set;
import java.io.IOException;

@EventBusSubscriber(modid = RecursiveAE2PatternProvider.MODID, value = Dist.CLIENT)
public final class ClientRecipeDefaults {
    private ClientRecipeDefaults() {}
    public static Path path() { return FMLPaths.CONFIGDIR.get().resolve("otter-patterns-recipe-defaults.json"); }
    public static void send(Set<String> defaults) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.containerMenu instanceof IRecipeTreeMenu)
            ClientPacketDistributor.sendToServer(new RecipeTreePackets.Defaults(player.containerMenu.containerId, defaults));
    }
    @SubscribeEvent public static void opened(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen)
                || !(screen.getMenu() instanceof IRecipeTreeMenu)) return;
        try { send(RecipeDefaults.load(path())); }
        catch (IOException e) { RecursiveAE2PatternProvider.LOGGER.warn("Could not read personal recipe defaults", e); }
    }
}
