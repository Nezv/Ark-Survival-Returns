package dev.nez.arksurvivalreturns.gametest;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.DataResult;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.feature.guardian.GuardianData;
import dev.nez.arksurvivalreturns.feature.guardian.GuardianEncounter;
import dev.nez.arksurvivalreturns.feature.guardian.GuardianGiganotosaurusEntity;
import dev.nez.arksurvivalreturns.feature.guardian.GuardianPolicy;
import dev.nez.arksurvivalreturns.feature.guardian.GuardianService;
import dev.nez.arksurvivalreturns.feature.guardian.GuardianState;
import dev.nez.arksurvivalreturns.feature.guardian.TribeProgressData;
import dev.nez.arksurvivalreturns.feature.taming.TorporService;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/** Allosaur Heart eligibility, guardian rules and the exactly-once encounter lifecycle. */
final class GuardianGameTests {
    private static final Identifier STRUCTURE = Identifier.fromNamespaceAndPath("ancient_remnants", "sentinel_monolith");

    static void heartDrop(GameTestHelper h) {
        ServerLevel world = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(8, 3, 8));
        FakePlayer hunter = FakePlayerFactory.get(world, new GameProfile(UUID.randomUUID(), "ArkGuardianHunter"));
        world.addFreshEntity(hunter);

        CreatureEntity wild = allosaurus(world, pos, EntitySpawnReason.NATURAL);
        h.assertTrue(wild.isNaturalWildlife(), "A natural Allosaurus must count as wildlife");
        int before = hearts(world, pos);
        wild.hurtServer(world, hunter.damageSources().playerAttack(hunter), 1000f);
        h.assertTrue(hearts(world, pos) - before == 1,
                "A player kill must drop exactly one heart, got " + (hearts(world, pos) - before));

        CreatureEntity spawned = allosaurus(world, pos, EntitySpawnReason.COMMAND);
        int spawnedBefore = hearts(world, pos);
        spawned.hurtServer(world, hunter.damageSources().playerAttack(hunter), 1000f);
        h.assertTrue(hearts(world, pos) == spawnedBefore, "A spawned Allosaurus must not drop a heart");

