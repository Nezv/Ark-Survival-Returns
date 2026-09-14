package dev.nez.arksurvivalreturns.feature.map;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Only the server can send this entitlement and the saved difficulty profile. */
public record DangerMapPayload(boolean unlocked, int originX, int originZ, int bandWidth) implements CustomPacketPayload {
    public static final Type<DangerMapPayload> TYPE = new Type<>(ArkSurvivalReturns.id("danger_map"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DangerMapPayload> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> { buf.writeBoolean(value.unlocked); buf.writeInt(value.originX);
                buf.writeInt(value.originZ); buf.writeVarInt(value.bandWidth); },
            buf -> new DangerMapPayload(buf.readBoolean(), buf.readInt(), buf.readInt(), buf.readVarInt()));
    @Override public Type<DangerMapPayload> type() { return TYPE; }
}
