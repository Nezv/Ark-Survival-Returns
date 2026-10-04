package dev.nez.arksurvivalreturns.feature.creature;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InertiaTest {
    private static final double STAG = Inertia.bulk(1.0, 2.2), PARASAUR = Inertia.bulk(2.6, 4.0), REX = Inertia.bulk(8.4, 13.5),
            TITANOSAUR = Inertia.bulk(30, 42);

    /** Ticks a body takes to turn through an angle from rest, checking the turn on the way. */
    private static int turn(double bulk, boolean predator, float angle, float hurry) {
        float rate = Inertia.turnRate(bulk, predator) * hurry, speed = 0, error = angle, last = 0;
        int ramp = Inertia.turnRamp(bulk), ticks = 0;
        while (Math.abs(error) >= 0.75f || Math.abs(speed) > rate / ramp) {
            speed = Inertia.turn(speed, error, rate, ramp);
            if (Math.abs(speed) > Math.abs(error) && Math.signum(speed) == Math.signum(error)) speed = error;
            assertTrue(Math.abs(speed) <= rate + 1e-4, "faster than the body turns");
            // The last tick lands on the heading, up to a quarter more than one step down.
            assertTrue(Math.abs(speed - last) <= rate / ramp * 1.25f + 1e-4, "the turn speed jumped");
            last = speed;
            error -= speed;
            assertTrue(error * Math.signum(angle) >= -0.75f, "the turn swung past its heading: " + error);
            assertTrue(++ticks < 2000, "the turn never ended");
        }
        return ticks;
    }

    @Test void heavyBodiesTurnStartAndStopSlowerThanLightOnes() {
        assertTrue(Inertia.turnRate(STAG, false) > Inertia.turnRate(PARASAUR, false));
        assertTrue(Inertia.turnRate(PARASAUR, false) > Inertia.turnRate(REX, true));
        assertTrue(Inertia.turnRate(REX, true) > Inertia.turnRate(TITANOSAUR, false));
        assertTrue(Inertia.turnRate(REX, true) > Inertia.turnRate(REX, false), "a hunter is more agile than a grazer of its bulk");
        double stag = Inertia.turnRate(STAG, false) * 20, rex = Inertia.turnRate(REX, true) * 20, giant = Inertia.turnRate(TITANOSAUR, false) * 20;
        assertTrue(stag > 110 && stag <= 170, "stag degrees a second: " + stag);
        assertTrue(rex > 25 && rex < 50, "Rex degrees a second: " + rex);
        assertEquals(12, giant, 0.01, "the slowest turn of all");
        assertTrue(Inertia.accelTicks(STAG) < Inertia.accelTicks(PARASAUR) && Inertia.accelTicks(PARASAUR) < Inertia.accelTicks(REX));
        assertTrue(Inertia.accelTicks(STAG) >= 8 && Inertia.accelTicks(TITANOSAUR) == 100);
        for (double bulk : new double[]{STAG, PARASAUR, REX, TITANOSAUR}) {
            assertTrue(Inertia.brakeTicks(bulk) < Inertia.accelTicks(bulk) && Inertia.brakeTicks(bulk) >= 4, "stopping is quicker than starting, never instant");
            assertTrue(Inertia.pivotAngle(bulk) >= 55 && Inertia.pivotAngle(bulk) <= 90);
        }
        assertTrue(Inertia.pivotAngle(REX) < Inertia.pivotAngle(STAG), "a giant lines up before it steps off");
    }

    @Test void aTurnGathersSpeedHoldsItsRateAndLandsOnTheHeading() {
        // Nobody turns about in a tick: a stag needs over a second, a Rex several, and a chase only shortens it.
        int stag = turn(STAG, false, 180, 1), rex = turn(REX, true, 180, 1), hurried = turn(REX, true, 180, Inertia.HURRY);
        assertTrue(stag >= 24 && stag <= 45, "stag about-face ticks: " + stag);
        assertTrue(rex >= 90 && rex <= 140, "Rex about-face ticks: " + rex);
        assertTrue(hurried < rex && hurried >= 55, "Rex about-face in a chase: " + hurried);
        assertTrue(turn(TITANOSAUR, false, 180, 1) >= 300, "a Titanosaur takes a quarter of a minute");
        // Either way round, and a small correction is quick.
        assertEquals(turn(PARASAUR, false, 90, 1), turn(PARASAUR, false, -90, 1));
        assertTrue(turn(PARASAUR, false, 10, 1) < turn(PARASAUR, false, 90, 1));
        assertTrue(turn(STAG, false, 2, 1) <= 6);
    }

    @Test void paceBuildsUpAndRunsOut() {
        float full = 0.3f, pace = 0;
        float up = full / Inertia.accelTicks(REX), down = full / Inertia.brakeTicks(REX);
        int ticks = 0;
        while (pace < full) { pace = Inertia.pace(pace, full, up, down); ticks++; assertTrue(pace <= full); }
        assertEquals(Inertia.accelTicks(REX), ticks, 1);
        ticks = 0;
        while (pace > 0) { pace = Inertia.pace(pace, 0, up, down); ticks++; assertTrue(pace >= 0); }
        assertEquals(Inertia.brakeTicks(REX), ticks, 1);
        assertEquals(0.1f, Inertia.pace(0.1f, 0.1f, up, down), "at its pace it stays there");
    }
}
