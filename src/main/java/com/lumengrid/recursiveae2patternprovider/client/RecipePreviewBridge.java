package com.lumengrid.recursiveae2patternprovider.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Optional viewer integration; the editor can still open without JEI. */
public final class RecipePreviewBridge {
    @FunctionalInterface public interface Draw { void draw(GuiGraphicsExtractor g, int x, int y, int width, int height, int mx, int my); }
    public record Preview(Component title, boolean processing, Draw draw) {}
    public interface Provider {
        List<Preview> previews(ItemStack output);
        void show(ItemStack output);
    }
    private static Provider provider;
    public static int generation;
    public static void set(Provider value) { provider = value; generation++; }
    public static boolean available() { return provider != null; }
    public static List<Preview> previews(ItemStack stack) { return provider == null ? List.of() : provider.previews(stack); }
    public static void show(ItemStack stack) { if (provider != null) provider.show(stack); }
}
