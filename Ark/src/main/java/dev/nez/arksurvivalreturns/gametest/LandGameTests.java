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
            h.assertTrue(SpawnRules.canSpawn(ModContent.CREATURES.get(Species.VELOCIRAPTOR).get(), world,
                    EntitySpawnReason.NATURAL, origin, RandomSource.create(2)), "Valid forest raptor spawn rejected");
            h.assertFalse(SpawnRules.speciesAllowed(Species.TYRANNOSAURUS, world.getBiome(origin), 1), "Apex allowed at danger 1");
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
