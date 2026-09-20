package dev.nez.arksurvivalreturns.feature.cargo;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client request to bulk-transfer between one tame's cargo and nearby storage. */
public record CargoTransferPayload(int entityId, boolean load) implements CustomPacketPayload {
    public static final Type<CargoTransferPayload> TYPE = new Type<>(ArkSurvivalReturns.id("cargo_transfer"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CargoTransferPayload> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeVarInt(value.entityId());
                buf.writeBoolean(value.load());
            },
            buf -> new CargoTransferPayload(buf.readVarInt(), buf.readBoolean()));
    @Override public Type<CargoTransferPayload> type() { return TYPE; }
}
