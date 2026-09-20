package dev.nez.arksurvivalreturns.feature.mass;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Dirty hooks for carried mass. Every way a player's carried items can change marks the player, and
 * the service recomputes once on the following tick; an open container menu is rechecked once a
 * second so shift-click moves are caught without a per-tick inventory scan.
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class MassEvents {
    @SubscribeEvent public static void pickup(ItemEntityPickupEvent.Post event) { mark(event.getPlayer()); }
    @SubscribeEvent public static void toss(ItemTossEvent event) { mark(event.getPlayer()); }
    @SubscribeEvent public static void crafted(PlayerEvent.ItemCraftedEvent event) { mark(event.getEntity()); }
    @SubscribeEvent public static void smelted(PlayerEvent.ItemSmeltedEvent event) { mark(event.getEntity()); }
    @SubscribeEvent public static void equipment(LivingEquipmentChangeEvent event) {
        if (event.getEntity() instanceof Player player) mark(player);
    }
    @SubscribeEvent public static void containerClosed(PlayerContainerEvent.Close event) { mark(event.getEntity()); }
    @SubscribeEvent public static void joined(PlayerEvent.PlayerLoggedInEvent event) { mark(event.getEntity()); }
    @SubscribeEvent public static void respawned(PlayerEvent.PlayerRespawnEvent event) { mark(event.getEntity()); }
    @SubscribeEvent public static void cloned(PlayerEvent.Clone event) { mark(event.getEntity()); }
    @SubscribeEvent public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) { mark(event.getEntity()); }
    @SubscribeEvent public static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) MassService.clear(player);
    }
    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) { MassService.tick(event.getServer()); }

    private static void mark(Player player) {
        if (player instanceof ServerPlayer serverPlayer) MassService.markDirty(serverPlayer);
    }

    private MassEvents() {}
}
