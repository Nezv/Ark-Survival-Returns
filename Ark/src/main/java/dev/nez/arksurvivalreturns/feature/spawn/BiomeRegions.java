package dev.nez.arksurvivalreturns.feature.spawn;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Divides one tile of land, 32 by 32 chunks, into its biome regions: the connected patches of one biome, at
 * one sample a chunk. A patch too small or too thin to hold wildlife of its own (a beach between the sea and a
 * plain, a pocket of a few chunks) is taken into the largest patch beside it, whose biome becomes its own.
 * The regions are numbered by size, largest first, so the same land always comes out the same.
 */
public final class BiomeRegions {
    public static final int SIDE = 32, CELLS = SIDE * SIDE;
    /** A patch of fewer chunks, or one without a single chunk surrounded by its own kind, is no region of its own. */
    public static final int SMALLEST = 16;
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /** The biome at the middle of a chunk of the tile; x and z run from 0 to 31. */
    @FunctionalInterface public interface Sampler { String biome(int x, int z); }

    /** Per chunk (z * 32 + x) the number of its region; per region its biome and how many chunks it holds. */
    public record Tile(byte[] region, List<String> biomes, int[] cells) {}

    public static Tile divide(Sampler sampler) {
        String[] biome = new String[CELLS];
        for (int z = 0; z < SIDE; z++) for (int x = 0; x < SIDE; x++) biome[z * SIDE + x] = sampler.biome(x, z);
        int[] patch = new int[CELLS];
        java.util.Arrays.fill(patch, -1);
        List<String> kinds = new ArrayList<>();
        List<Integer> sizes = new ArrayList<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int start = 0; start < CELLS; start++) {
            if (patch[start] >= 0) continue;
            int id = kinds.size(), size = 0;
            patch[start] = id;
            queue.add(start);
            while (!queue.isEmpty()) {
                int cell = queue.removeFirst();
                size++;
                for (int[] side : SIDES) {
                    int x = cell % SIDE + side[0], z = cell / SIDE + side[1];
                    if (x < 0 || z < 0 || x >= SIDE || z >= SIDE) continue;
                    int next = z * SIDE + x;
                    if (patch[next] < 0 && biome[next].equals(biome[start])) {
                        patch[next] = id;
                        queue.addLast(next);
                    }
                }
            }
            kinds.add(biome[start]);
            sizes.add(size);
        }
        int count = kinds.size();
        int[] size = new int[count];
        boolean[] gone = new boolean[count];
        for (int i = 0; i < count; i++) size[i] = sizes.get(i);
        // The smallest patch that cannot stand alone goes into its largest neighbour, until none is left.
        while (true) {
            boolean[] roomy = new boolean[count];
            for (int cell = 0; cell < CELLS; cell++) {
                boolean enclosed = true;
                for (int[] side : SIDES) {
                    int x = cell % SIDE + side[0], z = cell / SIDE + side[1];
                    // Beyond the tile the land goes on; it counts as more of the same.
                    if (x >= 0 && z >= 0 && x < SIDE && z < SIDE && patch[z * SIDE + x] != patch[cell]) enclosed = false;
                }
                if (enclosed) roomy[patch[cell]] = true;
            }
            int pick = -1;
            for (int i = 0; i < count; i++)
                if (!gone[i] && (size[i] < SMALLEST || !roomy[i]) && (pick < 0 || size[i] < size[pick])) pick = i;
            if (pick < 0) break;
            int into = -1;
            for (int cell = 0; cell < CELLS; cell++) {
                if (patch[cell] != pick) continue;
                for (int[] side : SIDES) {
                    int x = cell % SIDE + side[0], z = cell / SIDE + side[1];
                    if (x < 0 || z < 0 || x >= SIDE || z >= SIDE) continue;
                    int other = patch[z * SIDE + x];
                    if (other != pick && (into < 0 || size[other] > size[into] || size[other] == size[into] && other < into)) into = other;
                }
            }
            // A tile of one patch, however thin, is one region.
            if (into < 0) break;
            for (int cell = 0; cell < CELLS; cell++) if (patch[cell] == pick) patch[cell] = into;
            size[into] += size[pick];
            gone[pick] = true;
        }
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < count; i++) if (!gone[i]) order.add(i);
        // Patches were numbered in the order their first chunk is met, so equal sizes keep that order.
        order.sort((a, b) -> size[b] != size[a] ? Integer.compare(size[b], size[a]) : Integer.compare(a, b));
        int[] number = new int[count];
        List<String> biomes = new ArrayList<>();
        int[] cells = new int[order.size()];
        for (int i = 0; i < order.size(); i++) {
            number[order.get(i)] = i;
            biomes.add(kinds.get(order.get(i)));
            cells[i] = size[order.get(i)];
        }
        byte[] region = new byte[CELLS];
        for (int cell = 0; cell < CELLS; cell++) region[cell] = (byte) number[patch[cell]];
        return new Tile(region, biomes, cells);
    }

    private BiomeRegions() {}
}
