package dev.nez.arksurvivalreturns.feature.spawn;

/** Repeating equal-area danger regions. Integer shears curve the borders without changing area. */
public final class DangerBands {
    public static int level(int x, int z, int originX, int originZ, int width) {
        if (width <= 0) throw new IllegalArgumentException("Band width must be positive");
        long radius = 2L * width, period = 2 * radius;
        long u = (long) x - originX, v = (long) z - originZ;
        // Each shear is a bijection on a periodic integer grid, preserving the five area shares.
        u += curve(v, period, radius);
        v += curve(u, period, radius);
        long dx = Math.abs(Math.floorMod(u + radius, period) - radius);
        long dz = Math.abs(Math.floorMod(v + radius, period) - radius);
        double fraction = (double) Math.max(dx, dz) / radius;
        // A square of radius r occupies (r/R)^2 of the tile. Equal CDF intervals give 20% each.
        return 1 + (int) Math.min(4, Math.floor(5 * fraction * fraction));
    }
    private static long curve(long coordinate, long period, long radius) {
        return Math.round(radius * 0.20 * Math.sin(2 * Math.PI * Math.floorMod(coordinate, period) / period));
    }
    private DangerBands() {}
}
