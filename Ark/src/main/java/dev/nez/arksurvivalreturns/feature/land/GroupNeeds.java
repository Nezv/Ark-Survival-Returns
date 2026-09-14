package dev.nez.arksurvivalreturns.feature.land;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** One hunger clock per group. Inactive time is never replayed on load. */
public final class GroupNeeds {
    private double hunger;
    private long lastTick = Long.MIN_VALUE;
    private int feeding;
    private final Map<UUID, Long> meals = new HashMap<>();
    public GroupNeeds(double hunger) { this.hunger = finite(hunger); }
    public double hunger() { return hunger; }
    public boolean feeding() { return feeding > 0; }
    public boolean advance(long tick, double multiplier, int foragers, int members) {
        if (lastTick != Long.MIN_VALUE && tick - lastTick < 20 && tick >= lastTick) return false;
        int elapsed = lastTick == Long.MIN_VALUE ? 20 : (int)Math.clamp(tick - lastTick, 0, 20);
        lastTick = tick;
        hunger = finite(hunger + elapsed / 24000.0 * multiplier
                - elapsed / 400.0 * Math.clamp(foragers / (double)Math.max(1, members), 0, 1));
        feeding = Math.max(0, feeding - elapsed);
        meals.entrySet().removeIf(e -> tick - e.getValue() > 1200);
        return true;
    }
    public boolean feed(UUID victim, long tick) {
        if (meals.putIfAbsent(victim, tick) != null) return false;
        hunger = 0.05; feeding = 100;
        return true;
    }
    private static double finite(double value) { return Double.isFinite(value) ? Math.clamp(value, 0, 1) : 0.55; }
}
