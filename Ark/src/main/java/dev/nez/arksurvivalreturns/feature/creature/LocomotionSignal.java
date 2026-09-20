package dev.nez.arksurvivalreturns.feature.creature;

/**
 * Hysteresis over measured travel, replacing GeckoLib's render-state movement flag.
 *
 * <p>{@code AnimationTest.isMoving()} reports a smoothed walk-animation speed, so a creature pressed
 * against a collision, jostled by packmates or moving in short navigation hops keeps reporting motion
 * and never settles into its standing clip. This signal starts on real walking speed and only ends
 * after a sustained stop, ignoring sub-walking drift.
 */
public final class LocomotionSignal {
    public static final double START_BLOCKS_PER_SECOND = 1.0;
    public static final double STOP_BLOCKS_PER_SECOND = 0.25;
    public static final int STOP_TICKS = 3;
    private boolean moving;
    private int stillTicks;

    /** Feeds the travel of the last tick in blocks per second. */
    public void update(double blocksPerSecond) {
        if (blocksPerSecond >= START_BLOCKS_PER_SECOND) {
            moving = true;
            stillTicks = 0;
        } else if (!moving) {
            return;
        } else if (blocksPerSecond <= STOP_BLOCKS_PER_SECOND) {
            if (++stillTicks >= STOP_TICKS) moving = false;
        } else {
            stillTicks = 0;
        }
    }

    public boolean moving() { return moving; }
}
