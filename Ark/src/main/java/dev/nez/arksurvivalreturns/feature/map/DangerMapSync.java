package dev.nez.arksurvivalreturns.feature.map;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.spawn.ProgressionData;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class DangerMapSync {
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(DangerMapPayload.TYPE, DangerMapPayload.STREAM_CODEC);
    }
    /** The map is open to everyone (BUG-16: the top-zone unlock is gone); the client gets the danger profile. */
    public static void send(ServerPlayer player) {
        var profile = ProgressionData.get(player.level());
        PacketDistributor.sendToPlayer(player, new DangerMapPayload(true, profile.originX(), profile.originZ(), profile.bandWidth()));
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) send(p);
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) send(p);
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) send(p);
    }
    private DangerMapSync() {}
}
