package dev.nez.arksurvivalreturns.gametest;

import java.util.ArrayList;
import java.util.UUID;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.behavior.*;
import dev.nez.arksurvivalreturns.feature.creature.*;
import dev.nez.arksurvivalreturns.registry.ModContent;
import dev.nez.arksurvivalreturns.feature.spawn.TreeShelter;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.Vec3;

final class NighttimeGameTests {
    static void run(GameTestHelper h) {
        var world = h.getLevel();
        var clock = world.dimensionType().defaultClock().orElseThrow();
        long oldTime = world.getDefaultClockTime();
        double sleepShare = Config.DAY_SLEEP.get(); int transition = Config.NIGHT_TRANSITION.get();
        var entities = new ArrayList<Entity>();
        try {
            Config.DAY_SLEEP.set(1.0); Config.NIGHT_TRANSITION.set(0);
            for (int x = 16; x < 112; x++) for (int z = 16; z < 112; z++) h.setBlock(x, 1, z, Blocks.GRASS_BLOCK);
            int chunks = world.getChunkSource().getLoadedChunksCount();
            world.clockManager().setTotalTicks(clock, 6000);
            shelterRules(h, entities);
            biome(h, Biomes.FOREST);
            canopy(h, 40, 40, Blocks.OAK_LEAVES);
            var rex = create(h, Species.TYRANNOSAURUS, 40, 40); entities.add(rex);
            var pig = EntityType.PIG.create(world, EntitySpawnReason.COMMAND);
            pig.setNoAi(true); pig.setPos(rex.position().add(0, 0, 20)); world.addFreshEntity(pig); entities.add(pig);
            rex.wildlife().mind().restoreNeeds(0.8, 0.1, 0);
            for (int i = 0; i < 8; i++) rex.wildlife().think();
            h.assertTrue(rex.behavior() == BehaviorState.SLEEP && rex.getTarget() == null, "Day Rex hunted instead of sleeping");
            h.assertFalse(rex.nightActive(), "Day eyes enabled");
            var player = h.makeMockPlayer(GameType.SURVIVAL);
            player.setPos(rex.position().add(0, 0, 20)); world.addFreshEntity(player); entities.add(player);
            rex.wildlife().think();
            h.assertTrue(rex.behavior() == BehaviorState.SLEEP, "Distant player prevented daytime sleep");
            var crowd = new ArrayList<Entity>();
            for (int i = 0; i < 30; i++) {
                var animal = EntityType.PIG.create(world, EntitySpawnReason.COMMAND);
                animal.setNoAi(true); animal.setPos(rex.position().add(i % 5 * 0.1, 0, 2));
                world.addFreshEntity(animal); crowd.add(animal); entities.add(animal);
            }
            player.setPos(rex.position().add(0, 0, 9));
            rex.wildlife().think();
            h.assertFalse(rex.behavior().sleeping(), "Nearby player failed to wake Rex");
            h.assertTrue(rex.getTarget() == null, "Wake skipped warning and attacked");
            crowd.forEach(Entity::discard);
            player.discard(); pig.discard();
            for (int i = 0; i < 25; i++) rex.wildlife().think();
            h.assertTrue(rex.behavior() == BehaviorState.SLEEP, "Rex did not settle after calm delay");
            rex.hurtServer(world, rex.damageSources().generic(), 1);
            h.assertFalse(rex.behavior().sleeping(), "Damage did not wake immediately");
            var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, world.registryAccess());
            rex.saveWithoutId(output);
            var restored = ModContent.CREATURES.get(Species.TYRANNOSAURUS).get().create(world, EntitySpawnReason.COMMAND);
            restored.setNoAi(true); entities.add(restored); // Deserialize off-world to avoid duplicate UUID registration.
            restored.load(TagValueInput.create(ProblemReporter.DISCARDING, world.registryAccess(), output.buildResult()));
            h.assertTrue(restored.wildlife().mind().calmTicksRemaining() > 0, "Reload lost wake cooldown");
            h.assertTrue(restored.getHealth() == rex.getHealth() && restored.creatureLevel() == rex.creatureLevel(), "Reload changed HP or level");
            h.assertTrue(restored.wildlife().mind().hunger() == rex.wildlife().mind().hunger(), "Reload changed hunger");
            restored.discard(); rex.discard();

            world.clockManager().setTotalTicks(clock, 18000);
            rex = create(h, Species.TYRANNOSAURUS, 40, 40); entities.add(rex);
            rex.wildlife().mind().restoreNeeds(0.8, 0.1, 0);
            pig = EntityType.PIG.create(world, EntitySpawnReason.COMMAND); pig.setNoAi(true);
            double range = world.isRaining() ? 45 : 56;
            pig.setPos(rex.position().add(0, 0, range)); world.addFreshEntity(pig); entities.add(pig);
            h.assertTrue(Math.abs(WildlifeSenses.sightRange(rex) - 62.4) < 0.001, "Rex night range is not 1.3x daytime");
            h.assertTrue(WildlifeSenses.detect(rex, pig).visible(), "Expanded night vision failed beyond daytime range");
            for (int i = 0; i < 8; i++) rex.wildlife().think();
            h.assertTrue(rex.getTarget() == pig && rex.behavior() == BehaviorState.HUNT, "Night candidate search clipped enhanced range");
            h.assertTrue(rex.nightActive(), "Night eye state not synchronized");
            double hunger = rex.wildlife().mind().hunger();
            world.clockManager().setTotalTicks(clock, 6000); rex.wildlife().think();
            h.assertTrue(rex.wildlife().mind().hunger() - hunger < 0.001, "Clock skip applied catch-up hunger");
            h.assertFalse(rex.nightActive() || rex.behavior().sleeping(), "Dawn did not clear glow or slept during encounter");
            rex.discard(); pig.discard();

            world.clockManager().setTotalTicks(clock, 18000);
            var trike = create(h, Species.TRICERATOPS, 40, 40); entities.add(trike);
            trike.wildlife().think();
            h.assertTrue(trike.behavior() == BehaviorState.SLEEP, "Unthreatened night herbivore did not sleep");
            rex = create(h, Species.TYRANNOSAURUS, 40, 60); entities.add(rex);
            for (int i = 0; i < 4; i++) trike.wildlife().think();
            h.assertTrue(trike.behavior() == BehaviorState.FLEE, "Lone herbivore failed to flee a sensed predator");
            h.assertTrue(trike.getTarget() == null, "Fleeing herbivore retained a combat target");
            var partner = create(h, Species.TRICERATOPS, 48, 40); entities.add(partner);
            var difficulty = world.getCurrentDifficultyAt(trike.blockPosition());
            var herd = trike.finalizeSpawn(world, difficulty, EntitySpawnReason.COMMAND, null);
            partner.finalizeSpawn(world, difficulty, EntitySpawnReason.COMMAND, herd);
            for (int i = 0; i < 8; i++) trike.wildlife().think();
            h.assertTrue(trike.behavior() == BehaviorState.DEFEND, "Healthy night herd did not stand after initial flight");
            trike.setHealth(trike.getMaxHealth() * 0.3f); trike.wildlife().think();
            h.assertTrue(trike.behavior() == BehaviorState.FLEE, "Injured night herd member did not resume flight");
            var home = trike.position(); trike.setPos(home.add(24, 0, 0));
            rex.discard();
            for (int i = 0; i < 35; i++) trike.wildlife().think();
            h.assertTrue(trike.behavior() == BehaviorState.REGROUP, "Separated herbivore slept before regrouping: " + trike.behavior());
            trike.setPos(home); trike.wildlife().think();
            h.assertTrue(trike.behavior() == BehaviorState.SLEEP, "Herbivore failed to settle after threat disappeared");
            trike.discard(); partner.discard();

            for (var species : new Species[]{Species.PTERANODON, Species.ARGENTAVIS}) {
                var bird = create(h, species, 40, 40); entities.add(bird);
                h.assertFalse(WildlifeSenses.hasNightCycle(bird), "Night patch enrolled a flying species");
                double food = bird.wildlife().mind().hunger(); bird.wildlife().think();
                h.assertTrue(bird.behavior() != BehaviorState.SLEEP && !bird.nightActive(), "Bird received new sleep/glow state");
                h.assertTrue(bird.wildlife().mind().hunger() - food <= 10 / 24000.0 + 1e-10, "Bird received night hunger boost");
                double expected = (world.isBrightOutside() ? 1 : 0.7) * 32;
                h.assertTrue(Math.abs(WildlifeSenses.sightRange(bird) - expected) < 0.001, "Bird vision changed");
                bird.discard();
            }
            var nether = world.getServer().getLevel(net.minecraft.world.level.Level.NETHER);
            var timeless = ModContent.CREATURES.get(Species.TYRANNOSAURUS).get().create(nether, EntitySpawnReason.COMMAND);
            h.assertFalse(WildlifeSenses.hasNightCycle(timeless), "Fixed-sky dimension received a night schedule");
            h.assertTrue(chunks == world.getChunkSource().getLoadedChunksCount(), "Nighttime behavior loaded additional chunks");
            if (Boolean.getBoolean("arksurvivalreturns.benchmarkNighttime")) NighttimeLoadProbe.run(h);
        } finally {
            entities.forEach(Entity::discard);
            Config.DAY_SLEEP.set(sleepShare); Config.NIGHT_TRANSITION.set(transition);
            world.clockManager().setTotalTicks(clock, oldTime);
        }
        h.succeed();
    }
    /** The same canopy rule must survive spawn selection, full AI and both cheaper tiers. */
    private static void shelterRules(GameTestHelper h, ArrayList<Entity> entities) {
        var world = h.getLevel();
        var feet = h.absolutePos(new BlockPos(40, 2, 40));
        var clock = world.dimensionType().defaultClock().orElseThrow();
        boolean tiers = Config.BEHAVIOR_TIERS.get();
        try {
            biome(h, Biomes.PLAINS);
            h.assertFalse(TreeShelter.spawnAllowed(world, Species.TYRANNOSAURUS, feet), "Morning Rex spawned in plains");
            canopy(h, 40, 40, Blocks.OAK_LEAVES);
            h.assertFalse(TreeShelter.sheltered(world, Species.TYRANNOSAURUS, feet), "An isolated plains tree bypassed biome policy");
            biome(h, Biomes.FOREST);
            h.assertTrue(TreeShelter.spawnAllowed(world, Species.TYRANNOSAURUS, feet), "Covered forest spawn refused");
            canopy(h, 40, 40, Blocks.STONE);
            h.assertFalse(TreeShelter.sheltered(world, Species.TYRANNOSAURUS, feet), "Stone roof counted as trees");
            canopy(h, 40, 40, Blocks.AIR);
            h.assertFalse(TreeShelter.spawnAllowed(world, Species.TYRANNOSAURUS, feet), "Forest clearing accepted as shelter");
            world.clockManager().setTotalTicks(clock, 18000);
            biome(h, Biomes.PLAINS);
            h.assertTrue(TreeShelter.spawnAllowed(world, Species.TYRANNOSAURUS, feet), "Active-night plains spawn was blocked");
            world.clockManager().setTotalTicks(clock, 6000);
            biome(h, Biomes.FOREST);
            var rex = create(h, Species.TYRANNOSAURUS, 40, 40); entities.add(rex);
            rex.wildlife().mind().restoreNeeds(.1, .1, .95);
            rex.wildlife().think();
            h.assertFalse(rex.behavior().sleeping(), "Fatigued Rex lay in a forest clearing");
            // A new controller has no search cooldown: first candidate is eight blocks east with UUID offset zero.
            rex.discard();
            canopy(h, 48, 40, Blocks.OAK_LEAVES);
            rex = create(h, Species.TYRANNOSAURUS, 40, 40); entities.add(rex);
            rex.wildlife().think();
            h.assertFalse(rex.behavior().sleeping(), "Rex slept before reaching canopy");
            rex.wildlife().tick();
            h.assertTrue(!rex.getNavigation().isDone() || rex.getMoveControl().hasWanted(), "Rex did not seek nearby tree cover");
            canopy(h, 48, 40, Blocks.AIR);
            canopy(h, 40, 40, Blocks.OAK_LEAVES);
            for (int i = 0; i <= Config.SLEEP_CALM.get() / 10 + 2; i++) rex.wildlife().think();
            h.assertTrue(rex.behavior().sleeping(), "Covered Rex failed to sleep after settling");
            canopy(h, 40, 40, Blocks.AIR);
            rex.wildlife().think();
            h.assertFalse(rex.behavior().sleeping(), "Canopy loss left full-detail Rex asleep");
            rex.discard();
            Config.BEHAVIOR_TIERS.set(true);
            for (var tier : new BehaviorTier[]{BehaviorTier.AMBIENT, BehaviorTier.DORMANT}) {
                rex = create(h, Species.TYRANNOSAURUS, 40, 40); entities.add(rex);
                double distance = tier == BehaviorTier.AMBIENT
                        ? (Config.TIER_FULL_RADIUS.get() + Config.TIER_AMBIENT_RADIUS.get()) / 2.0
                        : (Config.TIER_AMBIENT_RADIUS.get() + Config.TIER_DORMANT_RADIUS.get()) / 2.0;
                BehaviorLod.useTestObservers(java.util.List.of(rex.position().add(distance, 0, 0)));
                rex.refreshBehaviorTier();
                h.assertTrue(rex.behaviorTier() == tier, "Failed to enter shelter-test tier " + tier);
                rex.tickCount = 100 - Math.floorMod(rex.getId(), 100);
                canopy(h, 40, 40, Blocks.OAK_LEAVES);
                rex.wildlife().tick();
                h.assertTrue(rex.behavior().sleeping(), tier + " refused valid canopy");
                canopy(h, 40, 40, Blocks.AIR);
                rex.tickCount += 100;
                rex.wildlife().tick();
                h.assertFalse(rex.behavior().sleeping(), tier + " retained sleep after canopy removal");
                rex.discard();
            }
        } finally {
            BehaviorLod.useTestObservers(null);
            Config.BEHAVIOR_TIERS.set(tiers);
        }
    }

    private static void biome(GameTestHelper h, net.minecraft.resources.ResourceKey<net.minecraft.world.level.biome.Biome> biome) {
        var result = net.minecraft.server.commands.FillBiomeCommand.fill(h.getLevel(),
                h.absolutePos(new BlockPos(24, 0, 24)), h.absolutePos(new BlockPos(64, 10, 64)),
                h.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME).getOrThrow(biome));
        h.assertTrue(result.right().isEmpty(), "Local shelter biome fixture failed: " + result.right());
    }

    private static void canopy(GameTestHelper h, int centerX, int centerZ, net.minecraft.world.level.block.Block block) {
        for (int x = centerX - 5; x <= centerX + 5; x++)
            for (int z = centerZ - 5; z <= centerZ + 5; z++) h.setBlock(x, 18, z, block);
    }

    private static CreatureEntity create(GameTestHelper h, Species species, int x, int z) {
        var entity = ModContent.CREATURES.get(species).get().create(h.getLevel(), EntitySpawnReason.COMMAND);
        entity.setUUID(new UUID(UUID.randomUUID().getMostSignificantBits(), 0));
        entity.setNoAi(true); entity.setOnGround(true); entity.initializeLevel(40);
        entity.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(x, 2, z))));
        entity.yBodyRot = 0; entity.setYRot(0); entity.yHeadRot = 0;
        entity.wildlife().mind().restoreNeeds(0.1, 0.1, 0);
        h.getLevel().addFreshEntity(entity);
        return entity;
    }
    private NighttimeGameTests() {}
}
