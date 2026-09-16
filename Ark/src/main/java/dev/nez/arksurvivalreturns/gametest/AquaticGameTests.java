package dev.nez.arksurvivalreturns.gametest;

import java.util.ArrayList;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.aquatic.AquaticHabitats;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.feature.land.LandHabitatData;
import dev.nez.arksurvivalreturns.feature.spawn.PopulationDirector;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

/**
 * Water-bound and semi-aquatic behavior: pool validation, saved occupancy, status, water
 * containment and shared pack identity.
 */
final class AquaticGameTests {
    static void ecology(GameTestHelper h) {
        var world = h.getLevel();
        var entities = new ArrayList<CreatureEntity>();
        boolean enabled = Config.AQUATIC_HABITATS.get();
        int chunks = world.getChunkSource().getLoadedChunksCount();
        java.util.UUID habitatId = null;
        try {
            Config.AQUATIC_HABITATS.set(true);
            // A 40x40 pool, six blocks deep, with a stone floor and open sky above it.
            for (int x = 24; x < 64; x++) for (int z = 24; z < 64; z++) {
                h.setBlock(x, 0, z, Blocks.STONE);
                for (int y = 1; y <= 6; y++) h.setBlock(x, y, z, Blocks.WATER);
                h.setBlock(x, 7, z, Blocks.AIR);
            }
            var middle = h.absolutePos(new BlockPos(44, 6, 44));
            var surface = AquaticHabitats.surfaceWater(world, middle.getX(), middle.getZ());
            h.assertTrue(surface != null && surface.getY() == middle.getY(), "Pool surface not recognised: " + surface);
            h.assertTrue(AquaticHabitats.depth(world, surface) >= 6, "Pool depth not measured");
            h.assertTrue(AquaticHabitats.siteAllowed(world, Species.MEGALODON, surface), "Deep pool rejected a large resident");

            // A single water block far from the pool is never a home pool.
            h.setBlock(8, 1, 100, Blocks.STONE);
            h.setBlock(8, 2, 100, Blocks.WATER);
            var puddlePos = h.absolutePos(new BlockPos(8, 2, 100));
            var puddle = AquaticHabitats.surfaceWater(world, puddlePos.getX(), puddlePos.getZ());
            h.assertTrue(puddle != null, "Puddle not detected");
            h.assertFalse(AquaticHabitats.siteAllowed(world, Species.PLESIOSAUR, puddle), "Shallow puddle accepted as a home pool");
            h.assertTrue(AquaticHabitats.plan(world, Species.PLESIOSAUR, puddle) == null, "Puddle planned as a pool");

            var group = PopulationDirector.trySpawnGroup(world, surface, Species.PLESIOSAUR, 4, RandomSource.create(11));
            entities.addAll(group);
            h.assertTrue(group.size() == 1, "Solo resident count mismatch: " + group.size());
            var resident = group.getFirst();
            var habitat = LandHabitatData.get(world).byId(resident.packId());
            h.assertTrue(habitat != null && habitat.species == Species.PLESIOSAUR, "Pool not saved");
            habitatId = habitat.id;
            h.assertTrue(habitat.capacity == 1 && habitat.members.size() == 1, "Saved occupancy mismatch");
            h.assertTrue(habitat.center.getY() >= surface.getY() - 1, "Saved pool center is not at the surface");

            resident.initializeLevel(24);
            resident.wildlife().mind().restoreNeeds(0.8, 0.1, 0.1);
            for (int i = 0; i < 12; i++) { resident.tickCount += 10; resident.wildlife().think(); }
            h.assertTrue(resident.behavior() != null, "No status reported for a resident");
            h.assertTrue(resident.wildlife().home().equals(habitat.center), "Resident home is not its pool");

            // Night raises the same active flag the land predators use, and prey is hunted at night.
            for (int i = 0; i < 80; i++) resident.tick();
            h.assertTrue(resident.getY() <= surface.getY(), "Resident left the water column: " + resident.getY());
            double distance = Math.hypot(resident.getX() - habitat.center.getX(), resident.getZ() - habitat.center.getZ());
            h.assertTrue(distance <= habitat.radius + 8, "Resident left its pool: " + distance);

            var out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, world.registryAccess());
            resident.saveWithoutId(out);
            var restored = ModContent.CREATURES.get(Species.PLESIOSAUR).get().create(world, EntitySpawnReason.COMMAND);
            restored.load(TagValueInput.create(ProblemReporter.DISCARDING, world.registryAccess(), out.buildResult()));
            h.assertTrue(restored.wildlife().home().equals(resident.wildlife().home()), "Pool home lost on reload");
            h.assertTrue(restored.wildlife().mind().hunger() == resident.wildlife().mind().hunger(), "Pool needs lost on reload");
            h.assertTrue(restored.getHealth() == resident.getHealth() && restored.creatureLevel() == resident.creatureLevel(), "Pool reload changed stats");
            restored.discard();

            // Semi-aquatic pack settings: one saved shoreline home shared by 2-3 crocodilians.
            h.assertFalse(Species.SARCO.solitary(), "Sarco lost its bask group");
            h.assertTrue(Species.SARCO.minGroup == 2 && Species.SARCO.maxGroup == 3, "Sarco pack size changed");
            h.assertTrue(Species.SARCO.swimmer() && !Species.SARCO.aquatic(), "Sarco realm changed");
            h.assertTrue(!Species.SARCO.swimWalk().equals(Species.SARCO.walk), "Sarco has no separate swim cycle");
            h.assertTrue(Species.SARCO.landHabitat() && Species.SARCO.sleeps(), "Sarco lost its land routine");
            h.assertTrue(Species.MEGALODON.aquatic() && !Species.MEGALODON.landHabitat() && !Species.MEGALODON.sleeps(),
                    "Water-bound species must not keep a ground routine");
            h.assertTrue(world.getChunkSource().getLoadedChunksCount() == chunks, "Aquatic behavior loaded chunks");
        } finally {
            entities.forEach(Entity::discard);
            if (habitatId != null) LandHabitatData.get(world).remove(habitatId);
            Config.AQUATIC_HABITATS.set(enabled);
        }
        h.succeed();
    }
    private AquaticGameTests() {}
}
