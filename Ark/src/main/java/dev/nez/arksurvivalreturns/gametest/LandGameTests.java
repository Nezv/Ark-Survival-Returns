package dev.nez.arksurvivalreturns.gametest;

import java.util.*;
import com.mojang.serialization.JsonOps;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.land.*;
import dev.nez.arksurvivalreturns.feature.creature.*;
import dev.nez.arksurvivalreturns.feature.spawn.*;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.Vec3;

final class LandGameTests {
    private static void terrain(GameTestHelper h) {
        for(int x=16;x<112;x++)for(int z=16;z<112;z++) {
            h.setBlock(x,0,z,Blocks.STONE);h.setBlock(x,1,z,x<48?Blocks.WATER:Blocks.GRASS_BLOCK);
        }
    }
    static void ecology(GameTestHelper h) {
        terrain(h);var world=h.getLevel();var data=LandHabitatData.get(world);
        var origin=h.absolutePos(new BlockPos(58,2,64));var water=h.absolutePos(new BlockPos(47,1,64));
        var oldProgression=ProgressionData.get(world);boolean enabled=Config.LAND_HABITATS.get();
        var entities=new ArrayList<CreatureEntity>();UUID habitatId=null;
        try {
            Config.LAND_HABITATS.set(true);
            world.getDataStorage().set(ProgressionData.TYPE,new ProgressionData(origin.getX()-512,origin.getZ(),256,true));
            int chunks=world.getChunkSource().getLoadedChunksCount();
            var index=LandWaterIndex.get(world);
            h.assertTrue(Boolean.TRUE.equals(index.observe(world,water)),"Connected surface river rejected");
            h.assertTrue(PopulationDirector.trySpawnGroup(world,origin,Species.VELOCIRAPTOR,3,RandomSource.create(2)).isEmpty(),"Partial new raptor group accepted");
            var group=PopulationDirector.trySpawnGroup(world,origin,Species.VELOCIRAPTOR,6,RandomSource.create(2));entities.addAll(group);
            h.assertTrue(group.size()>=4&&group.size()<=6,"Natural river raptor group failed: "+group.size());
            var first=group.getFirst();habitatId=first.packId();var habitat=data.byId(habitatId);
            h.assertTrue(habitat!=null&&habitat.capacity==group.size(),"Spawn not tied to one saved habitat");
            group.forEach(c -> c.setNoAi(true));
            h.assertTrue(group.stream().allMatch(c -> c.packId().equals(habitat.id)),"Group identities diverged");
            h.assertTrue(PopulationDirector.trySpawnGroup(world,origin,Species.VELOCIRAPTOR,6,RandomSource.create(4)).isEmpty(),"Occupied habitat duplicated");
            var pig=EntityTypes.PIG.create(world,EntitySpawnReason.COMMAND);
            LandHabitats.feed(first,pig);double fed=habitat.needs.hunger();LandHabitats.feed(group.getLast(),pig);
            h.assertTrue(fed==.05&&habitat.needs.hunger()==fed,"Shared kill feeding multiplied or failed");
            for(var member:group)member.wildlife().think();
            h.assertTrue(group.stream().allMatch(c -> Math.abs(c.wildlife().mind().hunger()-habitat.needs.hunger())<1e-10),"Member satiation diverged");
            var player=UUID.randomUUID();data.discover(habitat,player);
            var json=LandHabitatData.CODEC.encodeStart(JsonOps.INSTANCE,data).getOrThrow();
            var restored=LandHabitatData.CODEC.parse(JsonOps.INSTANCE,json).getOrThrow();
            h.assertTrue(restored.byId(habitat.id).members.equals(habitat.members),"Saved occupancy lost unloaded reservations");
            h.assertTrue(restored.discovered(player,origin,128).stream().anyMatch(a -> a.id.equals(habitat.id)),"Discovery lost on reload");
            h.assertTrue(restored.discovered(UUID.randomUUID(),origin,128).isEmpty(),"Discovery leaked between players");
            var out=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,world.registryAccess());first.saveWithoutId(out);
            var copy=ModContent.CREATURES.get(first.species()).get().create(world,EntitySpawnReason.COMMAND);
            copy.load(TagValueInput.create(ProblemReporter.DISCARDING,world.registryAccess(),out.buildResult()));
            h.assertTrue(copy.packId().equals(habitat.id)&&copy.creatureLevel()==first.creatureLevel()&&copy.getHealth()==first.getHealth(),"Entity migration changed identity/level/HP");
            var marker=new LandHabitatPayload.Marker(habitat.id,habitat.species,habitat.center,true,true);
            var packet=new LandHabitatPayload(world.dimension().identifier().toString(),List.of(marker,marker));
            h.assertTrue(packet.markers().size()==1,"Duplicate map ID accepted");
            var buf=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),world.registryAccess());
            try {
                LandHabitatPayload.STREAM_CODEC.encode(buf,packet);h.assertTrue(packet.equals(LandHabitatPayload.STREAM_CODEC.decode(buf)),"Land payload roundtrip failed");
                buf.clear();buf.writeUtf(packet.dimension(),256);buf.writeVarInt(129);boolean rejected=false;
                try{LandHabitatPayload.STREAM_CODEC.decode(buf);}catch(IllegalArgumentException e){rejected=true;}
                h.assertTrue(rejected,"Oversized land snapshot accepted");
            }finally{buf.release();}
            int reserved=habitat.members.size();first.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            h.assertTrue(habitat.members.size()==reserved&&!habitat.loaded.containsKey(first.getUUID()),"Unload freed capacity or retained entity reference");
            h.assertTrue(PopulationDirector.tryReplenishHabitat(world,habitat,6,RandomSource.create(3)).isEmpty(),"Unloaded member was replaced");
            group.get(1).discard();h.assertTrue(habitat.members.size()==reserved-1,"Permanent removal did not free capacity");
            h.assertTrue(PopulationDirector.tryReplenishHabitat(world,habitat,6,RandomSource.create(3)).isEmpty(),"Replacement ignored cooldown");
            habitat.replacementAt=world.getGameTime();
            var repair=PopulationDirector.tryReplenishHabitat(world,habitat,6,RandomSource.create(3));entities.addAll(repair);
            h.assertTrue(repair.size()==1&&habitat.members.size()==reserved,"Partial herd replenishment lost occupancy: "+repair.size());
            h.assertTrue(PopulationDirector.trySpawnGroup(world,h.absolutePos(new BlockPos(105,2,64)),Species.TRICERATOPS,4,RandomSource.create(8)).isEmpty(),"Dry land habitat escaped water exclusion");
            var far=new BlockPos(25_000_000,100,25_000_000);
            h.assertTrue(LandHabitats.plan(world,Species.TRICERATOPS,far)==null,"Unloaded water search produced a site");
            h.assertTrue(index.probesThisTick()<=Config.LAND_PROBES.get(),"Water work exceeded dimension budget");
            h.assertTrue(world.getChunkSource().getLoadedChunksCount()==chunks,"Land ecology forced chunk loads");
            data.remove(habitat.id);h.assertTrue(data.discovered(player,origin,128).stream().noneMatch(a -> a.id.equals(habitat.id)),"Removed habitat kept marker");
            h.succeed();
        } finally {
            entities.forEach(Entity::discard);if(habitatId!=null)data.remove(habitatId);
            Config.LAND_HABITATS.set(enabled);world.getDataStorage().set(ProgressionData.TYPE,oldProgression);
        }
    }
    static void movement(GameTestHelper h) {
        terrain(h);var world=h.getLevel();var group=new ArrayList<CreatureEntity>();
        var center=h.absolutePos(new BlockPos(64,2,64));var water=h.absolutePos(new BlockPos(47,1,64));
        LandWaterIndex.get(world).request(world,center,48);
        for(int delay: new int[]{100,200,300})h.runAfterDelay(delay,() -> LandWaterIndex.get(world).request(world,center,48));
        UUID id=UUID.randomUUID();
        for(int i=0;i<2;i++){
            var mob=ModContent.CREATURES.get(Species.TRICERATOPS).get().create(world,EntitySpawnReason.COMMAND);
            mob.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(60,2,60+i*8))));mob.setPersistenceRequired();mob.assignLandHabitat(id);
            mob.wildlife().mind().restoreNeeds(.1,.1,.1);mob.wildlife().mind().interruptSleep(1200);world.addFreshEntity(mob);group.add(mob);
        }
        var habitat=LandHabitats.register(world,new LandHabitats.Site(center,water,water.east(4).above(),1),group,2);
        habitat.destination=Vec3.atBottomCenterOf(center.east(20));habitat.heading=0;habitat.nextPlan=world.getGameTime()+500;
        var starts=group.stream().map(Entity::position).toList();
        h.runAfterDelay(400,() -> {
            try {
                for(int i=0;i<group.size();i++)h.assertTrue(group.get(i).getX()>starts.get(i).x+2,"Member did not follow shared eastward destination: "+i+" "+group.get(i).position());
                h.assertTrue(group.stream().allMatch(c -> c.wildlife().home().equals(center)),"Moving group moved its habitat anchor");
                h.assertTrue(group.getFirst().distanceToSqr(group.getLast())>1,"Group formation collapsed into one point");
                h.assertTrue(!LandWaterIndex.get(world).near(world,center,48).isEmpty(),"Incremental water scanner found no river");
                group.forEach(Entity::discard);habitat.valid=false;habitat.nextCheck=0;
                PopulationDirector.tryReplenishHabitat(world,habitat,4,RandomSource.create(1));
                h.assertTrue(habitat.valid&&habitat.members.isEmpty(),"Vacant habitat did not recover water validity or bypassed cooldown");
                h.succeed();
            }finally{group.forEach(Entity::discard);LandHabitatData.get(world).remove(habitat.id);}
        });
    }
    private LandGameTests() {}
}
