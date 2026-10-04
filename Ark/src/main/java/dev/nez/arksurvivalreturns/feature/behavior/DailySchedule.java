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

    /**
     * What an awake animal is busy with at this hour. Hunting games give each species feeding, drinking and
     * resting places with their hours; here the hours are the same idea and the places are found on the spot:
     * grazing ground, the nearest bank, the home range.
     */
    public enum Activity {
        SLEEP,
        /** Carnivores at night. */
        HUNT,
        /** Grazers on their feeding ground: most of the morning and the afternoon. */
        FEED,
        /** Watering time: dawn and dusk for grazers, dusk for hunters. Thirst can still send an animal at any hour. */
        DRINK,
        /** Lying up: the midday rest of a herd, the lazy first half of a hunter's afternoon. */
        REST,
        /** Nothing scheduled: loafing, or a hunter's round of its range before dusk. */
        ROAM
    }

    /** Share of daylight, from dawn: grazers water until the first mark, rest between the second and third, water again after the fourth. */
    private static final double DAWN_WATER = 0.08, REST_FROM = 0.42, REST_TO = 0.58, DUSK_WATER = 0.90;
    /** Share of a hunter's waking afternoon: lying up until the first mark, water after the second. */
    private static final double HUNTER_REST = 0.45, HUNTER_WATER = 0.85;

    public static Activity activity(long clock, long individual, boolean carnivore, int nightStart, int nightEnd,
            int transition, double carnivoreDaySleep) {
        var phase = phase(clock, individual, carnivore, nightStart, nightEnd, transition, carnivoreDaySleep);
        if (phase == Phase.SLEEP) return Activity.SLEEP;
        if (phase == Phase.HUNT) return Activity.HUNT;
        double day = dayProgress(clock - Math.floorMod(individual, transition + 1L), nightStart, nightEnd);
        if (!carnivore)
            return day < DAWN_WATER || day >= DUSK_WATER ? Activity.DRINK : day >= REST_FROM && day < REST_TO ? Activity.REST : Activity.FEED;
        double asleep = Math.clamp(carnivoreDaySleep, 0, 1);
        double afternoon = asleep >= 1 ? 1 : (day - asleep) / (1 - asleep);
        return afternoon < HUNTER_REST ? Activity.REST : afternoon < HUNTER_WATER ? Activity.ROAM : Activity.DRINK;
    }

    /** Spawn sites have no UUID yet: cover the sleep window of every possible individual offset. */
    public static boolean carnivoreSleepWindow(long clock, int nightStart, int nightEnd, int transition,
                                               double sleepShare) {
        double share = Math.clamp(sleepShare, 0, 1);
        if (share == 0) return false;
        int daylight = Math.floorMod(nightStart - nightEnd, NighttimeCycle.DAY_TICKS);
        // phase() treats equal endpoints as all day with dayProgress == 0.
        if (daylight == 0) return true;
        long sleepTicks = (long) Math.ceil(daylight * share);
        return Math.floorMod(clock - nightEnd, NighttimeCycle.DAY_TICKS)
                < Math.min(NighttimeCycle.DAY_TICKS, sleepTicks + Math.max(0, transition));
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
