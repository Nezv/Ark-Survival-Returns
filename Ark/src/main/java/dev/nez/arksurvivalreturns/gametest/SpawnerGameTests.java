package dev.nez.arksurvivalreturns.gametest;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.List;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.feature.spawn.LedgerModel;
import dev.nez.arksurvivalreturns.feature.spawn.NaturalPopulations;
import dev.nez.arksurvivalreturns.feature.spawn.PlainSight;
import dev.nez.arksurvivalreturns.feature.spawn.RegionalLedger;
import dev.nez.arksurvivalreturns.feature.spawn.SpawnRules;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Regression coverage for natural spawning: realm categories, world-generation accessors, the species
 * ranges, animal-like persistence and the population budget, the only thing that places Ark wildlife.
 */
public final class SpawnerGameTests {
    /** Vanilla spawn categories, the worldgen accessor contract, and no Ark creature in a vanilla spawn list. */
    public static void pipeline(GameTestHelper h) {
        for (var species : Species.values()) {
            var expected = species.aquatic() ? MobCategory.WATER_CREATURE : MobCategory.CREATURE;
            var actual = ModContent.CREATURES.get(species).get().getCategory();
            h.assertTrue(actual == expected, "Wrong spawn category for " + species + ": " + actual);
        }
        // The budget alone places Ark wildlife: a vanilla spawn list would ignore its spacing, ranges and caps.
        var registered = new java.util.IdentityHashMap<net.minecraft.world.entity.EntityType<?>, Species>();
        ModContent.CREATURES.forEach((species, holder) -> registered.put(holder.get(), species));
        var biomes = h.getLevel().registryAccess()
                .lookupOrThrow(net.minecraft.core.registries.Registries.BIOME).listElements().toList();
        for (var biome : biomes) for (var category : MobCategory.values())
            for (var weighted : biome.value().getMobSettings().getMobs(category).unwrap())
                h.assertTrue(registered.get(weighted.value().type()) == null, "Ark creature in a vanilla spawn list: "
                        + weighted.value().type() + " in " + biome.unwrapKey().map(key -> key.identifier().toString()).orElse("?"));
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
        var wild = trike.create(world, EntitySpawnReason.NATURAL);
        wild.snapTo(Vec3.atBottomCenterOf(pad), 0, 0);
        wild.finalizeSpawn(world, world.getCurrentDifficultyAt(pad), EntitySpawnReason.NATURAL, null);
        h.assertTrue(wild.isNaturalWildlife(), "A natural spawn is not natural wildlife");
        h.assertFalse(wild.isPersistenceRequired(), "Natural spawn must not require persistence");
        h.assertFalse(wild.removeWhenFarAway(1024), "Natural spawn still despawns when far away");
        wild.discard();
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

    /** Danger 3 places a regional large, and foliage stops vetoing apex bodies while solids still do. */
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
            // A Brontosaurus has the bulk of a Rex without its need for canopy while its kind sleeps, so
            // this holds at any hour.
            var giant = ModContent.CREATURES.get(Species.BRONTOSAURUS).get();
            h.assertTrue(SpawnRules.canSpawn(giant, world, EntitySpawnReason.NATURAL, pad, world.getRandom()),
                    "Open pad rejected a giant: " + SpawnRules.placement(giant, world, pad));
            for (int x = 8; x < 24; x++) for (int z = 8; z < 24; z++) h.setBlock(x, 6, z, Blocks.OAK_LEAVES);
            h.assertTrue(SpawnRules.canSpawn(giant, world, EntitySpawnReason.NATURAL, pad, world.getRandom()),
                    "Leaf canopy vetoed a giant: " + SpawnRules.placement(giant, world, pad));
            for (int x = 8; x < 24; x++) for (int z = 8; z < 24; z++) h.setBlock(x, 6, z, Blocks.STONE);
            h.assertFalse(SpawnRules.canSpawn(giant, world, EntitySpawnReason.NATURAL, pad, world.getRandom()),
                    "Solid roof accepted a giant");
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
                "Danger-3 ground never produced a regional large: "
                        + placed.stream().map(c -> c.species().id).toList());
        for (var creature : placed) creature.discard();
        h.succeed();
    }

    /**
     * The ledger model counts groups horizontally: a player mining 100 blocks under the floor gets the counted
     * circle filled once, to its target at most, instead of an endless spawn-and-cull loop. The groups are
     * encounters: apart from one another, no species twice, hunters the minority.
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
        int wanted = Config.POPULATION_GROUPS.get();
        double cull = Config.POPULATION_CULL_FRACTION.get();
        try {
            Config.POPULATION_MODEL.set(NaturalPopulations.Model.LEDGER);
            Config.POPULATION_RADIUS.set(60);
            Config.POPULATION_MIN_DISTANCE.set(8);
            Config.POPULATION_ATTEMPTS.set(4);
            Config.POPULATION_GROUPS_PER_PASS.set(4);
            Config.POPULATION_GROUPS.set(5);
            Config.POPULATION_CULL_FRACTION.set(0.25);
            int target = NaturalPopulations.targetFor(world, player);
            for (int pass = 0; pass < 20; pass++) NaturalPopulations.enforce(world, List.of(player));
            var placed = world.getEntitiesOfClass(CreatureEntity.class, border, CreatureEntity::isNaturalWildlife);
            h.assertTrue(!placed.isEmpty(), "The ledger budget placed nothing above a deep player");
            var herds = NaturalPopulations.groups(placed);
            // The budget counts a group where the middle of its members is; one anchored on the rim can fall outside.
            long counted = herds.stream().filter(group -> Math.hypot(group.x() - player.getX(), group.z() - player.getZ()) <= 60).count();
            h.assertTrue(counted <= target, "A deep player made the budget overspawn: " + counted + " groups > " + target);
            long hunters = herds.stream().filter(group -> group.species().predator).count();
            h.assertTrue(hunters <= Math.max(1, Math.round(target * NaturalPopulations.PREDATOR_GROUPS)),
                    "Hunters are not the minority: " + hunters + " of " + herds.size() + " groups");
            for (var group : herds) {
                h.assertTrue(group.members().size() <= group.species().maxGroup, "Oversized group: " + group.species());
                for (var other : herds) {
                    if (other == group) continue;
                    // A member may stand a few blocks from where its group was anchored.
                    double gap = Math.hypot(group.x() - other.x(), group.z() - other.z());
                    h.assertTrue(gap >= NaturalPopulations.GROUP_SPACING - 12, "Groups placed on top of each other: " + gap);
                    h.assertTrue(group.species() != other.species() || gap >= NaturalPopulations.SPECIES_SPACING - 12,
                            "A species repeats within sight of itself: " + group.species() + " at " + gap);
                }
            }
            for (var creature : placed) {
                double dx = creature.getX() - player.getX(), dz = creature.getZ() - player.getZ();
                h.assertTrue(dx * dx + dz * dz <= 68 * 68, "Placed outside the counted circle: " + creature.blockPosition());
                h.assertTrue(dev.nez.arksurvivalreturns.feature.spawn.SpeciesRange.lives(creature.species(), world.getBiome(creature.blockPosition())),
                        "Placed outside its range: " + creature.species());
            }
        } finally {
            Config.POPULATION_MODEL.set(model);
            Config.POPULATION_RADIUS.set(radius);
            Config.POPULATION_MIN_DISTANCE.set(minDistance);
            Config.POPULATION_ATTEMPTS.set(attempts);
            Config.POPULATION_GROUPS_PER_PASS.set(groups);
            Config.POPULATION_GROUPS.set(wanted);
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

    /**
     * Nothing appears or vanishes while somebody watches. The rule on bare ground: in front of a player an animal
     * is in plain sight, behind them it is not, a wall hides what does not tower over it and distance makes a small
     * animal a speck. Then the budget itself: around a player who looks one way no animal is placed that they could
     * make out, and of the groups to spare none goes while the player has turned to face it.
     */
    public static void sight(GameTestHelper h) {
        for (int x = 0; x < 128; x++) for (int z = 0; z < 128; z++) h.setBlock(x, 1, z, Blocks.GRASS_BLOCK);
        var world = h.getLevel();
        var border = new AABB(h.absolutePos(new BlockPos(64, 2, 64))).inflate(80, 160, 80);
        for (var creature : world.getEntitiesOfClass(CreatureEntity.class, border, CreatureEntity::isNaturalWildlife))
            creature.discard();
        var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        // Near one edge of the floor, looking along it: 'ahead' is the structure's own +z whichever way it was turned.
        player.snapTo(h.absoluteVec(new Vec3(64.5, 2, 12.5)));
        Vec3 at = player.position(), ahead = h.absoluteVec(new Vec3(64.5, 2, 13.5)).subtract(at).normalize();
        Vec3 right = new Vec3(-ahead.z, 0, ahead.x);
        float forwards = (float) Math.toDegrees(Math.atan2(-ahead.x, ahead.z));
        face(player, forwards);
        h.assertTrue(seen(world, player, at.add(ahead.scale(30)), 1f, 1f), "An animal 30 blocks ahead on open ground is not in plain sight");
        h.assertTrue(seen(world, player, at.add(ahead.scale(10)).add(right.scale(30)), 2f, 2f), "An animal 72 degrees to the side counts as off the screen");
        h.assertFalse(seen(world, player, at.add(ahead.scale(-10)).add(right.scale(40)), 3f, 3f), "An animal behind the shoulder is in plain sight");
        h.assertFalse(seen(world, player, at.add(ahead.scale(60)), 0.6f, 0.6f), "A small animal 60 blocks off is more than a speck");
        h.assertTrue(seen(world, player, at.add(ahead.scale(60)), 3f, 3f), "A large animal 60 blocks off is a speck");
        face(player, forwards + 180f);
        h.assertFalse(seen(world, player, at.add(ahead.scale(30)), 3f, 3f), "An animal behind the player is in plain sight");
        face(player, forwards);
        // A wall nine wide and six high, ten blocks ahead.
        for (int x = 60; x <= 68; x++) for (int y = 2; y <= 7; y++) h.setBlock(x, y, 22, Blocks.STONE);
        h.assertFalse(seen(world, player, at.add(ahead.scale(14)), 1f, 1f), "An animal behind a wall is in plain sight");
        h.assertTrue(seen(world, player, at.add(ahead.scale(14)), 1f, 9f), "An animal towering over the wall is hidden by it");
        h.assertTrue(seen(world, player, at.add(ahead.scale(14)).add(right.scale(12)), 1f, 1f), "An animal beside the wall is hidden by it");
        for (int x = 60; x <= 68; x++) for (int y = 2; y <= 7; y++) h.setBlock(x, y, 22, Blocks.AIR);

        var model = Config.POPULATION_MODEL.get();
        int radius = Config.POPULATION_RADIUS.get(), minDistance = Config.POPULATION_MIN_DISTANCE.get();
        int attempts = Config.POPULATION_ATTEMPTS.get(), groups = Config.POPULATION_GROUPS_PER_PASS.get();
        int wanted = Config.POPULATION_GROUPS.get();
        double cull = Config.POPULATION_CULL_FRACTION.get();
        try {
            Config.POPULATION_MODEL.set(NaturalPopulations.Model.LEDGER);
            Config.POPULATION_RADIUS.set(110);
            Config.POPULATION_MIN_DISTANCE.set(8);
            Config.POPULATION_ATTEMPTS.set(4);
            Config.POPULATION_GROUPS_PER_PASS.set(4);
            Config.POPULATION_GROUPS.set(6);
            Config.POPULATION_CULL_FRACTION.set(0.25);
            world.getRandom().setSeed(0x51647L);
            // Looking at the floor ahead: whatever is placed is placed where the player cannot make it out.
            for (int pass = 0; pass < 40; pass++) NaturalPopulations.enforce(world, List.of(player));
            var watching = world.getEntitiesOfClass(CreatureEntity.class, border, CreatureEntity::isNaturalWildlife);
            for (var creature : watching)
                h.assertFalse(seen(world, player, creature.position(), creature.getBbWidth(), creature.getBbHeight()),
                        "Placed in plain sight: " + creature.species() + " at " + creature.blockPosition());
            // What stood at the edges of the picture or as specks far off made up the count; the land is emptied again.
            watching.forEach(CreatureEntity::discard);
            // Looking away from the floor: it fills behind the player's back.
            face(player, forwards + 180f);
            for (int pass = 0; pass < 40; pass++) NaturalPopulations.enforce(world, List.of(player));
            var placed = world.getEntitiesOfClass(CreatureEntity.class, border, CreatureEntity::isNaturalWildlife);
            int herds = NaturalPopulations.groups(placed).size();
            h.assertTrue(herds >= 2,"The land behind a player's back stays empty: " + herds + " groups");
            // One group is wanted now and none tolerated above it. Facing them, none the player can make out is taken away.
            Config.POPULATION_GROUPS.set(1);
            Config.POPULATION_CULL_FRACTION.set(0.0);
            face(player, forwards);
            var watched = placed.stream().filter(c -> seen(world, player, c.position(), c.getBbWidth(), c.getBbHeight())).toList();
            h.assertTrue(!watched.isEmpty(), "Turned round, the player makes out none of the animals behind them");
            for (int pass = 0; pass < 6; pass++) NaturalPopulations.enforce(world, List.of(player));
            h.assertTrue(watched.stream().noneMatch(CreatureEntity::isRemoved), "An animal was taken away while the player watched it");
            // With their back turned again the spare groups beyond the near ground go.
            face(player, forwards + 180f);
            for (int pass = 0; pass < 12; pass++) NaturalPopulations.enforce(world, List.of(player));
            var left = world.getEntitiesOfClass(CreatureEntity.class, border, CreatureEntity::isNaturalWildlife);
            h.assertTrue(NaturalPopulations.groups(left).size() < herds, "No spare group left behind the player's back: " + herds);
        } finally {
            Config.POPULATION_MODEL.set(model);
            Config.POPULATION_RADIUS.set(radius);
            Config.POPULATION_MIN_DISTANCE.set(minDistance);
            Config.POPULATION_ATTEMPTS.set(attempts);
            Config.POPULATION_GROUPS_PER_PASS.set(groups);
            Config.POPULATION_GROUPS.set(wanted);
            Config.POPULATION_CULL_FRACTION.set(cull);
            for (var creature : world.getEntitiesOfClass(CreatureEntity.class, border, CreatureEntity::isNaturalWildlife))
                creature.discard();
        }
        h.succeed();
    }

    private static boolean seen(ServerLevel world, net.minecraft.world.entity.player.Player player, Vec3 feet, float width, float height) {
        return PlainSight.seenBy(world, player, feet.x, feet.y, feet.z, width, height);
    }

    private static void face(net.minecraft.world.entity.player.Player player, float yaw) {
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        player.setXRot(0f);
    }

    /**
     * The generated range tags are a copy of the species ranges over the shipped biomes, made from the profile
     * table in config/integrations. This writes the table as the running game classifies the biomes, and fails
     * when the copy no longer matches, saying how to refresh it.
     */
    public static void ranges(GameTestHelper h) {
        h.assertTrue(dev.nez.arksurvivalreturns.feature.spawn.SpeciesRange.complete(), "A species has no range and would never spawn");
        var registry = h.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME);
        var table = new com.google.gson.JsonObject();
        var stale = new java.util.ArrayList<String>();
        registry.listElements().sorted(java.util.Comparator.comparing(holder -> holder.key().identifier().toString())).forEach(holder -> {
            var profile = dev.nez.arksurvivalreturns.feature.spawn.SurfaceBiomes.profile(holder);
            var entry = new com.google.gson.JsonObject();
            entry.addProperty("type", profile.type().id());
            entry.addProperty("climate", profile.climate().name().toLowerCase(java.util.Locale.ROOT));
            entry.addProperty("moisture", profile.moisture().name().toLowerCase(java.util.Locale.ROOT));
            entry.addProperty("mountainous", profile.mountainous());
            entry.addProperty("snowy", profile.snowy());
            entry.addProperty("trees", profile.treeCover().name().toLowerCase(java.util.Locale.ROOT));
            var residents = new com.google.gson.JsonArray();
            for (var species : Species.values()) {
                boolean lives = dev.nez.arksurvivalreturns.feature.spawn.SpeciesRange.lives(species, profile);
                if (lives) residents.add(species.id);
                if (lives != holder.is(species.biomes)) stale.add(species.id + " in " + profile.biomeId());
            }
            // Read by people and the showcase, not by datagen: who the range puts here.
            entry.add("species", residents);
            table.add(profile.biomeId(), entry);
        });
        var root = new com.google.gson.JsonObject();
        root.addProperty("about", "Written by the spawn_ranges GameTest (run/diagnostics/biome-profiles.json); copy to config/integrations and run runData.");
        root.add("biomes", table);
        try {
            var out = java.nio.file.Path.of("diagnostics", "biome-profiles.json");
            java.nio.file.Files.createDirectories(out.getParent());
            java.nio.file.Files.writeString(out, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(root) + "\n");
        } catch (java.io.IOException failed) {
            h.fail("Cannot write the biome profile table: " + failed);
        }
        h.assertTrue(stale.isEmpty(), "Range tags out of step with the game for " + stale.size() + " pairs (" + stale.stream().limit(4).toList()
                + "): copy run/diagnostics/biome-profiles.json to config/integrations/ and run runData");
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
