package dev.nez.arksurvivalreturns.gametest;

import dev.nez.arksurvivalreturns.feature.aquatic.Water;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.feature.spawn.ProgressionData;
import dev.nez.arksurvivalreturns.feature.spawn.SpawnRules;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/** Water-bound spawning and containment without saved home pools. */
final class AquaticGameTests {
    static void ecology(GameTestHelper h) {
        var world = h.getLevel();
        var oldProgression = ProgressionData.get(world);
        int chunks = world.getChunkSource().getLoadedChunksCount();
        CreatureEntity resident = null;
        try {
            // A 40x40 pool, six blocks deep, with a stone floor and open sky above it.
            for (int x = 24; x < 64; x++) for (int z = 24; z < 64; z++) {
                h.setBlock(x, 0, z, Blocks.STONE);
                for (int y = 1; y <= 6; y++) h.setBlock(x, y, z, Blocks.WATER);
                h.setBlock(x, 7, z, Blocks.AIR);
            }
            var middle = h.absolutePos(new BlockPos(44, 6, 44));
            world.getDataStorage().set(ProgressionData.TYPE, new ProgressionData(middle.getX() - 512, middle.getZ(), 256, true));
            var surface = Water.surfaceWater(world, middle.getX(), middle.getZ());
            h.assertTrue(surface != null && surface.getY() == middle.getY(), "Pool surface not recognised: " + surface);
            h.assertTrue(Water.depth(world, surface) >= 6, "Pool depth not measured");
            h.assertTrue(Water.siteAllowed(world, Species.MEGALODON, surface), "Deep pool rejected a large resident");
            h.assertTrue(SpawnRules.canSpawnWater(ModContent.CREATURES.get(Species.MEGALODON).get(), world,
                    EntitySpawnReason.NATURAL, middle, RandomSource.create(11)), "Deep pool rejected by the spawn rule");

            // A single water block far from the pool is never a resident home.
            h.setBlock(8, 1, 100, Blocks.STONE);
            h.setBlock(8, 2, 100, Blocks.WATER);
            var puddlePos = h.absolutePos(new BlockPos(8, 2, 100));
            var puddle = Water.surfaceWater(world, puddlePos.getX(), puddlePos.getZ());
            h.assertTrue(puddle != null, "Puddle not detected");
            h.assertFalse(Water.siteAllowed(world, Species.PLESIOSAUR, puddle), "Shallow puddle accepted as a home");
            h.assertFalse(SpawnRules.canSpawnWater(ModContent.CREATURES.get(Species.PLESIOSAUR).get(), world,
                    EntitySpawnReason.NATURAL, puddle, RandomSource.create(3)), "Shallow puddle accepted by the spawn rule");

            resident = ModContent.CREATURES.get(Species.PLESIOSAUR).get().create(world, EntitySpawnReason.NATURAL);
            resident.finalizeSpawn(world, world.getCurrentDifficultyAt(middle), EntitySpawnReason.NATURAL, null);
            resident.setPos(Vec3.atCenterOf(middle.below(2)));
            resident.setPersistenceRequired();
            world.addFreshEntity(resident);
            resident.wildlife().mind().restoreNeeds(0.8, 0.1, 0.1);
            for (int i = 0; i < 12; i++) { resident.tickCount += 10; resident.wildlife().think(); }
            h.assertTrue(resident.behavior() != null, "No status reported for a resident");
            for (int i = 0; i < 80; i++) resident.tick();
            h.assertTrue(resident.getY() <= surface.getY(), "Resident left the water column: " + resident.getY());

            var out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, world.registryAccess());
            resident.saveWithoutId(out);
            var restored = ModContent.CREATURES.get(Species.PLESIOSAUR).get().create(world, EntitySpawnReason.COMMAND);
            restored.load(TagValueInput.create(ProblemReporter.DISCARDING, world.registryAccess(), out.buildResult()));
            h.assertTrue(restored.wildlife().home().equals(resident.wildlife().home()), "Water home lost on reload");
            h.assertTrue(restored.wildlife().mind().hunger() == resident.wildlife().mind().hunger(), "Needs lost on reload");
            h.assertTrue(restored.getHealth() == resident.getHealth() && restored.creatureLevel() == resident.creatureLevel(), "Reload changed stats");
            restored.discard();

            h.assertTrue(Species.MEGALODON.aquatic() && !Species.MEGALODON.landHabitat() && !Species.MEGALODON.sleeps(),
                    "Water-bound species must not keep a ground routine");
            h.assertTrue(world.getChunkSource().getLoadedChunksCount() == chunks, "Aquatic behavior loaded chunks");
        } finally {
            if (resident != null) resident.discard();
            world.getDataStorage().set(ProgressionData.TYPE, oldProgression);
        }
        h.succeed();
    }
    private AquaticGameTests() {}
}
