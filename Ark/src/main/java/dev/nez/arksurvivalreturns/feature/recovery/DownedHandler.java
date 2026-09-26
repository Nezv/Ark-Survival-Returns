package dev.nez.arksurvivalreturns.feature.recovery;

import java.util.Locale;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.taming.TamingService;
import dev.nez.arksurvivalreturns.feature.tribe.TribeService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Turns lethal damage into a rescue window instead of instant death.
 *
 * <p>Void, lava and {@code /kill} remain fatal, as do single hits above the overkill threshold; the
 * death drops items as usual. While downed, hits shorten the window and
 * movement and interactions are enforced by the shared unconscious-player handler.
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class DownedHandler {
    @SubscribeEvent public static void incoming(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!Config.DOWNED_ENABLED.get()) return;
        DownedState state = player.getData(RecoveryAttachments.DOWNED);
        float damage = event.getAmount();
        if (state.downed()) {
            event.setCanceled(true);
            state.bleed(damage, Config.DOWNED_BLEED_FACTOR.get());
            DownedSync.send(player, state);
            if (state.ticksLeft() <= 0) expire(player);
            return;
        }
        if (damage < player.getHealth()) return;
        var source = event.getSource();
        boolean lethal = DownedPolicy.lethal(damage, player.getMaxHealth(), Config.DOWNED_OVERKILL.get(),
                Config.DOWNED_VOID_LETHAL.get() && source.is(DamageTypes.FELL_OUT_OF_WORLD),
                Config.DOWNED_LAVA_LETHAL.get() && (source.is(DamageTypes.LAVA)
                        || source.is(DamageTypes.IN_FIRE) || source.is(DamageTypes.ON_FIRE)));
        if (lethal || source.is(DamageTypes.GENERIC_KILL)) return;
        event.setCanceled(true);
        player.setHealth(1.0f);
        state.start(Config.DOWNED_WINDOW.get(), player.position());
        DownedSync.send(player, state);
        notifyDown(player, state);
        TamingService.discovery(player, "journal/first_downed");
    }

    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        DownedState state = player.getData(RecoveryAttachments.DOWNED);
        if (!state.downed()) return;
        if (player.isDeadOrDying()) {
            state.clear();
            return;
        }
        state.tick();
        if (player.tickCount % 10 == 0) DownedSync.send(player, state);
        if (state.ticksLeft() <= 0) expire(player);
    }

    /** Bandage rescue: another player (or any ally, depending on config) pulls them back up. */
    public static void revive(ServerPlayer target, ServerPlayer reviver) {
        DownedState state = target.getData(RecoveryAttachments.DOWNED);
        if (!state.downed()) return;
        state.revive();
        target.setHealth(Math.max(1.0f, target.getMaxHealth() * (float) (double) Config.DOWNED_REVIVE_FRACTION.get()));
        DownedSync.clear(target);
        target.sendSystemMessage(Component.translatable("downed.arksurvivalreturns.revived",
                reviver.getDisplayName()), false);
        reviver.sendSystemMessage(Component.translatable("downed.arksurvivalreturns.revived_self",
                target.getDisplayName()), true);
        TamingService.discovery(reviver, "journal/first_revive");
    }

    private static void expire(ServerPlayer player) {
        // Bypasses the downed interception: generic_kill is on the lethal list.
        player.hurtServer(player.level(), player.damageSources().genericKill(), Float.MAX_VALUE);
    }

    private static void notifyDown(ServerPlayer player, DownedState state) {
        String seconds = String.format(Locale.ROOT, "%.0f", state.ticksLeft() / 20.0);
        player.sendSystemMessage(Component.translatable("downed.arksurvivalreturns.downed", seconds), false);
        if (!Config.DOWNED_NOTIFY_TRIBE.get()) return;
        for (ServerPlayer other : player.level().getServer().getPlayerList().getPlayers()) {
            if (other == player || !TribeService.sameTribe(other.getUUID(), player.getUUID())) continue;
            var pos = player.blockPosition();
            other.sendSystemMessage(Component.translatable("downed.arksurvivalreturns.tribe_down",
                    player.getDisplayName(), pos.getX(), pos.getY(), pos.getZ(), seconds), false);
        }
    }

    private DownedHandler() {}
}
