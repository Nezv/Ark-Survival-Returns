package dev.nez.arksurvivalreturns.feature.spawn;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BiomePatchSurveyTest {
    private static BiomePatchSurvey.Sample sample(String biome, int y) {
        return new BiomePatchSurvey.Sample(BiomeProfile.classify(biome, Set.of("c:is_forest"), 0.5f, 0.5f), y);
    }
    @Test void countsOnlyConnectedExactBiomeAndPreservesSurfaceAltitude() {
        var patch = BiomePatchSurvey.measure(-17, -9, 64, (x, z) -> {
            // Two cells joined edge-to-edge, plus a disconnected same-biome island across a gap.
            boolean connected = z == -9 && (x == -17 || x == -9);
            boolean island = z == -9 && x == 7;
            return sample(connected || island ? "test:forest" : "test:birch_forest", x == -17 ? 70 : 110);
        }).orElseThrow();
        assertEquals(2, patch.cells());
        assertEquals(128, patch.areaBlocks());
        assertEquals(16, patch.spanX());
        assertEquals(8, patch.spanZ());
        assertEquals(70, patch.minY());
        assertEquals(110, patch.maxY());
        assertTrue(patch.enclosed());
    }
    @Test void excludesHolesAndDiagonalOnlyConnections() {
        var patch = BiomePatchSurvey.measure(0, 0, 64, (x, z) -> {
            boolean ring = Math.abs(x) <= 16 && Math.abs(z) <= 16 && !(x == 8 && z == 8);
            boolean diagonalIsland = x == 24 && z == 24;
            return sample(ring || diagonalIsland ? "test:forest" : "test:plains", 64);
        }).orElseThrow();
        assertEquals(24, patch.cells());
        assertTrue(patch.enclosed());
    }
    @Test void unloadedTerrainIsUnknownRatherThanAClosedBoundary() {
        var patch = BiomePatchSurvey.measure(0, 0, 64, (x, z) ->
                x == 0 && z == 0 ? sample("test:forest", 64) : null).orElseThrow();
        assertEquals(1, patch.cells());
        assertEquals(4, patch.unavailableColumns());
        assertFalse(patch.enclosed());
        assertTrue(BiomePatchSurvey.measure(0, 0, 64, (x, z) -> null).isEmpty());
    }
    @Test void radiusLimitsAreExplicitAndSamplingNeverEscapesBoundsOrRepeats() {
        var seen = new HashSet<String>();
        var patch = BiomePatchSurvey.measure(0, 0, 16, (x, z) -> {
            assertTrue(Math.abs(x) <= 16 && Math.abs(z) <= 16);
            assertTrue(seen.add(x + "," + z));
            return sample("test:forest", 64);
        }).orElseThrow();
        assertEquals(25, patch.cells());
        assertTrue(patch.rangeLimited());
        assertFalse(patch.budgetLimited());
        assertFalse(patch.enclosed());
    }
    @Test void hugeRegionsStopAtTheColumnBudget() {
        int[] calls = {0};
        var patch = BiomePatchSurvey.measure(0, 0, 512, (x, z) -> {
            calls[0]++;
            return sample("test:forest", 64);
        }).orElseThrow();
        assertEquals(BiomePatchSurvey.MAX_SAMPLES, calls[0]);
        assertEquals(calls[0], patch.sampledColumns());
        assertTrue(patch.budgetLimited());
        assertFalse(patch.enclosed());
    }
}
