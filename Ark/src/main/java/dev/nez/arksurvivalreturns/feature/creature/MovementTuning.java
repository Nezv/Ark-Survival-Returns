package dev.nez.arksurvivalreturns.feature.creature;

/** Vanilla ground kinematics: mob forward input is its speed attribute too (quadratic, not linear). */
public final class MovementTuning {
    // 20 ticks/sec, 0.98 input damping, ordinary-ground horizontal drag 0.6 * 0.91.
    public static final double LAND_COEFFICIENT = 20 * 0.98 / (1 - 0.6 * 0.91);
    public static double attribute(double sprintRatio, double playerBlocksPerSecond) {
        return Math.sqrt(sprintRatio * playerBlocksPerSecond / LAND_COEFFICIENT);
    }
    public static double blocksPerSecond(double controlledSpeed) { return LAND_COEFFICIENT * controlledSpeed * controlledSpeed; }
    public static double animationRate(double actualBlocksPerSecond, double height, double cycleSeconds, boolean running, double strideScale) {
        if (actualBlocksPerSecond < 0.03) return 0;
        double stride = height * (running ? 0.95 : 0.55) * strideScale;
        return Math.clamp(actualBlocksPerSecond * cycleSeconds / stride, 0.05, 4.0);
    }
    /**
     * Playback rate for a clip whose planted feet slide at {@code naturalBlocksPerSecond} (measured from the
     * rig by tools/build_behavior_clips.py): at this rate the feet stay put on the ground. Giants that move
     * slower than their stride implies step slower instead of skating; the clamp only guards extremes.
     */
    public static double matchedRate(double actualBlocksPerSecond, double naturalBlocksPerSecond, double strideScale) {
        if (actualBlocksPerSecond < 0.03 || !(naturalBlocksPerSecond > 0)) return actualBlocksPerSecond < 0.03 ? 0 : 1;
        return Math.clamp(actualBlocksPerSecond / (naturalBlocksPerSecond * strideScale), 0.3, 2.2);
    }
    /**
     * Navigation speed modifier for a ground speed in blocks per second, given the creature's movement
     * attribute (the inverse of {@link #blocksPerSecond}).
     */
    public static double modifierFor(double blocksPerSecond, double attribute) {
        if (!(attribute > 0) || !(blocksPerSecond > 0)) return 0;
        return Math.sqrt(blocksPerSecond / LAND_COEFFICIENT) / attribute;
    }
    /**
     * Wandering speed: close to the pace the walk clip was authored for, kept between a fifth and two fifths
     * of full pursuit so roaming never outruns the hunt, and varied per individual.
     */
    public static double wanderBlocksPerSecond(double naturalWalk, double sprintBlocksPerSecond, double individual) {
        double low = 0.2 * sprintBlocksPerSecond, high = 0.4 * sprintBlocksPerSecond;
        double preferred = Double.isNaN(naturalWalk) ? 0.3 * sprintBlocksPerSecond : naturalWalk * 0.85;
        return Math.clamp(preferred, low, high) * individual;
    }
    private MovementTuning() {}
}
