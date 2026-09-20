package dev.nez.arksurvivalreturns.feature.mass;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Server-to-client load HUD sync. */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class MassSync {
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(MassPayload.TYPE, MassPayload.STREAM_CODEC);
    }

    public static void send(ServerPlayer player, MassService.Load load, boolean mount, boolean visible) {
        if (player.isFakePlayer()) return;
        PacketDistributor.sendToPlayer(player,
                new MassPayload((float) load.mass(), (float) load.capacity(), load.band().ordinal(), visible, mount));
    }

    public static void clear(ServerPlayer player) {
        if (player.isFakePlayer()) return;
        PacketDistributor.sendToPlayer(player, new MassPayload(0f, 0f, MassRules.Band.NORMAL.ordinal(), false, false));
    }

    private MassSync() {}
}
