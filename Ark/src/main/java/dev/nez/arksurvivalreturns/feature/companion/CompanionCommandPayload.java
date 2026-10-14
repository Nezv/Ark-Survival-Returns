package dev.nez.arksurvivalreturns.feature.companion;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client request from the command wheel: one tame by entity id, or {@link #ALL} own tames within whistle range. */
public record CompanionCommandPayload(int entityId, CompanionCommand command) implements CustomPacketPayload {
    public static final int ALL = -1;
    public static final Type<CompanionCommandPayload> TYPE = new Type<>(ArkSurvivalReturns.id("companion_command"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CompanionCommandPayload> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeVarInt(value.entityId());
                buf.writeVarInt(value.command().ordinal());
            },
            buf -> new CompanionCommandPayload(buf.readVarInt(), CompanionCommand.byOrdinal(buf.readVarInt())));
    @Override public Type<CompanionCommandPayload> type() { return TYPE; }
}
