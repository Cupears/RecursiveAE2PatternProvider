package com.lumengrid.recursiveae2patternprovider;

import appeng.api.crafting.IPatternDetails;
import appeng.crafting.pattern.AECraftingPattern;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import java.util.*;

/** Item-type restrictions local to one encoded pattern, never personal defaults. */
public final class PatternIngredientLocks {
    public static final int MAX_LOCKS = 128, MAX_SCOPE = 272;
    private static final int MAX_STORED_LOCKS = MAX_LOCKS + 8; // One logical root input can occupy all nine slots.
    private static final String KEY = "recursiveIngredientLocks";
    private PatternIngredientLocks() {}
    public interface Slots { int recursiveAE2$inputForSlot(int slot); }
    public static Map<String, String> read(ItemStack stack) {
        var data = stack.get(DataComponents.CUSTOM_DATA); if (data == null) return Map.of();
        var result = new TreeMap<String, String>();
        for (String line : data.copyTag().getString(KEY).orElse("").split("\n")) {
            var pair = line.split("\t", -1);
            if (pair.length == 2 && validScope(pair[0]) && PatternRecipeRules.valid(pair[1]) && result.size() < MAX_STORED_LOCKS)
                result.put(pair[0], pair[1]);
        }
        return Map.copyOf(result);
    }
    public static boolean validScope(String key) {
        if (key.length() > MAX_SCOPE) return false;
        if (key.matches("root/[0-8]")) return true;
        int split = key.lastIndexOf('#');
        if (split < 1 || !key.substring(split + 1).matches("[0-8]")) return false;
        return key.substring(0, split).equals("root") || PatternRecipeRules.valid(key.substring(0, split));
    }
    public static void write(ItemStack stack, Map<String, String> locks) {
        if (locks.size() > MAX_STORED_LOCKS || locks.entrySet().stream().anyMatch(e -> !validScope(e.getKey()) || !PatternRecipeRules.valid(e.getValue())))
            throw new IllegalArgumentException("Invalid ingredient locks");
        var old = stack.get(DataComponents.CUSTOM_DATA); var tag = old == null ? new CompoundTag() : old.copyTag();
        if (locks.isEmpty()) tag.remove(KEY);
        else tag.putString(KEY, String.join("\n", new TreeMap<>(locks).entrySet().stream().map(e -> e.getKey() + "\t" + e.getValue()).toList()));
        if (tag.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA); else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
    public static String scope(List<RecipeTree.Node> nodes, RecipeTree.Node node) {
        if (node.recipe() || node.choice() < 0) return "";
        var parent = nodes.get(node.parent());
        return (node.parent() == 0 ? "root" : parent.recipeId()) + "#" + node.choice();
    }
    public static Map<String, String> logical(ItemStack stack, IPatternDetails raw) {
        var result = new TreeMap<String, String>();
        for (var e : read(stack).entrySet()) {
            if (e.getKey().startsWith("root/")) {
                if (raw instanceof Slots slots) {
                    int input = slots.recursiveAE2$inputForSlot(Integer.parseInt(e.getKey().substring(5)));
                    if (input >= 0) result.put("root#" + input, e.getValue());
                }
            } else result.put(e.getKey(), e.getValue());
        }
        return result;
    }
    public static Map<String, String> physical(Map<String, String> logical, IPatternDetails raw) {
        var result = new TreeMap<String, String>();
        for (var e : logical.entrySet()) {
            if (e.getKey().startsWith("root#")) {
                if (!(raw instanceof Slots slots)) throw new IllegalArgumentException("Pattern has no crafting slots");
                int input = Integer.parseInt(e.getKey().substring(5)); boolean found = false;
                for (int s = 0; s < 9; s++) if (slots.recursiveAE2$inputForSlot(s) == input) { result.put("root/" + s, e.getValue()); found = true; }
                if (!found) throw new IllegalArgumentException("Unknown crafting input");
            } else result.put(e.getKey(), e.getValue());
        }
        if (result.size() > MAX_STORED_LOCKS) throw new IllegalArgumentException("Too many ingredient locks");
        return result;
    }
    public static boolean maySave(Map<String, String> proposed, Map<String, String> original, List<RecipeTree.Node> nodes) {
        if (proposed.size() > MAX_LOCKS) return false;
        return proposed.entrySet().stream().allMatch(e -> validScope(e.getKey()) && !e.getKey().startsWith("root/")
                && PatternRecipeRules.valid(e.getValue()) && (Objects.equals(original.get(e.getKey()), e.getValue())
                || nodes.stream().anyMatch(n -> !n.recipe() && n.note() != 2 && !n.item().isEmpty() && scope(nodes, n).equals(e.getKey())
                && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(n.item().getItem()).toString().equals(e.getValue()))));
    }
    public static Map<Integer, String> forRecipe(Map<String, String> locks, String recipe) {
        var result = new HashMap<Integer, String>();
        locks.forEach((key, value) -> { if (key.startsWith(recipe + "#")) result.put(Integer.parseInt(key.substring(recipe.length() + 1)), value); });
        return result;
    }
    public static boolean validRootCombination(IPatternDetails raw, Map<String, String> physical, net.minecraft.world.level.Level level) {
        if (!(raw instanceof AECraftingPattern crafting)) return physical.keySet().stream().noneMatch(k -> k.startsWith("root/"));
        var input = new ArrayList<ItemStack>();
        for (var sparse : crafting.getSparseInputs()) input.add(sparse != null && sparse.what() instanceof appeng.api.stacks.AEItemKey key ? key.toStack() : ItemStack.EMPTY);
        for (var e : physical.entrySet()) if (e.getKey().startsWith("root/")) {
            int slot = Integer.parseInt(e.getKey().substring(5));
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.Identifier.parse(e.getValue())).orElseThrow().value();
            input.set(slot, new ItemStack(item));
        }
        try {
            var output = crafting.assemble(net.minecraft.world.item.crafting.CraftingInput.of(3, 3, input), level);
            var expected = crafting.getPrimaryOutput();
            return expected.what() instanceof appeng.api.stacks.AEItemKey key && ItemStack.matches(output, key.toStack((int)expected.amount()));
        } catch (RuntimeException e) { return false; }
    }
    public static void copy(ItemStack oldPattern, ItemStack newPattern) {
        var locks = new TreeMap<>(read(oldPattern));
        // Slot locks are safe to carry only while the encoded recipe/grid stays the same.
        if (!Objects.equals(oldPattern.get(appeng.api.ids.AEComponents.ENCODED_CRAFTING_PATTERN),
                newPattern.get(appeng.api.ids.AEComponents.ENCODED_CRAFTING_PATTERN))) locks.keySet().removeIf(k -> k.startsWith("root/"));
        write(newPattern, locks);
    }
}
