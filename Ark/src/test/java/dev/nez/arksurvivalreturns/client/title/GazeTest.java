package dev.nez.arksurvivalreturns.client.title;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The menu creature's look-around: bounded, smooth, and it really looks both ways. */
class GazeTest {
    private static final double RANGE = 38, FRAME = 1 / 60.0;

    @Test void staysInRangeAndNeverSnaps() {
        for (long seed = 1; seed <= 20; seed++) {
            Gaze gaze = new Gaze(seed);
            double[] last = gaze.at(100, RANGE);
            double minYaw = 0, maxYaw = 0;
            for (double t = 100 + FRAME; t < 400; t += FRAME) {
                double[] now = gaze.at(t, RANGE);
                assertTrue(Math.abs(now[0]) <= RANGE + 2, "yaw " + now[0]);
                assertTrue(now[1] >= -7 && now[1] <= 18, "pitch " + now[1]);
                // At most ~6 degrees a frame at 60 fps (a quick glance), never a jump.
                assertTrue(Math.abs(now[0] - last[0]) < 6 && Math.abs(now[1] - last[1]) < 6, "smooth at " + t);
                minYaw = Math.min(minYaw, now[0]);
                maxYaw = Math.max(maxYaw, now[0]);
                last = now;
            }
            assertTrue(minYaw < -RANGE * 0.4 && maxYaw > RANGE * 0.4, "looks to both sides (seed " + seed + ")");
        }
    }

    @Test void holdsBetweenTurns() {
        Gaze gaze = new Gaze(7);
        int still = 0, frames = 0;
        double[] last = gaze.at(0, RANGE);
        for (double t = FRAME; t < 120; t += FRAME) {
            double[] now = gaze.at(t, RANGE);
            if (Math.abs(now[0] - last[0]) < 0.1) still++;
            frames++;
            last = now;
        }
        assertTrue(still > frames / 3, "most of the time the head is held, not sweeping");
    }

    @Test void startleRaisesTheHead() {
        Gaze gaze = new Gaze(3);
        gaze.at(10, RANGE);
        gaze.startle(10, RANGE);
        double[] later = gaze.at(10.5, RANGE);
        assertTrue(later[1] > 8, "looks up toward the flash, pitch " + later[1]);
    }
}
