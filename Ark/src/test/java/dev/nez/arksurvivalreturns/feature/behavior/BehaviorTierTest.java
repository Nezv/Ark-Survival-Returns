package dev.nez.arksurvivalreturns.feature.behavior;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BehaviorTierTest {
    private static final BehaviorTier.Radii RADII = new BehaviorTier.Radii(64, 128, 256, 8);

    private static BehaviorTier at(double distance, BehaviorTier previous) {
        return BehaviorTier.classify(distance * distance, previous, RADII);
    }

    @Test void distancesMapToTheThreeTiers() {
        assertEquals(BehaviorTier.FULL, at(10, BehaviorTier.DORMANT));
        assertEquals(BehaviorTier.FULL, at(64, BehaviorTier.DORMANT));
        assertEquals(BehaviorTier.AMBIENT, at(65, BehaviorTier.DORMANT));
        assertEquals(BehaviorTier.AMBIENT, at(128, BehaviorTier.DORMANT));
        assertEquals(BehaviorTier.DORMANT, at(129, BehaviorTier.DORMANT));
        assertEquals(BehaviorTier.DORMANT, at(10_000, BehaviorTier.FULL));
    }

    @Test void demotionWaitsForTheMarginSoBordersDoNotFlicker() {
        assertEquals(BehaviorTier.FULL, at(70, BehaviorTier.FULL));
        assertEquals(BehaviorTier.AMBIENT, at(73, BehaviorTier.FULL));
        assertEquals(BehaviorTier.AMBIENT, at(135, BehaviorTier.AMBIENT));
        assertEquals(BehaviorTier.DORMANT, at(137, BehaviorTier.AMBIENT));
        assertEquals(BehaviorTier.AMBIENT, at(70, BehaviorTier.AMBIENT), "promotion happens at the radius itself");
    }

    @Test void radiiAreSortedAndPoseRefreshStopsBeyondTheOuterRadius() {
        var inverted = new BehaviorTier.Radii(200, 100, 50, 99);
        assertTrue(inverted.full() <= inverted.ambient() && inverted.ambient() <= inverted.dormant());
        assertEquals(32, inverted.margin());
        assertTrue(BehaviorTier.posed(256 * 256, RADII));
        assertFalse(BehaviorTier.posed(257 * 257, RADII));
    }
}
