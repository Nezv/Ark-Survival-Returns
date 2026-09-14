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
}
