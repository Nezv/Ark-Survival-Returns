package dev.nez.arksurvivalreturns.feature.debug;

import java.util.*;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record DinoDebugPayload(UUID target, String dimension, String name, int page, int pages, List<String> lines) implements CustomPacketPayload {
    public static final UUID NO_TARGET = new UUID(0, 0);
    public static final Type<DinoDebugPayload> TYPE = new Type<>(ArkSurvivalReturns.id("dino_debug"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DinoDebugPayload> STREAM_CODEC = StreamCodec.of((buf, p) -> {
        buf.writeUUID(p.target); buf.writeUtf(p.dimension, 256); buf.writeUtf(p.name, 128);
        buf.writeVarInt(p.page); buf.writeVarInt(p.pages); buf.writeVarInt(p.lines.size());
        for (String line : p.lines) buf.writeUtf(line, 128);
    }, buf -> {
        UUID target = buf.readUUID(); String dimension = buf.readUtf(256), name = buf.readUtf(128);
        int page = buf.readVarInt(), pages = buf.readVarInt(), count = buf.readVarInt();
        if (count < 0 || count > DinoDebugSnapshot.PAGE_LINES || pages < 1 || pages > DinoDebugSnapshot.MAX_LINES
                || page < 0 || page >= pages) throw new IllegalArgumentException("Invalid dinosaur debug page");
        var lines = new ArrayList<String>();
        for (int i = 0; i < count; i++) lines.add(buf.readUtf(128));
        return new DinoDebugPayload(target, dimension, name, page, pages, lines);
    });
    public DinoDebugPayload { lines = List.copyOf(lines); }
    public static DinoDebugPayload empty(String dimension) { return new DinoDebugPayload(NO_TARGET, dimension, "", 0, 1, List.of()); }
    @Override public Type<DinoDebugPayload> type() { return TYPE; }

    public record TurnPage(UUID target, int direction) implements CustomPacketPayload {
        public static final Type<TurnPage> TYPE = new Type<>(ArkSurvivalReturns.id("dino_debug_page"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TurnPage> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeUUID(p.target); buf.writeInt(p.direction); }, buf -> new TurnPage(buf.readUUID(), buf.readInt()));
        @Override public Type<TurnPage> type() { return TYPE; }
    }
}
