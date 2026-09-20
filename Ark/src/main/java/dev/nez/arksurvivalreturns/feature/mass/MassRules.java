package dev.nez.arksurvivalreturns.feature.mass;

import dev.nez.arksurvivalreturns.Config;

/**
 * Effective mass thresholds. STANDARD reads the configured ratios; RELAXED raises capacities by half
 * and starts every penalty 25 points later; OFF disables mass entirely.
 *
 * <p>Capacity is a movement budget, never an inventory limit: nothing here refuses an item
 * movement, so players may overload deliberately to rearrange or drop cargo.
 */
public final class MassRules {
    public enum Preset { STANDARD, RELAXED, OFF }
    public enum Band { NORMAL, WARN, OVERLOAD, HEAVY }

    public record Profile(double capacityMultiplier, double warning, double slow, double heavy, double speedFloor) {}

    /** Relaxed duo: 1.5x capacities, warning at 100%, slowdown from 125%, floor at 60%. */
    public static final Profile RELAXED = new Profile(1.5, 1.0, 1.25, 1.5, 0.6);

    public static boolean enabled() {
        return Config.MASS_ENABLED.get() && Config.MASS_PRESET.get() != Preset.OFF;
    }

    public static Profile profile() {
        if (Config.MASS_PRESET.get() == Preset.RELAXED) return RELAXED;
        return new Profile(1.0, Config.MASS_WARNING_RATIO.get(), Config.MASS_SLOW_RATIO.get(),
                Config.MASS_HEAVY_RATIO.get(), Config.MASS_SPEED_FLOOR.get());
    }

    public static double playerCapacity() {
        return Math.max(1.0, Config.MASS_PLAYER_CAPACITY.get() * profile().capacityMultiplier());
    }

    /** Fast Load and work jobs stop before crossing this ratio; manual loading may exceed it. */
    public static double automationCeiling() {
        return Config.MASS_AUTOMATION_CEILING.get();
    }

    public static Band band(double ratio) {
        return band(ratio, profile());
    }

    /** Warning bands carry no movement penalty; only the overloaded bands do. */
    public static boolean sprintAllowed(double ratio) {
        return sprintAllowed(ratio, profile());
    }

    /** True from the overload line up: the band where sprint is denied and behavior changes. */
    public static boolean overloaded(double ratio) {
        return overloaded(ratio, profile());
    }

    public static boolean overloaded(double ratio, Profile profile) {
        return ratio >= profile.slow();
    }

    /** A rider this low on air forces an overloaded swimmer to surface instead of holding depth. */
    public static boolean forcedSurface(double air, double maxAir) {
        return maxAir > 0.0 && air <= maxAir * 0.3;
    }

    public static double speedFactor(double ratio) {
        return speedFactor(ratio, profile());
    }

    public static Band band(double ratio, Profile profile) {
        if (ratio >= profile.heavy()) return Band.HEAVY;
        if (ratio >= profile.slow()) return Band.OVERLOAD;
        if (ratio >= profile.warning()) return Band.WARN;
        return Band.NORMAL;
    }

    public static boolean sprintAllowed(double ratio, Profile profile) {
        return ratio < profile.slow();
    }

    /** One down to the floor between the slow and heavy points; never below the floor. */
    public static double speedFactor(double ratio, Profile profile) {
        if (ratio <= profile.slow()) return 1.0;
        if (ratio >= profile.heavy()) return profile.speedFloor();
        double progress = (ratio - profile.slow()) / (profile.heavy() - profile.slow());
        return 1.0 - progress * (1.0 - profile.speedFloor());
    }

    private MassRules() {}
}
