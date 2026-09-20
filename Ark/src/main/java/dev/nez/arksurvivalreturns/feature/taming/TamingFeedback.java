package dev.nez.arksurvivalreturns.feature.taming;

import java.util.UUID;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Player-facing and world-facing feedback for sedation and taming transitions. */
public final class TamingFeedback {
    /** Result of one feeding interaction, so the caller can answer with a precise reason. */
    public enum Result {
        ACCEPTED,
        WRONG_FOOD,
        NOT_HUNGRY,
        COOLDOWN,
        WRONG_STATE,
        NOT_CLAIMANT,
        ALREADY_TAMED,
        EXCLUDED;

        public Component message() {
            return Component.translatable("taming.arksurvivalreturns." + name().toLowerCase(java.util.Locale.ROOT));
        }
    }

    /** Announced once when a creature collapses, so a nearby player knows why it fell. */
    public static void knockout(LivingEntity entity) {
        announce(entity, true);
    }

    public static void wake(LivingEntity entity, boolean tamed) {
        announce(entity, false);
        if (tamed) hearts(entity);
    }

    private static void announce(LivingEntity entity, boolean falling) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        var message = Component.translatable(falling
                ? "taming.arksurvivalreturns.knockout"
                : "taming.arksurvivalreturns.wake", entity.getDisplayName());
        for (var player : level.players()) {
            if (player.distanceToSqr(entity) <= 48 * 48) tell(player, message, true);
        }
    }

    /** Action-bar text for the acting player; chat text for the tame completion. */
    private static void tell(Player player, Component message, boolean overlay) {
        if (player instanceof net.minecraft.server.level.ServerPlayer server) server.sendSystemMessage(message, overlay);
        else player.sendSystemMessage(message);
    }

    /** Vanilla horse-style success burst, sent to every tracking client exactly once. */
    public static void hearts(LivingEntity entity) {
        if (entity.level() instanceof ServerLevel level) level.broadcastEntityEvent(entity, (byte) 7);
    }

    public static void smoke(LivingEntity entity) {
        if (entity.level() instanceof ServerLevel level) level.broadcastEntityEvent(entity, (byte) 6);
    }

    public static void particles(LivingEntity entity, ParticleOptions options) {
        for (int i = 0; i < 7; i++) {
            entity.level().addParticle(options, entity.getRandomX(1.0), entity.getRandomY() + 0.5,
                    entity.getRandomZ(1.0), entity.getRandom().nextGaussian() * 0.02,
                    entity.getRandom().nextGaussian() * 0.02, entity.getRandom().nextGaussian() * 0.02);
        }
    }

    public static void accepted(Player player, CreatureEntity creature, float progress, boolean favourite) {
        tell(player, Component.translatable(favourite
                ? "taming.arksurvivalreturns.fed_favourite"
                : "taming.arksurvivalreturns.fed", creature.getDisplayName(),
                Math.round(progress)), progress < 100f);
    }

    public static void denied(Player player, CreatureEntity creature, Result result) {
        tell(player, Component.translatable("taming.arksurvivalreturns.denied",
                creature.getDisplayName(), result.message()), true);
    }

    /** A wild creature that is awake or held by another player cannot be reached. */
    public static void notFeedable(Player player, CreatureEntity creature) {
        tell(player, Component.translatable("taming.arksurvivalreturns.denied_access",
                creature.getDisplayName()), true);
    }

    public static void completed(Player player, CreatureEntity creature) {
        tell(player, Component.translatable("taming.arksurvivalreturns.tamed",
                creature.getDisplayName()), false);
        hearts(creature);
    }

    public static void claimed(Player player, CreatureEntity creature) {
        tell(player, Component.translatable("taming.arksurvivalreturns.claim",
                creature.getDisplayName()), true);
    }

    public static void truce(Player feeder, CreatureEntity creature) {
        tell(feeder, Component.translatable("taming.arksurvivalreturns.truce",
                creature.getDisplayName()), true);
    }

    public static String ownerName(UUID owner) {
        return owner == null ? "-" : owner.toString();
    }

    private TamingFeedback() {}
}
