package dev.nez.arksurvivalreturns.feature.creature;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MovementTuningTest {
    @Test void pursuitTargetsAreExpressedInPlayerSprintUnits() {
        for (double ratio : new double[]{1.05, 1.25, 1.8, 2.0, 2.2})
            assertEquals(5.612 * ratio, MovementTuning.blocksPerSecond(MovementTuning.attribute(ratio, 5.612)), 0.000001);
    }
    @Test void BiggerBodiesHaveSlowerCadenceAtTheSameTravelSpeed() {
        double small = MovementTuning.animationRate(6, 10, 1, true, 1);
        double large = MovementTuning.animationRate(6, 20, 1, true, 1);
        assertEquals(small / 2, large, 0.000001);
        assertEquals(0, MovementTuning.animationRate(0, 20, 1, true, 1));
        assertEquals(2 * small, MovementTuning.animationRate(12, 10, 1, true, 1), 0.000001);
    }
    @Test void PlantedFeetSetThePlaybackRate() {
        // Moving at the clip's own foot speed plays it at authored speed; half the speed, half the rate.
        assertEquals(1, MovementTuning.matchedRate(2.4, 2.4, 1), 1e-9);
        assertEquals(0.5, MovementTuning.matchedRate(1.2, 2.4, 1), 1e-9);
        assertEquals(0.3, MovementTuning.matchedRate(0.1, 2.4, 1), 1e-9, "Extremes stay clamped");
        assertEquals(2.2, MovementTuning.matchedRate(20, 2.4, 1), 1e-9);
        assertEquals(0, MovementTuning.matchedRate(0, 2.4, 1), "A stationary body does not cycle its legs");
        assertEquals(1, MovementTuning.matchedRate(3, Double.NaN, 1), "Unmeasured clips play at authored speed");
        // A scaled-up body strides farther per cycle, so it cycles slower at the same speed.
        assertEquals(0.5, MovementTuning.matchedRate(2.4, 2.4, 2), 1e-9);
    }
    @Test void SpeedModifierInvertsTheAttributeCurve() {
        double attribute = MovementTuning.attribute(1.8, 5.612);
        for (double speed : new double[]{0.5, 2, 5.612, 10.1})
            assertEquals(speed, MovementTuning.blocksPerSecond(attribute * MovementTuning.modifierFor(speed, attribute)), 1e-9);
        assertEquals(0, MovementTuning.modifierFor(3, 0));
    }
    @Test void WanderingStaysBetweenAFifthAndTwoFifthsOfTheSprint() {
        assertEquals(0.85 * 3, MovementTuning.wanderBlocksPerSecond(3, 10, 1), 1e-9);
        assertEquals(2, MovementTuning.wanderBlocksPerSecond(0.5, 10, 1), 1e-9, "A shuffling clip is sped up to a fifth");
        assertEquals(4, MovementTuning.wanderBlocksPerSecond(9, 10, 1), 1e-9, "A fast walk clip is capped at two fifths");
        assertEquals(3, MovementTuning.wanderBlocksPerSecond(Double.NaN, 10, 1), 1e-9);
        assertEquals(3.3, MovementTuning.wanderBlocksPerSecond(Double.NaN, 10, 1.1), 1e-9);
    }
}
