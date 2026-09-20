package dev.nez.arksurvivalreturns.gametest;

import java.util.HashSet;
import java.util.function.Consumer;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.creature.*;
import dev.nez.arksurvivalreturns.feature.spawn.*;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.storage.*;
import net.neoforged.neoforge.registries.DeferredRegister;

/** In-game checks exercise loaded registries, real loot tables, entity serialization and combat. */
public final class ArkGameTests {
    public static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, ArkSurvivalReturns.MOD_ID);
    static {
        FUNCTIONS.register("levels_persist", () -> ArkGameTests::levelsPersist);
        FUNCTIONS.register("packs_and_damage", () -> ArkGameTests::packsAndDamage);
        FUNCTIONS.register("spawn_rules", () -> ArkGameTests::spawnRules);
        FUNCTIONS.register("grass_berries", () -> ArkGameTests::grassBerries);
        FUNCTIONS.register("progression", () -> ArkGameTests::progression);
        FUNCTIONS.register("land_ecology", () -> LandGameTests::ecology);
        FUNCTIONS.register("land_movement", () -> LandGameTests::movement);
        FUNCTIONS.register("population", () -> ArkGameTests::population);
        FUNCTIONS.register("behavior", () -> ArkGameTests::behavior);
        FUNCTIONS.register("combat_timing", () -> ArkGameTests::combatTiming);
        FUNCTIONS.register("flying_ecology", () -> FlyingGameTests::ecology);
        FUNCTIONS.register("flying_pteranodon", () -> h -> FlyingGameTests.flight(h, Species.PTERANODON));
        FUNCTIONS.register("flying_argentavis", () -> h -> FlyingGameTests.flight(h, Species.ARGENTAVIS));
        FUNCTIONS.register("nighttime", () -> NighttimeGameTests::run);
        FUNCTIONS.register("creature_expansion", () -> CreatureExpansionGameTests::run);
        FUNCTIONS.register("aquatic_ecology", () -> AquaticGameTests::ecology);
        FUNCTIONS.register("collection_registration", () -> CollectionGameTests::registration);
        FUNCTIONS.register("collection_cold", () -> CollectionGameTests::cold);
        FUNCTIONS.register("theme_alignment", () -> ThemeGameTests::run);
        FUNCTIONS.register("taming_roster", () -> TamingGameTests::roster);
        FUNCTIONS.register("taming_torpor", () -> TamingGameTests::torpor);
        FUNCTIONS.register("taming_passive_feeding", () -> TamingGameTests::passiveFeeding);
        FUNCTIONS.register("taming_knockout_feeding", () -> TamingGameTests::knockoutFeeding);
        FUNCTIONS.register("taming_wake_before_completion", () -> TamingGameTests::wakeBeforeCompletion);
        FUNCTIONS.register("taming_riding", () -> TamingGameTests::riding);
        FUNCTIONS.register("taming_persistence", () -> TamingGameTests::persistence);
        FUNCTIONS.register("taming_player_sedation", () -> TamingGameTests::playerSedation);
        FUNCTIONS.register("taming_aerial_feeding", () -> TamingGameTests::aerialFeeding);
        FUNCTIONS.register("taming_completion", () -> TamingGameTests::completionKeepsSleep);
        FUNCTIONS.register("taming_claim_expiry", () -> TamingGameTests::claimExpiry);
        FUNCTIONS.register("taming_ordinary_mob", () -> TamingGameTests::ordinaryMobRestraint);
        FUNCTIONS.register("companion", () -> CompanionGameTests::run);
        FUNCTIONS.register("tribe_permissions", () -> TribeGameTests::permissions);
        FUNCTIONS.register("journal_pack", () -> JournalGameTests::pack);
        FUNCTIONS.register("journal_taming_unlock", () -> JournalGameTests::tamingUnlock);
        FUNCTIONS.register("camp_starter_kit", () -> CampGameTests::starterKit);
        FUNCTIONS.register("camp_bedroll_spawn", () -> CampGameTests::bedrollSpawn);
        FUNCTIONS.register("recovery_cache", () -> RecoveryGameTests::recoveryCache);
        FUNCTIONS.register("downed_revive", () -> DownedGameTests::downedRevive);
        FUNCTIONS.register("spawn_pipeline", () -> SpawnerGameTests::pipeline);
        FUNCTIONS.register("spawn_budget", () -> SpawnerGameTests::budget);
        FUNCTIONS.register("spawn_apex", () -> SpawnerGameTests::apex);
        FUNCTIONS.register("mass_load", () -> MassGameTests::load);
        FUNCTIONS.register("cargo_load", () -> CargoGameTests::load);
        FUNCTIONS.register("cargo_transfer", () -> CargoGameTests::transfer);
    }
    private static CreatureEntity create(GameTestHelper h, Species species) {
        var entity = ModContent.CREATURES.get(species).get().create(h.getLevel(), EntitySpawnReason.COMMAND);
        entity.setNoAi(true);
        entity.setPos(net.minecraft.world.phys.Vec3.atCenterOf(h.absolutePos(new BlockPos(8, 3, 8))));
        return entity;
    }
    private static void levelsPersist(GameTestHelper h) {
        for (var species : Species.values()) {
            var original = create(h, species);
            original.initializeLevel(40);
            original.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(species.speed / 1.5);
            original.wildlife().mind().restoreNeeds(0.7, 0.6, 0.5);
            original.setHealth(original.getMaxHealth() * 0.4f);
            double hp = original.getHealth(), max = original.getMaxHealth(), damage = original.getAttributeValue(Attributes.ATTACK_DAMAGE);
            var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, h.getLevel().registryAccess());
            original.saveWithoutId(output);
            var restored = create(h, species);
            restored.load(TagValueInput.create(ProblemReporter.DISCARDING, h.getLevel().registryAccess(), output.buildResult()));
            restored.initializeLevel(80); // Repeated initialization must not reroll or stack growth.
            restored.tick();
            h.assertTrue(restored.creatureLevel() == 40, "Level changed after reload: " + species);
            h.assertTrue(Math.abs(restored.getHealth() - hp) < 0.01, "Reload healed creature: " + species);
            h.assertTrue(restored.getMaxHealth() == max, "Reload changed max HP: " + species);
            h.assertTrue(restored.getAttributeValue(Attributes.ATTACK_DAMAGE) == damage, "Reload changed damage: " + species);
            h.assertTrue(restored.packId().equals(original.packId()), "Pack identity was lost");
            h.assertTrue(Math.abs(restored.getAttributeValue(Attributes.MOVEMENT_SPEED) - species.speed) < 0.00001, "Saved creature speed was not upgraded");
            h.assertTrue(Math.abs(restored.getBbHeight() - species.height) < 0.001 && Math.abs(restored.getBbWidth() - species.width) < 0.001, "Scaled hitbox mismatch");
            h.assertTrue(restored.wildlife().home().equals(original.wildlife().home()), "Saved home changed");
            h.assertTrue(restored.wildlife().mind().hunger() == 0.7 && restored.wildlife().mind().thirst() == 0.6 && restored.wildlife().mind().fatigue() == 0.5, "Saved needs changed");
        }
        h.succeed();
    }
    private static void packsAndDamage(GameTestHelper h) {
        var first = create(h, Species.VELOCIRAPTOR);
        var second = create(h, Species.VELOCIRAPTOR);
        var other = create(h, Species.VELOCIRAPTOR);
        var world = h.getLevel();
        var difficulty = world.getCurrentDifficultyAt(first.blockPosition());
        var group = first.finalizeSpawn(world, difficulty, EntitySpawnReason.NATURAL, null);
        second.finalizeSpawn(world, difficulty, EntitySpawnReason.NATURAL, group);
        other.finalizeSpawn(world, difficulty, EntitySpawnReason.NATURAL, null);
        h.assertTrue(first.packId().equals(second.packId()), "Spawn pack split");
        h.assertTrue(!first.packId().equals(other.packId()), "Separate spawn packs merged");
        var low = create(h, Species.VELOCIRAPTOR); low.initializeLevel(1);
        var high = create(h, Species.VELOCIRAPTOR); high.initializeLevel(80);
        var victimA = create(h, Species.TRICERATOPS); victimA.initializeLevel(1);
        var victimB = create(h, Species.TRICERATOPS); victimB.initializeLevel(1);
        h.assertTrue(low.doHurtTarget(world, victimA), "Low level attack did not land");
        h.assertTrue(high.doHurtTarget(world, victimB), "High level attack did not land");
        h.assertTrue(victimB.getHealth() < victimA.getHealth(), "Level does not scale actual melee damage");
        h.assertTrue(high.getMaxHealth() > low.getMaxHealth(), "Level does not scale HP");
        h.succeed();
    }
    private static void spawnRules(GameTestHelper h) {
        h.setBiome(Biomes.PLAINS);
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) h.setBlock(x, 1, z, Blocks.GRASS_BLOCK);
        var pos = h.absolutePos(new BlockPos(8, 2, 8));
        var trike = ModContent.CREATURES.get(Species.TRICERATOPS).get();
        var rex = ModContent.CREATURES.get(Species.TYRANNOSAURUS).get();
        var world = h.getLevel();
        h.assertTrue(SpawnRules.canSpawn(trike, world, EntitySpawnReason.NATURAL, pos, world.getRandom()), "Valid plains spawn rejected");
        h.assertFalse(SpawnRules.speciesAllowed(Species.TYRANNOSAURUS, world.getBiome(pos), 1), "Rex allowed in danger 1");
        h.assertTrue(SpawnRules.speciesAllowed(Species.TYRANNOSAURUS, world.getBiome(pos), 5), "Legacy plains tag blocked high-danger Rex");
        h.setBlock(8, 2, 8, Blocks.WATER);
        h.assertFalse(SpawnRules.canSpawn(trike, world, EntitySpawnReason.NATURAL, pos, world.getRandom()), "Underwater spawn accepted");
        h.setBlock(8, 2, 8, Blocks.AIR);
        h.setBlock(8, 7, 8, Blocks.STONE);
        h.assertFalse(SpawnRules.canSpawn(trike, world, EntitySpawnReason.NATURAL, pos, world.getRandom()), "Covered spawn accepted");
        h.setBlock(8, 7, 8, Blocks.AIR);
        h.setBlock(8, 7, 8, Blocks.OAK_LEAVES);
        h.assertTrue(SpawnRules.canSpawn(trike, world, EntitySpawnReason.NATURAL, pos, world.getRandom()), "Safe forest canopy rejected");
        h.setBlock(8, 7, 8, Blocks.AIR);
        var first = create(h, Species.TRICERATOPS);
        var pack = first.finalizeSpawn(world, world.getCurrentDifficultyAt(pos), EntitySpawnReason.NATURAL, null);
        var second = create(h, Species.TRICERATOPS);
        second.finalizeSpawn(world, world.getCurrentDifficultyAt(pos), EntitySpawnReason.NATURAL, pack);
        h.assertTrue(first.packId().equals(second.packId()), "Spawn cluster did not share one pack");
        first.discard(); second.discard();
        h.succeed();
    }
    private static void progression(GameTestHelper h) {
        var world = h.getLevel();
        var profile = ProgressionData.get(world);
        h.assertTrue(create(h, Species.ARGENTAVIS) instanceof FlyingCreatureEntity, "Argentavis missing flying classification");
        h.assertTrue(create(h, Species.PTERANODON) instanceof FlyingCreatureEntity, "Pteranodon missing flying classification");
        h.assertTrue(Species.ARGENTAVIS.cohesionDistance() >= 30, "Argent flock collapses into a tight pack");
        h.assertTrue(FlyingCreatureEntity.altitudeWeight(Species.ARGENTAVIS, 63 + 8, 63)
                < FlyingCreatureEntity.altitudeWeight(Species.ARGENTAVIS, 63 + 40, 63), "Lowland altitude penalty defeated");
        // GameTestServer moves world spawn to its random test arena after ServerStartedEvent.
        // The original anchor must stay easy and must not follow that later spawn change.
        var spawn = new BlockPos(profile.originX(), 64, profile.originZ());
        h.assertTrue(profile.levelAt(spawn) == 1, "Initial spawn is not easy");
        var serialized = ProgressionData.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, profile).getOrThrow();
        var restored = ProgressionData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, serialized).getOrThrow();
        var owner = java.util.UUID.randomUUID();
        var other = java.util.UUID.randomUUID();
        var unlocks = new dev.nez.arksurvivalreturns.feature.map.MapUnlockData(java.util.List.of());
        h.assertFalse(unlocks.isUnlocked(owner), "Map unlocked before progression");
        h.assertTrue(unlocks.hasAccess(owner, false), "Development map access still requires a tame");
        h.assertFalse(unlocks.hasAccess(owner, true), "Progression gate ignored when enabled");
        unlocks.setUnlocked(owner, true);
        var savedUnlocks = dev.nez.arksurvivalreturns.feature.map.MapUnlockData.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, unlocks).getOrThrow();
        var restoredUnlocks = dev.nez.arksurvivalreturns.feature.map.MapUnlockData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, savedUnlocks).getOrThrow();
        h.assertTrue(restoredUnlocks.isUnlocked(owner), "Map unlock lost on reload");
        h.assertTrue(restoredUnlocks.hasAccess(owner, true), "Earned unlock rejected with gate enabled");
        h.assertFalse(restoredUnlocks.isUnlocked(other), "Map entitlement leaked to another player");
        restoredUnlocks.setUnlocked(owner, false);
        h.assertFalse(restoredUnlocks.isUnlocked(owner), "Operator cannot revoke map entitlement");
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), world.registryAccess());
        try {
            var packet = new dev.nez.arksurvivalreturns.feature.map.DangerMapPayload(true, profile.originX(), profile.originZ(), profile.bandWidth());
            dev.nez.arksurvivalreturns.feature.map.DangerMapPayload.STREAM_CODEC.encode(buffer, packet);
            h.assertTrue(packet.equals(dev.nez.arksurvivalreturns.feature.map.DangerMapPayload.STREAM_CODEC.decode(buffer)), "Map profile changed in transit");
        } finally { buffer.release(); }
        for (int i = 0; i < 5; i++) {
            var pos = new BlockPos(profile.originX() + i * profile.bandWidth(), 64, profile.originZ());
            h.assertTrue(restored.levelAt(pos) == profile.levelAt(pos), "Saved progression changed");
        }
        var biomes = world.registryAccess().lookupOrThrow(Registries.BIOME);
        for (var biome : biomes.listElements().toList()) {
            h.assertTrue(SpawnRules.speciesAllowed(Species.PTERANODON, biome, 1), "Biome has no starter wildlife: " + biome);
            for (var species : Species.values()) for (int danger = 1; danger <= 5; danger++) {
                h.assertTrue(SpawnRules.speciesAllowed(species, biome, danger) == (danger >= species.minimumDanger()), "Displayed danger and eligibility disagree: " + species);
            }
        }
        var visit = new BiomeAnnouncements.Visit();
        var a = new BiomeAnnouncements.Region(net.minecraft.world.level.Level.OVERWORLD.identifier(), Biomes.PLAINS.identifier(), 1);
        var b = new BiomeAnnouncements.Region(a.dimension(), a.biome(), 2);
        var c = new BiomeAnnouncements.Region(a.dimension(), Biomes.FOREST.identifier(), 2);
        h.assertTrue(visit.update(a), "Arrival did not announce");
        h.assertFalse(visit.update(a), "Repeated message while standing still");
        h.assertFalse(visit.update(b), "Boundary jitter announced too soon");
        h.assertFalse(visit.update(a), "Boundary jitter repeated old region");
        h.assertFalse(visit.update(b), "First crossing check announced too soon");
        h.assertTrue(visit.update(b), "Danger-only crossing did not announce");
        h.assertFalse(visit.update(c), "Biome change did not debounce");
        h.assertTrue(visit.update(c), "Biome name change did not announce");
        h.assertFalse(visit.update(a), "Return did not debounce");
        h.assertTrue(visit.update(a), "Return visit was suppressed");
        var far = new BlockPos(20_000_000, 64, 20_000_000);
        int chunks = world.getChunkSource().getLoadedChunksCount();
        h.assertTrue(SpawnRules.surface(world, far.getX(), far.getZ()) == null, "Unloaded terrain was sampled");
        h.assertFalse(SpawnRules.canSpawn(ModContent.CREATURES.get(Species.PTERANODON).get(), world,
                EntitySpawnReason.NATURAL, far, world.getRandom()), "Unloaded spawn accepted");
        h.assertTrue(world.getChunkSource().getLoadedChunksCount() == chunks, "Spawn check loaded chunks");
        h.succeed();
    }
    private static void population(GameTestHelper h) {
        for (int x = 0; x < 64; x++) for (int z = 0; z < 64; z++) h.setBlock(x, 1, z, Blocks.GRASS_BLOCK);
        var world = h.getLevel();
        var viewer = h.absolutePos(new BlockPos(32, 2, 32));
        var oldProgression = ProgressionData.get(world);
        var animals = new java.util.ArrayList<CreatureEntity>();
        try {
            world.getDataStorage().set(ProgressionData.TYPE, new ProgressionData(viewer.getX() - 512, viewer.getZ(), 256, true));
            h.assertTrue(BiomeTier.at(world, viewer).dangerLevel() == 5, "Fixture is not danger 5");
            var trike = ModContent.CREATURES.get(Species.TRICERATOPS).get();
            h.assertTrue(SpawnRules.canSpawn(trike, world, EntitySpawnReason.NATURAL, viewer, world.getRandom()), "Valid danger-5 trike spawn rejected");
            h.assertTrue(SpawnRules.canSpawn(trike, world, EntitySpawnReason.NATURAL, viewer.above(4), world.getRandom()) == false, "Covered spawn accepted");
            h.assertFalse(SpawnRules.speciesAllowed(Species.TYRANNOSAURUS, world.getBiome(viewer), 1), "Apex allowed at danger 1");
            h.assertTrue(SpawnRules.speciesAllowed(Species.TYRANNOSAURUS, world.getBiome(viewer), 5), "Apex rejected at danger 5");
            // A vanilla spawn cluster shares one pack identity and stays inside the local level band.
            var difficulty = world.getCurrentDifficultyAt(viewer);
            net.minecraft.world.entity.SpawnGroupData pack = null;
            for (int i = 0; i < 3; i++) {
                var mob = ModContent.CREATURES.get(Species.TRICERATOPS).get().create(world, EntitySpawnReason.NATURAL);
                mob.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(viewer.offset(i, 0, 0)));
                pack = mob.finalizeSpawn(world, difficulty, EntitySpawnReason.NATURAL, pack);
                world.addFreshEntity(mob);
                animals.add(mob);
            }
            h.assertTrue(animals.stream().allMatch(c -> c.packId().equals(animals.getFirst().packId())), "Spawn cluster split its pack");
            for (var creature : animals) {
                var tier = BiomeTier.at(world, creature.blockPosition());
                h.assertTrue(creature.creatureLevel() >= Config.MIN_LEVEL.get(tier).get()
                        && creature.creatureLevel() <= Config.MAX_LEVEL.get(tier).get(), "Level outside local danger range");
            }
        } finally {
            animals.forEach(net.minecraft.world.entity.Entity::discard);
            world.getDataStorage().set(ProgressionData.TYPE, oldProgression);
        }
        h.succeed();
    }
    private static void combatTiming(GameTestHelper h) {
        var world = h.getLevel();
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) h.setBlock(x, 1, z, Blocks.GRASS_BLOCK);
        var raptor = create(h, Species.VELOCIRAPTOR);
        world.addFreshEntity(raptor);
        var pig = net.minecraft.world.entity.EntityType.PIG.create(world, EntitySpawnReason.COMMAND);
        pig.setNoAi(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        pig.setHealth(1000);
        pig.setPos(raptor.position().add(0, 0, 1.0));
        world.addFreshEntity(pig);
        var clips = CreatureAttackClips.of(Species.VELOCIRAPTOR);
        int hitDelay = (int)Math.round(clips.attackTicks() * Config.COMBAT_HIT_FRACTION.get());
        int cooldown = clips.attackTicks() + (int)Math.round(clips.attackTicks() * Config.COMBAT_RECOVERY_FRACTION.get());
        float before = pig.getHealth();
        float[] baseline = {before};
        h.assertTrue(raptor.strike(pig), "Raptor strike was rejected");
        h.assertTrue(pig.getHealth() == before, "Damage landed before the wind-up");
        h.runAfterDelay(hitDelay + 2, () -> {
            h.assertTrue(pig.getHealth() < baseline[0], "The bite never landed on its hit frame");
            baseline[0] = pig.getHealth();
            h.assertFalse(raptor.canStrike(), "Attack cooldown ended with the clip");
        });
        h.runAfterDelay(cooldown + 4, () -> {
            h.assertTrue(raptor.canStrike(), "Attack cooldown never ended");
            // Within sight but out of melee reach: the wind-up starts, the hit has to whiff.
            pig.setPos(raptor.position().add(0, 0, 6));
            h.assertTrue(raptor.strike(pig), "Out-of-reach wind-up was rejected (line of sight is clear)");
        });
        h.runAfterDelay(cooldown + 4 + clips.attackTicks() + 2, () -> {
            h.assertTrue(pig.getHealth() == baseline[0], "A whiffed swing still landed damage");
            raptor.discard(); pig.discard();
            h.succeed();
        });
    }
    private static void behavior(GameTestHelper h) {
        var world = h.getLevel();
        long originalTime = world.getDefaultClockTime();
        var clock = world.dimensionType().defaultClock().orElseThrow();
        world.clockManager().setTotalTicks(clock, 18000); // Wildlife predation is now a nighttime routine.
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) h.setBlock(x, 1, z, Blocks.GRASS_BLOCK);
        var predator = create(h, Species.VELOCIRAPTOR);
        predator.initializeLevel(40);
        predator.wildlife().mind().restoreNeeds(0.8, 0.1, 0.1);
        predator.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(5, 2, 4))));
        predator.setYRot(0); predator.yBodyRot = 0; predator.yHeadRot = 0; predator.setOnGround(true);
        var prey = net.minecraft.world.entity.EntityType.PIG.create(world, EntitySpawnReason.COMMAND);
        prey.setNoAi(true);
        prey.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(5, 2, 12))));
        world.addFreshEntity(predator); world.addFreshEntity(prey);
        int chunks = world.getChunkSource().getLoadedChunksCount();
        try {
            var creative = h.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);
            h.assertFalse(dev.nez.arksurvivalreturns.feature.behavior.WildlifeSenses.validTarget(creative), "Creative player became prey");
            h.assertTrue(dev.nez.arksurvivalreturns.feature.behavior.WildlifeSenses.detect(predator, prey).visible(), "Open sight failed");
            for (int i = 0; i < 8; i++) predator.wildlife().think();
            h.assertTrue(predator.behavior() == dev.nez.arksurvivalreturns.feature.behavior.BehaviorState.HUNT && predator.getTarget() == prey, "Hungry predator did not hunt wildlife");
            float hp = prey.getHealth();
            for (int x = 0; x < 16; x++) for (int y = 2; y <= 10; y++) h.setBlock(x, y, 8, Blocks.STONE);
            h.assertFalse(dev.nez.arksurvivalreturns.feature.behavior.WildlifeSenses.detect(predator, prey).visible(), "Sight penetrated wall");
            predator.wildlife().think();
            h.assertTrue(predator.behavior() == dev.nez.arksurvivalreturns.feature.behavior.BehaviorState.INVESTIGATE && predator.getTarget() == null, "Hidden target remained in combat");
            h.assertTrue(prey.getHealth() == hp, "Melee hit through wall");
            for (int x = 0; x < 16; x++) for (int y = 2; y <= 10; y++) h.setBlock(x, y, 8, Blocks.AIR);
            prey.setPos(predator.position().add(0, 0, 2)); prey.setHealth(0.1f);
            for (int i = 0; i < 8; i++) predator.wildlife().think();
            h.assertTrue(predator.isStriking(), "Close visible predator never began its strike");
            // combat_timing covers the wind-up; land the bite directly to verify the kill feeds the mind.
            h.assertTrue(predator.doHurtTarget(world, prey), "Close visible attack failed");
            h.assertFalse(prey.isAlive(), "Close visible attack failed");
            h.assertTrue(predator.wildlife().mind().hunger() < 0.1, "Kill did not satisfy predator hunger");
            h.assertTrue(world.getChunkSource().getLoadedChunksCount() == chunks,
                    "Behavior loaded chunks: was=" + chunks + " now=" + world.getChunkSource().getLoadedChunksCount());
        } finally { predator.discard(); prey.discard(); world.clockManager().setTotalTicks(clock, originalTime); }
        h.succeed();
    }
    private static final class MovementProbe extends CreatureEntity {
        MovementProbe(net.minecraft.server.level.ServerLevel world, Species species) { super(ModContent.CREATURES.get(species).get(), world, species); }
        void waterStep(net.minecraft.world.phys.Vec3 input) { travelInWater(input, 0, false, getY()); }
    }
    private static boolean berry(ItemStack stack) { return ModContent.BERRIES.values().stream().anyMatch(i -> stack.is(i.get())); }
    private static void grassBerries(GameTestHelper h) {
        var world = h.getLevel();
        var pos = h.absolutePos(new BlockPos(8, 2, 8));
        var found = new HashSet<Item>();
        int berryRolls = 0, seeds = 0, tallRolls = 0, fiberRolls = 0;
        world.getRandom().setSeed(73921L);
        for (int i = 0; i < 2000; i++) {
            var drops = Block.getDrops(Blocks.SHORT_GRASS.defaultBlockState(), world, pos, null, null, ItemStack.EMPTY);
            for (var stack : drops) {
                if (berry(stack)) { found.add(stack.getItem()); berryRolls++; h.assertTrue(stack.getCount() >= 1 && stack.getCount() <= 2, "Invalid berry stack size"); }
                if (stack.is(ModContent.PLANT_FIBER.get())) fiberRolls++;
                if (stack.is(Items.WHEAT_SEEDS)) seeds++;
            }
            var sheared = Block.getDrops(Blocks.SHORT_GRASS.defaultBlockState(), world, pos, null, null, new ItemStack(Items.SHEARS));
            h.assertTrue(sheared.stream().noneMatch(ArkGameTests::berry), "Shears duplicated berries");
            h.assertTrue(sheared.stream().noneMatch(s -> s.is(ModContent.PLANT_FIBER.get())), "Shears duplicated plant fiber");
            var upper = Block.getDrops(Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER), world, pos, null, null, ItemStack.EMPTY);
            h.assertTrue(upper.stream().noneMatch(ArkGameTests::berry), "Tall grass upper half duplicated berries");
            h.assertTrue(upper.stream().noneMatch(s -> s.is(ModContent.PLANT_FIBER.get())), "Tall grass upper half duplicated fiber");
            var lower = Block.getDrops(Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER), world, pos, null, null, ItemStack.EMPTY);
            if (lower.stream().anyMatch(ArkGameTests::berry)) tallRolls++;
        }
        h.assertTrue(found.size() == 4, "Not all four berries are obtainable");
        h.assertTrue(berryRolls > 500 && berryRolls < 900, "Unexpected berry drop rate: " + berryRolls);
        h.assertTrue(tallRolls > 500 && tallRolls < 900, "Tall grass did not yield one normal berry roll");
        h.assertTrue(fiberRolls > 700 && fiberRolls < 1100, "Unexpected plant fiber drop rate: " + fiberRolls);
        h.assertTrue(seeds > 0, "Vanilla seed drops were lost");
        var dirt = Block.getDrops(Blocks.GRASS_BLOCK.defaultBlockState(), world, pos, null, null, ItemStack.EMPTY);
        h.assertTrue(dirt.stream().noneMatch(ArkGameTests::berry), "Grass blocks should not drop berries");
        h.succeed();
    }
    private ArkGameTests() {}
}
