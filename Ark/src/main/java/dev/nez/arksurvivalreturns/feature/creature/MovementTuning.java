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
    private MovementTuning() {}
}
