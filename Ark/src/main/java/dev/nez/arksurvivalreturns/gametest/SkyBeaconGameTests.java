package dev.nez.arksurvivalreturns.gametest;

import com.mojang.authlib.GameProfile;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.guardian.*;
import dev.nez.arksurvivalreturns.feature.taming.TorporService;
import dev.nez.arksurvivalreturns.registry.ModContent;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

final class SkyBeaconGameTests {
    static void assets(GameTestHelper h) {
        ServerLevel world = h.getLevel();
        h.assertTrue(world.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(SkyBeaconStructure.ID).isPresent(),
                "The native beacon is not registered");
        var set = world.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET)
                .get(ArkSurvivalReturns.id("sky_beacons")).orElseThrow().value();
        var placement = (net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement)set.placement();
        h.assertTrue(placement.spacing() == 32 && placement.separation() == 30, "Beacon spacing changed");
        for (String variant : SkyBeaconStructure.VARIANTS) {
            var template = world.getStructureManager().getOrCreate(ArkSurvivalReturns.id("sky_beacon/" + variant));
            h.assertTrue(template.getSize().equals(SkyBeaconStructure.SIZE), "Missing beacon " + variant);
        }
        h.assertTrue(world.getServer().reloadableRegistries().getLootTable(SkyBeaconStructure.LOOT)
                != net.minecraft.world.level.storage.loot.LootTable.EMPTY, "The loot crate has no loot table");
        h.succeed();
    }

    static void placement(GameTestHelper h) {
        var world = h.getLevel();
        BlockPos origin = h.absolutePos(new BlockPos(8, 40, 8));
        var template = world.getStructureManager().getOrCreate(ArkSurvivalReturns.id("sky_beacon/white"));
        var settings = new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings()
                .setIgnoreEntities(false).setFinalizeEntities(true);
        // Place in the dedicated empty test arena; this exercises the native NBT entity placement path.
        h.assertTrue(template.placeInWorld(world, origin, origin, settings, world.getRandom(), 2), "Template placement failed");
        var area = new AABB(net.minecraft.world.phys.Vec3.atLowerCornerOf(origin), net.minecraft.world.phys.Vec3.atLowerCornerOf(origin.offset(SkyBeaconStructure.SIZE)));
        var dragons = world.getEntitiesOfClass(GuardianDragonEntity.class, area);
        h.assertTrue(dragons.size() == 1 && dragons.getFirst().beaconVariant() == 1, "Template must contain exactly one white guardian");
        var dragon = dragons.getFirst();
        BlockPos nest = origin.offset(SkyBeaconStructure.NEST);
        h.assertTrue(world.getBlockState(nest).is(ModContent.NESTS.get(dev.nez.arksurvivalreturns.feature.creature.Species.DRAGON).get()),
                "The monolith's eye holds no dragon nest");
        h.assertTrue(world.getBlockEntity(origin.offset(SkyBeaconStructure.CRATE))
                instanceof dev.nez.arksurvivalreturns.feature.station.StorageCrateBlockEntity crate
                && SkyBeaconStructure.LOOT.equals(crate.getLootTable()), "The monolith's eye holds no loot crate");
        h.assertTrue(world.getBlockState(nest.above(3)).isAir() && world.getBlockState(nest.below()).isSolidRender()
                && world.getBlockState(nest.above(10)).isSolidRender(), "The nest does not sit on the floor of an open eye");
        h.runAfterDelay(5, () -> {
            h.assertTrue(dragon.beaconHome().equals(nest), "Template entity home used local coordinates");
            h.assertTrue(dragon.getDeltaMovement().lengthSqr() > 0, "Dragon did not leave the nest");
            dragon.discard();
            // Remove only blocks placed by this test, inside its known template bounds; nothing breaks, drops or spills.
            int quiet = net.minecraft.world.level.block.Block.UPDATE_CLIENTS | net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE
                    | net.minecraft.world.level.block.Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS;
            for (BlockPos pos : BlockPos.betweenClosed(origin, origin.offset(SkyBeaconStructure.SIZE).offset(-1, -1, -1)))
                if (!world.isEmptyBlock(pos)) world.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), quiet);
            h.succeed();
        });
    }

    static void persistence(GameTestHelper h) {
        var world = h.getLevel();
        var boss = ModContent.GUARDIAN_DRAGON.get().create(world, EntitySpawnReason.STRUCTURE);
        BlockPos home = h.absolutePos(new BlockPos(8, 24, 8));
        boss.snapTo(home.getX()+.5, home.getY(), home.getZ()+.5);
        boss.setBeaconVariant(2);
        world.addFreshEntity(boss);
        h.runAfterDelay(5, () -> {
            boss.hurtServer(world, world.damageSources().generic(), 8);
            float wounded = boss.getHealth();
            var out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, world.registryAccess());
            boss.saveWithoutId(out);
            var restored = ModContent.GUARDIAN_DRAGON.get().create(world, EntitySpawnReason.LOAD);
            restored.load(TagValueInput.create(ProblemReporter.DISCARDING, world.registryAccess(), out.buildResult()));
            h.assertTrue(home.equals(restored.beaconHome()) && restored.beaconVariant() == 2, "Home or variant was not saved");
            h.assertTrue(restored.getHealth() == wounded && wounded < restored.getMaxHealth(), "Reload healed the guardian");
            h.assertFalse(TorporService.eligible(restored), "Beacon guardian can be sedated");
            h.assertFalse(restored.isNaturalWildlife(), "Beacon guardian counts toward wildlife");
            h.assertTrue(restored.isPersistenceRequired() && restored.isNoGravity(), "Guardian fell or despawned");
            var player = h.makeMockPlayer(GameType.SURVIVAL);
            player.snapTo(home.getX(),home.getY()-80,home.getZ());
            h.assertFalse(restored.defendsAgainst(player), "Guardian targets players on the ground far below its nest");
            // More than the wound above, or the hit falls inside its invulnerable ticks.
            boss.hurtServer(world, player.damageSources().playerAttack(player), 16);
            h.assertTrue(boss.defendsAgainst(player), "A wounded guardian ignores its attacker below the nest");
            player.snapTo(home.getX()+100,home.getY(),home.getZ());
            h.assertFalse(restored.defendsAgainst(player), "Guardian targets beyond its leash");
            boss.discard(); restored.discard(); player.discard(); h.succeed();
        });
    }

    static void rewards(GameTestHelper h) {
        var world = h.getLevel();
        BlockPos home = h.absolutePos(new BlockPos(8, 24, 8));
        var player = FakePlayerFactory.get(world, new GameProfile(UUID.randomUUID(), "BeaconHunter"));
        var boss = ModContent.GUARDIAN_DRAGON.get().create(world, EntitySpawnReason.STRUCTURE);
        boss.snapTo(home.getX()+.5,home.getY(),home.getZ()+.5);
        boss.anchorAt(home);
        world.addFreshEntity(boss);
        String key = "sky_beacon|" + world.dimension().identifier() + "|" + home.asLong();
        GuardianData.get(world).remove(key);
        boss.hurtServer(world, player.damageSources().playerAttack(player), 10000);
        h.assertTrue(TribeProgressData.get(world).has(player.getUUID(), TribeProgressData.WORKSHOP_SCHEMATIC),
                "Dragon victory did not unlock the workshop");
        AABB landing = new AABB(home).inflate(3);
        var drops = world.getEntitiesOfClass(ItemEntity.class, landing, e ->
                e.getItem().is(ModContent.GUARDIAN_TROPHY.get()) || e.getItem().is(ModContent.WORKSHOP_SCHEMATIC.get()));
        h.assertTrue(drops.size() == 2, "Victory rewards did not land in the nest");
        boss.die(player.damageSources().playerAttack(player));
        h.assertTrue(world.getEntitiesOfClass(ItemEntity.class, landing, e ->
                e.getItem().is(ModContent.GUARDIAN_TROPHY.get()) || e.getItem().is(ModContent.WORKSHOP_SCHEMATIC.get())).size() == 2,
                "Repeated death duplicated rewards");
        drops.forEach(ItemEntity::discard);
        boss.discard(); player.discard(); GuardianData.get(world).remove(key); h.succeed();
    }
}
