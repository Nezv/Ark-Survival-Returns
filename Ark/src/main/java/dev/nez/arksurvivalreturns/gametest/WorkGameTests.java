package dev.nez.arksurvivalreturns.gametest;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import dev.nez.arksurvivalreturns.feature.companion.CompanionOrder;
import dev.nez.arksurvivalreturns.feature.companion.CompanionService;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.feature.taming.TamingService;
import dev.nez.arksurvivalreturns.feature.tribe.TribeService;
import dev.nez.arksurvivalreturns.feature.work.WorkGoal;
import dev.nez.arksurvivalreturns.feature.work.WorkProfiles;
import dev.nez.arksurvivalreturns.feature.work.WorkProtection;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/** Work orders: job profiles, permissions, supervision, yields and player-build protection. */
final class WorkGameTests {
    static void harvest(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        FakePlayer owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "ArkWorkerOwner"));
        FakePlayer stranger = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "ArkWorkerStranger"));
        CreatureEntity trike = ModContent.CREATURES.get(Species.TRICERATOPS).get().create(level, EntitySpawnReason.COMMAND);
        trike.setNoAi(true);
        trike.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(8, 3, 8))));
        owner.setPos(trike.position());
        stranger.setPos(trike.position());
        level.addFreshEntity(owner);
        level.addFreshEntity(stranger);
        TamingService.of(trike).setOwner(owner.getUUID());
        trike.applyTameState();

        h.assertTrue(WorkProfiles.of(Species.TRICERATOPS).job() == WorkProfiles.Job.FORAGE, "Trike must be a forager");
        h.assertTrue(TribeService.canWork(trike, owner), "The owner always keeps work permission");
        h.assertFalse(TribeService.canWork(trike, stranger), "A stranger must not supervise a job");
        h.assertTrue(WorkGoal.supervised(trike), "The nearby owner must supervise");
        owner.setPos(trike.position().add(200, 0, 0));
        h.assertFalse(WorkGoal.supervised(trike), "The job must pause without a nearby authorized survivor");
        owner.setPos(trike.position());

        CompanionService.setOrder(trike, CompanionOrder.WORK);
        h.assertTrue(CompanionService.of(trike).order() == CompanionOrder.WORK, "WORK order was not stored");
        h.assertTrue(CompanionService.of(trike).anchor() != null, "A work order anchors where it was given");

        BlockPos grass = new BlockPos(9, 3, 8);
        BlockPos tuft = new BlockPos(7, 3, 8);
        BlockPos bush = new BlockPos(8, 3, 9);
        h.setBlock(grass, Blocks.GRASS_BLOCK.defaultBlockState());
        h.setBlock(tuft, Blocks.SHORT_GRASS.defaultBlockState());
        h.setBlock(bush, Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, 3));

        int chunks = level.getChunkSource().getLoadedChunksCount();
        h.assertTrue(WorkGoal.isTarget(trike, level, h.absolutePos(grass)), "Grass must be a valid target");
        h.assertFalse(WorkGoal.isTarget(trike, level, h.absolutePos(new BlockPos(8, 3, 8))), "Air is not a target");

        WorkGoal.harvest(trike, h.absolutePos(tuft));
        h.assertTrue(level.getBlockState(h.absolutePos(tuft)).isAir(), "A grass tuft is cut");
        h.assertTrue(count(trike, ModContent.PLANT_FIBER.get()) >= 1, "Foraging must yield plant fiber");

        WorkGoal.harvest(trike, h.absolutePos(grass));
        h.assertTrue(level.getBlockState(h.absolutePos(grass)).is(Blocks.GRASS_BLOCK),
                "Grass is grazed in place, not dug up");

        WorkGoal.harvest(trike, h.absolutePos(bush));
        h.assertTrue(count(trike, Items.SWEET_BERRIES) >= 2, "A ripe bush yields sweet berries");
        h.assertTrue(level.getBlockState(h.absolutePos(bush)).getValue(SweetBerryBushBlock.AGE) == 1,
                "The bush resets to growing");

        // A block a player placed is never harvested.
        BlockPos protectedTuft = new BlockPos(10, 3, 8);
        h.setBlock(protectedTuft, Blocks.SHORT_GRASS.defaultBlockState());
        WorkProtection.get(level).record(h.absolutePos(protectedTuft));
        h.assertFalse(WorkGoal.isTarget(trike, level, h.absolutePos(protectedTuft)), "A placed block is protected");
        WorkGoal.harvest(trike, h.absolutePos(protectedTuft));
        h.assertFalse(level.getBlockState(h.absolutePos(protectedTuft)).isAir(), "The protected tuft must survive");
        WorkProtection.get(level).forget(h.absolutePos(protectedTuft));
        h.assertFalse(WorkProtection.get(level).protectedAt(h.absolutePos(protectedTuft)),
                "Breaking forgets the protection entry");

        h.assertTrue(level.getChunkSource().getLoadedChunksCount() == chunks, "Work scans must not load chunks");
        trike.discard();
        owner.discard();
        stranger.discard();
        h.succeed();
    }

    private static int count(CreatureEntity creature, Item item) {
        int total = 0;
        for (int slot = 0; slot < creature.tamingInventory().getContainerSize(); slot++) {
            var stack = creature.tamingInventory().getItem(slot);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    private WorkGameTests() {}
}
