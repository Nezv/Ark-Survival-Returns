package dev.nez.arksurvivalreturns.feature.mass;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Player load HUD state: current mass, capacity, band ordinal and whether the gauge is shown. */
public record MassPayload(float mass, float capacity, int band, boolean visible) implements CustomPacketPayload {
    public static final Type<MassPayload> TYPE = new Type<>(ArkSurvivalReturns.id("mass_load"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MassPayload> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeFloat(value.mass());
                buf.writeFloat(value.capacity());
                buf.writeVarInt(value.band());
                buf.writeBoolean(value.visible());
            },
            buf -> new MassPayload(buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readBoolean()));
    @Override public Type<MassPayload> type() { return TYPE; }
}
