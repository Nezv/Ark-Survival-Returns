package dev.nez.arksurvivalreturns.gametest;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.cargo.CargoProfiles;
import dev.nez.arksurvivalreturns.feature.cargo.CargoTransferService;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.feature.mass.MassCalculator;
import dev.nez.arksurvivalreturns.feature.mass.MassRules;
import dev.nez.arksurvivalreturns.feature.mass.MassService;
import dev.nez.arksurvivalreturns.feature.taming.TamingService;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/** Cargo capacity, harness gating, mount overload and the bounded Fast Load/Unload transfer. */
final class CargoGameTests {
    private static CreatureEntity create(GameTestHelper h, Species species, BlockPos relative) {
        var entity = ModContent.CREATURES.get(species).get().create(h.getLevel(), EntitySpawnReason.COMMAND);
        entity.setNoAi(true);
        entity.setPos(Vec3.atCenterOf(h.absolutePos(relative)));
        return entity;
    }

    /** Load and Unload only reach storage in the player's sight, so the hauler stands beside the tame. */
    private static FakePlayer hauler(GameTestHelper h, String name) {
        FakePlayer player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), name));
        player.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(8, 3, 5))));
        return player;
    }

    private static Container chest(GameTestHelper h, BlockPos relative, ItemStack content) {
        h.setBlock(relative, Blocks.CHEST.defaultBlockState());
        Container chest = (Container) h.getLevel().getBlockEntity(h.absolutePos(relative));
        chest.setItem(0, content);
        return chest;
    }

    static void load(GameTestHelper h) {
        FakePlayer owner = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "ArkCargoOwner"));
        CreatureEntity trike = create(h, Species.TRICERATOPS, new BlockPos(8, 3, 8));
        TamingService.of(trike).setOwner(owner.getUUID());
        trike.applyTameState();

        h.assertTrue(trike.harnessTier() == CargoProfiles.Harness.NONE, "A fresh tame has no harness");
        h.assertTrue(Math.abs(MassService.creatureCapacity(trike) - Config.MASS_BARE_CAPACITY.get()) < 0.001,
                "Without a harness the bare allowance applies");

        trike.harnessSlot().setItem(0, new ItemStack(ModContent.PACK_HARNESS.get()));
        h.assertTrue(trike.harnessTier() == CargoProfiles.Harness.PACK, "The pack harness must register");
        h.assertTrue(Math.abs(MassService.creatureCapacity(trike) - Config.CARGO_CAPACITY.get(Species.TRICERATOPS).get()) < 0.001,
                "The pack harness must unlock the species capacity");

        trike.tamingInventory().setItem(0, new ItemStack(Items.STONE, 64));
        MassService.refreshCreature(trike);
        var load = MassService.creatureLoad(trike);
        double expected = 64.0 + MassCalculator.massOf(trike.harnessSlot().getItem(0));
        h.assertTrue(Math.abs(load.mass() - expected) < 0.2, "The hold should weigh stone plus rig, got " + load.mass());
        h.assertTrue(load.band() == MassRules.Band.NORMAL, "A light hold must not warn");

        for (int slot = 1; slot <= 3; slot++) trike.tamingInventory().setItem(slot, new ItemStack(Items.RAW_IRON, 64));
        MassService.refreshCreature(trike);
        load = MassService.creatureLoad(trike);
        h.assertTrue(load.ratio() > 1.0, "The hold should be overloaded, got " + load.ratio());
        h.assertTrue(trike.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(MassService.OVERLOAD_MODIFIER) != null,
                "An overloaded animal must be slowed");

        // A reinforced species refuses to treat the light harness as enough.
        CreatureEntity bronto = create(h, Species.BRONTOSAURUS, new BlockPos(12, 3, 8));
        TamingService.of(bronto).setOwner(owner.getUUID());
        bronto.harnessSlot().setItem(0, new ItemStack(ModContent.PACK_HARNESS.get()));
        h.assertTrue(Math.abs(MassService.creatureCapacity(bronto) - Config.MASS_BARE_CAPACITY.get()) < 0.001,
                "A reinforced species must not accept the light harness");
        bronto.harnessSlot().setItem(0, new ItemStack(ModContent.REINFORCED_HARNESS.get()));
        h.assertTrue(Math.abs(MassService.creatureCapacity(bronto) - Config.CARGO_CAPACITY.get(Species.BRONTOSAURUS).get()) < 0.001,
                "The reinforced harness must unlock the heavy capacity");

        trike.tamingInventory().clearContent();
        MassService.refreshCreature(trike);
        h.assertTrue(trike.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(MassService.OVERLOAD_MODIFIER) == null,
                "Emptying the hold must remove the slowdown");
        trike.discard();
        bronto.discard();
        h.succeed();
    }

    static void transfer(GameTestHelper h) {
        FakePlayer owner = hauler(h, "ArkCargoHauler");
        CreatureEntity trike = create(h, Species.TRICERATOPS, new BlockPos(8, 3, 8));
        TamingService.of(trike).setOwner(owner.getUUID());
        trike.applyTameState();
        trike.harnessSlot().setItem(0, new ItemStack(ModContent.PACK_HARNESS.get()));
        ServerLevel level = h.getLevel();
        int chunks = level.getChunkSource().getLoadedChunksCount();

        BlockPos near = new BlockPos(10, 3, 8);
        h.setBlock(near, Blocks.CHEST.defaultBlockState());
        Container chest = (Container) level.getBlockEntity(h.absolutePos(near));
        chest.setItem(0, new ItemStack(Items.RAW_IRON, 64));
        h.assertTrue(CargoTransferService.load(owner, trike) == 64, "Fast Load must move the whole stack");
        h.assertTrue(chest.getItem(0).isEmpty(), "The chest must be emptied by Fast Load");
        h.assertTrue(trike.tamingInventory().getItem(0).getCount() == 64, "The load must land in the hold");

        // The ceiling stops the second stack: 64 + 128 units would cross the 350 capacity.
        chest.setItem(0, new ItemStack(Items.RAW_IRON, 64));
        chest.setItem(1, new ItemStack(Items.RAW_IRON, 64));
        int second = CargoTransferService.load(owner, trike);
        h.assertTrue(second == 64, "Fast Load must stop at the ceiling, moved " + second);
        h.assertTrue(!chest.getItem(1).isEmpty(), "The stack past the ceiling must stay in storage");
        h.assertTrue(MassService.creatureLoad(trike).ratio() <= MassRules.automationCeiling() + 0.001,
                "Fast Load must never cross the automation ceiling");

        BlockPos other = new BlockPos(6, 3, 8);
        h.setBlock(other, Blocks.CHEST.defaultBlockState());
        Container secondChest = (Container) level.getBlockEntity(h.absolutePos(other));
        int inHold = count(trike.tamingInventory());
        int unloaded = CargoTransferService.unload(owner, trike);
        h.assertTrue(unloaded == inHold, "Fast Unload must move the whole hold, moved " + unloaded);
        h.assertTrue(count(trike.tamingInventory()) == 0, "The hold must empty on Fast Unload");
        h.assertTrue(count(chest) + count(secondChest) == inHold + 64,
                "Unloaded cargo must be in the nearby storage");
        h.assertTrue(level.getChunkSource().getLoadedChunksCount() == chunks,
                "Cargo transfer must never load a chunk");
        trike.discard();
        h.succeed();
    }

    /**
     * Fast Load reaches what the player could open by hand: a chest in sight and one seen past another
     * chest, but not one behind a wall, which loads once the wall is gone.
     */
    static void reach(GameTestHelper h) {
        FakePlayer owner = hauler(h, "ArkCargoReach");
        // The radius-8 volume must remain inside this test's large plot.
        owner.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(64, 3, 61))));
        CreatureEntity trike = create(h, Species.TRICERATOPS, new BlockPos(64, 3, 64));
        TamingService.of(trike).setOwner(owner.getUUID());
        trike.applyTameState();
        trike.harnessSlot().setItem(0, new ItemStack(ModContent.PACK_HARNESS.get()));
        Container cargo = trike.tamingInventory();

        chest(h, new BlockPos(66, 3, 64), new ItemStack(Items.RAW_IRON));
        chest(h, new BlockPos(64, 3, 66), ItemStack.EMPTY);
        chest(h, new BlockPos(64, 3, 67), new ItemStack(Items.GOLD_INGOT));
        for (int z = 60; z <= 68; z++) {
            for (int y = 3; y <= 6; y++) h.setBlock(new BlockPos(67, y, z), Blocks.STONE.defaultBlockState());
        }
        chest(h, new BlockPos(68, 3, 64), new ItemStack(Items.DIAMOND));

        int radius = Config.CARGO_TRANSFER_RADIUS.get();
        Config.CARGO_TRANSFER_RADIUS.set(8);
        try {
            reachChecks(h, owner, trike, cargo);
        } finally {
            Config.CARGO_TRANSFER_RADIUS.set(radius);
        }
        trike.discard();
        h.succeed();
    }

    private static void reachChecks(GameTestHelper h, FakePlayer owner, CreatureEntity trike, Container cargo) {
        int moved = CargoTransferService.load(owner, trike);
        h.assertTrue(cargo.countItem(Items.RAW_IRON) == 1, "A chest in sight must load");
        h.assertTrue(cargo.countItem(Items.GOLD_INGOT) == 1, "A chest seen past another chest must load");
        h.assertTrue(cargo.countItem(Items.DIAMOND) == 0, "A chest behind a wall must not load");
        h.assertTrue(moved == 2, "Fast Load moved " + moved + " instead of the two visible stacks");

        for (int z = 60; z <= 68; z++) {
            for (int y = 3; y <= 6; y++) h.setBlock(new BlockPos(67, y, z), Blocks.AIR.defaultBlockState());
        }
        h.assertTrue(CargoTransferService.load(owner, trike) == 1, "Without the wall the far chest must load");
        h.assertTrue(cargo.countItem(Items.DIAMOND) == 1, "The far chest's diamond must land in the hold");

        // Opposite corners at maximum horizontal and vertical reach catch scan truncation in any chunk alignment.
        chest(h, new BlockPos(56, 7, 56), new ItemStack(Items.EMERALD));
        chest(h, new BlockPos(72, 7, 72), new ItemStack(Items.LAPIS_LAZULI));
        int chunks = h.getLevel().getChunkSource().getLoadedChunksCount();
        h.assertTrue(CargoTransferService.load(owner, trike) == 2, "The full radius-8 scan must reach both outer corners");
        h.assertTrue(cargo.countItem(Items.EMERALD) == 1 && cargo.countItem(Items.LAPIS_LAZULI) == 1,
                "Storage at the edge of the scan volume was skipped");
        h.assertTrue(h.getLevel().getChunkSource().getLoadedChunksCount() == chunks, "The full scan loaded a chunk");
    }

    /**
     * The hold and the harness drop on death even with doMobLoot off, a creature that leaves the level is
     * forgotten by the load map, and the population budget never culls a creature someone is taming.
     */
    static void deathAndCull(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        FakePlayer owner = hauler(h, "ArkCargoMourner");
        CreatureEntity trike = create(h, Species.TRICERATOPS, new BlockPos(8, 3, 8));
        level.addFreshEntity(trike);
        TamingService.of(trike).setOwner(owner.getUUID());
        trike.applyTameState();
        trike.harnessSlot().setItem(0, new ItemStack(ModContent.PACK_HARNESS.get()));
        trike.tamingInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 5));
        var rules = level.getGameRules();
        rules.set(net.minecraft.world.level.gamerules.GameRules.MOB_DROPS, false, level.getServer());
        try {
            trike.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
        } finally {
            rules.set(net.minecraft.world.level.gamerules.GameRules.MOB_DROPS, true, level.getServer());
        }
        var around = trike.getBoundingBox().inflate(4);
        int stone = 0, harness = 0;
        for (var item : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, around)) {
            if (item.getItem().is(Items.COBBLESTONE)) stone += item.getItem().getCount();
            if (item.getItem().is(ModContent.PACK_HARNESS.get())) harness += item.getItem().getCount();
            item.discard();
        }
        h.assertTrue(stone == 5 && harness == 1, "doMobLoot off deleted the hold (" + stone + " stone, " + harness + " harness)");

        CreatureEntity leaving = create(h, Species.TRICERATOPS, new BlockPos(4, 3, 4));
        level.addFreshEntity(leaving);
        leaving.tamingInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        MassService.refreshCreature(leaving);
        h.assertTrue(MassService.creatureLoad(leaving).mass() > 0, "The load must be tracked while in the level");
        leaving.discard();
        h.assertTrue(MassService.creatureLoad(leaving).mass() == 0, "A creature that left the level must be forgotten");

        CreatureEntity wild = create(h, Species.PARASAUR, new BlockPos(12, 3, 12));
        h.assertTrue(dev.nez.arksurvivalreturns.feature.spawn.NaturalPopulations.cullable(wild), "Plain wildlife must stay cullable");
        TamingService.of(wild).claim(owner.getUUID(), level.getGameTime());
        h.assertFalse(dev.nez.arksurvivalreturns.feature.spawn.NaturalPopulations.cullable(wild), "A claimed creature was cullable");
        CreatureEntity fed = create(h, Species.PARASAUR, new BlockPos(12, 3, 4));
        TamingService.of(fed).setProgress(10f);
        h.assertFalse(dev.nez.arksurvivalreturns.feature.spawn.NaturalPopulations.cullable(fed), "A half-tamed creature was cullable");
        CreatureEntity stocked = create(h, Species.PARASAUR, new BlockPos(4, 3, 12));
        stocked.tamingInventory().setItem(0, new ItemStack(Items.WHEAT, 3));
        h.assertFalse(dev.nez.arksurvivalreturns.feature.spawn.NaturalPopulations.cullable(stocked), "A creature holding food was cullable");
        h.succeed();
    }

    static void overloadFlight(GameTestHelper h) {
        Player rider = h.makeMockPlayer(GameType.SURVIVAL);
        CreatureEntity bird = create(h, Species.PTERANODON, new BlockPos(8, 3, 8));
        TamingService.of(bird).setOwner(rider.getUUID());
        bird.applyTameState();
        bird.setItemSlot(EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
        bird.harnessSlot().setItem(0, new ItemStack(ModContent.PACK_HARNESS.get()));
        bird.tamingInventory().setItem(0, new ItemStack(Items.STONE, 64));
        bird.tamingInventory().setItem(1, new ItemStack(Items.STONE, 64));
        MassService.refreshCreature(bird);
        h.assertTrue(MassService.overloaded(bird), "128 units must overload a 100-capacity scout");
        // The rider's client moves a ridden mount, so the overload must travel as synced entity data.
        h.assertTrue(bird.overloaded(), "The overload flag must be set for the rider's client");
        h.assertTrue(rider.startRiding(bird), "The rider must mount");

        bird.setOnGround(true);
        bird.travel(new Vec3(0, 1, 0));
        h.assertTrue(bird.getDeltaMovement().y <= 0.001, "An overloaded bird must not take off");
        h.assertFalse(bird.overloadWarningDue(), "The refused takeoff must have warned the rider");

        bird.setOnGround(false);
        bird.travel(new Vec3(0, 1, 0));
        h.assertTrue(bird.getDeltaMovement().y < 0.0, "An airborne overloaded bird must descend in control");

        bird.tamingInventory().clearContent();
        MassService.refreshCreature(bird);
        h.assertFalse(bird.overloaded(), "Emptying the hold must clear the synced overload flag");
        bird.discard();
        h.succeed();
    }

    static void overloadSwim(GameTestHelper h) {
        Player rider = h.makeMockPlayer(GameType.SURVIVAL);
        CreatureEntity shark = create(h, Species.MEGALODON, new BlockPos(8, 3, 8));
        TamingService.of(shark).setOwner(rider.getUUID());
        shark.applyTameState();
        shark.setItemSlot(EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
        shark.tamingInventory().setItem(0, new ItemStack(Items.STONE, 64));
        shark.tamingInventory().setItem(1, new ItemStack(Items.STONE, 64));
        MassService.refreshCreature(shark);
        h.assertTrue(MassService.overloaded(shark), "128 units must overload the bare 100 allowance");
        h.assertTrue(rider.startRiding(shark), "The rider must mount");

        shark.travel(new Vec3(0, -1, 0));
        h.assertTrue(shark.getDeltaMovement().y >= 0.0, "An overloaded swimmer must not dive");
        shark.discard();
        h.succeed();
    }

    private static int count(Container container) {
        int total = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) total += container.getItem(slot).getCount();
        return total;
    }

    private CargoGameTests() {}
}
