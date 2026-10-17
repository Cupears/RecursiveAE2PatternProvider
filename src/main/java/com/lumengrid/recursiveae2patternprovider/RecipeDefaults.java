package com.lumengrid.recursiveae2patternprovider;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Personal defaults live on the client; encoded rules travel with each pattern. */
public final class RecipeDefaults {
    private RecipeDefaults() {}
    public static Set<String> load(Path file) throws IOException {
        if (!Files.exists(file)) return Set.of();
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            var object = JsonParser.parseReader(reader).getAsJsonObject();
            var values = object.getAsJsonArray("blacklistedRecipes");
            if (values == null) throw new IOException("Missing blacklistedRecipes list");
            Set<String> result = new TreeSet<>();
            for (var value : values) {
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                        || !PatternRecipeRules.valid(value.getAsString())) throw new IOException("Invalid recipe default");
                result.add(value.getAsString());
            }
            if (result.size() > PatternRecipeRules.MAX_EXCLUSIONS) throw new IOException("Too many recipe defaults");
            return Set.copyOf(result);
        } catch (RuntimeException e) { throw new IOException("Invalid recipe defaults file", e); }
    }
    public static void save(Path file, Set<String> ids) throws IOException {
        if (ids.size() > PatternRecipeRules.MAX_EXCLUSIONS || ids.stream().anyMatch(id -> !PatternRecipeRules.valid(id)))
            throw new IOException("Invalid recipe defaults");
        Files.createDirectories(file.toAbsolutePath().getParent());
        var array = new JsonArray(); new TreeSet<>(ids).forEach(array::add);
        var object = new JsonObject(); object.add("blacklistedRecipes", array);
        var temp = Files.createTempFile(file.toAbsolutePath().getParent(), "recipe-defaults-", ".tmp");
        try {
            Files.writeString(temp, new GsonBuilder().setPrettyPrinting().create().toJson(object) + "\n", StandardCharsets.UTF_8);
            try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
}
