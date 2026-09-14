package dev.nez.arksurvivalreturns.feature.spawn;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DangerBandsTest {
    @Test void spawnIsEasyAndTravelReturnsToEasyAfterHighDanger() {
        int originX = -417, originZ = 829, width = 256;
        for (int x = -10; x <= 10; x++) for (int z = -10; z <= 10; z++)
            assertEquals(1, DangerBands.level(originX + x, originZ + z, originX, originZ, width));
        for (int axis = 0; axis < 2; axis++) {
            boolean[] found = new boolean[6];
            boolean returned = false;
            for (int distance = 0; distance <= 4 * width; distance++) {
                int level = DangerBands.level(originX + (axis == 0 ? distance : 0), originZ + (axis == 1 ? distance : 0), originX, originZ, width);
                if (level == 1 && found[5]) returned = true;
                found[level] = true;
            }
            for (int i = 1; i <= 5; i++) assertTrue(found[i], "Missing danger " + i);
            assertTrue(returned, "Level 5 never returned to easy");
        }
    }
    @Test void eachDifficultyOccupiesOneFifthOfACompleteRepeatingTile() {
        for (int width : new int[]{96, 256}) {
            int period = 4 * width;
            int[] counts = new int[6];
            for (int x = 0; x < period; x++) for (int z = 0; z < period; z++)
                counts[DangerBands.level(x, z, 0, 0, width)]++;
            for (int i = 1; i <= 5; i++)
                assertEquals(0.2, (double) counts[i] / (period * period), 0.005, "Unequal area for level " + i);
        }
    }
    @Test void neighboringBlocksCannotSkipLevelsIncludingTileSeams() {
        int width = 96, period = 4 * width;
        int[][] directions = {{1,0}, {0,1}, {1,1}, {-1,1}};
        for (int x = -period; x <= period; x++) for (int z = -period; z <= period; z++) {
            int level = DangerBands.level(x, z, 0, 0, width);
            for (int[] d : directions)
                assertTrue(Math.abs(level - DangerBands.level(x + d[0], z + d[1], 0, 0, width)) <= 1);
        }
    }
    @Test void periodicityAndWorldBorderCoordinatesAreStable() {
        for (int x = -30_000_000; x <= 30_000_000; x += 731_117) {
            int z = -x / 3;
            int rank = DangerBands.level(x, z, -30_000_000, 30_000_000, 256);
            assertTrue(rank >= 1 && rank <= 5);
            assertEquals(rank, DangerBands.level(x + 1024, z - 1024, -30_000_000, 30_000_000, 256));
        }
        assertThrows(IllegalArgumentException.class, () -> DangerBands.level(0, 0, 0, 0, 0));
    }
}
