package dev.nez.arksurvivalreturns.feature.creature;

/** Independent curves: sublinear health growth and gentler square-root damage growth. */
public final class LevelScaling {
    public static int clamp(int level) { return Math.clamp(level, 1, 100); }
    public static double health(double base, int level, double growth) {
        return Math.min(1024.0, base * (1.0 + growth * Math.pow(clamp(level) - 1, 0.85)));
    }
    public static double damage(double base, int level, double growth) {
        return base * (1.0 + growth * Math.sqrt(clamp(level) - 1));
    }
    private LevelScaling() {}
}
