package dev.nez.arksurvivalreturns.feature.land;

import java.util.*;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Explicit species IDs preserve the flying protocol; no entity positions or needs are streamed. */
public record LandHabitatPayload(String dimension, List<Marker> markers) implements CustomPacketPayload {
    public static final int MAX_MARKERS=128;
    public record Marker(UUID id, Species species, BlockPos center, boolean valid, boolean occupied) {
        public Marker { if(species.flyer())throw new IllegalArgumentException("Flying land marker"); }
    }
    public LandHabitatPayload {
        if(markers.size()>MAX_MARKERS)throw new IllegalArgumentException("Too many land markers");
        var unique=new LinkedHashMap<UUID,Marker>();markers.forEach(m -> unique.putIfAbsent(m.id,m));markers=List.copyOf(unique.values());
    }
    public static final Type<LandHabitatPayload> TYPE=new Type<>(ArkSurvivalReturns.id("land_habitats"));
    public static final StreamCodec<RegistryFriendlyByteBuf,LandHabitatPayload> STREAM_CODEC=StreamCodec.of((buf,p) -> {
        buf.writeUtf(p.dimension,256);buf.writeVarInt(p.markers.size());
        for(var m:p.markers){buf.writeUUID(m.id);buf.writeUtf(m.species.id,64);buf.writeBlockPos(m.center);buf.writeBoolean(m.valid);buf.writeBoolean(m.occupied);}
    },buf -> {
        String dimension=buf.readUtf(256);int count=buf.readVarInt();
        if(count<0||count>MAX_MARKERS)throw new IllegalArgumentException("Invalid land marker count");
        var markers=new ArrayList<Marker>();
        for(int i=0;i<count;i++) {
            var id=buf.readUUID();String name=buf.readUtf(64);
            var species=Arrays.stream(Species.values()).filter(s -> !s.flyer()&&s.id.equals(name)).findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown land species"));
            markers.add(new Marker(id,species,buf.readBlockPos(),buf.readBoolean(),buf.readBoolean()));
        }
        return new LandHabitatPayload(dimension,markers);
    });
    @Override public Type<LandHabitatPayload> type(){return TYPE;}
}
