package com.lumengrid.recursiveae2patternprovider;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Items;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class IngredientChoicesTest {
    @Test void unifiedDuplicatesPreferAcceptedReplacement(MinecraftServer server) {
        assertEquals(List.of(Items.IRON_INGOT), IngredientChoices.prefer(
                List.of(Items.COPPER_INGOT, Items.IRON_INGOT), i -> false,
                i -> i == Items.COPPER_INGOT ? Items.IRON_INGOT : null));
    }
    @Test void replacementsOutsideIngredientAreNeverSubstituted(MinecraftServer server) {
        assertEquals(List.of(Items.COPPER_INGOT), IngredientChoices.prefer(
                List.of(Items.COPPER_INGOT), i -> true, i -> Items.IRON_INGOT));
    }
    @Test void hiddenDuplicatesAreOmittedWhenVisibleChoicesExist(MinecraftServer server) {
        assertEquals(List.of(Items.IRON_INGOT), IngredientChoices.prefer(
                List.of(Items.COPPER_INGOT, Items.IRON_INGOT, Items.IRON_INGOT),
                i -> i == Items.COPPER_INGOT, i -> null));
    }
    @Test void hiddenOnlyAndUncraftableIngredientsRemainHonest(MinecraftServer server) {
        assertEquals(List.of(Items.RAW_IRON), IngredientChoices.prefer(
                List.of(Items.RAW_IRON), i -> true, i -> null));
        assertEquals(List.of(Items.RAW_IRON, Items.RAW_COPPER), IngredientChoices.prefer(
                List.of(Items.RAW_IRON, Items.RAW_COPPER), i -> false, i -> null));
        assertTrue(IngredientChoices.prefer(List.of(), i -> false, i -> null).isEmpty());
    }
}