        CreatureEntity tamed = allosaurus(world, pos, EntitySpawnReason.NATURAL);
        tamed.onTamed(hunter.getUUID());
        h.assertFalse(tamed.isNaturalWildlife(), "A tamed Allosaurus must leave the wildlife pool");
        int tamedBefore = hearts(world, pos);
        tamed.hurtServer(world, hunter.damageSources().playerAttack(hunter), 1000f);
        h.assertTrue(hearts(world, pos) == tamedBefore, "A tamed Allosaurus must not drop a heart");
        discard(world, pos, ModContent.ALLOSAUR_HEART.get().getDefaultInstance());
        hunter.discard();
        h.succeed();
    }

    static void policy(GameTestHelper h) {
        h.assertTrue(GuardianPolicy.bossHealth(400, 1, 0, 200, 60, 4) == 400, "One player is the base pool");
        h.assertTrue(GuardianPolicy.bossHealth(400, 2, 2, 200, 60, 4) == 720, "The duo pool is wrong");
        h.assertTrue(GuardianPolicy.bossHealth(400, 2, 9, 200, 60, 4) == 840, "Tame health must cap at four");
        h.assertTrue(GuardianPolicy.tameDamage(true, 40, 0.1) == 40, "Registered tames deal full damage");
        h.assertTrue(Math.abs(GuardianPolicy.tameDamage(false, 40, 0.1) - 4) < 1e-9,
                "Unregistered tames must be scaled down");
        h.assertTrue(GuardianPolicy.tameDamage(false, 40, 0.0) == 0, "A zero factor disables the damage");
        h.assertFalse(GuardianPolicy.resetDue(100, 0, 600), "An active arena must not reset");
        h.assertTrue(GuardianPolicy.resetDue(1000, 300, 600), "The grace window must end the attempt");
        h.assertTrue(GuardianPolicy.retryReady(500, 400), "The retry window must reopen");
        h.assertFalse(GuardianPolicy.retryReady(300, 400), "The retry window must stay closed before its time");
        h.succeed();
    }

    static void registration(GameTestHelper h) {
        ServerLevel world = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(8, 3, 8));
        var guardian = ModContent.GUARDIAN_GIGANOTOSAURUS.get().create(world, EntitySpawnReason.MOB_SUMMONED);
        guardian.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 0f);
        world.addFreshEntity(guardian);
        h.assertFalse(guardian.isNaturalWildlife(), "The guardian must never count as wildlife");
        h.assertFalse(guardian.removeWhenFarAway(100000), "The guardian must never despawn");
        h.assertTrue(guardian.isPersistenceRequired(), "The guardian must persist");
        h.assertFalse(TorporService.eligible(guardian), "The guardian must be immune to sedation");
        h.assertTrue(guardian.getAttributeValue(Attributes.ARMOR) >= 8.0, "The guardian armor default is missing");
        guardian.discard();
        h.succeed();
    }

    static void persistence(GameTestHelper h) {
        ServerLevel world = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(8, 3, 8));
        GuardianEncounter encounter = new GuardianEncounter("test|key", world.dimension(), pos, STRUCTURE,
                UUID.randomUUID(), GuardianState.ACTIVE, Optional.of(UUID.randomUUID()), Set.of(UUID.randomUUID()),
                Set.of(UUID.randomUUID()), 640.0, false, 1234L, 99L);
        DataResult<net.minecraft.nbt.Tag> encoded = GuardianEncounter.CODEC.encodeStart(NbtOps.INSTANCE, encounter);
        GuardianEncounter decoded = GuardianEncounter.CODEC.parse(NbtOps.INSTANCE, encoded.getOrThrow()).getOrThrow();
        h.assertTrue(decoded.equals(encounter), "The encounter must survive a codec round-trip");
        h.assertTrue(decoded.withState(GuardianState.READY).state() == GuardianState.READY, "State transition lost");

        GuardianData data = GuardianData.get(world);
        data.put(encounter);
        h.assertTrue(data.find("test|key").isPresent(), "The encounter was not stored");
        data.remove("test|key");
        h.assertFalse(data.find("test|key").isPresent(), "The encounter was not removed");
        h.succeed();
    }

    static void rewards(GameTestHelper h) {
        ServerLevel world = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(8, 3, 8));
        UUID tribe = UUID.randomUUID();
        String key = "test|rewards|" + tribe;
        var guardian = ModContent.GUARDIAN_GIGANOTOSAURUS.get().create(world, EntitySpawnReason.MOB_SUMMONED);
        guardian.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 0f);
        world.addFreshEntity(guardian);
        guardian.initializeGuardian(key, pos, 100.0, 1.0, 8.0);
        GuardianData data = GuardianData.get(world);
        data.put(new GuardianEncounter(key, world.dimension(), pos, STRUCTURE, tribe, GuardianState.ACTIVE,
                Optional.of(guardian.getUUID()), Set.of(), Set.of(), 100.0, false, 0L, 0L));

        guardian.hurtServer(world, world.damageSources().generic(), 1000f);
        h.assertTrue(TribeProgressData.get(world).has(tribe, TribeProgressData.WORKSHOP_SCHEMATIC),
                "The first victory must grant the shared schematic flag");
        h.assertTrue(data.find(key).map(e -> e.state() == GuardianState.DEFEATED && e.rewardsIssued()).orElse(false),
                "The encounter must be marked defeated with rewards issued");
        int schematics = drops(world, pos, ModContent.WORKSHOP_SCHEMATIC.get().getDefaultInstance());
        h.assertTrue(schematics == 1, "Exactly one schematic must be produced, got " + schematics);
        // Re-entering the death path must not pay a second time.
        GuardianService.onBossDeath(guardian);
        h.assertTrue(drops(world, pos, ModContent.WORKSHOP_SCHEMATIC.get().getDefaultInstance()) == schematics,
                "Rewards were issued twice");
        data.remove(key);
        discard(world, pos, ModContent.WORKSHOP_SCHEMATIC.get().getDefaultInstance());
        discard(world, pos, ModContent.GUARDIAN_TROPHY.get().getDefaultInstance());
        h.succeed();
    }

    private static CreatureEntity allosaurus(ServerLevel world, BlockPos pos, EntitySpawnReason reason) {
        CreatureEntity creature = ModContent.CREATURES.get(Species.ALLOSAURUS).get().create(world, reason);
        creature.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 0f);
        creature.finalizeSpawn(world, world.getCurrentDifficultyAt(pos), reason, null);
        world.addFreshEntity(creature);
        return creature;
    }

    private static int hearts(ServerLevel world, BlockPos pos) {
        return drops(world, pos, ModContent.ALLOSAUR_HEART.get().getDefaultInstance());
    }

    private static int drops(ServerLevel world, BlockPos pos, net.minecraft.world.item.ItemStack stack) {
        return world.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(8),
                item -> net.minecraft.world.item.ItemStack.isSameItemSameComponents(item.getItem(), stack)).size();
    }

    private static void discard(ServerLevel world, BlockPos pos, net.minecraft.world.item.ItemStack stack) {
        for (ItemEntity item : world.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(8),
                candidate -> net.minecraft.world.item.ItemStack.isSameItemSameComponents(candidate.getItem(), stack))) {
            item.discard();
        }
    }

    private GuardianGameTests() {}
}
