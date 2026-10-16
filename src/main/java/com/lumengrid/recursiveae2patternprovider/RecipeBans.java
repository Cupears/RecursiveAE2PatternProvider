package com.lumengrid.recursiveae2patternprovider;

import net.minecraft.nbt.CompoundTag;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Per-pattern record of which crafting recipes the player has forbidden the
 * recursive generator from using.
 *
 * <p>Two granularities are stored:
 * <ul>
 *   <li><b>edges</b> {@code (outputItemId, recipeId)} &mdash; "do not make this item
 *       with this recipe". This is the unit the inspector's recipe cards map to.</li>
 *   <li><b>recipes</b> {@code recipeId} &mdash; "never use this recipe for anything"
 *       (the shift-click "ban everywhere" convenience).</li>
 * </ul>
 * AE2 resolves patterns globally per item, so an {@code (item, recipe)} pair is the
 * finest grain that can actually influence a crafting plan.
 */
public final class RecipeBans {
    public static final RecipeBans EMPTY = new RecipeBans(Set.of(), Set.of());

    // '|' never appears in a resource location, so it cleanly joins item+recipe.
    private static final String EDGE_SEPARATOR = "|";
    private static final String ENTRY_SEPARATOR = "\n";
    private static final String TAG_EDGES = "recipeBanEdges";
    private static final String TAG_RECIPES = "recipeBanAll";

    private final Set<String> edges;
    private final Set<String> recipes;

    private RecipeBans(Set<String> edges, Set<String> recipes) {
        this.edges = edges;
        this.recipes = recipes;
    }

    public static String edgeKey(String itemId, String recipeId) {
        return itemId + EDGE_SEPARATOR + recipeId;
    }

    /** Edge key for a specific item-node instance: {@code item|recipe|nodeIndex}. */
    public static String edgeKey(String itemId, String recipeId, int itemNodeIndex) {
        return itemId + EDGE_SEPARATOR + recipeId + EDGE_SEPARATOR + itemNodeIndex;
    }

    public boolean isBanned(String itemId, String recipeId) {
        return recipes.contains(recipeId) || edges.contains(edgeKey(itemId, recipeId));
    }

    /**
     * Whether this exact {@code (item, recipe, node)} edge is banned. A per-item ban
     * ({@code item|recipe}) applies to all instances, so it also counts as banned here.
     */
    public boolean isBanned(String itemId, String recipeId, int itemNodeIndex) {
        return isBanned(itemId, recipeId)
                || edges.contains(edgeKey(itemId, recipeId, itemNodeIndex));
    }

    public boolean isRecipeBannedEverywhere(String recipeId) {
        return recipes.contains(recipeId);
    }

    public boolean isEmpty() {
        return edges.isEmpty() && recipes.isEmpty();
    }

    /** Flip a single {@code (item, recipe)} edge, returning an updated copy. */
    public RecipeBans toggledEdge(String itemId, String recipeId) {
        Set<String> next = new LinkedHashSet<>(edges);
        String key = edgeKey(itemId, recipeId);
        if (!next.remove(key)) {
            next.add(key);
        }
        return new RecipeBans(next, recipes);
    }

    /** Flip a single {@code (item, recipe, node)} edge, returning an updated copy. */
    public RecipeBans toggledEdge(String itemId, String recipeId, int itemNodeIndex) {
        Set<String> next = new LinkedHashSet<>(edges);
        String key = edgeKey(itemId, recipeId, itemNodeIndex);
        if (!next.remove(key)) {
            next.add(key);
        }
        return new RecipeBans(next, recipes);
    }

    /** Flip the "ban this recipe everywhere" flag, returning an updated copy. */
    public RecipeBans toggledRecipe(String recipeId) {
        Set<String> next = new LinkedHashSet<>(recipes);
        if (!next.remove(recipeId)) {
            next.add(recipeId);
        }
        return new RecipeBans(edges, next);
    }

    public static RecipeBans of(Collection<String> edges, Collection<String> recipes) {
        return new RecipeBans(new LinkedHashSet<>(edges), new LinkedHashSet<>(recipes));
    }

    public static RecipeBans merge(Collection<RecipeBans> parts) {
        Set<String> edges = new LinkedHashSet<>();
        Set<String> recipes = new LinkedHashSet<>();
        for (RecipeBans part : parts) {
            edges.addAll(part.edges);
            recipes.addAll(part.recipes);
        }
        if (edges.isEmpty() && recipes.isEmpty()) {
            return EMPTY;
        }
        return new RecipeBans(edges, recipes);
    }

    public Set<String> edges() {
        return Collections.unmodifiableSet(edges);
    }

    public Set<String> recipes() {
        return Collections.unmodifiableSet(recipes);
    }

    public void writeTag(CompoundTag tag) {
        if (edges.isEmpty()) {
            tag.remove(TAG_EDGES);
        } else {
            tag.putString(TAG_EDGES, String.join(ENTRY_SEPARATOR, edges));
        }
        if (recipes.isEmpty()) {
            tag.remove(TAG_RECIPES);
        } else {
            tag.putString(TAG_RECIPES, String.join(ENTRY_SEPARATOR, recipes));
        }
    }

    public static RecipeBans readTag(CompoundTag tag) {
        Set<String> edges = split(tag.getString(TAG_EDGES).orElse(""));
        Set<String> recipes = split(tag.getString(TAG_RECIPES).orElse(""));
        if (edges.isEmpty() && recipes.isEmpty()) {
            return EMPTY;
        }
        return new RecipeBans(edges, recipes);
    }

    private static Set<String> split(String joined) {
        Set<String> out = new LinkedHashSet<>();
        if (joined.isEmpty()) {
            return out;
        }
        for (String entry : joined.split(ENTRY_SEPARATOR)) {
            if (!entry.isEmpty()) {
                out.add(entry);
            }
        }
        return out;
    }
}
