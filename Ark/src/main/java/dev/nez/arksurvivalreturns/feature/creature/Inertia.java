package dev.nez.arksurvivalreturns.feature.creature;

/**
 * What a body's size does to its movement. A heavy animal cannot swing round, start or stop the way a
 * small one can: its turn gathers speed, holds a rate set by its bulk and eases onto the heading, and its
 * pace builds up and runs out over a time that grows with it. No world access; ticks are game ticks.
 */
public final class Inertia {
    /** One length for the bulk of a body: the side of a square with the area of its silhouette, in blocks. */
    public static double bulk(double width, double height) {
        return Math.sqrt(Math.max(0.25, width) * Math.max(0.25, height));
    }

    /**
     * Degrees per tick the body turns at most while calm: about 140 degrees a second for a stag, 75 for a
     * Parasaur, 50 for a Triceratops, 30 for a Brontosaurus and 12 for a Titanosaur. Hunters are a quarter
     * more agile than grazers of their bulk.
     */
    public static float turnRate(double bulk, boolean predator) {
        double perSecond = Math.clamp(190 / Math.pow(bulk, 0.8), 12, 170) * (predator ? 1.25 : 1);
        return (float) (Math.min(190, perSecond) / 20);
    }

    /** In a chase or a flight the body throws itself round faster. */
    public static final float HURRY = 1.6f;

    /** Ticks a turn takes to gather its full rate, and again to ease off: a quarter second to a second. */
    public static int turnRamp(double bulk) {
        return (int) Math.round(Math.clamp(2 + 2.2 * Math.pow(bulk, 0.75), 3, 24));
    }

    /** Ticks from a standstill to full speed: 0.6 s for a stag, 1 s for a Parasaur, 2.6 s for a Rex, 5 s at most. */
    public static int accelTicks(double bulk) {
        return (int) Math.round(Math.clamp(6 + 4.4 * bulk, 8, 100));
    }

    /** Ticks from full speed to a standstill: stopping is quicker than starting, never instant. */
    public static int brakeTicks(double bulk) {
        return Math.max(4, (int) Math.round(accelTicks(bulk) * 0.6));
    }

    /**
     * How far off its heading a body may be and still walk: beyond this it stops and turns on the spot first.
     * A small animal swings round on the move; a giant lines up before it steps off.
     */
    public static float pivotAngle(double bulk) {
        return (float) Math.clamp(95 - 4 * bulk, 55, 90);
    }

    /**
     * One tick of an inertial turn.
     *
     * @param speed  current signed turn speed in degrees per tick
     * @param error  signed degrees still to turn
     * @param rate   the most the body turns in a tick
     * @param ramp   ticks from rest to that rate
     * @return the new turn speed: it climbs by one ramp step, holds the rate, and near the heading falls to the
     *         speed from which the body can still stop on it
     */
    public static float turn(float speed, float error, float rate, int ramp) {
        float step = rate / Math.max(1, ramp);
        // Slowing by one step a tick from speed v, the body still turns v * (v / step + 1) / 2 degrees: the
        // fastest it may go is the speed that stops exactly on the heading.
        float stopping = (float) ((-step + Math.sqrt(step * step + 8.0 * step * Math.abs(error))) / 2);
        float wanted = Math.signum(error) * Math.min(rate, stopping);
        return speed + Math.clamp(wanted - speed, -step, step);
    }

    /** One tick of pace toward what is asked: up by the acceleration step, down by the braking step. */
    public static float pace(float pace, float wanted, float accelStep, float brakeStep) {
        return wanted > pace ? Math.min(wanted, pace + accelStep) : Math.max(wanted, pace - brakeStep);
    }

    private Inertia() {}
}
