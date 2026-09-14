package dev.nez.arksurvivalreturns.feature.creature;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LevelScalingTest {
    @Test void levelOneHasBaseStatsAndInvalidLevelsAreBounded() {
        assertEquals(32, LevelScaling.health(32, 1, 0.1));
        assertEquals(5, LevelScaling.damage(5, -9, 0.14));
        assertEquals(LevelScaling.health(32, 100, 0.1), LevelScaling.health(32, 9999, 0.1));
    }
    @Test void growthIsMonotonicSublinearAndHealthOutpacesDamage() {
        for (int level = 2; level <= 100; level++) {
            assertTrue(LevelScaling.health(32, level, 0.1) > LevelScaling.health(32, level-1, 0.1));
            assertTrue(LevelScaling.damage(5, level, 0.14) > LevelScaling.damage(5, level-1, 0.14));
        }
        assertTrue(LevelScaling.health(32, 80, 0.1)/32 > LevelScaling.damage(5, 80, 0.14)/5);
        assertTrue(LevelScaling.health(32, 80, 0.1)-LevelScaling.health(32, 79, 0.1)
                < LevelScaling.health(32, 2, 0.1)-32);
    }
    @Test void extremeConfigurationRespectsMinecraftAttributeCap() {
        assertEquals(1024, LevelScaling.health(190, 100, 0.2));
        assertEquals(190, LevelScaling.health(190, 100, 0));
        assertEquals(20, LevelScaling.damage(20, 100, 0));
    }
}
