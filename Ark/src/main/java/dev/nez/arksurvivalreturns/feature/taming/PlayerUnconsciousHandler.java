package dev.nez.arksurvivalreturns.feature.taming;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.Config;

/**
 * Server-side enforcement of incapacitated-player restrictions (sedation and the downed state).
 *
 * <p>Every restriction here is a real server rule: interactions, attacks, item use, item dropping and
 * mounting are refused, and voluntary movement is clamped against the position the player fell at.
 * Knockback and other external impulses are preserved by opening a short window during which the anchor
 * follows the pushed player, so a knockout is not a way to become immune to being shoved off a bridge.
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class PlayerUnconsciousHandler {
    @SubscribeEvent public static void tick(PlayerTickEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        // Downed players use the same lockdown but their own anchor and timer.
        var downed = player.getData(dev.nez.arksurvivalreturns.feature.recovery.RecoveryAttachments.DOWNED);
        if (downed.downed()) {
            holdDowned(player, downed);
            return;
        }
        // Never create sedation state for a player who has never been sedated.
        if (!TorporService.tracked(player)) return;
        var state = TorporService.of(player);
        if (!TorporService.restricted(player)) {
            if (state.hasAnchor()) state.clearAnchor();
            return;
        }
        hold(player, state);
    }

    /** Clears intent, refuses mounts, and clamps voluntary movement while leaving physics alone. */
    private static void hold(ServerPlayer player, TorporState state) {
        Input input = player.getLastClientInput();
        boolean intent = input.forward() || input.backward() || input.left() || input.right();
        player.setLastClientInput(Input.EMPTY);
        player.setJumping(false);
        player.setSprinting(false);
        player.setShiftKeyDown(false);
        if (player.isUsingItem()) player.stopUsingItem();
        if (player.getVehicle() != null) player.stopRiding();

        if (!state.hasAnchor()) {
            state.setAnchor(player.position());
            return;
        }
        if (state.externalTicks() > 0) {
            // An external impulse is moving the player: let it finish and keep the anchor with it.
            state.tickExternal();
            state.setAnchor(player.position());
            return;
        }
        Vec3 anchor = state.anchor();
        double drift = Math.hypot(player.getX() - anchor.x, player.getZ() - anchor.z);
        if (!intent && drift <= Config.PLAYER_MOVEMENT_TOLERANCE.get()) return;
        player.connection.teleport(anchor.x, player.getY(), anchor.z, player.getYRot(), player.getXRot());
        player.setDeltaMovement(0.0, player.getDeltaMovement().y, 0.0);
        deny(player, "taming.arksurvivalreturns.denied.move");
    }

    /** Downed players keep their input cleared and drift no farther than the rescue tolerance. */
    private static void holdDowned(ServerPlayer player, dev.nez.arksurvivalreturns.feature.recovery.DownedState state) {
        Input input = player.getLastClientInput();
        boolean intent = input.forward() || input.backward() || input.left() || input.right();
        player.setLastClientInput(Input.EMPTY);
        player.setJumping(false);
        player.setSprinting(false);
        player.setShiftKeyDown(false);
        if (player.isUsingItem()) player.stopUsingItem();
        if (player.getVehicle() != null) player.stopRiding();
        if (!state.hasAnchor()) {
            state.setAnchor(player.position());
            return;
        }
        Vec3 anchor = state.anchor();
        double drift = Math.hypot(player.getX() - anchor.x, player.getZ() - anchor.z);
        if (!intent && drift <= Config.PLAYER_MOVEMENT_TOLERANCE.get()) return;
        player.connection.teleport(anchor.x, player.getY(), anchor.z, player.getYRot(), player.getXRot());
        player.setDeltaMovement(0.0, player.getDeltaMovement().y, 0.0);
        deny(player, "downed.arksurvivalreturns.denied");
    }

    @SubscribeEvent public static void attack(AttackEntityEvent event) {
        if (deny(event.getEntity(), "taming.arksurvivalreturns.denied.attack")) event.setCanceled(true);
    }

    @SubscribeEvent public static void interact(PlayerInteractEvent.EntityInteract event) {
        if (deny(event.getEntity(), "taming.arksurvivalreturns.denied.interact")) {
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
        }
    }

    @SubscribeEvent public static void interactSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (deny(event.getEntity(), "taming.arksurvivalreturns.denied.interact")) {
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
        }
    }

    @SubscribeEvent public static void rightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (deny(event.getEntity(), "taming.arksurvivalreturns.denied.interact")) {
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
        }
    }

    @SubscribeEvent public static void rightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (deny(event.getEntity(), "taming.arksurvivalreturns.denied.interact")) {
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
        }
    }

    @SubscribeEvent public static void leftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (deny(event.getEntity(), "taming.arksurvivalreturns.denied.interact")) event.setCanceled(true);
    }

    @SubscribeEvent public static void useItem(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity() instanceof ServerPlayer player
                && deny(player, "taming.arksurvivalreturns.denied.use")) event.setCanceled(true);
    }

    @SubscribeEvent public static void toss(net.neoforged.neoforge.event.entity.item.ItemTossEvent event) {
        // Dropping is refused as well: the inventory stays intact for the player who is asleep.
        if (event.getPlayer() instanceof ServerPlayer player && TorporService.restricted(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent public static void mount(EntityMountEvent event) {
        if (!event.isMounting() || !(event.getEntityMounting() instanceof ServerPlayer player)) return;
        if (deny(player, "taming.arksurvivalreturns.denied.mount")) event.setCanceled(true);
    }

    /** Damage is allowed and pushes the player: it opens the window that lets the impulse play out. */
    @SubscribeEvent public static void damaged(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!TorporService.restricted(player)) return;
        TorporService.of(player).openImpulseWindow(Config.PLAYER_IMPULSE_TICKS.get());
    }

    /** Death clears sedation and the downed state for players; logout and login deliberately do not. */
    @SubscribeEvent public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TorporService.clear(player);
            player.getData(dev.nez.arksurvivalreturns.feature.recovery.RecoveryAttachments.DOWNED).clear();
            TamingService.onWakeWithoutCompletion(player);
        }
    }

    private static boolean deny(net.minecraft.world.entity.player.Player player, String key) {
        if (!(player instanceof ServerPlayer server)) return false;
        if (server.getData(dev.nez.arksurvivalreturns.feature.recovery.RecoveryAttachments.DOWNED).downed()) {
            server.sendSystemMessage(Component.translatable("downed.arksurvivalreturns.denied"), true);
            return true;
        }
        if (!TorporService.restricted(server)) return false;
        var state = TorporService.of(server);
        if (state.allowDenialMessage(server.tickCount)) {
            server.sendSystemMessage(Component.translatable("taming.arksurvivalreturns.unconscious",
                    Component.translatable(key)), true);
        }
        return true;
    }

    private PlayerUnconsciousHandler() {}
}
