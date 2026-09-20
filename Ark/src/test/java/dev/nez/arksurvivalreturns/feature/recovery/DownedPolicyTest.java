package dev.nez.arksurvivalreturns.feature.recovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DownedPolicyTest {
    @Test void hazardsAlwaysKill() {
        assertTrue(DownedPolicy.lethal(1f, 20f, 1.5, true, false), "Void damage kills outright");
        assertTrue(DownedPolicy.lethal(1f, 20f, 1.5, false, true), "Lava damage kills outright");
    }

    @Test void overkillKillsAtTheThreshold() {
        assertFalse(DownedPolicy.lethal(20f, 20f, 1.5, false, false), "A full-health hit still leaves a rescue window");
        assertTrue(DownedPolicy.lethal(30f, 20f, 1.5, false, false), "The overkill threshold kills");
        assertFalse(DownedPolicy.lethal(29f, 20f, 1.5, false, false), "Below the threshold only downs");
    }

    @Test void bleedOutShortensAndFloors() {
        assertEquals(90, DownedPolicy.bleedOut(100, 10f, 1.0));
        assertEquals(100, DownedPolicy.bleedOut(100, 10f, 0.0), "Zero factor disables bleeding");
        assertEquals(0, DownedPolicy.bleedOut(5, 100f, 1.0), "The window cannot go negative");
        assertEquals(60, DownedPolicy.bleedOut(100, 10f, 4.0), "Fractional damage rounds up");
    }
}
