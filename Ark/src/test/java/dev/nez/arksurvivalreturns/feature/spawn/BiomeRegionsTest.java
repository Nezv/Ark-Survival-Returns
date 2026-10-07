package dev.nez.arksurvivalreturns.feature.spawn;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** How a tile of land falls into biome regions: what stands alone, what goes into its neighbour, and that it never changes. */
class BiomeRegionsTest {
    /** Sea in the west, a beach one chunk wide, plains with a pocket of six forest chunks and a wood of eight by eight. */
    private static String coast(int x, int z) {
        if (x < 10) return "ocean";
        if (x == 10) return "beach";
        if (x >= 20 && x < 22 && z >= 3 && z < 6) return "forest";
        if (x >= 14 && x < 22 && z >= 16 && z < 24) return "dark_forest";
        return "plains";
    }

    private static int at(BiomeRegions.Tile tile, int x, int z) { return tile.region()[z * BiomeRegions.SIDE + x]; }

    @Test void aTileOfOneBiomeIsOneRegion() {
        BiomeRegions.Tile tile = BiomeRegions.divide((x, z) -> "plains");
        assertEquals(List.of("plains"), tile.biomes());
        assertArrayEquals(new int[]{BiomeRegions.CELLS}, tile.cells());
    }

    @Test void aBeachAndAPocketGoIntoTheLandBesideThem() {
        BiomeRegions.Tile tile = BiomeRegions.divide(BiomeRegionsTest::coast);
        // The beach lies between the sea (320 chunks) and the plains (602): the plains are the larger.
        assertEquals(List.of("plains", "ocean", "dark_forest"), tile.biomes());
        assertArrayEquals(new int[]{602 + 32 + 6, 320, 64}, tile.cells());
        assertEquals(0, at(tile, 25, 5), "open plains");
        assertEquals(0, at(tile, 10, 5), "the beach belongs to the plains");
        assertEquals(0, at(tile, 20, 4), "the pocket of forest belongs to the plains");
        assertEquals(1, at(tile, 2, 2), "the sea is a region of its own");
        assertEquals(2, at(tile, 15, 18), "a wood of 64 chunks is a region of its own");
    }

    @Test void stripesTooThinToStandAloneBecomeOneRegion() {
        BiomeRegions.Tile tile = BiomeRegions.divide((x, z) -> x % 2 == 0 ? "sand" : "grass");
        assertEquals(1, tile.biomes().size());
        assertArrayEquals(new int[]{BiomeRegions.CELLS}, tile.cells());
    }

    @Test void theSameLandComesOutTheSame() {
        BiomeRegions.Tile first = BiomeRegions.divide(BiomeRegionsTest::coast), second = BiomeRegions.divide(BiomeRegionsTest::coast);
        assertArrayEquals(first.region(), second.region());
        assertEquals(first.biomes(), second.biomes());
        int cells = 0;
        for (int count : first.cells()) cells += count;
        assertEquals(BiomeRegions.CELLS, cells);
    }
}
