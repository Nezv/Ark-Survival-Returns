package dev.nez.arksurvivalreturns.feature.map;

import java.util.ArrayList;
import java.util.List;
import dev.nez.arksurvivalreturns.feature.spawn.DangerBands;

/** Bounded screen-space raster: no biome queries, world access, or chunk generation. */
public final class DangerMapView {
    public record Cell(int left, int top, int right, int bottom, int danger) {}
    @FunctionalInterface public interface Exploration {
        boolean contains(int minX, int minZ, int maxX, int maxZ);
    }
    public static List<Cell> cells(int width, int height, double cameraX, double cameraZ, double scale,
                                   int originX, int originZ, int bandWidth) {
        return cells(width, height, cameraX, cameraZ, scale, originX, originZ, bandWidth, (a,b,c,d) -> true);
    }
    public static List<Cell> cells(int width, int height, double cameraX, double cameraZ, double scale,
                                   int originX, int originZ, int bandWidth, Exploration explored) {
        if (width <= 0 || height <= 0 || !Double.isFinite(scale) || scale <= 0
                || !Double.isFinite(cameraX) || !Double.isFinite(cameraZ) || bandWidth < 96 || bandWidth > 1024)
            return List.of();
        int step = Math.max(3, (int)Math.ceil(Math.sqrt((double)width * height / 12000)));
        var cells = new ArrayList<Cell>();
        for (int y = 0; y < height; y += step) {
            int previous = 0, start = 0;
            for (int x = 0; x < width; x += step) {
                double wx = cameraX + (x + Math.min(step, width - x) / 2.0 - width / 2.0) / scale;
                double wz = cameraZ + (y + Math.min(step, height - y) / 2.0 - height / 2.0) / scale;
                int rank = Math.abs(wx) > 30_000_000 || Math.abs(wz) > 30_000_000 ? 0 :
                        DangerBands.level((int)Math.floor(wx), (int)Math.floor(wz), originX, originZ, bandWidth);
                if (rank != 0 && !explored.contains(
                        (int)Math.floor(cameraX + (x - width / 2.0) / scale),
                        (int)Math.floor(cameraZ + (y - height / 2.0) / scale),
                        (int)Math.floor(cameraX + (Math.min(width, x + step) - width / 2.0) / scale),
                        (int)Math.floor(cameraZ + (Math.min(height, y + step) - height / 2.0) / scale))) rank = 0;
                if (rank != previous) {
                    if (previous > 0) cells.add(new Cell(start, y, x, Math.min(height, y + step), previous));
                    start = x; previous = rank;
                }
            }
            if (previous > 0) cells.add(new Cell(start, y, width, Math.min(height, y + step), previous));
        }
        return cells;
    }
    private DangerMapView() {}
}
