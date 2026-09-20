package dev.nez.arksurvivalreturns.feature.creature;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LocomotionSignalTest {
    @Test void stationaryCreatureStaysStopped() {
        var signal = new LocomotionSignal();
        for (int i = 0; i < 100; i++) signal.update(0);
        assertFalse(signal.moving());
    }
    @Test void jostleBelowTheStartThresholdNeverCountsAsTravel() {
        var signal = new LocomotionSignal();
        for (int i = 0; i < 100; i++) signal.update(0.4);
        assertFalse(signal.moving());
    }
    @Test void realTravelStartsImmediately() {
        var signal = new LocomotionSignal();
        signal.update(2.0);
        assertTrue(signal.moving());
    }
    @Test void slowDriftAfterTravelKeepsTheLocomotionClip() {
        var signal = new LocomotionSignal();
        signal.update(2.0);
        for (int i = 0; i < 20; i++) signal.update(0.5);
        assertTrue(signal.moving());
    }
    @Test void sustainedStopEndsLocomotionAfterThreeQuietTicks() {
        var signal = new LocomotionSignal();
        signal.update(2.0);
        signal.update(0.1);
        signal.update(0.1);
        assertTrue(signal.moving());
        signal.update(0.1);
        assertFalse(signal.moving());
    }
    @Test void aSingleQuietTickDoesNotEndLocomotion() {
        var signal = new LocomotionSignal();
        signal.update(2.0);
        signal.update(0.1);
        signal.update(2.0);
        signal.update(0.1);
        assertTrue(signal.moving());
    }
    @Test void aQuietTickRestartsTheStopCountdown() {
        var signal = new LocomotionSignal();
        signal.update(2.0);
        signal.update(0.1);
        signal.update(0.1);
        signal.update(0.1);
        assertFalse(signal.moving());
        signal.update(1.0);
        assertTrue(signal.moving());
    }
}
