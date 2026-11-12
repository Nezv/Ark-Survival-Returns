package dev.nez.arksurvivalreturns.feature.taming;

/**
 * The settled pace of the three vanilla movement updates a ridden creature goes through, and the per-tick push
 * that settles on a target pace (BUG-03). Each update adds the push along a rider's input of length one, moves,
 * then keeps a share of the speed: on plain ground (block friction 0.6) the push is the speed itself and 0.546 is
 * kept, in the air 0.91, in water 0.8. The pace a push settles on is push / (1 - keep) a tick. Pure arithmetic.
 */
public final class RideKinematics {
    public enum Medium {
        GROUND(0.6 * 0.91), AIR(0.91), WATER(0.8);
        public final double keep;
        Medium(double keep) { this.keep = keep; }
    }

    /** Blocks a second a push per tick settles on. */
    public static double settledBlocksPerSecond(double push, Medium medium) {
        return push / (1 - medium.keep) * 20;
    }

    /** The push per tick that settles on a pace in blocks a second. */
    public static double push(double blocksPerSecond, Medium medium) {
        return blocksPerSecond / 20 * (1 - medium.keep);
    }

    /** Simulates the update tick by tick from rest, as a check of the closed form. */
    public static double simulate(double push, Medium medium, int ticks) {
        double velocity = 0;
        for (int i = 0; i < ticks; i++) velocity = (velocity + push) * medium.keep;
        // The block moved during a tick is the speed before the keep is applied.
        return velocity / medium.keep * 20;
    }

    private RideKinematics() {}
}
