package dev.nez.arksurvivalreturns.feature.recorder;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Client signals for the session recorder: the player can move, and the marker key. */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class RecorderPayloads {
    /** Sent once per join when the loading screens are gone, with the client settings the server cannot see. */
    public record Ready(int renderDistance, int simulationDistance, String shaders, boolean distantHorizons, int fps,
            int guiScale, String window) implements CustomPacketPayload {
        public static final Type<Ready> TYPE = new Type<>(ArkSurvivalReturns.id("recorder_ready"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Ready> STREAM_CODEC = StreamCodec.of((buf, p) -> {
            buf.writeVarInt(p.renderDistance); buf.writeVarInt(p.simulationDistance); buf.writeUtf(p.shaders, 128);
            buf.writeBoolean(p.distantHorizons); buf.writeVarInt(p.fps); buf.writeVarInt(p.guiScale); buf.writeUtf(p.window, 32);
        }, buf -> new Ready(buf.readVarInt(), buf.readVarInt(), buf.readUtf(128), buf.readBoolean(), buf.readVarInt(),
                buf.readVarInt(), buf.readUtf(32)));
        @Override public Type<Ready> type() { return TYPE; }
    }

    /** The marker key: flags this moment in a running recording. */
    public record Mark() implements CustomPacketPayload {
        public static final Type<Mark> TYPE = new Type<>(ArkSurvivalReturns.id("recorder_mark"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Mark> STREAM_CODEC = StreamCodec.of((buf, p) -> {}, buf -> new Mark());
        @Override public Type<Mark> type() { return TYPE; }
    }

    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(Ready.TYPE, Ready.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) SessionRecorder.clientReady(player, payload);
        });
        registrar.playToServer(Mark.TYPE, Mark.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) SessionRecorder.clientMark(player);
        });
    }

    private RecorderPayloads() {}
}
