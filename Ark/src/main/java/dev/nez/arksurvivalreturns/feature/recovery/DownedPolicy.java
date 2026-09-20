package dev.nez.arksurvivalreturns.feature.recovery;

/** Pure downed rules; no Minecraft types so the unit tests can run without a game bootstrap. */
public final class DownedPolicy {
    /** Whether damage should kill outright instead of leaving the player downed. */
    public static boolean lethal(float damage, float maxHealth, double overkillMultiplier, boolean voidDamage, boolean lavaDamage) {
        if (voidDamage || lavaDamage) return true;
        return damage >= maxHealth * Math.max(1.0, overkillMultiplier);
    }

    /** Remaining rescue ticks after taking a hit; never negative. */
    public static int bleedOut(int ticksLeft, float damage, double factor) {
        return Math.max(0, ticksLeft - (int) Math.ceil(damage * Math.max(0.0, factor)));
    }

    private DownedPolicy() {}
}
