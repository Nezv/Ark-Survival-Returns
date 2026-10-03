package dev.nez.arksurvivalreturns.feature.spawn;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Optional;

/** Bounded flood fill of one connected surface biome, on an eight-block sampling grid. */
public final class BiomePatchSurvey {
    public static final int STEP = 8;
    public static final int MAX_RADIUS = 512;
    public static final int MAX_SAMPLES = 8192;
    private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    public record Sample(BiomeProfile profile, int surfaceY) {}
    /** Null means unavailable terrain, never a confirmed biome boundary. */
    @FunctionalInterface public interface Sampler { Sample at(int x, int z); }
    private record Cell(int x, int z) {}

    public record Patch(BiomeProfile profile, int cells, int spanX, int spanZ, int minY, int maxY,
                        int sampledColumns, int unavailableColumns, boolean rangeLimited, boolean budgetLimited) {
        public long areaBlocks() { return (long) cells * STEP * STEP; }
        public double equivalentDiameter() { return 2 * Math.sqrt(areaBlocks() / Math.PI); }
        /** Enclosed only at this grid's resolution; thin strips and small holes can fall between samples. */
        public boolean enclosed() { return unavailableColumns == 0 && !rangeLimited && !budgetLimited; }
    }

    public static Optional<Patch> measure(int x, int z, int radius, Sampler sampler) {
        if (radius < STEP || radius > MAX_RADIUS) throw new IllegalArgumentException("radius must be 8..512");
        Sample origin = sampler.at(x, z);
        if (origin == null) return Optional.empty();
        Cell start = new Cell(x, z);
        var queue = new ArrayDeque<Cell>();
        var visited = new HashSet<Cell>();
        queue.add(start);
        visited.add(start);
        int cells = 0, sampled = 1, unavailable = 0;
        int minX = x, maxX = x, minZ = z, maxZ = z;
        int minY = origin.surfaceY(), maxY = minY;
        boolean rangeLimited = false, budgetLimited = false;
        while (!queue.isEmpty()) {
            Cell cell = queue.removeFirst();
            Sample sample;
            if (cell.equals(start)) sample = origin;
            else {
                if (sampled == MAX_SAMPLES) { budgetLimited = true; break; }
                sampled++;
                sample = sampler.at(cell.x(), cell.z());
            }
            if (sample == null) { unavailable++; continue; }
            // Nearby disconnected patches and adjacent biomes of the same type remain separate.
            if (!sample.profile().biomeId().equals(origin.profile().biomeId())) continue;
            cells++;
            minX = Math.min(minX, cell.x()); maxX = Math.max(maxX, cell.x());
            minZ = Math.min(minZ, cell.z()); maxZ = Math.max(maxZ, cell.z());
            minY = Math.min(minY, sample.surfaceY()); maxY = Math.max(maxY, sample.surfaceY());
            for (int[] direction : DIRECTIONS) {
                Cell next = new Cell(cell.x() + direction[0] * STEP, cell.z() + direction[1] * STEP);
                if (Math.abs((long) next.x() - x) > radius || Math.abs((long) next.z() - z) > radius) {
                    rangeLimited = true;
                    continue;
                }
                if (visited.add(next)) queue.addLast(next);
            }
        }
        return Optional.of(new Patch(origin.profile(), cells, maxX - minX + STEP, maxZ - minZ + STEP,
                minY, maxY, sampled, unavailable, rangeLimited, budgetLimited));
    }

    private BiomePatchSurvey() {}
}
