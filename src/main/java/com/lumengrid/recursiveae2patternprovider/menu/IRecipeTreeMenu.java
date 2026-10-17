package com.lumengrid.recursiveae2patternprovider.menu;

import net.minecraft.world.inventory.Slot;

/** The server-owned encoded pattern edited by the terminal tree button. */
public interface IRecipeTreeMenu {
    Slot recursiveAE2$treeSlot();
    void recursiveAE2$setDefaults(java.util.Set<String> defaults);
    java.util.Set<String> recursiveAE2$defaults();
}
