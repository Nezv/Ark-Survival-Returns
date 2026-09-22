package dev.nez.arksurvivalreturns.feature.guardian;

/**
 * Pure encounter rules: health scaling, tame damage, retreat grace and reset cooldown.
 *
 * <p>No Minecraft types, so the balance math is exercised directly by the game tests and can never
 * be skewed by world state.
 */
public final class GuardianPolicy {
    /** One base value, an extra share for each participant after the first and a smaller tame share. */
    public static double bossHealth(double base, int participants, int tames, double perExtraPlayer,
            double perTame, int tameContributionCap) {
        int extraPlayers = Math.max(0, participants - 1);
        int countedTames = Math.clamp(tames, 0, tameContributionCap);
        return base + extraPlayers * perExtraPlayer + countedTames * perTame;
    }

    /** Unregistered tames deal a configurable fraction of their damage; registered ones deal full damage. */
    public static double tameDamage(boolean registered, double amount, double factor) {
        if (registered) return amount;
        return amount * Math.clamp(factor, 0.0, 1.0);
    }

    /** True once the arena has been empty for the whole grace window. */
    public static boolean resetDue(long now, long emptySince, int graceTicks) {
        return emptySince > 0 && now - emptySince >= graceTicks;
    }

    /** True once the retreat cooldown elapsed and the free retry window opens. */
    public static boolean retryReady(long now, long resetAt) {
        return now >= resetAt;
    }

    public static boolean insideArena(double distanceSq, double radius) {
        return distanceSq <= radius * radius;
    }

    private GuardianPolicy() {}
}
