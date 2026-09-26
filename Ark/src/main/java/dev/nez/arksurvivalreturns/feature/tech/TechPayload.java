package dev.nez.arksurvivalreturns.feature.tech;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Bounded read-only snapshot protocol. The client cannot grant or choose a team's progress. */
public record TechPayload(int request, String document) implements CustomPacketPayload {
    public static final Type<TechPayload> TYPE = new Type<>(ArkSurvivalReturns.id("tech_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TechPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeVarInt(p.request); buf.writeUtf(p.document, 65536); },
            buf -> new TechPayload(buf.readVarInt(), buf.readUtf(65536)));
    @Override public Type<TechPayload> type() { return TYPE; }

    public record Request(int request) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(ArkSurvivalReturns.id("tech_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> buf.writeVarInt(p.request), buf -> new Request(buf.readVarInt()));
        @Override public Type<Request> type() { return TYPE; }
    }
}
