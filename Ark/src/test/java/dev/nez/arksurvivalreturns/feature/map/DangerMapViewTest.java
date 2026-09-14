package dev.nez.arksurvivalreturns.feature.map;

import dev.nez.arksurvivalreturns.feature.spawn.DangerBands;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DangerMapViewTest {
    @Test void unknownTerrainNeverReceivesTint() {
        assertTrue(DangerMapView.cells(300, 200, 0, 0, 1, 0, 0, 256, (a,b,c,d) -> false).isEmpty());
        var cells = DangerMapView.cells(300, 200, 0, 0, 1, 0, 0, 256,
                (minX,minZ,maxX,maxZ) -> minX >= 0 && minZ >= 0 && maxX <= 64 && maxZ <= 64);
        assertFalse(cells.isEmpty());
        for (var cell : cells) {
            assertTrue(cell.left() >= 150 && cell.top() >= 100);
            assertTrue(cell.right() <= 214 && cell.bottom() <= 164);
        }
    }
    @Test void rasterMatchesServerAtDifferentPansAndZooms() {
        for (double scale : new double[]{0.25, 1, 8}) {
            int width = 600, height = 300;
            double cx = -421, cz = 791;
            var cells = DangerMapView.cells(width, height, cx, cz, scale, -190, 475, 256);
            int[][] covered = new int[height][width];
            for (var cell : cells) {
                assertTrue(cell.left() >= 0 && cell.right() <= width && cell.top() >= 0 && cell.bottom() <= height);
                for (int y = cell.top(); y < cell.bottom(); y++) for (int x = cell.left(); x < cell.right(); x++) {
                    assertEquals(0, covered[y][x], "Overlapping cells"); covered[y][x] = cell.danger();
                }
            }
            int step = Math.max(3, (int)Math.ceil(Math.sqrt((double)width * height / 12000)));
            for (int y = 0; y < height; y += step) for (int x = 0; x < width; x += step) {
                int wx = (int)Math.floor(cx + (x + Math.min(step, width-x)/2.0 - width/2.0)/scale);
                int wz = (int)Math.floor(cz + (y + Math.min(step, height-y)/2.0 - height/2.0)/scale);
                assertEquals(DangerBands.level(wx, wz, -190, 475, 256), covered[y][x]);
            }
        }
    }
    @Test void pathologicalViewsAndWorldBorderAreBounded() {
        assertTrue(DangerMapView.cells(500, 300, 0, 0, 0, 0, 0, 256).isEmpty());
        assertTrue(DangerMapView.cells(500, 300, 0, 0, Double.NaN, 0, 0, 256).isEmpty());
        assertTrue(DangerMapView.cells(500, 300, 0, 0, 1, 0, 0, 0).isEmpty());
        assertTrue(DangerMapView.cells(500, 300, 40_000_000, 0, 1, 0, 0, 256).isEmpty());
        assertTrue(DangerMapView.cells(3840, 2160, 0, 0, 0.0001, 0, 0, 96).size() < 12500);
    }
}
