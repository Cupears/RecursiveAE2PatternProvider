package com.lumengrid.recursiveae2patternprovider;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE = BUILDER
            .comment("Enable recursive AE2 pattern generation. When enabled, the mod will automatically generate dependency patterns for missing crafting ingredients. Works with all AE2 pattern types (crafting, processing, smithing, stonecutting, etc.)")
            .define("enableRecursiveAE2PatternProvider", true);

    public static final ModConfigSpec.BooleanValue ENABLE_ENCODING_GUI_TOGGLE = BUILDER
            .comment("Show the recursive pattern toggle button in the Pattern Encoding Terminal GUI. Default is true")
            .define("enableEncodingGuiToggle", true);

    public static final ModConfigSpec.BooleanValue DEFAULT_RECURSIVE_MODE = BUILDER
            .comment("Start newly opened pattern encoding terminals in recursive mode. The terminal toggle can override this for the current session. Uses the server setting in multiplayer; existing patterns are not converted.")
            .define("defaultRecursiveMode", false);

    public static final ModConfigSpec.IntValue RECURSION_DEPTH = BUILDER
            .comment("Maximum recursion depth for dependency pattern generation. -1 = no limit, 0 = disable recursion, positive values = max depth")
            .defineInRange("recursionDepth", -1, -1, 100);

    public static final ModConfigSpec.BooleanValue DEFAULT_ALLOW_SUBSTITUTES = BUILDER
            .comment("Default setting for allowing item substitutes in auto-generated patterns when parent pattern has no substitute info. When true, allows equivalent items (e.g., different wood types) to be used in recipes")
            .define("defaultAllowSubstitutes", false);

    public static final ModConfigSpec.BooleanValue DEFAULT_ALLOW_FLUID_SUBSTITUTES = BUILDER
            .comment("Default setting for allowing fluid substitutes in auto-generated patterns when parent pattern has no substitute info. When true, allows equivalent fluids to be used in recipes")
            .define("defaultAllowFluidSubstitutes", false);

    public static final ModConfigSpec.ConfigValue<String> RECIPE_ITEM = BUILDER
            .comment("Item required to craft recursive patterns. Use format: 'namespace:item_name'. Default is 'minecraft:iron_ingot'")
            .define("recipeItem", "minecraft:iron_ingot");

    public static final ModConfigSpec.IntValue MAX_ALTERNATIVES_PER_ITEM = BUILDER
            .comment("Maximum recipes per dependency in static selection mode (retainRecipeAlternatives = false)")
            .defineInRange("maxAlternativesPerItem", 1, 1, 64);

    public static final ModConfigSpec.BooleanValue RETAIN_RECIPE_ALTERNATIVES = BUILDER
            .comment("Publish valid recipe alternatives for AE2 to check against stock during each crafting calculation. Includes stored block/nugget conversions without expanding compression chains. Publishes at most 64 alternatives per item; when false, uses maxAlternativesPerItem and static selection.")
            .define("retainRecipeAlternatives", true);

    public static final ModConfigSpec.BooleanValue GENERATED_INGREDIENT_ALTERNATIVES = BUILDER
            .comment("Publish validated ingredient variants for generated patterns (e.g. accepted log/plank choices). Each variant uses exact item inputs during execution so substitutions cannot consume stock reserved for another dependency. Does not change manually encoded root patterns. When false, generated patterns inherit the parent substitution setting.")
            .define("generatedIngredientAlternatives", true);

    public static final ModConfigSpec.BooleanValue SKIP_SELF_REFERENTIAL_RECIPES = BUILDER
            .comment("Ignore recipes that consume their own output (e.g. 'clear settings' / NBT-wipe recipes) when generating dependency patterns")
            .define("skipSelfReferentialRecipes", true);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> RECIPE_BLACKLIST = BUILDER
            .comment("Recipe ids that are never used for auto-generated patterns. Use an exact id ('modid:recipe_name') or a prefix ending with '*' ('modid:*', 'modid:reset/*')")
            .defineListAllowEmpty("recipeBlacklist", List.of(), () -> "", o -> o instanceof String);

    static final ModConfigSpec SPEC = BUILDER.build();
}
