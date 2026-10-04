package dev.nez.arksurvivalreturns.gametest;

import java.util.*;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.land.*;
import dev.nez.arksurvivalreturns.feature.creature.*;
import dev.nez.arksurvivalreturns.feature.spawn.*;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Danger-gated ground spawning and pack movement without saved habitats. */
final class LandGameTests {
    private static void terrain(GameTestHelper h) {
        for (int x = 16; x < 112; x++) for (int z = 16; z < 112; z++) {
            h.setBlock(x, 0, z, Blocks.STONE);
            h.setBlock(x, 1, z, x < 48 ? Blocks.WATER : Blocks.GRASS_BLOCK);
        }
    }
    static void ecology(GameTestHelper h) {
        terrain(h);
        var world = h.getLevel();
        var origin = h.absolutePos(new BlockPos(70, 2, 64));
        var oldProgression = ProgressionData.get(world);
        int chunks = world.getChunkSource().getLoadedChunksCount();
        try {
            world.getDataStorage().set(ProgressionData.TYPE, new ProgressionData(origin.getX() - 512, origin.getZ(), 256, true));
            // A grazer: a hunter's placement also depends on the hour (it needs canopy while its kind sleeps).
            h.assertTrue(SpawnRules.canSpawn(ModContent.CREATURES.get(Species.PARASAUR).get(), world,
                    EntitySpawnReason.NATURAL, origin, RandomSource.create(2)), "Valid bank spawn rejected");
            h.assertFalse(SpawnRules.speciesAllowed(Species.TYRANNOSAURUS, world.getBiome(origin), 1), "Apex allowed at danger 1");
            // Feeding ground: grass and soil to graze, sand to root in; bare rock holds nothing.
            h.assertTrue(LandWildlife.forage(world, Species.PARASAUR, origin), "Grass is not grazing ground");
            h.setBlock(72, 1, 64, Blocks.SAND);
            h.assertTrue(LandWildlife.forage(world, Species.LYSTROSAURUS, h.absolutePos(new BlockPos(72, 2, 64))), "Sand is not feeding ground");
            h.setBlock(74, 1, 64, Blocks.STONE);
            h.assertFalse(LandWildlife.forage(world, Species.PARASAUR, h.absolutePos(new BlockPos(74, 2, 64))), "Bare rock is feeding ground");
            // Snow browsing is a cold-adapted abstraction, never a warm-species one.
            h.setBlock(64, 1, 64, Blocks.SNOW_BLOCK);
            var browse = h.absolutePos(new BlockPos(64, 2, 64));
            h.assertTrue(LandWildlife.forage(world, Species.MAMMOTH, browse), "Cold browser cannot use snow cover");
            h.assertFalse(LandWildlife.forage(world, Species.PARASAUR, browse), "Warm species browsed snow");
            h.assertTrue(Boolean.TRUE.equals(LandWildlife.coldHydration(world, h.absolutePos(new BlockPos(64, 1, 64)))),
                    "Snow block hydration rejected");
            h.assertTrue(LandWildlife.leash(Species.VELOCIRAPTOR) >= LandWildlife.roam(Species.VELOCIRAPTOR),
                    "Return radius below roam radius");
            h.assertTrue(world.getChunkSource().getLoadedChunksCount() == chunks, "Land ecology forced chunk loads");
        } finally {
            world.getDataStorage().set(ProgressionData.TYPE, oldProgression);
        }
        h.succeed();
    }
    /**
     * A timid grazer beside a hunter: it ignores one that sleeps or keeps far off, watches one that roams
     * nearer, and bolts only from one inside its flight distance. A real session had grazers fleeing, walking
     * back and fleeing again every three seconds beside a sleeping pack.
     */
    static void vigilance(GameTestHelper h) {
        terrain(h);
        var world = h.getLevel();
        var clock = world.dimensionType().defaultClock().orElseThrow();
        long originalTime = world.getDefaultClockTime();
        world.clockManager().setTotalTicks(clock, 6000);
        var hunter = ModContent.CREATURES.get(Species.DILOPHOSAUR).get().create(world, EntitySpawnReason.COMMAND);
        hunter.setNoAi(true);
        world.addFreshEntity(hunter);
        var spawned = new ArrayList<Entity>(List.of(hunter));
        // A fresh grazer per case, facing the hunter down the z axis, fed and rested so only the hunter moves it.
        java.util.function.BiFunction<Integer, dev.nez.arksurvivalreturns.feature.behavior.BehaviorState, CreatureEntity> grazer = (gap, hunting) -> {
            hunter.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(64, 2, 54 + gap))));
            hunter.setBehavior(hunting);
            var mob = ModContent.CREATURES.get(Species.PARASAUR).get().create(world, EntitySpawnReason.COMMAND);
            mob.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(64, 2, 54))));
            mob.setYRot(0); mob.yBodyRot = 0; mob.yHeadRot = 0; mob.setOnGround(true);
            mob.wildlife().mind().restoreNeeds(.05, .05, .05);
            world.addFreshEntity(mob);
            spawned.add(mob);
            return mob;
        };
        var roam = dev.nez.arksurvivalreturns.feature.behavior.BehaviorState.ROAM;
        var flee = dev.nez.arksurvivalreturns.feature.behavior.BehaviorState.FLEE;
        try {
            var beside = grazer.apply(12, dev.nez.arksurvivalreturns.feature.behavior.BehaviorState.SLEEP);
            for (int i = 0; i < 20; i++) {
                beside.wildlife().think();
                h.assertFalse(beside.behavior().alarm(), "A grazer was alarmed by a sleeping hunter 12 blocks off: " + beside.behavior());
            }
            beside.discard();
            var far = grazer.apply(32, roam);
            for (int i = 0; i < 20; i++) {
                far.wildlife().think();
                h.assertFalse(far.behavior().alarm(), "A grazer was alarmed by a hunter 32 blocks off: " + far.behavior());
            }
            far.discard();
            var watching = grazer.apply(22, roam);
            int changes = 0, watched = 0;
            var last = watching.behavior();
            for (int i = 0; i < 40; i++) {
                watching.wildlife().think();
                h.assertTrue(watching.behavior() != flee, "A grazer fled from a hunter that kept its distance");
                if (watching.behavior() == dev.nez.arksurvivalreturns.feature.behavior.BehaviorState.ALERT) watched++;
                if (watching.behavior() != last) { changes++; last = watching.behavior(); }
            }
            h.assertTrue(watched >= 12, "A grazer did not watch a hunter roaming 22 blocks off: " + watched + " of 40 decisions");
            // Habituation: a hunter that keeps its distance is not stared at all afternoon.
            h.assertFalse(watching.behavior().alarm(), "Still on alert after 20 seconds of a hunter keeping its distance: " + watching.behavior());
            h.assertTrue(changes <= 3, "The watching grazer changed state " + changes + " times in 20 seconds");
            // The hunter comes on: the interest is back at once, and inside the flight distance the grazer bolts.
            hunter.setPos(watching.position().add(0, 0, 9));
            for (int i = 0; i < 6; i++) watching.wildlife().think();
            h.assertTrue(watching.behavior() == flee, "A grazer let a hunter it had grown used to walk up to it: " + watching.behavior());
            watching.discard();
            var pressed = grazer.apply(10, roam);
            for (int i = 0; i < 6; i++) pressed.wildlife().think();
            h.assertTrue(pressed.behavior() == flee, "A grazer did not bolt from a hunter 10 blocks off: " + pressed.behavior());
        } finally {
            world.clockManager().setTotalTicks(clock, originalTime);
            spawned.forEach(Entity::discard);
        }
        h.succeed();
    }
    static void movement(GameTestHelper h) {
        terrain(h);
        var world = h.getLevel();
        var group = new ArrayList<CreatureEntity>();
        var center = h.absolutePos(new BlockPos(64, 2, 64));
        var difficulty = world.getCurrentDifficultyAt(center);
        SpawnGroupData pack = null;
        for (int i = 0; i < 2; i++) {
            var mob = ModContent.CREATURES.get(Species.TRICERATOPS).get().create(world, EntitySpawnReason.NATURAL);
            pack = mob.finalizeSpawn(world, difficulty, EntitySpawnReason.NATURAL, pack);
            mob.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(60, 2, 60 + i * 4))));
            mob.setPersistenceRequired();
            mob.wildlife().mind().restoreNeeds(.1, .1, .1);
            mob.wildlife().mind().interruptSleep(1200);
            world.addFreshEntity(mob);
            group.add(mob);
        }
        h.assertTrue(group.get(0).packId().equals(group.get(1).packId()), "Vanilla spawn cluster split the pack");
        // A displaced member keeps the saved home and turns back instead of following the leader.
        var stray = group.getLast();
        var own = stray.wildlife().home();
        stray.setPos(Vec3.atBottomCenterOf(own).add(200, 0, 0));
        for (int i = 0; i < 3; i++) stray.wildlife().think();
        h.assertTrue(stray.behavior() == dev.nez.arksurvivalreturns.feature.behavior.BehaviorState.RETURN_HOME,
                "Displaced pack member did not return home: " + stray.behavior());
        h.assertTrue(LandWildlife.distanceSqr(group.getFirst().wildlife().home(), stray.wildlife().home()) < 100,
                "Pack members lost their shared home area");
        group.forEach(Entity::discard);
        h.succeed();
    }
    private LandGameTests() {}
}
