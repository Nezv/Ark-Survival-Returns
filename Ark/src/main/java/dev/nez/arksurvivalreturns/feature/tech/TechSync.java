package dev.nez.arksurvivalreturns.feature.tech;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.google.gson.Gson;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.Config;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class TechSync {
    private static final Map<UUID, Integer> LAST_REQUEST = new HashMap<>();
    private static final Gson GSON = new Gson();

    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(TechPayload.TYPE, TechPayload.STREAM_CODEC);
        registrar.playToServer(TechPayload.Request.TYPE, TechPayload.Request.STREAM_CODEC, (packet, context) -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            int now = player.level().getServer().getTickCount();
            Integer previous = LAST_REQUEST.get(player.getUUID());
            if (previous != null && now - previous < 10) return;
            LAST_REQUEST.put(player.getUUID(), now);
            var tree = TechTree.current();
            TechView view = TechView.empty();
            if (Config.TECH_ENABLED.get() && tree != null) {
                boolean linked = TechFtbBridge.pull(player);
                view = TechView.project(tree, TechProgressData.get(player.level()).progress(TechService.tribeOf(player)), linked);
            }
            PacketDistributor.sendToPlayer(player, new TechPayload(packet.request(), GSON.toJson(view)));
        });
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_REQUEST.remove(event.getEntity().getUUID());
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { LAST_REQUEST.clear(); }
    private TechSync() {}
}
