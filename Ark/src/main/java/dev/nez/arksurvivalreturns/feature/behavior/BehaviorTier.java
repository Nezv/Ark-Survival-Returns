package dev.nez.arksurvivalreturns.feature.behavior;

/**
 * Level of detail of a wild creature's behaviour, chosen from the distance to the nearest player.
 *
 * <ul>
 *   <li>{@link #FULL}: perception, needs, hunting, fleeing, drinking and the timed choreography.</li>
 *   <li>{@link #AMBIENT}: a cheap routine for creatures that can still be seen: short walks, turns,
 *       pauses, grazing and the daily sleep schedule. No entity scans, no needs, no hunting.</li>
 *   <li>{@link #DORMANT}: no routine. Inside the outer radius the pose still follows the daily schedule
 *       (asleep or standing) on a slow timer; beyond it nothing runs at all.</li>
 * </ul>
 * Promotion happens at a radius; demotion waits for the radius plus a margin, so a player standing on
 * a border does not make a creature flicker between routines.
 */
public enum BehaviorTier {
    FULL, AMBIENT, DORMANT;

    /** Radii in blocks; the constructor sorts them so a misconfigured file can never invert the tiers. */
    public record Radii(int full, int ambient, int dormant, int margin) {
        public Radii {
            full = Math.max(8, full);
            ambient = Math.max(full, ambient);
            dormant = Math.max(ambient, dormant);
            margin = Math.clamp(margin, 0, 32);
        }
    }

    public static BehaviorTier classify(double distanceSq, BehaviorTier previous, Radii radii) {
        double fullEdge = radii.full() + (previous == FULL ? radii.margin() : 0);
        if (distanceSq <= fullEdge * fullEdge) return FULL;
        double ambientEdge = radii.ambient() + (previous != DORMANT ? radii.margin() : 0);
        if (distanceSq <= ambientEdge * ambientEdge) return AMBIENT;
        return DORMANT;
    }

    /** Whether a dormant creature at this distance still settles its pose on the daily schedule. */
    public static boolean posed(double distanceSq, Radii radii) {
        return distanceSq <= (double) radii.dormant() * radii.dormant();
    }
}
