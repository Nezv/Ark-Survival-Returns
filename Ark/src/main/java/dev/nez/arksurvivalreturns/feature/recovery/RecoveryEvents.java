package dev.nez.arksurvivalreturns.feature.recovery;

import java.util.List;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.Config;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Turns a player's death drops into a recovery cache instead of loose items. */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class RecoveryEvents {
    @SubscribeEvent public static void drops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!Config.RECOVERY_ENABLED.get()) return;
        List<ItemStack> stacks = event.getDrops().stream()
                .map(ItemEntity::getItem).filter(stack -> !stack.isEmpty()).map(ItemStack::copy).toList();
        if (stacks.isEmpty()) return;
        if (RecoveryService.create(player, stacks) != null) event.setCanceled(true);
    }

    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) RecoveryService.remind(player);
    }

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) RecoveryService.remind(player);
    }

    private RecoveryEvents() {}
}
