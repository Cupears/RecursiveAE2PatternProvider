package com.lumengrid.recursiveae2patternprovider;

import appeng.core.definitions.AEItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public class PatternUtil {

    public static boolean isAE2Pattern(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        try {
            return stack.is(AEItems.CRAFTING_PATTERN.asItem()) ||
                    stack.is(AEItems.PROCESSING_PATTERN.asItem()) ||
                    stack.is(AEItems.SMITHING_TABLE_PATTERN.asItem()) ||
                    stack.is(AEItems.STONECUTTING_PATTERN.asItem());
        } catch (Exception e) {
            RecursiveAE2PatternProvider.LOGGER.debug("Error checking if item is AE2 pattern: {}", e.getMessage());
        }

        return false;
    }

    public static boolean isRecursive(ItemStack patternStack) {
        if (patternStack.isEmpty() || !isAE2Pattern(patternStack)) {
            return false;
        }

        try {
            var customData = patternStack.get(DataComponents.CUSTOM_DATA);
            if (customData != null) {
                CompoundTag tag = customData.copyTag();
                return tag.getBoolean("recursive").orElse(false);
            }
        } catch (Exception e) {
            RecursiveAE2PatternProvider.LOGGER.debug("Failed to read recursive flag: {}", e.getMessage());
        }

        return false;
    }

    /** Apply the current terminal choice without discarding unrelated item data. */
    public static void setRecursive(ItemStack pattern, boolean enabled) {
        var existingData = pattern.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = existingData != null ? existingData.copyTag() : new CompoundTag();
        if (enabled) tag.putBoolean("recursive", true);
        else tag.remove("recursive");
        if (tag.isEmpty()) pattern.remove(DataComponents.CUSTOM_DATA);
        else pattern.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /** Recipe bans the player stored on this pattern; {@link RecipeBans#EMPTY} if none. */
    public static RecipeBans getBans(ItemStack patternStack) {
        if (patternStack.isEmpty()) {
            return RecipeBans.EMPTY;
        }
        try {
            var customData = patternStack.get(DataComponents.CUSTOM_DATA);
            if (customData != null) {
                return RecipeBans.readTag(customData.copyTag());
            }
        } catch (Exception e) {
            RecursiveAE2PatternProvider.LOGGER.debug("Failed to read recipe bans: {}", e.getMessage());
        }
        return RecipeBans.EMPTY;
    }

    /** Store (or clear) recipe bans without discarding unrelated item data. */
    public static void setBans(ItemStack pattern, RecipeBans bans) {
        var existingData = pattern.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = existingData != null ? existingData.copyTag() : new CompoundTag();
        bans.writeTag(tag);
        if (tag.isEmpty()) pattern.remove(DataComponents.CUSTOM_DATA);
        else pattern.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static ItemStack createRecursivePattern(ItemStack originalPattern) {
        try {
            ItemStack newPattern = originalPattern.copy();

            setRecursive(newPattern, true);

            return newPattern;

        } catch (Exception e) {
            RecursiveAE2PatternProvider.LOGGER.error("Failed to create recursive pattern for JEI: {}", e.getMessage());
            return originalPattern.copy();
        }
    }
}
