package dev.nez.arksurvivalreturns.feature.behavior;

import java.util.SplittableRandom;

/**
 * Smooth closed flight curves around a nest. A bird follows a point that slides along the curve, so its
 * path banks and climbs continuously instead of snapping between random waypoints. Each lap may pick a
 * new shape: a wide wobbling loop, a figure eight that crosses over the nest, or a tight thermal circle
 * that slowly gains height.
 */
public final class FlightPath {
    public enum Kind { LOOP, FIGURE_EIGHT, THERMAL }

    /**
     * @param radius    mean distance from the centre, blocks
     * @param wobble    radial wobble of a loop, blocks
     * @param lobes     wobbles per lap
     * @param altitude  mean height above the centre, blocks
     * @param swell     altitude swing over a lap, blocks
     * @param climb     height gained per lap (thermals), blocks
     * @param direction +1 counter-clockwise, -1 clockwise
     */
    public record Shape(Kind kind, double radius, double wobble, int lobes, double altitude, double swell,
            double climb, double phase, double heading, int direction) {}

    /** A new lap shape for a bird roaming {@code roam} blocks around its centre. */
    public static Shape pick(SplittableRandom random, double roam, double baseAltitude, boolean soarer) {
        double reach = Math.max(10, roam);
        // The low point of a lap stays at least five blocks above the centre whenever the base allows it.
        double swellCap = Math.max(1, baseAltitude - 5);
        double roll = random.nextDouble();
        int direction = random.nextBoolean() ? 1 : -1;
        double phase = random.nextDouble() * Math.PI * 2, heading = random.nextDouble() * Math.PI * 2;
        if (soarer && roll < 0.3) {
            double radius = Math.min(reach * 0.35, 8 + random.nextDouble() * 6);
            return new Shape(Kind.THERMAL, radius, 0, 1, baseAltitude, 1, 3 + random.nextDouble() * 3, phase, heading, direction);
        }
        if (roll < 0.6) {
            double radius = reach * (0.4 + random.nextDouble() * 0.4);
            return new Shape(Kind.LOOP, radius, radius * (0.1 + random.nextDouble() * 0.2), 2 + random.nextInt(2),
                    baseAltitude, Math.min(swellCap, 2 + random.nextDouble() * 4), 0, phase, heading, direction);
        }
        double radius = reach * (0.45 + random.nextDouble() * 0.35);
        return new Shape(Kind.FIGURE_EIGHT, radius, 0, 1, baseAltitude, Math.min(swellCap, 2 + random.nextDouble() * 3), 0,
                phase, heading, direction);
    }

    /** Offset from the centre {x, y, z} at curve parameter theta (one lap per 2 pi). */
    public static double[] offset(Shape shape, double theta) {
        double t = theta * shape.direction();
        double x, z;
        switch (shape.kind()) {
            case FIGURE_EIGHT -> {
                // Gerono lemniscate: two lobes that cross over the nest.
                x = shape.radius() * Math.sin(t);
                z = shape.radius() * Math.sin(t) * Math.cos(t);
            }
            default -> {
                double r = shape.radius() + shape.wobble() * Math.sin(shape.lobes() * t + shape.phase());
                x = r * Math.cos(t);
                z = r * Math.sin(t);
            }
        }
        double cos = Math.cos(shape.heading()), sin = Math.sin(shape.heading());
        double y = shape.altitude() + shape.swell() * Math.sin(2 * theta + shape.phase())
                + shape.climb() * theta / (Math.PI * 2);
        return new double[]{x * cos - z * sin, y, x * sin + z * cos};
    }

    /** How far to advance theta so the point moves about {@code blocks} along the curve. */
    public static double step(Shape shape, double theta, double blocks) {
        double epsilon = 1e-3;
        double[] a = offset(shape, theta), b = offset(shape, theta + epsilon);
        double length = Math.sqrt(square(b[0] - a[0]) + square(b[1] - a[1]) + square(b[2] - a[2])) / epsilon;
        return length < 1e-6 ? 0.05 : Math.min(0.5, blocks / length);
    }

    private static double square(double v) { return v * v; }

    private FlightPath() {}
}
