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
    /** The downed player gives up (held Sneak for three seconds); the server checks they are downed. */
    public record GiveUp() implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
        public static final Type<GiveUp> TYPE = new Type<>(ArkSurvivalReturns.id("downed_give_up"));
        public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, GiveUp> STREAM_CODEC =
                net.minecraft.network.codec.StreamCodec.unit(new GiveUp());
        @Override public Type<GiveUp> type() { return TYPE; }
    }

    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(DownedPayload.TYPE, DownedPayload.STREAM_CODEC);
        event.registrar("1").playToServer(GiveUp.TYPE, GiveUp.STREAM_CODEC, (packet, context) -> {
            if (context.player() instanceof ServerPlayer player) DownedHandler.giveUp(player);
        });
    }

    public static void send(ServerPlayer player, DownedState state) {
        PacketDistributor.sendToPlayer(player, new DownedPayload(state.downed(), state.ticksLeft(), state.totalTicks()));
    }

    /** Call after the state is cleared: it also tells nearby clients the player is no longer downed. */
    public static void clear(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new DownedPayload(false, 0, 0));
        share(player);
    }

    /** Pushes the synced attachment to the player and everyone tracking them. */
    public static void share(ServerPlayer player) {
        player.syncData(RecoveryAttachments.DOWNED);
    }

    private DownedSync() {}
}
