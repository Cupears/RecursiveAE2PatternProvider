package com.lumengrid.recursiveae2patternprovider.menu;

import net.minecraft.world.item.ItemStack;

/**
 * Implemented by the pattern encoding menu so the tree inspector can read and write the
 * recursive crafting pattern currently held in the terminal's encoded-pattern slot.
 */
public interface IInspectablePatternMenu {

    /** The recursive crafting pattern in the encoded slot, or {@link ItemStack#EMPTY} if none. */
    ItemStack recursiveAE2$getInspectPattern();

    /** Replace the encoded-pattern slot's contents (used after editing its bans). */
    void recursiveAE2$setInspectPattern(ItemStack stack);
}
