package dev.nez.arksurvivalreturns.feature.behavior;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.nez.arksurvivalreturns.feature.behavior.DailySchedule.Phase.*;

class DailyScheduleTest {
    private static DailySchedule.Phase carnivore(long clock) { return DailySchedule.phase(clock, 0, true, 13000, 23000, 0, 0.5); }
    private static DailySchedule.Phase herbivore(long clock) { return DailySchedule.phase(clock, 0, false, 13000, 23000, 0, 0.5); }

    @Test void carnivoresHuntAtNightSleepInTheMorningAndRoamInTheAfternoon() {
        assertEquals(HUNT, carnivore(18000));
        assertEquals(HUNT, carnivore(22999));
        assertEquals(SLEEP, carnivore(23000), "dawn starts the morning sleep");
        assertEquals(SLEEP, carnivore(3000));
        assertEquals(SLEEP, carnivore(5999));
        assertEquals(ROAM, carnivore(6000), "noon ends the sleep with the default half-day share");
        assertEquals(ROAM, carnivore(12000));
        assertEquals(HUNT, carnivore(13000));
    }

    @Test void herbivoresSleepAtNightAndRoamAllDay() {
        assertEquals(SLEEP, herbivore(13000));
        assertEquals(SLEEP, herbivore(20000));
        assertEquals(ROAM, herbivore(23000));
        assertEquals(ROAM, herbivore(6000));
        assertEquals(ROAM, herbivore(12999));
    }

    @Test void sleepShareStretchesTheMorningAndIndividualsShiftTheirClocks() {
        assertEquals(SLEEP, DailySchedule.phase(6000, 0, true, 13000, 23000, 0, 1.0), "full share sleeps all day");
        assertEquals(ROAM, DailySchedule.phase(23500, 0, true, 13000, 23000, 0, 0.0), "zero share never sleeps by day");
        int awake = 0;
        for (long individual = 0; individual < 601; individual++)
            if (DailySchedule.phase(13300, individual, false, 13000, 23000, 600, 0.5) == ROAM) awake++;
        assertTrue(awake > 250 && awake < 350, "about half the herd is still awake 300 ticks into dusk: " + awake);
        assertEquals(0.5, DailySchedule.dayProgress(6000, 13000, 23000), 1e-9);
        assertEquals(0, DailySchedule.dayProgress(23000, 13000, 23000), 1e-9);
    }
}
