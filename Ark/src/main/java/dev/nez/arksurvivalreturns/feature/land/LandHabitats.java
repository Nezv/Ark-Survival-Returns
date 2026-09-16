package dev.nez.arksurvivalreturns.feature.land;

import java.util.*;
import dev.nez.arksurvivalreturns.*;
import dev.nez.arksurvivalreturns.feature.behavior.*;
import dev.nez.arksurvivalreturns.feature.creature.*;
import dev.nez.arksurvivalreturns.feature.spawn.*;
import dev.nez.arksurvivalreturns.feature.land.LandFamily;
import dev.nez.arksurvivalreturns.feature.aquatic.AquaticHabitats;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Group decisions, saved occupancy and bounded terrain work, independent of the flying controller. */
@EventBusSubscriber(modid=ArkSurvivalReturns.MOD_ID)
public final class LandHabitats {
    public record Site(BlockPos center, BlockPos water, BlockPos approach, double weight) {}
    private record Shore(BlockPos water, BlockPos approach) {}
    private static final class Budget { long tick=Long.MIN_VALUE; int paths, plans; }
    private static final Map<ServerLevel, Budget> BUDGETS = new WeakHashMap<>();
    public static boolean enabled(ServerLevel world) { return Config.LAND_HABITATS.get() && world.dimension()==Level.OVERWORLD; }
    public static int roam(Species s) { return Config.LAND_ROAM.get(s.family()).get(); }
    public static int leash(Species s) { return Math.max(roam(s), Config.LAND_LEASH.get(s.family()).get()); }
    public static double waterWeight(Species species, BlockPos center, BlockPos water) {
        var f=species.family(); return LandFamily.waterWeight(Math.sqrt(LandHabitatData.distanceSqr(center,water)),
                Config.LAND_WATER_PREFERRED.get(f).get(),Config.LAND_WATER_MAX.get(f).get());
    }
    public static boolean allowPath(ServerLevel world, boolean planning) {
        var budget=BUDGETS.computeIfAbsent(world,w -> new Budget());
        if(budget.tick!=world.getGameTime()){budget.tick=world.getGameTime();budget.paths=0;budget.plans=0;}
        if(budget.paths>=8 || planning&&budget.plans>=2) return false;
        budget.paths++; if(planning)budget.plans++; return true;
    }
    public static boolean navigationLoaded(ServerLevel world, CreatureEntity mob, double range) {
        return SpawnRules.loaded(world,new AABB(mob.blockPosition()).inflate(range+9));
    }
    public static Site plan(ServerLevel world, Species species, BlockPos origin) {
        var index=LandWaterIndex.get(world); int radius=Config.LAND_WATER_MAX.get(species.family()).get();
        index.request(world,origin,radius);
        for(var water:index.near(world,origin,radius)) {
            double weight=waterWeight(species,origin,water); if(weight<=0)continue;
            var shore=shore(world,species,water); if(shore==null)continue;
            var approach=shore.approach; water=shore.water;
            weight=waterWeight(species,origin,water); if(weight<=0)continue;
            var probe=ModContent.CREATURES.get(species).get().create(world,EntitySpawnReason.NATURAL);
            if(probe==null)return null; probe.setPos(Vec3.atBottomCenterOf(origin));probe.setOnGround(true);
            int length=(int)Math.ceil(Math.sqrt(origin.distSqr(approach)))+8;
            if(length>160 || !navigationLoaded(world,probe,length) || !allowPath(world,true))return null;
            probe.getNavigation().setRequiredPathLength(length);
            var path=probe.getNavigation().createPath(approach,1,length);
            if(path!=null&&path.canReach()) return new Site(origin.immutable(),water.immutable(),approach,weight);
        }
        // A frozen region has no exposed shore for most of the year. Cold-adapted species accept a
        // snow patch or a shallow ice layer over water as their hydration point instead of failing.
        return planCold(world, species, origin);
    }
    /**
     * Cold fallback site: dry supported ground with a snow or ice-over-water drink point nearby.
     * Warm species are rejected here, and this path never touches the shared surface-water index.
     */
    public static Site planCold(ServerLevel world, Species species, BlockPos origin) {
        if (!species.coldAdapted()) return null;
        int spread = Math.max(8, Config.LAND_ROAM.get(species.family()).get() / 2);
    for (int attempt = 0; attempt < 24; attempt++) {
        var pos = SpawnRules.placementSurface(world, species,
                origin.getX() + world.getRandom().nextInt(spread * 2 + 1) - spread,
                origin.getZ() + world.getRandom().nextInt(spread * 2 + 1) - spread);
        if (pos == null || Math.abs(pos.getY() - origin.getY()) > 8) continue;
        var point = hydrationNear(world, pos, 6);
        if (point == null) continue;
        var box = SpawnRules.bounds(species, pos);
        if (!SpawnRules.loaded(world, box.inflate(1)) || !world.noCollision(null, box, true)) continue;
        if (!allowPath(world, true)) return null;
        return new Site(pos.immutable(), point, pos.immutable(), 0.6);
    }
    return null;
}
/**
 * Cold hydration: snow cover, powder snow, or ice that a bounded check confirms sits over water.
 * Arbitrary packed-ice structures are never treated as a drink source. Null means unknown terrain.
 */
public static Boolean coldHydration(ServerLevel world, BlockPos pos) {
    if (world.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) return null;
    var state = world.getBlockState(pos);
    if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW)) return true;
    if (state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE))
        return world.getFluidState(pos.below()).is(FluidTags.WATER);
    return world.getFluidState(pos).is(FluidTags.WATER) && world.canSeeSky(pos.above());
}
private static BlockPos hydrationNear(ServerLevel world, BlockPos center, int radius) {
    for (int x = -radius; x <= radius; x += 3)
        for (int z = -radius; z <= radius; z += 3) {
            var pos = center.offset(x, 0, z);
            if (Boolean.TRUE.equals(coldHydration(world, pos))) return pos.immutable();
            // A snowfield or frozen river sits at the level below the standing position.
            var below = pos.below();
            if (Boolean.TRUE.equals(coldHydration(world, below))) return below.immutable();
            if (Boolean.TRUE.equals(coldHydration(world, pos.above()))) return pos.above().immutable();
        }
    return null;
}
    private static Shore shore(ServerLevel world, Species species, BlockPos sample) {
        for (var direction : Direction.Plane.HORIZONTAL) for (int shift=0;shift<=8;shift+=2) {
            var water=sample.relative(direction,shift);
            var verified=LandWaterIndex.get(world).observe(world,water); if(verified==null)return null; if(!verified)continue;
            var stand=approach(world,species,water,null);
            if(stand!=null)return new Shore(water,stand);
        }
        return null;
    }
    private static BlockPos approach(ServerLevel world, Species species, BlockPos water, CreatureEntity member) {
        int edge=(int)Math.ceil(species.width/2)+1;
        for(var direction:Direction.Plane.HORIZONTAL) {
            var candidate=water.relative(direction,edge);
            var pos=SpawnRules.placementSurface(world,species,candidate.getX(),candidate.getZ());
            if(pos==null||Math.abs(pos.getY()-water.getY()-1)>2||!world.isPositionEntityTicking(pos))continue;
            var box=SpawnRules.bounds(species,pos);
            if(SpawnRules.loaded(world,box.inflate(1))&&world.getWorldBorder().isWithinBounds(box)&&world.noCollision(member,box,true))return pos;
        }
        return null;
    }
    public static LandHabitatData.Habitat register(ServerLevel world, Site site, List<CreatureEntity> group, int capacity) {
        if(group.isEmpty())throw new IllegalArgumentException("An empty spawn cannot register a habitat");
        var first=group.getFirst();
        var h=new LandHabitatData.Habitat(first.packId(),first.species(),site.center,site.water,capacity,
                group.stream().map(Entity::getUUID).toList(),List.of(),group.stream().mapToDouble(c -> c.wildlife().mind().hunger()).average().orElse(0.55),0,true);
        LandHabitatData.get(world).add(h); group.forEach(c -> attach(world,h,c));
        h.nextCheck=world.getGameTime()+Config.LAND_RECHECK.get()+Math.floorMod(h.id.hashCode(),200);
        return h;
    }
    private static void attach(ServerLevel world, LandHabitatData.Habitat h, CreatureEntity mob) {
        if(h.species!=mob.species())return;
        if(h.members.add(mob.getUUID()))LandHabitatData.get(world).setDirty();
        h.loaded.put(mob.getUUID(),mob); mob.assignLandHabitat(h.id);
    }
    public static LandHabitatData.Habitat group(CreatureEntity mob) {
        if (!mob.species().landHabitat()||!(mob.level() instanceof ServerLevel world)||!enabled(world))return null;
        var h=LandHabitatData.get(world).byId(mob.packId());
        if(h!=null&&h.species==mob.species()) {attach(world,h,mob);return h;} return null;
    }
    public static LandHabitatData.Habitat think(CreatureEntity mob) {
        if(!(mob.level() instanceof ServerLevel world)||!mob.species().landHabitat()||!enabled(world))return null;
        var h=group(mob);
        if(h==null && mob.isNaturalWildlife() && Math.floorMod(mob.tickCount+mob.getId(),100)<10
                && Config.NATURAL_SPAWNS.get()&&world.getGameRules().get(GameRules.SPAWN_MOBS)) {
            var peers=world.getEntitiesOfClass(CreatureEntity.class,mob.getBoundingBox().inflate(64),
                    c -> c.isAlive()&&c.isNaturalWildlife()&&c.species()==mob.species()&&c.packId().equals(mob.packId()));
            if(!peers.isEmpty() && peers.stream().noneMatch(c -> c.getId()<mob.getId())) {
                var origin=SpawnRules.placementSurface(world,mob.species(),mob.wildlife().home().getX(),mob.wildlife().home().getZ());
                if(origin!=null) {var site=plan(world,mob.species(),origin); if(site!=null)h=register(world,site,peers,Math.max(peers.size(),mob.species().minGroup));}
            }
        }
        if(h==null)return null;
        var habitat=h;
        int foragers=(int)h.loaded.values().stream().filter(c -> c.isAlive() && world.isPositionEntityTicking(c.blockPosition())
                &&c.behavior()==BehaviorState.FORAGE&&forage(world,habitat.species,c.blockPosition())).count();
        boolean night=night(world,h);
        if(h.needs.advance(world.getGameTime(),h.species.predator&&night&&Config.NIGHTTIME.get()?Config.NIGHT_HUNGER.get():1,foragers,h.members.size())) {
            LandHabitatData.get(world).setDirty();
            boolean grazing=!h.species.predator&&(h.needs.hunger()>=0.4||h.routine==BehaviorState.FORAGE&&h.needs.hunger()>0.1);
            double fatigue=h.loaded.values().stream().filter(c -> c.isAlive()).mapToDouble(c -> c.wildlife().mind().fatigue()).average().orElse(0);
            boolean rest=fatigue>0.7 || h.routine==BehaviorState.REST && fatigue>0.1;
            var routine=h.needs.feeding()?BehaviorState.FEED:grazing?BehaviorState.FORAGE:rest?BehaviorState.REST:
                    h.species.predator&&(!Config.NIGHTTIME.get()||night)&&h.needs.hunger()>=0.4?BehaviorState.SEARCH:BehaviorState.ROAM;
            if(routine!=h.routine){h.routine=routine;h.nextPlan=0;}
            if(world.getGameTime()>=h.nextPlan) chooseDestination(world,h);
            revalidate(world,h);
        }
        // Invalid water can recover while occupied; no marker is erased because terrain is absent.
        return habitat;
    }
    /** Also called by population passes so vacant habitats can recover without a living member. */
    public static void revalidate(ServerLevel world, LandHabitatData.Habitat h) {
        if(world.getGameTime()<h.nextCheck)return;
        if(h.species.aquatic()) {AquaticHabitats.revalidate(world,h);return;}
        var result=h.species.coldAdapted()?coldHydration(world,h.water):LandWaterIndex.get(world).observe(world,h.water);
        // Unknown terrain/budget is not invalid terrain. Retry later without loading it.
        h.nextCheck=world.getGameTime()+(result==null?100:Config.LAND_RECHECK.get());
        if(result==null)return;
        h.valid=result;LandHabitatData.get(world).setDirty();
        if(result)return;
        Site site=plan(world,h.species,h.center);
        for(int i=0;i<4&&site==null;i++) {
            double angle=i*Math.PI/2;
            var pos=SpawnRules.placementSurface(world,h.species,h.center.getX()+(int)(Math.cos(angle)*32),h.center.getZ()+(int)(Math.sin(angle)*32));
            if(pos!=null)site=plan(world,h.species,pos);
        }
        if(site!=null)LandHabitatData.get(world).relocate(h,site.center,site.water);
    }
    public static boolean night(ServerLevel world, LandHabitatData.Habitat h) {
        return Config.NIGHTTIME.get()&&NighttimeCycle.individualNight(world.getDefaultClockTime(),h.id.getLeastSignificantBits(),
                Config.NIGHT_START.get(),Config.NIGHT_END.get(),Config.NIGHT_TRANSITION.get());
    }
    public static boolean forage(ServerLevel world, Species species, BlockPos pos) {
        if(world.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)==null)return false;
        var ground=world.getBlockState(pos.below());
        if(ground.is(Blocks.GRASS_BLOCK)||ground.is(Blocks.PODZOL)||ground.is(Blocks.MYCELIUM)||ground.is(Blocks.MOSS_BLOCK)||ground.is(Blocks.PALE_MOSS_BLOCK))return true;
        // Snow cover over browse ground is used as a browsing abstraction, never as terrain damage.
        if(species!=null&&species.coldAdapted())return ground.is(Blocks.SNOW)||ground.is(Blocks.SNOW_BLOCK)||ground.is(Blocks.POWDER_SNOW);
        return false;
    }
    public static boolean forage(ServerLevel world, BlockPos pos) { return forage(world, null, pos); }
    private static void chooseDestination(ServerLevel world, LandHabitatData.Habitat h) {
        h.nextPlan=world.getGameTime()+(h.routine==BehaviorState.SEARCH?60:100)+world.getRandom().nextInt(100);
        for(int attempt=0;attempt<4;attempt++) {
            double angle=world.getRandom().nextDouble()*Math.PI*2, radius=Math.sqrt(world.getRandom().nextDouble())*Math.max(4,roam(h.species)-h.species.width*2);
            var p=SpawnRules.placementSurface(world,h.species,h.center.getX()+(int)(Math.cos(angle)*radius),h.center.getZ()+(int)(Math.sin(angle)*radius));
            if(p==null||Math.abs(p.getY()-h.center.getY())>6||!world.isPositionEntityTicking(p)||h.routine==BehaviorState.FORAGE&&!forage(world,h.species,p))continue;
            var box=SpawnRules.bounds(h.species,p);if(!SpawnRules.loaded(world,box.inflate(1))||!world.getWorldBorder().isWithinBounds(box))continue;
            h.heading=angle;h.destination=Vec3.atBottomCenterOf(p);return;
        }
        if(h.destination==null)h.destination=Vec3.atBottomCenterOf(h.center);
    }
    public static Vec3 destination(ServerLevel world, LandHabitatData.Habitat h, CreatureEntity mob) {
        if(h.destination==null)return null;
        var members=h.members.stream().sorted().toList();int slot=Math.max(0,members.indexOf(mob.getUUID()));
        double spacing=mob.getBbWidth()+2;
        double side=(slot%3-1)*spacing, behind=(slot/3)*spacing;
        if(members.size()==1){side=0;behind=0;}
        double x=h.destination.x-Math.cos(h.heading)*behind-Math.sin(h.heading)*side;
        double z=h.destination.z-Math.sin(h.heading)*behind+Math.cos(h.heading)*side;
        double dx=x-h.center.getX()-0.5,dz=z-h.center.getZ()-0.5,d=Math.sqrt(dx*dx+dz*dz),limit=roam(h.species);
        if(d>limit){x=h.center.getX()+0.5+dx*limit/d;z=h.center.getZ()+0.5+dz*limit/d;}
        var p=SpawnRules.surface(world,(int)Math.floor(x),(int)Math.floor(z));
        if(p==null||Math.abs(p.getY()-mob.getY())>6)return null;
        var box=SpawnRules.bounds(mob.species(),p);
        return SpawnRules.loaded(world,box.inflate(1))&&world.getWorldBorder().isWithinBounds(box)&&world.noCollision(mob,box,true)?Vec3.atBottomCenterOf(p):null;
    }
    public static Vec3 drinkingDestination(ServerLevel world, LandHabitatData.Habitat h, CreatureEntity mob) {
        if(!h.valid)return null;var p=approach(world,mob.species(),h.water,mob);return p==null?null:Vec3.atBottomCenterOf(p);
    }
    public static boolean atWater(ServerLevel world, LandHabitatData.Habitat h, CreatureEntity mob) {
        if(!h.valid)return false;
        if(h.species.coldAdapted()) {
            // Snow or ice over water is the drink point; the animal browses it without breaking blocks.
            double reach=mob.getBbWidth()/2+3;
            if(LandHabitatData.distanceSqr(mob.blockPosition(),h.water)<=reach*reach
                    &&Math.abs(mob.getY()-h.water.getY())<=3)return true;
        }
        if(world.getChunkSource().getChunkNow(h.water.getX()>>4,h.water.getZ()>>4)==null)return false;
        if(!world.getBlockState(h.water).is(Blocks.WATER))return false;
        double edge=mob.getBbWidth()/2+2;
        return LandHabitatData.distanceSqr(mob.blockPosition(),h.water)<=edge*edge&&Math.abs(mob.getY()-h.water.getY()-1)<=2;
    }
    public static void feed(CreatureEntity mob, Entity victim) {
        var h=group(mob);if(h==null||!(mob.level() instanceof ServerLevel world))return;
        if(h.needs.feed(victim.getUUID(),world.getGameTime())) {
            h.loaded.values().forEach(c -> {c.wildlife().mind().ate();c.setTarget(null);});LandHabitatData.get(world).setDirty();
        }
    }
    public static void removed(CreatureEntity mob, Entity.RemovalReason reason) {
        if(!mob.species().landHabitat()||!(mob.level() instanceof ServerLevel world))return;
        var h=LandHabitatData.get(world).byId(mob.packId());if(h==null)return;
        h.loaded.remove(mob.getUUID());
        if(reason.shouldDestroy()&&h.members.remove(mob.getUUID())) {
            h.replacementAt=world.getGameTime()+(mob.species().aquatic()?Config.AQUATIC_REPOPULATE.get():Config.LAND_REPOPULATE.get());LandHabitatData.get(world).setDirty();
        }
    }
    @SubscribeEvent public static void tick(LevelTickEvent.Pre event) {
        if(event.getLevel() instanceof ServerLevel world&&enabled(world))LandWaterIndex.get(world).tick(world);
    }
    @SubscribeEvent public static void left(net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent event) {
        if(event.getEntity() instanceof CreatureEntity creature && creature.getRemovalReason()!=null) removed(creature,creature.getRemovalReason());
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { LandWaterIndex.clear();BUDGETS.clear(); }
    private LandHabitats() {}
}
