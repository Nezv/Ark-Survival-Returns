package dev.nez.arksurvivalreturns.feature.recovery;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Downed HUD state, sent only to the downed player. */
public record DownedPayload(boolean downed, int ticksLeft, int totalTicks) implements CustomPacketPayload {
    public static final Type<DownedPayload> TYPE = new Type<>(ArkSurvivalReturns.id("downed"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DownedPayload> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> { buf.writeBoolean(value.downed); buf.writeVarInt(value.ticksLeft); buf.writeVarInt(value.totalTicks); },
            buf -> new DownedPayload(buf.readBoolean(), buf.readVarInt(), buf.readVarInt()));
    @Override public Type<DownedPayload> type() { return TYPE; }
}
