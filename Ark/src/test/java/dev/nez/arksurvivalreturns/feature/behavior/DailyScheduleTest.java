package dev.nez.arksurvivalreturns.feature.behavior;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.nez.arksurvivalreturns.feature.behavior.DailySchedule.Phase.*;

class DailyScheduleTest {
    private static DailySchedule.Phase carnivore(long clock) { return DailySchedule.phase(clock, 0, true, 13000, 23000, 0, 0.5); }
    private static DailySchedule.Phase herbivore(long clock) { return DailySchedule.phase(clock, 0, false, 13000, 23000, 0, 0.5); }

    @Test void spawnSleepWindowCoversAllIndividualOffsetsIncludingShortAndWrappingWindows() {
        for (int[] window : new int[][]{{13000, 23000}, {3000, 18000}, {1000, 1000}})
            for (double share : new double[]{0, 0.0001, 0.5, 1})
                for (int clock = -500; clock < 24500; clock += 37) {
                    boolean any = false;
                    for (int offset = 0; offset <= 600; offset++)
                        any |= DailySchedule.phase(clock, offset, true, window[0], window[1], 600, share) == SLEEP;
                    assertEquals(any, DailySchedule.carnivoreSleepWindow(clock, window[0], window[1], 600, share));
                }
    }
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

    @Test void theWakingDayHasFeedingWateringAndRestingHours() {
        java.util.function.BiFunction<Boolean, Integer, DailySchedule.Activity> at =
                (carnivore, clock) -> DailySchedule.activity(clock, 0, carnivore, 13000, 23000, 0, 0.5);
        // A grazer: water at dawn, graze, lie up at midday, graze, water at dusk, sleep.
        assertEquals(DailySchedule.Activity.DRINK, at.apply(false, 23200));
        assertEquals(DailySchedule.Activity.FEED, at.apply(false, 2000));
        assertEquals(DailySchedule.Activity.REST, at.apply(false, 6000));
        assertEquals(DailySchedule.Activity.FEED, at.apply(false, 9000));
        assertEquals(DailySchedule.Activity.DRINK, at.apply(false, 12500));
        assertEquals(DailySchedule.Activity.SLEEP, at.apply(false, 18000));
        // A hunter: asleep all morning, lying up, a round of its range, water before the night's hunt.
        assertEquals(DailySchedule.Activity.SLEEP, at.apply(true, 2000));
        assertEquals(DailySchedule.Activity.REST, at.apply(true, 7000));
        assertEquals(DailySchedule.Activity.ROAM, at.apply(true, 10000));
        assertEquals(DailySchedule.Activity.DRINK, at.apply(true, 12500));
        assertEquals(DailySchedule.Activity.HUNT, at.apply(true, 18000));
        // The activity never disagrees with the phase about sleep and the hunt, whatever the individual's offset.
        int feeding = 0;
        for (int clock = 0; clock < 24000; clock += 97) for (long own : new long[]{0, 311, -77}) for (boolean carnivore : new boolean[]{false, true}) {
            var phase = DailySchedule.phase(clock, own, carnivore, 13000, 23000, 600, 0.5);
            var activity = DailySchedule.activity(clock, own, carnivore, 13000, 23000, 600, 0.5);
            assertEquals(phase == SLEEP, activity == DailySchedule.Activity.SLEEP);
            assertEquals(phase == HUNT, activity == DailySchedule.Activity.HUNT);
            if (!carnivore && own == 0 && activity == DailySchedule.Activity.FEED) feeding++;
        }
        assertTrue(feeding * 97 > 0.35 * 24000, "a grazer feeds well over a third of the whole day");
    }
}
