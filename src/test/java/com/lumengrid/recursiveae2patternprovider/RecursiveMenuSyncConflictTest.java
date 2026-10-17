package com.lumengrid.recursiveae2patternprovider;

import appeng.menu.guisync.DataSynchronization;
import appeng.menu.guisync.GuiSync;
import appeng.menu.me.items.PatternEncodingTermMenu;
import com.lumengrid.recursiveae2patternprovider.menu.RecursiveMenuSync;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
public class RecursiveMenuSyncConflictTest {
    // TransparentAE2's TerminalMenuMixin injects these IDs into MEStorageMenu,
    // which is an ancestor of PatternEncodingTermMenu. AE2 scans the whole hierarchy.
    public static class TransparentParentFields {
        @GuiSync(30000) public boolean networkAvailable;
        @GuiSync(30001) public boolean renderingEnabled;
    }
    public static class RecursiveChildFields extends TransparentParentFields {
        @GuiSync(RecursiveMenuSync.RECURSIVE_MODE) public boolean recursiveMode;
    }
    @Test void opensAndSynchronizesWithTransparentParentFields(MinecraftServer server) {
        var source = new RecursiveChildFields();
        var target = new RecursiveChildFields();
        var serverSync = assertDoesNotThrow(() -> new DataSynchronization(source));
        var clientSync = assertDoesNotThrow(() -> new DataSynchronization(target));
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
        try {
            source.networkAvailable = true; source.renderingEnabled = true; source.recursiveMode = true;
            serverSync.writeFull(buf); clientSync.readUpdate(buf, new ShortOpenHashSet());
            assertTrue(target.networkAvailable); assertTrue(target.renderingEnabled); assertTrue(target.recursiveMode);
            buf.clear(); source.recursiveMode = false;
            serverSync.writeUpdate(buf); clientSync.readUpdate(buf, new ShortOpenHashSet());
            assertFalse(target.recursiveMode); assertTrue(target.networkAvailable); assertTrue(target.renderingEnabled);
        } finally { buf.release(); }
    }
    @Test void transformedTerminalUsesTheSameConflictFreeId(MinecraftServer server) throws Exception {
        var field = PatternEncodingTermMenu.class.getDeclaredField("recursiveModeActive");
        short id = field.getAnnotation(GuiSync.class).value();
        assertEquals(RecursiveMenuSync.RECURSIVE_MODE, id);
        assertNotEquals(30000, id); assertNotEquals(30001, id);
        var seen = new HashSet<Short>();
        for (Class<?> type = PatternEncodingTermMenu.class;
             type != net.minecraft.world.inventory.AbstractContainerMenu.class && type != Object.class;
             type = type.getSuperclass()) {
            for (var candidate : type.getDeclaredFields()) {
                var sync = candidate.getAnnotation(GuiSync.class);
                if (sync != null) assertTrue(seen.add(sync.value()), "Duplicate GUI sync field: " + candidate);
            }
        }
    }
}
