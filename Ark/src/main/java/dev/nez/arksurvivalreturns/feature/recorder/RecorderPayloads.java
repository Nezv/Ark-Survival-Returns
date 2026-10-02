package dev.nez.arksurvivalreturns.feature.recorder;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Signals of the session recorder: the player can move, a marked moment, and the recording's start and end. */
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

    /** Flags this moment in a running recording: the marker key, or a step of the scripted player. */
    public record Mark(String note) implements CustomPacketPayload {
        public static final Type<Mark> TYPE = new Type<>(ArkSurvivalReturns.id("recorder_mark"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Mark> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> buf.writeUtf(p.note, 64), buf -> new Mark(buf.readUtf(64)));
        @Override public Type<Mark> type() { return TYPE; }
    }

    /** Server to client: the recording started, or it ended and the file is closed (complete says nothing is missing). */
    public record Status(boolean recording, boolean complete, String detail) implements CustomPacketPayload {
        public static final Type<Status> TYPE = new Type<>(ArkSurvivalReturns.id("recorder_status"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Status> STREAM_CODEC = StreamCodec.of((buf, p) -> {
            buf.writeBoolean(p.recording); buf.writeBoolean(p.complete); buf.writeUtf(p.detail, 256);
        }, buf -> new Status(buf.readBoolean(), buf.readBoolean(), buf.readUtf(256)));
        @Override public Type<Status> type() { return TYPE; }
    }

    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(Ready.TYPE, Ready.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) SessionRecorder.clientReady(player, payload);
        });
        registrar.playToServer(Mark.TYPE, Mark.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) SessionRecorder.clientMark(player, payload.note());
        });
        registrar.playToClient(Status.TYPE, Status.STREAM_CODEC);
    }

    private RecorderPayloads() {}
}
