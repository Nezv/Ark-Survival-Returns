package dev.nez.arksurvivalreturns.feature.recovery;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Server-to-client downed HUD sync. */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class DownedSync {
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(DownedPayload.TYPE, DownedPayload.STREAM_CODEC);
    }

    public static void send(ServerPlayer player, DownedState state) {
        PacketDistributor.sendToPlayer(player, new DownedPayload(state.downed(), state.ticksLeft(), state.totalTicks()));
    }

    public static void clear(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new DownedPayload(false, 0, 0));
    }

    private DownedSync() {}
}
