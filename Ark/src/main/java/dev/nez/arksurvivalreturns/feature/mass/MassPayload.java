package dev.nez.arksurvivalreturns.feature.mass;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Load HUD state: mass, capacity, band ordinal, gauge visibility and whether the mount bar is shown. */
public record MassPayload(float mass, float capacity, int band, boolean visible, boolean mount) implements CustomPacketPayload {
    public static final Type<MassPayload> TYPE = new Type<>(ArkSurvivalReturns.id("mass_load"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MassPayload> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeFloat(value.mass());
                buf.writeFloat(value.capacity());
                buf.writeVarInt(value.band());
                buf.writeBoolean(value.visible());
                buf.writeBoolean(value.mount());
            },
            buf -> new MassPayload(buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean()));
    @Override public Type<MassPayload> type() { return TYPE; }
}
