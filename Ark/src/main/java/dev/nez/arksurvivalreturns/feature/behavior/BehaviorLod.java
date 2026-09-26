package dev.nez.arksurvivalreturns.feature.behavior;

import java.util.List;
import dev.nez.arksurvivalreturns.Config;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Server side of the behaviour level of detail: the tier radii from the config and the distance to the
 * nearest observer. GameTests drive creatures without real players (mock players are not in the level's
 * player list), so tiers stay at FULL on the GameTest server unless a test supplies its own observers.
 */
public final class BehaviorLod {
    private static final boolean GAME_TEST_SERVER = System.getProperty("neoforge.enabledGameTestNamespaces") != null;
    private static volatile List<Vec3> testObservers;

    public static boolean enabled() {
        return Config.BEHAVIOR_TIERS.get() && (!GAME_TEST_SERVER || testObservers != null);
    }

    public static BehaviorTier.Radii radii() {
        return new BehaviorTier.Radii(Config.TIER_FULL_RADIUS.get(), Config.TIER_AMBIENT_RADIUS.get(),
                Config.TIER_DORMANT_RADIUS.get(), Config.TIER_MARGIN.get());
    }

    /** Squared distance from the entity to the nearest player (or test observer); infinite when none. */
    public static double nearestObserverSq(ServerLevel level, Entity entity) {
        double nearest = Double.POSITIVE_INFINITY;
        var observers = testObservers;
        if (observers != null) {
            for (var point : observers) nearest = Math.min(nearest, entity.distanceToSqr(point));
            return nearest;
        }
        for (var player : level.players()) nearest = Math.min(nearest, entity.distanceToSqr(player));
        return nearest;
    }

    /** GameTest hook: evaluate tiers against these points instead of real players; null restores the default. */
    public static void useTestObservers(List<Vec3> observers) {
        testObservers = observers == null ? null : List.copyOf(observers);
    }

    private BehaviorLod() {}
}
