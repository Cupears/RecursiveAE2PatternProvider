package com.lumengrid.recursiveae2patternprovider;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import java.util.*;

/** Exact recipe IDs excluded from this encoded pattern's generated dependencies. */
public final class PatternRecipeRules {
    public static final int MAX_EXCLUSIONS = 128;
    public static final int MAX_ID_LENGTH = 256;
    private static final String KEY = "recursiveRecipeExclusions";
    private PatternRecipeRules() {}

    public static Set<String> read(ItemStack pattern) {
        var data = pattern.get(DataComponents.CUSTOM_DATA);
        if (data == null) return Set.of();
        Set<String> result = new TreeSet<>();
        for (String id : data.copyTag().getString(KEY).orElse("").split("\n")) {
            if (valid(id) && result.size() < MAX_EXCLUSIONS) result.add(id);
        }
        return Collections.unmodifiableSet(result);
    }

    public static boolean valid(String id) {
        return !id.isEmpty() && id.length() <= MAX_ID_LENGTH && Identifier.tryParse(id) != null
                && id.equals(Identifier.parse(id).toString());
    }

    public static void write(ItemStack pattern, Set<String> excluded) {
        if (excluded.size() > MAX_EXCLUSIONS || excluded.stream().anyMatch(id -> !valid(id)))
            throw new IllegalArgumentException("Invalid recipe exclusions");
        var old = pattern.get(DataComponents.CUSTOM_DATA);
        var tag = old == null ? new CompoundTag() : old.copyTag();
        if (excluded.isEmpty()) tag.remove(KEY);
        else tag.putString(KEY, String.join("\n", new TreeSet<>(excluded)));
        if (tag.isEmpty()) pattern.remove(DataComponents.CUSTOM_DATA);
        else pattern.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static void copy(ItemStack oldPattern, ItemStack newPattern) {
        write(newPattern, read(oldPattern));
        PatternIngredientLocks.copy(oldPattern, newPattern);
    }

    public static void copyOrDefault(ItemStack oldPattern, ItemStack newPattern, Set<String> defaults) {
        write(newPattern, oldPattern.isEmpty() ? defaults : read(oldPattern));
        PatternIngredientLocks.copy(oldPattern, newPattern);
    }

    /** Changes may only affect IDs shown by the server or already saved on the item. */
    public static boolean maySave(Set<String> proposed, Set<String> original, Set<String> shown) {
        return proposed.size() <= MAX_EXCLUSIONS && proposed.stream().allMatch(id -> valid(id)
                && (shown.contains(id) || original.contains(id)));
    }
}
