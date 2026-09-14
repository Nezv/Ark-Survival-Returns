package dev.nez.arksurvivalreturns.gametest;

import java.util.*;
import com.mojang.serialization.JsonOps;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.creature.*;
import dev.nez.arksurvivalreturns.feature.flying.*;
import dev.nez.arksurvivalreturns.feature.spawn.*;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;

/** Real blocks, loaded terrain, server authority, flight movement and contact damage. */
final class FlyingGameTests {
    static void ecology(GameTestHelper h) {
        var world=h.getLevel(); var data=HabitatData.get(world);
        for(int x=16;x<112;x++)for(int z=16;z<112;z++){h.setBlock(x,0,z,Blocks.STONE);h.setBlock(x,1,z,x<24?Blocks.WATER:Blocks.SAND);}
        var origin=h.absolutePos(new BlockPos(30,2,64));
        int chunks=world.getChunkSource().getLoadedChunksCount();
        h.assertTrue(FlyerHabitats.siteAllowed(world,Species.PTERANODON,origin),"Shoreline sand rejected");
        h.assertFalse(FlyerHabitats.siteAllowed(world,Species.PTERANODON,h.absolutePos(new BlockPos(90,2,64))),"Dry inland sand accepted");
        var inland=h.absolutePos(new BlockPos(90,2,64));
        world.setBlock(inland.below(),Blocks.GRASS_BLOCK.defaultBlockState(),3);
        h.assertFalse(FlyerHabitats.siteAllowed(world,Species.PTERANODON,inland),"Non-sand Pteranodon nest accepted");
        var peak=new BlockPos(origin.getX()+55,Config.ARGENT_NEST_Y.get()+1,origin.getZ());
        world.setBlock(peak.below(),Blocks.STONE.defaultBlockState(),3);
        h.assertTrue(FlyerHabitats.siteAllowed(world,Species.ARGENTAVIS,peak),"Argentavis minimum height rejected");
        world.removeBlock(peak.below(),false);world.setBlock(peak.below(2),Blocks.STONE.defaultBlockState(),3);
        h.assertFalse(FlyerHabitats.siteAllowed(world,Species.ARGENTAVIS,peak.below()),"Argentavis below height allowed");
        h.assertTrue(FlyerHabitats.spawn(world,origin,Species.PTERANODON,2,RandomSource.create(4)).isEmpty(),"Undersized colony created");
        var group=FlyerHabitats.spawn(world,origin,Species.PTERANODON,4,RandomSource.create(4));
        h.assertTrue(group.size()>=3 && group.size()<=4,"Natural colony not 3-4: "+group.size());
        var bird=(FlyingCreatureEntity)group.getFirst();var habitat=data.byId(bird.habitatId());
        h.assertTrue(habitat!=null && habitat.nests().size()==group.size(),"Nest count or saved colony missing");
        h.assertTrue(group.stream().allMatch(b -> b.packId().equals(habitat.id())),"Colony split into several population groups");
        h.assertTrue(FlyerHabitats.spawn(world,origin,Species.PTERANODON,4,RandomSource.create(5)).isEmpty(),"Full colony duplicated");
        var player=h.makeMockPlayer(GameType.SURVIVAL);player.setPos(Vec3.atBottomCenterOf(origin));world.addFreshEntity(player);
        var nest=habitat.nests().getFirst();
        h.assertTrue(NestBlock.takeEgg(world,nest,player),"Egg not collected");
        h.assertFalse(NestBlock.takeEgg(world,nest,player),"Empty nest duplicated egg");
        h.assertTrue(data.atNest(nest)!=null,"Egg removal deleted habitat record");
        h.assertTrue(player.getInventory().countItem(ModContent.NEST_EGGS.get(Species.PTERANODON).get())==1,"Wrong collectible egg inventory");
        data.discover(habitat,player.getUUID());
        var saved=HabitatData.CODEC.encodeStart(JsonOps.INSTANCE,data).getOrThrow();var restored=HabitatData.CODEC.parse(JsonOps.INSTANCE,saved).getOrThrow();
        h.assertTrue(restored.atNest(nest).id().equals(habitat.id()),"Habitat identity lost on reload");
        h.assertTrue(restored.discovered(player.getUUID(),origin,128).stream().anyMatch(a -> a.id().equals(habitat.id())),"Discovery lost on reload");
        h.assertTrue(restored.discovered(UUID.randomUUID(),origin,128).isEmpty(),"Discovery leaked to another player");
        for(var member:group) {
            var out=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,world.registryAccess());member.saveWithoutId(out);
            var copy=(FlyingCreatureEntity)ModContent.CREATURES.get(Species.PTERANODON).get().create(world,EntitySpawnReason.COMMAND);
            copy.load(TagValueInput.create(ProblemReporter.DISCARDING,world.registryAccess(),out.buildResult()));
            h.assertTrue(copy.habitatId().equals(habitat.id()) && copy.eggThief()==null,"Saved nest or cleared aggression incorrect");
            h.assertTrue(copy.creatureLevel()==member.creatureLevel() && copy.getHealth()==member.getHealth(),"Migration changed level/health");
        }
        var buf=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),world.registryAccess());
        try {
            var packet=new HabitatPayload(world.dimension().identifier().toString(),List.of(new HabitatPayload.Marker(habitat.id(),false,habitat.center())));
            HabitatPayload.STREAM_CODEC.encode(buf,packet);h.assertTrue(packet.equals(HabitatPayload.STREAM_CODEC.decode(buf)),"Habitat packet round trip failed");
            buf.clear();buf.writeUtf("minecraft:overworld",256);buf.writeVarInt(HabitatPayload.MAX_MARKERS+1);
            boolean rejected=false;try{HabitatPayload.STREAM_CODEC.decode(buf);}catch(IllegalArgumentException e){rejected=true;}
            h.assertTrue(rejected,"Unbounded habitat packet accepted");
        } finally {buf.release();}
        group.forEach(Entity::discard);player.discard();
        for(var pos:habitat.nests())world.removeBlock(pos,false);
        h.assertTrue(data.byId(habitat.id())==null,"Destroyed habitat kept its map marker");
        var unloaded=new BlockPos(25_000_000,100,25_000_000);
        h.assertTrue(FlyerHabitats.spawn(world,unloaded,Species.PTERANODON,4,RandomSource.create(1)).isEmpty(),"Spawned in unloaded terrain");
        h.assertTrue(world.getChunkSource().getLoadedChunksCount()==chunks,"Ecology forced chunk loads");
        h.succeed();
    }
    static void flight(GameTestHelper h,Species species) {
        var world=h.getLevel();var data=HabitatData.get(world);
        for(int x=16;x<112;x++)for(int z=16;z<112;z++){h.setBlock(x,0,z,Blocks.STONE);h.setBlock(x,1,z,Blocks.SAND);}
        var nest=h.absolutePos(new BlockPos(60,2,60));world.setBlock(nest,ModContent.NESTS.get(species).get().defaultBlockState(),3);
        var habitat=new HabitatData.Habitat(UUID.randomUUID(),species,nest,List.of(nest),Set.of());data.add(habitat);
        var bird=(FlyingCreatureEntity)ModContent.CREATURES.get(species).get().create(world,EntitySpawnReason.COMMAND);
        bird.setPersistenceRequired();bird.initializeLevel(1);bird.assignHabitat(habitat,nest);
        bird.setPos(Vec3.atBottomCenterOf(nest).add(0,8,0));world.addFreshEntity(bird);
        var player=h.makeMockPlayer(GameType.SURVIVAL);player.setPos(Vec3.atBottomCenterOf(nest.offset(8,0,0)));world.addFreshEntity(player);
        var bystander=h.makeMockPlayer(GameType.SURVIVAL);bystander.setPos(Vec3.atBottomCenterOf(nest.offset(-8,0,0)));world.addFreshEntity(bystander);
        double food=bird.wildlife().mind().hunger(),water=bird.wildlife().mind().thirst();
        for(int i=0;i<100;i++)bird.wildlife().think();
        h.assertTrue(bird.wildlife().mind().hunger()==food && bird.wildlife().mind().thirst()==water,"Flyer needs changed");
        bird.setTarget(player);h.assertFalse(bird.doHurtTarget(world,player),"External target authorized attack");
        bird.hurtServer(world,bird.damageSources().playerAttack(player),1);
        h.assertTrue(bird.eggThief()==null,"Direct damage authorized egg defense");
        Vec3 start=bird.position();
        h.runAfterDelay(20,() -> {
            h.assertTrue(bird.position().distanceToSqr(start)>0.5 && bird.getY()>nest.getY()+3,"Bird did not move through air");
            h.assertTrue(bird.getTarget()==null && player.getHealth()==20,"Neutral bird attacked player");
            // Controlled safe approach exercises real landing movement and perching without waiting a random timer.
            bird.setPos(Vec3.atBottomCenterOf(nest).add(0,2,0));bird.setDeltaMovement(Vec3.ZERO);
            h.assertTrue(bird.beginPerching(world),"Safe nest perch refused; nest="+bird.nestPosition()+" block="+world.getBlockState(nest)+" safe="+bird.safePerch(world));
        });
        h.runAfterDelay(75,() -> {
            h.assertTrue(bird.flightPhase()==FlyingCreatureEntity.Phase.PERCH,"Bird failed to land/perch: "+bird.flightPhase()+" y="+bird.getY());
            h.assertTrue(NestBlock.takeEgg(world,nest,player),"Theft failed");
            h.assertTrue(player.getUUID().equals(bird.eggThief()),"Wrong thief selected");
            h.assertFalse(bird.doHurtTarget(world,bystander),"Bystander accepted as attack target");
        });
        h.runAfterDelay(240,() -> {
            h.assertTrue(player.getHealth()<20,"Swoop never dealt real contact damage: "+species+" phase="+bird.flightPhase()+" bird="+bird.position()+" thief="+player.position());
            h.assertTrue(bystander.getHealth()==20,"Defense damaged bystander");
            player.setPos(Vec3.atBottomCenterOf(nest.offset(90,0,0)));
        });
        final float[] shelteredHealth = {20};
        h.runAfterDelay(245,() -> {
            h.assertTrue(bird.eggThief()==null && bird.getTarget()==null,"Leash did not clear defense");
            player.setPos(Vec3.atBottomCenterOf(nest.offset(8,0,0)));
            var shelter=nest.offset(8,0,0);
            for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)for(int y=0;y<=3;y++)
                if(Math.abs(x)==2 || Math.abs(z)==2 || y==3)world.setBlock(shelter.offset(x,y,z),Blocks.STONE.defaultBlockState(),3);
            bird.setPos(Vec3.atBottomCenterOf(nest).add(0,12,0));bird.setDeltaMovement(Vec3.ZERO);
            bird.defendEgg(player);shelteredHealth[0]=player.getHealth();
        });
        h.runAfterDelay(320,() -> {
            h.assertTrue(player.getHealth()==shelteredHealth[0],"Swoop damaged a sheltered thief through blocks");
            h.assertTrue(bird.eggThief()==null,"Occluded thief was tracked indefinitely");
            var shelter=nest.offset(8,0,0);
            for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)for(int y=0;y<=3;y++)
                if(Math.abs(x)==2 || Math.abs(z)==2 || y==3)world.removeBlock(shelter.offset(x,y,z),false);
            var creative=h.makeMockPlayer(GameType.CREATIVE);creative.setPos(player.position());
            bird.defendEgg(creative);h.assertTrue(bird.eggThief()==null,"Creative player provoked defense");
            var spectator=h.makeMockPlayer(GameType.SPECTATOR);spectator.setPos(player.position());
            bird.defendEgg(spectator);h.assertTrue(bird.eggThief()==null,"Spectator provoked defense");
            int old=Config.EGG_DEFENSE_TICKS.get();
            try {Config.EGG_DEFENSE_TICKS.set(100);bird.defendEgg(player);}finally{Config.EGG_DEFENSE_TICKS.set(old);}
            // Preserve live world time while suppressing movement so timeout is tested independently of a kill/leash.
            bird.setNoAi(true);
        });
        h.runAfterDelay(425,() -> {
            bird.tickFlight(world);
            h.assertTrue(bird.eggThief()==null && bird.getTarget()==null,"Egg defense outlived its timeout");
            var waterPos=nest.offset(20,0,0);world.setBlock(waterPos,Blocks.WATER.defaultBlockState(),3);
            bird.setPos(Vec3.atBottomCenterOf(waterPos));bird.setDeltaMovement(Vec3.ZERO);bird.setNoAi(false);
        });
        h.runAfterDelay(475,() -> {
            h.assertTrue(bird.getY()>nest.getY()+1,"Bird could not escape water; phase="+bird.flightPhase()+" y="+bird.getY()+" fluid="+bird.isInWater());
            bird.discard();player.discard();bystander.discard();world.removeBlock(nest,false);world.removeBlock(nest.offset(20,0,0),false);h.succeed();
        });
    }
    private FlyingGameTests() {}
}
