package dev.nez.arksurvivalreturns.feature.behavior;

/**
 * Stable per-individual variation, so a herd reacts as a group of animals and not as synchronized copies.
 * Every value derives from the creature's UUID bits and an event salt: the same animal always has the
 * same temperament, while two herd mates differ.
 */
public final class Desync {
    /** Deterministic value in [0, 1) for one individual and salt (SplitMix64 finalizer). */
    public static double unit(long seed, long salt) {
        long z = seed + 0x9E3779B97F4A7C15L * (salt + 1);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return (z >>> 11) * 0x1.0p-53;
    }

    /**
     * Ticks before an alarm reaches this animal: the call ripples outward from its source, and each
     * individual needs its own moment to lift its head. Timid animals react faster.
     */
    public static int reactionDelay(long seed, int event, double distance, boolean timid) {
        double ripple = Math.clamp(distance, 0, 64) * 0.55;
        double own = 4 + unit(seed, 11 + event) * 14;
        return (int) Math.round((ripple + own) * (timid ? 0.7 : 1.0));
    }

    /** Travel speed multiplier of this individual, 0.9 to 1.1. */
    public static double speedFactor(long seed) { return 0.9 + unit(seed, 3) * 0.2; }

    /** Degrees an escape bends away from the straight line, different each alarm, within +-35. */
    public static double headingJitter(long seed, int event) { return (unit(seed, 101 + event) * 2 - 1) * 35; }

    /** Animation rate multiplier, 0.94 to 1.06, so gait cycles drift out of phase. */
    public static float animationRate(long seed) { return (float) (0.94 + unit(seed, 7) * 0.12); }

    /** Angle in radians of this member's place around the group anchor. */
    public static double formationAngle(long seed) { return unit(seed, 19) * Math.PI * 2; }

    /** Distance of this member's place from the group anchor, within the cohesion radius. */
    public static double formationRadius(long seed, double cohesion, double bodyWidth) {
        double inner = Math.min(cohesion * 0.8, 2 + bodyWidth);
        return inner + unit(seed, 23) * Math.max(0, cohesion * 0.8 - inner);
    }

    private Desync() {}
}
