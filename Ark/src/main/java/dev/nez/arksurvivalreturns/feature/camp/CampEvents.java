package dev.nez.arksurvivalreturns.feature.camp;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** First-join camp setup. */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class CampEvents {
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) StarterKitService.onLogin(player);
    }

    private CampEvents() {}
}
