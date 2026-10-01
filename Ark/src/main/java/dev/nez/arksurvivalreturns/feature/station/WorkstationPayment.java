package dev.nez.arksurvivalreturns.feature.station;

import java.util.ArrayDeque;
import java.util.Arrays;

/** Integral max flow prevents overlapping item/tag costs from counting a stack twice.
 * Rows are requirements, columns are inventory slots. A failed plan never mutates inventory. */
public final class WorkstationPayment {
    public record Plan(int[] consumed, int[][] allocation) {}
    public static Plan plan(int[] available, int[] cost, boolean[][] matches, int times) {
        if (times <= 0 || cost.length == 0) return null;
        int slots = available.length, requirements = cost.length, sink = 1 + slots + requirements, size = sink + 1;
        int[][] remaining = new int[size][size]; long total = 0;
        for (int slot = 0; slot < slots; slot++) remaining[0][1 + slot] = available[slot];
        for (int r = 0; r < requirements; r++) {
            long need = (long) cost[r] * times; total += need;
            if (cost[r] <= 0 || total > Integer.MAX_VALUE) return null;
            remaining[1 + slots + r][sink] = (int) need;
            for (int slot = 0; slot < slots; slot++) if (matches[r][slot]) remaining[1 + slot][1 + slots + r] = available[slot];
        }
        int paid = 0;
        while (paid < total) {
            int[] parent = new int[size]; Arrays.fill(parent, -1); parent[0] = 0;
            ArrayDeque<Integer> queue = new ArrayDeque<>(); queue.add(0);
            while (!queue.isEmpty() && parent[sink] == -1) {
                int at = queue.removeFirst();
                for (int next = 1; next < size; next++) if (parent[next] == -1 && remaining[at][next] > 0) {
                    parent[next] = at; queue.add(next);
                }
            }
            if (parent[sink] == -1) return null;
            int send = Integer.MAX_VALUE;
            for (int at = sink; at != 0; at = parent[at]) send = Math.min(send, remaining[parent[at]][at]);
            for (int at = sink; at != 0; at = parent[at]) { remaining[parent[at]][at] -= send; remaining[at][parent[at]] += send; }
            paid += send;
        }
        int[] consumed = new int[slots]; int[][] allocation = new int[requirements][slots];
        for (int slot = 0; slot < slots; slot++) {
            consumed[slot] = available[slot] - remaining[0][1 + slot];
            for (int r = 0; r < requirements; r++) allocation[r][slot] = remaining[1 + slots + r][1 + slot];
        }
        return new Plan(consumed, allocation);
    }
    public static int maxCrafts(int[] available, int[] cost, boolean[][] matches) {
        long sum = 0, price = 0;
        for (int count : available) sum += count;
        for (int count : cost) { if (count <= 0) return 0; price += count; }
        if (price == 0) return 0;
        int low = 0, high = (int) Math.min(Integer.MAX_VALUE, sum / price);
        while (low < high) {
            int mid = low + (high - low + 1) / 2;
            if (plan(available, cost, matches, mid) != null) low = mid; else high = mid - 1;
        }
        return low;
    }
    private WorkstationPayment() {}
}
