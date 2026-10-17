package com.lumengrid.recursiveae2patternprovider;

import appeng.api.stacks.AEItemKey;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Presentation groups never change recipe identities or crafting inputs. */
public final class TreePresentation {
    private static final List<String> COLORS = List.of("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink",
            "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");
    private static final List<String> WOODS = List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "pale_oak", "bamboo", "crimson", "warped");
    private TreePresentation() {}
    private record IngredientChoice(int id) {}
    private record Form(String family, int count, DataComponentPatch components) {}
    private record RecipeForm(AEItemKey output, int count, List<Form> grid) {}
    public static String family(String id) {
        int separator = id.indexOf(':');
        String namespace = id.substring(0, separator + 1), path = id.substring(separator + 1);
        for (String color : COLORS) if (path.startsWith(color + "_")) return namespace + "{color}_" + path.substring(color.length() + 1);
        if (namespace.equals("minecraft:")) {
            String prefix = path.startsWith("stripped_") ? "stripped_" : "";
            String plain = path.substring(prefix.length());
            for (String wood : WOODS) if (plain.startsWith(wood + "_")) {
                String type = plain.substring(wood.length() + 1);
                if (Set.of("planks", "log", "wood", "stem", "hyphae", "slab", "stairs", "fence", "fence_gate", "door", "trapdoor", "button", "pressure_plate").contains(type))
                    return namespace + prefix + "{wood}_" + type;
            }
        }
        return id;
    }
    private static Form form(ItemStack stack) {
        return new Form(family(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()), stack.getCount(), stack.getComponentsPatch());
    }
    public static Object groupKey(RecipeTree.Node node) {
        if (node.item().isEmpty()) return null;
        if (!node.recipe()) return node.choice() >= 0 ? new IngredientChoice(node.choice()) : null;
        if (node.note() == 2) return null;
        var grid = node.grid().stream().map(TreePresentation::form).toList();
        if (grid.stream().noneMatch(f -> f.family().contains("{"))) return null;
        return new RecipeForm(AEItemKey.of(node.item()), node.item().getCount(), grid);
    }
    public static boolean compressedId(String id) {
        String lower = id.toLowerCase(Locale.ROOT);
        return lower.contains("compressed") || lower.startsWith("compressium:") || lower.startsWith("compression:");
    }
    public static boolean compressed(RecipeTree.Node node) {
        return compressedId(node.recipeId()) || compressedId(BuiltInRegistries.ITEM.getKey(node.item().getItem()).toString())
                || node.recipe() && node.grid().stream().filter(s -> !s.isEmpty())
                .anyMatch(s -> compressedId(BuiltInRegistries.ITEM.getKey(s.getItem()).toString()));
    }
    public record Zoom(double zoom, double panX, double panY) {}
    public static Zoom zoomAt(double oldZoom, double panX, double panY, double x, double canvasY, double wheel) {
        double next = Math.clamp(oldZoom * Math.pow(1.15, wheel), .15, 1.5);
        double ratio = next / oldZoom;
        return new Zoom(next, x - (x - panX) * ratio, canvasY - (canvasY - panY) * ratio);
    }
}
