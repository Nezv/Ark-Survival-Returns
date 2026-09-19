package dev.nez.arksurvivalreturns.feature.flying;

import java.util.*;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Bounded server snapshot: one coordinate per discovered colony, never per bird. */
public record HabitatPayload(String dimension, List<Marker> markers) implements CustomPacketPayload {
    public static final int MAX_MARKERS = 128;
    public record Marker(UUID id, Species species, BlockPos center) {
        public Marker { if (!species.flyer()) throw new IllegalArgumentException("Non-flying nest marker"); }
    }
    public HabitatPayload { markers = List.copyOf(markers); if (markers.size() > MAX_MARKERS) throw new IllegalArgumentException("Too many habitat markers"); }
    public static final Type<HabitatPayload> TYPE = new Type<>(ArkSurvivalReturns.id("flying_habitats"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HabitatPayload> STREAM_CODEC = StreamCodec.of((buf,p) -> {
        buf.writeUtf(p.dimension,256); buf.writeVarInt(p.markers.size());
        for (var m:p.markers) { buf.writeUUID(m.id); buf.writeUtf(m.species.id,64); buf.writeBlockPos(m.center); }
    },buf -> {
        String dim = buf.readUtf(256); int count = buf.readVarInt();
        if (count < 0 || count > MAX_MARKERS) throw new IllegalArgumentException("Invalid habitat marker count");
        var list = new ArrayList<Marker>(count); var ids = new HashSet<UUID>();
        for (int i=0;i<count;i++) {
            var id = buf.readUUID(); String name = buf.readUtf(64);
            var species = Arrays.stream(Species.values()).filter(s -> s.flyer() && s.id.equals(name)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown flying species"));
            var m = new Marker(id,species,buf.readBlockPos());
            if (ids.add(m.id)) list.add(m);
        }
        return new HabitatPayload(dim,list);
    });
    @Override public Type<HabitatPayload> type() { return TYPE; }
}
