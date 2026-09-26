package dev.nez.arksurvivalreturns.client.title;

import java.util.SplittableRandom;

/**
 * Where a menu creature is looking: holds a direction for a few seconds, then turns its head to another with
 * an eased move, now and then back to the front. A lightning strike makes it glance up toward the sky.
 * Angles are degrees; positive yaw turns to the creature's left, positive pitch raises the head.
 */
public final class Gaze {
    private final SplittableRandom random;
    private double fromYaw, fromPitch, toYaw, toPitch;
    private double moveStart, moveSeconds, holdUntil = Double.NaN;

    public Gaze(long seed) { random = new SplittableRandom(seed); }

    /** Yaw and pitch at {@code now} (seconds on any monotonic clock); {@code range} bounds the yaw. */
    public double[] at(double now, double range) {
        if (Double.isNaN(holdUntil)) holdUntil = now + 1.5 + random.nextDouble() * 1.5;
        if (now >= holdUntil) next(now, range);
        double[] base = base(now);
        // A slow drift keeps a held pose alive.
        return new double[]{base[0] + 1.6 * Math.sin(now * 0.9), base[1] + 0.9 * Math.sin(now * 1.3 + 1.7)};
    }

    /** Snap attention toward the sky, as when lightning strikes. */
    public void startle(double now, double range) {
        double[] current = base(now);
        double yaw = current[0] + (random.nextBoolean() ? 1 : -1) * range * 0.35;
        begin(now, current, clamp(yaw, range), 11 + random.nextDouble() * 5, 0.3 + random.nextDouble() * 0.1,
                1.6 + random.nextDouble() * 1.2);
    }

    private double[] base(double now) {
        double t = moveSeconds <= 0 ? 1 : Math.min(1, Math.max(0, (now - moveStart) / moveSeconds));
        double e = t * t * t * (t * (6 * t - 15) + 10);   // smootherstep: no snap at either end
        return new double[]{fromYaw + (toYaw - fromYaw) * e, fromPitch + (toPitch - fromPitch) * e};
    }

    private void next(double now, double range) {
        double[] current = base(now);
        double roll = random.nextDouble();
        double yaw, pitch, hold;
        if (roll < 0.22) {
            yaw = random.nextDouble() * 6 - 3;                       // back to the front
            pitch = random.nextDouble() * 4 - 1;
            hold = 2.5 + random.nextDouble() * 3.5;
        } else {
            // Prefer the other side from where the head is, so the glances sweep the clearing.
            double side = current[0] > 0 ? -1 : 1;
            if (random.nextDouble() < 0.25) side = -side;
            yaw = side * range * (0.35 + 0.65 * random.nextDouble());
            pitch = -5 + random.nextDouble() * 13;
            hold = 1.2 + random.nextDouble() * 3.0;
        }
        double distance = Math.hypot(yaw - current[0], pitch - current[1]);
        begin(now, current, clamp(yaw, range), pitch, 0.45 + distance / 55.0 + random.nextDouble() * 0.3, hold);
    }

    private void begin(double now, double[] current, double yaw, double pitch, double seconds, double hold) {
        fromYaw = current[0];
        fromPitch = current[1];
        toYaw = yaw;
        toPitch = pitch;
        moveStart = now;
        moveSeconds = seconds;
        holdUntil = now + seconds + hold;
    }

    private static double clamp(double yaw, double range) { return Math.max(-range, Math.min(range, yaw)); }
}
