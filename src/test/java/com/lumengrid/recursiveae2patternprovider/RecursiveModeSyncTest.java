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
    void newEncodingSessionsDefaultToRecursive(MinecraftServer server) {
        assertEquals(true, Config.DEFAULT_RECURSIVE_MODE.getDefault());
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

    @Test
    void explicitOffClearsRecursiveFlagAndPreservesOtherData(MinecraftServer server) {
        var pattern = new net.minecraft.world.item.ItemStack(appeng.core.definitions.AEItems.CRAFTING_PATTERN.asItem());
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("owner", "Cupears");
        pattern.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
        PatternUtil.setRecursive(pattern, true);
        assertTrue(PatternUtil.isRecursive(pattern));
        PatternUtil.setRecursive(pattern, false);
        assertFalse(PatternUtil.isRecursive(pattern));
        assertEquals("Cupears", pattern.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA).copyTag().getString("owner").orElseThrow());
        assertFalse(pattern.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA).copyTag().contains("recursive"));
    }

    @Test
    void explicitOffLeavesNoEmptyCustomData(MinecraftServer server) {
        var original = new net.minecraft.world.item.ItemStack(appeng.core.definitions.AEItems.CRAFTING_PATTERN.asItem());
        var recursive = PatternUtil.createRecursivePattern(original);
        assertFalse(PatternUtil.isRecursive(original));
        assertTrue(PatternUtil.isRecursive(recursive));
        PatternUtil.setRecursive(recursive, false);
        assertNull(recursive.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA));
    }

    @Test
    void absentWirelessTerminalSkipsBothIntegrationMixins(MinecraftServer server) {
        assertNull(net.neoforged.fml.loading.FMLLoader.getCurrent().getLoadingModList().getModFileById("wcwt"));
        var plugin = new com.lumengrid.recursiveae2patternprovider.mixin.RecursiveAE2MixinPlugin();
        assertFalse(plugin.shouldApplyMixin("com.lhy.wcwt.menu.WirelessComprehensiveWorkTerminalMenu", "com.lumengrid.recursiveae2patternprovider.mixin.WirelessComprehensiveWorkTerminalMenuMixin"));
        assertFalse(plugin.shouldApplyMixin("com.lhy.wcwt.client.gui.WirelessComprehensiveWorkTerminalScreen", "com.lumengrid.recursiveae2patternprovider.mixin.client.WirelessComprehensiveWorkTerminalScreenMixin"));
        assertTrue(plugin.shouldApplyMixin("appeng.menu.me.items.PatternEncodingTermMenu", "com.lumengrid.recursiveae2patternprovider.mixin.PatternEncodingTermMenuMixin"));
    }

    private static IRecursivePatternMenu emptyMenu() throws Exception {
        // Exercise the actual transformed menu and AE2 field codec without opening a world GUI.
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) field.get(null);
        return (IRecursivePatternMenu) unsafe.allocateInstance(PatternEncodingTermMenu.class);
    }
}
