package dev.nez.arksurvivalreturns.feature.recovery;

import java.util.Locale;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.taming.TamingService;
import dev.nez.arksurvivalreturns.feature.tribe.TribeService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Turns lethal damage into a rescue window instead of instant death.
 *
 * <p>Void, lava and {@code /kill} remain fatal, as do single hits above the overkill threshold; the
 * death drops items as usual. While downed, hits shorten the window and
 * movement and interactions are enforced by the shared unconscious-player handler.
 *
 * <p>Going down is decided on the damage that would really land: after shields, armor, enchantments and
 * effects, against health plus absorption. A hit the player would survive never downs them.
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class DownedHandler {
    /** While downed, hits are absorbed into the rescue window instead of health. */
    @SubscribeEvent public static void incoming(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!Config.DOWNED_ENABLED.get()) return;
        // /kill and the bleed-out always land, downed or not: absorbing them would call expire() again from
        // inside its own finishing blow and recurse until the stack overflows.
        if (event.getSource().is(DamageTypes.GENERIC_KILL)) return;
        DownedState state = player.getData(RecoveryAttachments.DOWNED);
        if (!state.downed()) return;
        event.setCanceled(true);
        state.bleed(event.getAmount(), Config.DOWNED_BLEED_FACTOR.get(), Config.DOWNED_BLEED_PER_HIT.get());
        DownedSync.send(player, state);
        // A drained window ends on the next player tick, never inside this damage event.
    }

    /**
     * Going down, decided on the mitigated damage (armor and effects applied; absorption comes after this
     * event). Runs last, after the Amber Amulet and every other damage modifier.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void mitigated(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!Config.DOWNED_ENABLED.get()) return;
        // A playthrough damage test measures this blow and cancels it itself; the player must not go down first.
        if (dev.nez.arksurvivalreturns.feature.playthrough.Playthrough.measuring(player)) return;
        var source = event.getSource();
        if (source.is(DamageTypes.GENERIC_KILL)) return;
        DownedState state = player.getData(RecoveryAttachments.DOWNED);
        float damage = event.getNewDamage();
        float pool = player.getHealth() + player.getAbsorptionAmount();
        if (state.downed() || damage < pool) return;
        boolean lethal = DownedPolicy.lethal(damage, player.getMaxHealth(), Config.DOWNED_OVERKILL.get(),
                Config.DOWNED_VOID_LETHAL.get() && source.is(DamageTypes.FELL_OUT_OF_WORLD),
                Config.DOWNED_LAVA_LETHAL.get() && (source.is(DamageTypes.LAVA)
                        || source.is(DamageTypes.IN_FIRE) || source.is(DamageTypes.ON_FIRE)));
        if (lethal) return;
        // Absorption soaks first and health ends at exactly 1; the hit itself still lands (knockback, sound).
        event.setNewDamage(Math.max(0.0f, pool - 1.0f));
        state.start(Config.DOWNED_WINDOW.get(), player.position());
        DownedSync.send(player, state);
        DownedSync.share(player);
        notifyDown(player, state);
        TamingService.discovery(player, "journal/first_downed");
    }

    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) step(player);
    }

    /** One tick of the rescue window; public so the headless suite can drive it (fake players never tick). */
    public static void step(ServerPlayer player) {
        DownedState state = player.getData(RecoveryAttachments.DOWNED);
        if (!state.downed()) {
            // A sedated player lies down through the same forced pose; only clear it when neither state holds.
            if (player.getForcedPose() == net.minecraft.world.entity.Pose.SWIMMING
                    && !dev.nez.arksurvivalreturns.feature.taming.TorporService.restricted(player)) player.setForcedPose(null);
            return;
        }
        if (player.isDeadOrDying()) {
            state.clear();
            player.setForcedPose(null);
            return;
        }
        // Lying on the ground, with a red mark above them that other players can find (BUG-08).
        player.setForcedPose(net.minecraft.world.entity.Pose.SWIMMING);
        if (player.tickCount % 20 == 0)
            player.level().sendParticles(new net.minecraft.core.particles.DustParticleOptions(0xD01818, 2.0f),
                    player.getX(), player.getY() + 1.6, player.getZ(), 4, 0.15, 0.3, 0.15, 0.0);
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

    /** Downed or knocked out: such a player neither crafts nor gives orders (BUG-19). */
    public static boolean incapacitated(net.minecraft.world.entity.player.Player player) {
        return player.hasData(RecoveryAttachments.DOWNED) && player.getData(RecoveryAttachments.DOWNED).downed()
                || dev.nez.arksurvivalreturns.feature.taming.TorporService.restricted(player);
    }

    /** The downed player held Sneak for three seconds: they give up and die now (BUG-08). */
    public static void giveUp(ServerPlayer player) {
        if (player.getData(RecoveryAttachments.DOWNED).downed()) expire(player);
    }

    private static void expire(ServerPlayer player) {
        // Leave the downed state before the finishing blow, so it is an ordinary death.
        player.getData(RecoveryAttachments.DOWNED).clear();
        player.setForcedPose(null);
        DownedSync.clear(player);
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
