package dev.nez.arksurvivalreturns.feature.behavior;

/** Clock arithmetic only: needs advance with simulated ticks, never with clock jumps. */
public final class NighttimeCycle {
    public static final int DAY_TICKS = 24000;
    public static boolean night(long clock, int start, int end) {
        int time = (int)Math.floorMod(clock, DAY_TICKS);
        return start < end ? time >= start && time < end : start > end && (time >= start || time < end);
    }
    public static boolean individualNight(long clock, long individual, int start, int end, int transitionTicks) {
        return night(clock - Math.floorMod(individual, transitionTicks + 1L), start, end);
    }
    public static double sight(double daytimeRange, boolean dark, boolean landHunterNight, double nightMultiplier) {
        return daytimeRange * (landHunterNight ? nightMultiplier : dark ? 0.7 : 1.0);
    }
    private NighttimeCycle() {}
}
