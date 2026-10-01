package dev.nez.arksurvivalreturns.feature.station;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class WorkstationPayload {
    /** One bench per packet keeps the snapshot below the vanilla custom-payload limit. */
    public record Snapshot(String station, String json, boolean reset) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(ArkSurvivalReturns.id("workstation_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> STREAM_CODEC = StreamCodec.of(
                (b, p) -> { b.writeUtf(p.station, 128); b.writeUtf(p.json, 262144); b.writeBoolean(p.reset); },
                b -> new Snapshot(b.readUtf(128), b.readUtf(262144), b.readBoolean()));
        @Override public Type<Snapshot> type() { return TYPE; }
    }
    /** key and variant disambiguate recipes with the same output. containerId rejects stale clicks. */
    public record Craft(String station, String item, int times, String key, int variant, int containerId) implements CustomPacketPayload {
        public static final Type<Craft> TYPE = new Type<>(ArkSurvivalReturns.id("workstation_craft"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Craft> STREAM_CODEC = StreamCodec.of(
                (b, p) -> { b.writeUtf(p.station, 128); b.writeUtf(p.item, 256); b.writeVarInt(p.times); b.writeUtf(p.key, 512); b.writeVarInt(p.variant); b.writeVarInt(p.containerId); },
                b -> new Craft(b.readUtf(128), b.readUtf(256), b.readVarInt(), b.readUtf(512), b.readVarInt(), b.readVarInt()));
        @Override public Type<Craft> type() { return TYPE; }
    }
    public record Result(int containerId, String item, int count, boolean apply) implements CustomPacketPayload {
        public static final Type<Result> TYPE = new Type<>(ArkSurvivalReturns.id("workstation_result"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Result> STREAM_CODEC = StreamCodec.of(
                (b, p) -> { b.writeVarInt(p.containerId); b.writeUtf(p.item, 256); b.writeVarInt(p.count); b.writeBoolean(p.apply); },
                b -> new Result(b.readVarInt(), b.readUtf(256), b.readVarInt(), b.readBoolean()));
        @Override public Type<Result> type() { return TYPE; }
    }
    private WorkstationPayload() {}
}
