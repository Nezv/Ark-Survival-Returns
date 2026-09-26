package dev.nez.arksurvivalreturns.feature.behavior;

/**
 * Daily routine of land wildlife. Carnivores hunt at night, sleep through the morning and roam in the
 * afternoon; herbivores sleep at night and graze, drink and roam by day. Each animal shifts the clock by
 * its own offset (up to the configured transition), so a herd falls asleep and wakes one by one.
 */
public final class DailySchedule {
    public enum Phase {
        /** Awake and roaming: herbivores all day, carnivores in the afternoon. */
        ROAM,
        /** Night activity of carnivores: hunting is allowed. */
        HUNT,
        SLEEP
    }

    public static Phase phase(long clock, long individual, boolean carnivore, int nightStart, int nightEnd,
            int transition, double carnivoreDaySleep) {
        long shifted = clock - Math.floorMod(individual, transition + 1L);
        if (NighttimeCycle.night(shifted, nightStart, nightEnd)) return carnivore ? Phase.HUNT : Phase.SLEEP;
        if (!carnivore) return Phase.ROAM;
        return dayProgress(shifted, nightStart, nightEnd) < Math.clamp(carnivoreDaySleep, 0, 1) ? Phase.SLEEP : Phase.ROAM;
    }

    /** Share of the daylight window elapsed since dawn (night end), in [0, 1). */
    public static double dayProgress(long clock, int nightStart, int nightEnd) {
        int day = Math.floorMod(nightStart - nightEnd, NighttimeCycle.DAY_TICKS);
        if (day == 0) return 0;
        int since = Math.floorMod((int) Math.floorMod(clock, NighttimeCycle.DAY_TICKS) - nightEnd, NighttimeCycle.DAY_TICKS);
        return Math.min(since, day - 1) / (double) day;
    }

    private DailySchedule() {}
}
