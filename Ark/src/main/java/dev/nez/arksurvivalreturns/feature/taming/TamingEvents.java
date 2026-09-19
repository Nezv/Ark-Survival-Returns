package dev.nez.arksurvivalreturns.feature.taming;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Cross-cutting taming reactions: the damage penalty, the aerial truce rule and the tame bookkeeping that
 * has to leave the wildlife systems.
 *
 * <p>Every handler logs a rejected transition once, never per tick.
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class TamingEvents {
    /** Damage during an attempt costs progress, and attacking a truce holder cancels that truce. */
    @SubscribeEvent public static void damaged(LivingDamageEvent.Post event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide() || event.getInflictedDamage() <= 0f) return;
        if (TorporService.tracked(entity)) TamingService.onDamaged(entity, event.getSource().getEntity());
    }

    /** A tamed creature stops counting as wildlife but keeps its needs, sleep and threat responses. */
    @SubscribeEvent public static void joined(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof CreatureEntity creature) || event.getLevel().isClientSide()) return;
        if (TamingService.isTamed(creature)) creature.applyTameState();
    }

    /** Sedation is cleared when a player leaves a world on death; logout deliberately keeps it. */
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;
        TorporService.clear(event.getOriginal());
        TamingService.onWakeWithoutCompletion(event.getOriginal());
    }

    private TamingEvents() {}
}
