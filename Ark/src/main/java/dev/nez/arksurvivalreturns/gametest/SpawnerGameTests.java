package dev.nez.arksurvivalreturns.gametest;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.List;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.feature.spawn.LedgerModel;
import dev.nez.arksurvivalreturns.feature.spawn.NaturalPopulations;
import dev.nez.arksurvivalreturns.feature.spawn.RegionalLedger;
import dev.nez.arksurvivalreturns.feature.spawn.SpawnRules;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Regression coverage for natural spawning: realm categories, world-generation accessors,
 * the vanilla chunk-generation spawner path, animal-like persistence and the population budget.
 */
public final class SpawnerGameTests {
    /** Vanilla spawn categories, the worldgen accessor contract and the chunk-generation spawner. */
    public static void pipeline(GameTestHelper h) {
        for (var species : Species.values()) {
            var expected = species.aquatic() ? MobCategory.WATER_CREATURE : MobCategory.CREATURE;
            var actual = ModContent.CREATURES.get(species).get().getCategory();
            h.assertTrue(actual == expected, "Wrong spawn category for " + species + ": " + actual);
        }
        // No loaded biome spawn list may contain a mod creature wider than vanilla's single-chunk clamp.
        var registered = new java.util.IdentityHashMap<net.minecraft.world.entity.EntityType<?>, Species>();
        ModContent.CREATURES.forEach((species, holder) -> registered.put(holder.get(), species));
        var biomes = h.getLevel().registryAccess()
                .lookupOrThrow(net.minecraft.core.registries.Registries.BIOME).listElements().toList();
        int listed = 0;
        for (var biome : biomes) {
            for (var weighted : biome.value().getMobSettings().getMobs(MobCategory.CREATURE).unwrap()) {
                var species = registered.get(weighted.value().type());
                if (species == null) continue;
                listed++;
                h.assertTrue(species.chunkSpawnSafe(), "Oversized chunk-generation spawn: " + species
                        + " in " + biome.unwrapKey().map(key -> key.identifier().toString()).orElse("?"));
            }
        }
        h.assertTrue(listed > 0, "No mod creature reached any biome spawn list");
        h.setBiome(Biomes.PLAINS);
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) h.setBlock(x, 1, z, Blocks.GRASS_BLOCK);
        // A stepped natural bank: the western half stays one block lower than the rest.
        for (int x = 8; x < 16; x++) for (int z = 0; z < 16; z++) h.setBlock(x, 2, z, Blocks.GRASS_BLOCK);
        var world = h.getLevel();
        // The eastern half is one block higher, so the valid stance is the top of that bank.
        var pad = h.absolutePos(new BlockPos(8, 3, 8));
        var trike = ModContent.CREATURES.get(Species.TRICERATOPS).get();
        // The world generation path passes a ServerLevelAccessor that is not a ServerLevel.
        ServerLevelAccessor facade = facade(world);
        h.assertFalse(facade instanceof ServerLevel, "The facade must not be a ServerLevel");
        h.assertTrue(SpawnPlacements.checkSpawnRules(trike, facade, EntitySpawnReason.CHUNK_GENERATION, pad, world.getRandom()),
                "World-generation accessor rejected a valid bank spawn");
        h.assertFalse(SpawnPlacements.checkSpawnRules(trike, facade, EntitySpawnReason.CHUNK_GENERATION, pad.above(4), world.getRandom()),
                "World-generation accessor accepted a covered spawn");
        // The real vanilla chunk-generation spawner must produce mod creatures from the biome list.
        var biome = world.getBiome(pad);
        var spawns = biome.value().getMobSettings().getMobs(MobCategory.CREATURE);
        boolean hasMod = spawns.unwrap().stream().anyMatch(weighted -> ModContent.CREATURES.values().stream()
                .anyMatch(holder -> holder.isBound() && holder.get() == weighted.value().type()));
        var border = new AABB(pad).inflate(24);
        h.assertTrue(hasMod, "Plains spawn list has no mod creatures: " + spawns.unwrap().size() + " entries");
        int spawned = 0;
        // A fixed random source keeps the weighted pick reproducible across test runs. Habitats spread
        // one list budget over many species, so fewer rolls land on a species legal at this danger.
        var random = net.minecraft.util.RandomSource.create(0x5EEDL);
        for (int i = 0; i < 1000 && spawned == 0; i++) {
            NaturalSpawner.spawnMobsForChunkGeneration(facade, biome,
                    new ChunkPos(pad.getX() >> 4, pad.getZ() >> 4), random);
            spawned = world.getEntitiesOfClass(CreatureEntity.class, border, CreatureEntity::isNaturalWildlife).size();
        }
        h.assertTrue(spawned > 0, "Vanilla chunk-generation spawner produced no mod creatures (mobs="
                + world.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, border, c -> true).size() + ")");
        var wild = world.getEntitiesOfClass(CreatureEntity.class, border, CreatureEntity::isNaturalWildlife).getFirst();
        h.assertFalse(wild.isPersistenceRequired(), "Natural spawn must not require persistence");
        h.assertFalse(wild.removeWhenFarAway(1024), "Natural spawn still despawns when far away");
        for (var creature : world.getEntitiesOfClass(CreatureEntity.class, border, c -> true)) creature.discard();
        h.succeed();
    }

    /** The independent budget places wildlife even though it ignores the vanilla mob cap. */
    public static void budget(GameTestHelper h) {
        for (int x = 0; x < 64; x++) for (int z = 0; z < 64; z++) h.setBlock(x, 1, z, Blocks.GRASS_BLOCK);
        var world = h.getLevel();
        var center = h.absolutePos(new BlockPos(32, 2, 32));
        var border = new AABB(center).inflate(64);
        for (var creature : world.getEntitiesOfClass(CreatureEntity.class, border, CreatureEntity::isNaturalWildlife))
            creature.discard();
        var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.snapTo(Vec3.atBottomCenterOf(center));
        int radius = Config.POPULATION_RADIUS.get(), target = Config.POPULATION_TARGET.get();
        int minDistance = Config.POPULATION_MIN_DISTANCE.get(), attempts = Config.POPULATION_ATTEMPTS.get();
        var model = Config.POPULATION_MODEL.get();
        try {
            Config.POPULATION_MODEL.set(NaturalPopulations.Model.BUDGET);
            Config.POPULATION_RADIUS.set(24);
            Config.POPULATION_TARGET.set(4);
            Config.POPULATION_MIN_DISTANCE.set(8);
            Config.POPULATION_ATTEMPTS.set(4);
            NaturalPopulations.enforce(world, List.of(player));
        } finally {
            Config.POPULATION_MODEL.set(model);
            Config.POPULATION_RADIUS.set(radius);
            Config.POPULATION_TARGET.set(target);
            Config.POPULATION_MIN_DISTANCE.set(minDistance);
            Config.POPULATION_ATTEMPTS.set(attempts);
        }
        var placed = world.getEntitiesOfClass(CreatureEntity.class, border, CreatureEntity::isNaturalWildlife);
        h.assertTrue(!placed.isEmpty(), "Population budget placed no wildlife");
        for (var creature : placed) creature.discard();
        h.succeed();
    }

    /** Danger 5 places a regional large, and foliage stops vetoing apex bodies while solids still do. */
    public static void apex(GameTestHelper h) {
        for (int x = 0; x < 64; x++) for (int z = 0; z < 64; z++) h.setBlock(x, 1, z, Blocks.GRASS_BLOCK);
        var world = h.getLevel();
        var center = h.absolutePos(new BlockPos(32, 2, 32));
        var oldProgression = dev.nez.arksurvivalreturns.feature.spawn.ProgressionData.get(world);
        // Origin at the center is the easy middle of the tile; one band radius south is extreme.
        world.getDataStorage().set(dev.nez.arksurvivalreturns.feature.spawn.ProgressionData.TYPE,
                new dev.nez.arksurvivalreturns.feature.spawn.ProgressionData(center.getX(), center.getZ(), 256, true));
        var border = new AABB(center).inflate(64);
        for (var creature : world.getEntitiesOfClass(CreatureEntity.class, border, CreatureEntity::isNaturalWildlife))
            creature.discard();
        var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.snapTo(Vec3.atBottomCenterOf(center));
        int radius = Config.POPULATION_RADIUS.get(), target = Config.POPULATION_TARGET.get();
        int minDistance = Config.POPULATION_MIN_DISTANCE.get(), attempts = Config.POPULATION_ATTEMPTS.get();
        world.getRandom().setSeed(0x5EEDBA5EL);
        var model = Config.POPULATION_MODEL.get();
        try {
            Config.POPULATION_MODEL.set(NaturalPopulations.Model.BUDGET);
            Config.POPULATION_RADIUS.set(40);
            Config.POPULATION_TARGET.set(3);
            Config.POPULATION_MIN_DISTANCE.set(8);
            Config.POPULATION_ATTEMPTS.set(4);
            for (int pass = 0; pass < 8; pass++) NaturalPopulations.enforce(world, List.of(player));
            var easy = world.getEntitiesOfClass(CreatureEntity.class, border, CreatureEntity::isNaturalWildlife);
            h.assertTrue(easy.stream().noneMatch(c -> NaturalPopulations.isRegionalLarge(c.species())),
                    "Danger 1 produced a regional large: " + easy.stream().map(c -> c.species().id).toList());
            for (var creature : easy) creature.discard();
            world.getDataStorage().set(dev.nez.arksurvivalreturns.feature.spawn.ProgressionData.TYPE,
                    new dev.nez.arksurvivalreturns.feature.spawn.ProgressionData(center.getX(), center.getZ() - 512, 256, true));
            for (int pass = 0; pass < 40; pass++) {
                NaturalPopulations.enforce(world, List.of(player));
                var placed = world.getEntitiesOfClass(CreatureEntity.class, border, CreatureEntity::isNaturalWildlife);
                if (placed.stream().anyMatch(c -> NaturalPopulations.isRegionalLarge(c.species()))) break;
                // Keep the small target open: every pass then retries the missing apex first, so the
                // result does not hang on the first group the random sequence happens to place.
                placed.forEach(CreatureEntity::discard);
            }
            // Foliage tolerance: an apex may stand under a leaf canopy, never under solid rock.
            for (int x = 8; x < 24; x++) for (int z = 8; z < 24; z++) h.setBlock(x, 1, z, Blocks.GRASS_BLOCK);
            var pad = h.absolutePos(new BlockPos(16, 2, 16));
            var rex = ModContent.CREATURES.get(Species.TYRANNOSAURUS).get();
            h.assertTrue(SpawnRules.canSpawn(rex, world, EntitySpawnReason.NATURAL, pad, world.getRandom()),
                    "Open pad rejected a Rex: " + SpawnRules.placement(rex, world, pad));
            for (int x = 8; x < 24; x++) for (int z = 8; z < 24; z++) h.setBlock(x, 6, z, Blocks.OAK_LEAVES);
            h.assertTrue(SpawnRules.canSpawn(rex, world, EntitySpawnReason.NATURAL, pad, world.getRandom()),
                    "Leaf canopy vetoed a Rex: " + SpawnRules.placement(rex, world, pad));
            for (int x = 8; x < 24; x++) for (int z = 8; z < 24; z++) h.setBlock(x, 6, z, Blocks.STONE);
            h.assertFalse(SpawnRules.canSpawn(rex, world, EntitySpawnReason.NATURAL, pad, world.getRandom()),
                    "Solid roof accepted a Rex");
        } finally {
            Config.POPULATION_MODEL.set(model);
            Config.POPULATION_RADIUS.set(radius);
            Config.POPULATION_TARGET.set(target);
            Config.POPULATION_MIN_DISTANCE.set(minDistance);
            Config.POPULATION_ATTEMPTS.set(attempts);
            world.getDataStorage().set(dev.nez.arksurvivalreturns.feature.spawn.ProgressionData.TYPE, oldProgression);
        }
        var placed = world.getEntitiesOfClass(CreatureEntity.class, border, CreatureEntity::isNaturalWildlife);
        h.assertTrue(placed.stream().anyMatch(c -> NaturalPopulations.isRegionalLarge(c.species())),
                "Danger-5 ground never produced a regional large: "
                        + placed.stream().map(c -> c.species().id).toList());
        for (var creature : placed) creature.discard();
        h.succeed();
    }

    /**
     * The ledger model counts horizontally: a player mining 100 blocks under the floor gets the counted
     * circle filled once, at its target, instead of an endless spawn-and-cull loop.
     */
    public static void ledgerDensity(GameTestHelper h) {
        for (int x = 0; x < 128; x++) for (int z = 0; z < 128; z++) h.setBlock(x, 1, z, Blocks.GRASS_BLOCK);
        var world = h.getLevel();
        var center = h.absolutePos(new BlockPos(64, 2, 64));
        var border = new AABB(center).inflate(72, 160, 72);
        for (var creature : world.getEntitiesOfClass(CreatureEntity.class, border, CreatureEntity::isNaturalWildlife))
            creature.discard();
        var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.snapTo(Vec3.atBottomCenterOf(center.below(100)));
        var model = Config.POPULATION_MODEL.get();
        int radius = Config.POPULATION_RADIUS.get(), minDistance = Config.POPULATION_MIN_DISTANCE.get();
        int attempts = Config.POPULATION_ATTEMPTS.get(), groups = Config.POPULATION_GROUPS_PER_PASS.get();
        double density = Config.POPULATION_DENSITY.get(), cull = Config.POPULATION_CULL_FRACTION.get();
        try {
            Config.POPULATION_MODEL.set(NaturalPopulations.Model.LEDGER);
            Config.POPULATION_RADIUS.set(48);
            Config.POPULATION_MIN_DISTANCE.set(8);
            Config.POPULATION_ATTEMPTS.set(4);
            Config.POPULATION_GROUPS_PER_PASS.set(4);
            Config.POPULATION_DENSITY.set(0.4);
            Config.POPULATION_CULL_FRACTION.set(0.25);
            int target = NaturalPopulations.targetFor(world, player);
            for (int pass = 0; pass < 12; pass++) NaturalPopulations.enforce(world, List.of(player));
            var placed = world.getEntitiesOfClass(CreatureEntity.class, border, CreatureEntity::isNaturalWildlife);
            h.assertTrue(!placed.isEmpty(), "The ledger budget placed nothing above a deep player");
            int ceiling = (int) Math.ceil(target * 1.25) + 6;
            h.assertTrue(placed.size() <= ceiling, "A deep player made the budget overspawn: " + placed.size() + " > " + ceiling);
            for (var creature : placed) {
                double dx = creature.getX() - player.getX(), dz = creature.getZ() - player.getZ();
                h.assertTrue(dx * dx + dz * dz <= 56 * 56, "Placed outside the counted circle: " + creature.blockPosition());
            }
        } finally {
            Config.POPULATION_MODEL.set(model);
            Config.POPULATION_RADIUS.set(radius);
            Config.POPULATION_MIN_DISTANCE.set(minDistance);
            Config.POPULATION_ATTEMPTS.set(attempts);
            Config.POPULATION_GROUPS_PER_PASS.set(groups);
            Config.POPULATION_DENSITY.set(density);
            Config.POPULATION_CULL_FRACTION.set(cull);
            for (var creature : world.getEntitiesOfClass(CreatureEntity.class, border, CreatureEntity::isNaturalWildlife))
                creature.discard();
        }
        h.succeed();
    }

    /**
     * Hunting and taming take animals out of their region's ledger, wild predation does not (the model owns
     * it), and the ledger survives a save.
     */
    public static void ledgerFeedback(GameTestHelper h) {
        var world = h.getLevel();
        var model = Config.POPULATION_MODEL.get();
        var spawned = new java.util.ArrayList<CreatureEntity>();
        try {
            Config.POPULATION_MODEL.set(NaturalPopulations.Model.LEDGER);
            var ledger = RegionalLedger.get(world);
            var pos = h.absolutePos(new BlockPos(16, 2, 16));
            java.util.function.Function<Species, CreatureEntity> wild = species -> {
                var creature = ModContent.CREATURES.get(species).get().create(world, EntitySpawnReason.NATURAL);
                creature.snapTo(Vec3.atBottomCenterOf(pos), 0, 0);
                creature.finalizeSpawn(world, world.getCurrentDifficultyAt(pos), EntitySpawnReason.NATURAL, null);
                world.addFreshEntity(creature);
                spawned.add(creature);
                return creature;
            };
            double preyShare = RegionalLedger.animalShare(false), predatorShare = RegionalLedger.animalShare(true);
            var before = ledger.at(world, pos.getX(), pos.getZ());
            var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var hunted = wild.apply(Species.PARASAUR);
            h.assertTrue(hunted.isNaturalWildlife(), "Test prey is not natural wildlife");
            hunted.hurtServer(world, world.damageSources().playerAttack(player), Float.MAX_VALUE);
            h.assertTrue(hunted.isDeadOrDying(), "The hunted Parasaur survived");
            var afterHunt = ledger.at(world, pos.getX(), pos.getZ());
            h.assertTrue(Math.abs(Math.max(LedgerModel.FLOOR, before.prey() - preyShare) - afterHunt.prey()) < 1e-6,
                    "A player kill did not take one prey animal: " + before + " -> " + afterHunt);
            h.assertTrue(Math.abs(before.predators() - afterHunt.predators()) < 1e-9, "A prey kill changed the predators");
            var victim = wild.apply(Species.PARASAUR);
            var rex = wild.apply(Species.TYRANNOSAURUS);
            victim.hurtServer(world, world.damageSources().mobAttack(rex), Float.MAX_VALUE);
            h.assertTrue(victim.isDeadOrDying(), "The Rex's prey survived");
            var afterPredation = ledger.at(world, pos.getX(), pos.getZ());
            h.assertTrue(Math.abs(afterPredation.prey() - afterHunt.prey()) < 1e-9, "Wild predation was counted twice");
            rex.onTamed(player.getUUID());
            var afterTame = ledger.at(world, pos.getX(), pos.getZ());
            h.assertTrue(Math.abs(Math.max(LedgerModel.FLOOR, afterHunt.predators() - predatorShare) - afterTame.predators()) < 1e-6,
                    "Taming a wild predator did not take it from the ledger: " + afterHunt + " -> " + afterTame);
            var saved = RegionalLedger.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, ledger).getOrThrow();
            var reloaded = RegionalLedger.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, saved).getOrThrow()
                    .at(world, pos.getX(), pos.getZ());
            h.assertTrue(Math.abs(reloaded.prey() - afterTame.prey()) < 1e-9 && Math.abs(reloaded.predators() - afterTame.predators()) < 1e-9,
                    "The ledger changed through a save: " + afterTame + " -> " + reloaded);
        } finally {
            Config.POPULATION_MODEL.set(model);
            spawned.forEach(net.minecraft.world.entity.Entity::discard);
        }
        h.succeed();
    }

    /** A ServerLevelAccessor that is deliberately not a ServerLevel, as world generation provides. */
    private static ServerLevelAccessor facade(ServerLevel world) {
        return (ServerLevelAccessor) Proxy.newProxyInstance(ServerLevelAccessor.class.getClassLoader(),
                new Class<?>[]{ServerLevelAccessor.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getLevel")) return world;
                    if (method.getName().equals("equals")) return proxy == args[0];
                    if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                    if (method.getName().equals("toString")) return "SpawnRulesFacade";
                    try {
                        return method.invoke(world, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    private SpawnerGameTests() {}
}
