package dev.nez.arksurvivalreturns.gametest;

import java.util.ArrayList;
import java.util.UUID;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.behavior.*;
import dev.nez.arksurvivalreturns.feature.creature.*;
import dev.nez.arksurvivalreturns.registry.ModContent;
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
