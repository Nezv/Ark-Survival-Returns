package dev.nez.arksurvivalreturns.feature.spawn;

import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BiomeProfileTest {
    @Test void forestClimateAndMountainTerrainAreIndependent() {
        var profile = BiomeProfile.classify("terralith:alpine_grove",
                Set.of("minecraft:is_taiga", "c:is_mountain", "c:is_snowy"), 0.2f, 0.9f);
        assertEquals(BiomeProfile.Type.TAIGA, profile.type());
        assertEquals(BiomeProfile.Climate.COLD, profile.climate());
        assertEquals(BiomeProfile.Moisture.WET, profile.moisture());
        assertTrue(profile.mountainous());
        assertTrue(profile.snowy());
    }
    @Test void waterAndCavesTakePrecedenceOverVegetationAndSnow() {
        assertEquals(BiomeProfile.Type.OCEAN, BiomeProfile.classify("test:frozen_ocean",
                Set.of("minecraft:is_ocean", "c:is_snowy"), 0, 0.5f).type());
        assertEquals(BiomeProfile.Type.WETLAND, BiomeProfile.classify("terralith:ice_marsh",
                Set.of("c:is_swamp", "c:is_snowy"), 0, 0.5f).type());
        assertEquals(BiomeProfile.Type.CAVE, BiomeProfile.classify("terralith:cave/fungal_caves",
                Set.of("c:is_cave", "c:is_mushroom"), 0.5f, 0.5f).type());
    }
    @Test void knownTagGapsAndPackOverridesAreExplicit() {
        assertEquals(BiomeProfile.Type.FOREST, BiomeProfile.classify("minecraft:cherry_grove",
                Set.of("minecraft:is_mountain"), 0.5f, 0.5f).type());
        assertEquals(BiomeProfile.Type.GRASSLAND, BiomeProfile.classify("terralith:blooming_plateau",
                Set.of(), 0.5f, 0.5f).type());
        assertEquals(BiomeProfile.Type.WETLAND, BiomeProfile.classify("terralith:blooming_plateau",
                Set.of("arksurvivalreturns:ecology/wetland"), 0.5f, 0.5f).type());
        assertEquals(BiomeProfile.Type.SKY_ISLAND, BiomeProfile.classify("terralith:skylands_spring",
                Set.of("terralith:skylands"), 0.5f, 0.5f).type());
    }
    @Test void unknownModsAreNotGuessedFromNamesOrAssignedUniversalHabitat() {
        assertEquals(BiomeProfile.Type.UNKNOWN, BiomeProfile.classify("other:forest_of_fire",
                Set.of(), 2, 0).type());
        var tagged = BiomeProfile.classify("other:forest_of_fire", Set.of("c:is_forest"), 2, 0);
        assertEquals(BiomeProfile.Type.FOREST, tagged.type());
        assertEquals(BiomeProfile.Climate.HOT, tagged.climate());
        assertEquals(BiomeProfile.Moisture.DRY, tagged.moisture());
    }
}
