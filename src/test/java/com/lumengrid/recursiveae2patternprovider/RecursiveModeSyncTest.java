package com.lumengrid.recursiveae2patternprovider;

import appeng.menu.guisync.GuiSync;
import appeng.menu.guisync.SynchronizedField;
import appeng.menu.me.items.PatternEncodingTermMenu;
import com.lumengrid.recursiveae2patternprovider.menu.IRecursivePatternMenu;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class RecursiveModeSyncTest {
    @Test
    void existingInstallsRemainOptIn(MinecraftServer server) {
        assertEquals(false, Config.DEFAULT_RECURSIVE_MODE.getDefault());
    }

    @Test
    void serverDefaultAndManualOverrideReachClientMenu(MinecraftServer server) throws Exception {
        var field = PatternEncodingTermMenu.class.getDeclaredField("recursiveModeActive");
        assertTrue(Modifier.isPublic(field.getModifiers()));
        assertEquals(30000, field.getAnnotation(GuiSync.class).value());
        var serverMenu = emptyMenu();
        var clientMenu = emptyMenu();
        var serverField = SynchronizedField.create(serverMenu, field);
        var clientField = SynchronizedField.create(clientMenu, field);
        for (boolean enabled : new boolean[]{true, false, true}) {
            serverMenu.setRecursiveMode(enabled);
            clientMenu.setRecursiveMode(!enabled);
            var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
            try {
                serverField.write(buf);
                clientField.read(buf);
                assertEquals(enabled, clientMenu.isRecursiveMode());
                assertEquals(0, buf.readableBytes());
            } finally { buf.release(); }
        }
    }

    private static IRecursivePatternMenu emptyMenu() throws Exception {
        // Exercise the actual transformed menu and AE2 field codec without opening a world GUI.
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) field.get(null);
        return (IRecursivePatternMenu) unsafe.allocateInstance(PatternEncodingTermMenu.class);
    }
}
