package dev.nez.arksurvivalreturns.feature.mass;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Threshold math is pure, so it is checked without a running game. */
final class MassRulesTest {
    private static final MassRules.Profile STANDARD = new MassRules.Profile(1.0, 0.75, 1.0, 1.25, 0.35);

    @Test void warningBandCarriesNoPenalty() {
        for (double ratio : new double[]{0.75, 0.8, 0.99, 1.0 - 1e-9}) {
            assertEquals(MassRules.Band.WARN, MassRules.band(ratio, STANDARD), "ratio " + ratio);
            assertTrue(MassRules.sprintAllowed(ratio, STANDARD), "warning must not deny sprint at " + ratio);
            assertEquals(1.0, MassRules.speedFactor(ratio, STANDARD), 1e-9, "warning must not slow movement");
        }
    }

    @Test void overloadStartsAtFullCapacity() {
        assertEquals(MassRules.Band.OVERLOAD, MassRules.band(1.0, STANDARD));
        assertFalse(MassRules.sprintAllowed(1.0, STANDARD), "sprint is denied at 100%, not at the warning band");
        assertEquals(1.0, MassRules.speedFactor(1.0, STANDARD), 1e-9, "slowdown starts at the overload line");
    }

    @Test void slowdownRampsToTheFloor() {
        assertEquals(0.675, MassRules.speedFactor(1.125, STANDARD), 1e-9);
        assertEquals(MassRules.Band.HEAVY, MassRules.band(1.25, STANDARD));
        assertEquals(0.35, MassRules.speedFactor(1.25, STANDARD), 1e-9);
        assertEquals(0.35, MassRules.speedFactor(3.0, STANDARD), 1e-9, "never below the floor");
    }

    @Test void relaxedProfileShiftsEveryPoint() {
        assertEquals(1.5, MassRules.RELAXED.capacityMultiplier(), 1e-9);
        assertEquals(MassRules.Band.NORMAL, MassRules.band(0.99, MassRules.RELAXED));
        assertEquals(MassRules.Band.WARN, MassRules.band(1.0, MassRules.RELAXED));
        assertTrue(MassRules.sprintAllowed(1.0, MassRules.RELAXED), "relaxed warning stays penalty-free");
        assertEquals(MassRules.Band.OVERLOAD, MassRules.band(1.25, MassRules.RELAXED));
        assertEquals(MassRules.Band.HEAVY, MassRules.band(1.5, MassRules.RELAXED));
        assertEquals(0.6, MassRules.speedFactor(1.5, MassRules.RELAXED), 1e-9);
    }
}
