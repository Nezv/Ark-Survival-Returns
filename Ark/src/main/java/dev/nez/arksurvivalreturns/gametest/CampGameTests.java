package dev.nez.arksurvivalreturns.gametest;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.camp.StarterKitData;
import dev.nez.arksurvivalreturns.feature.camp.StarterKitService;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import dev.nez.arksurvivalreturns.feature.camp.BedrollBlock;
import dev.nez.arksurvivalreturns.feature.camp.MattressBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/** Camp setup: one-time starter kit and the bedroll respawn contract. */
final class CampGameTests {
    /** A fresh UUID per run keeps the world-scoped grant data from leaking between runs. */
    private static FakePlayer survivor(ServerLevel world) {
        return FakePlayerFactory.get(world, new GameProfile(UUID.randomUUID(), "ArkCampTest"));
    }

    static void starterKit(GameTestHelper h) {
        ServerLevel world = h.getLevel();
        FakePlayer player = survivor(world);
        player.getInventory().clearContent();
        var data = StarterKitData.get(world);
        h.assertFalse(data.granted(player.getUUID()), "A fresh survivor already had a kit");
        StarterKitService.onLogin(player);
        h.assertTrue(count(player, ModContent.FLINT_KNIFE.get()) == 1, "The kit did not include a flint knife");
        h.assertTrue(count(player, ModContent.BEDROLL_ITEM.get()) == 0 && count(player, ModContent.MATTRESS_ITEM.get()) == 0
                        && count(player, ModContent.PLANT_FIBER.get()) == 0,
                "The kit is only a flint knife");
        h.assertTrue(data.granted(player.getUUID()), "The kit grant was not recorded");
        // Rejoining never duplicates the kit.
        StarterKitService.onLogin(player);
        h.assertTrue(count(player, ModContent.FLINT_KNIFE.get()) == 1, "Rejoining duplicated the kit");
        // The config switch blocks new kits but does not retract a recorded grant.
        boolean enabled = Config.CAMP_STARTER_KIT.get();
        FakePlayer newcomer = survivor(world);
        newcomer.getInventory().clearContent();
        Config.CAMP_STARTER_KIT.set(false);
        try {
            StarterKitService.onLogin(player);
            StarterKitService.onLogin(newcomer);
            h.assertTrue(newcomer.getInventory().isEmpty(), "A disabled kit was granted to a fresh survivor");
            h.assertFalse(data.granted(newcomer.getUUID()), "A disabled kit consumed the survivor's future grant");
            Config.CAMP_STARTER_KIT.set(true);
            StarterKitService.onLogin(newcomer);
            StarterKitService.onLogin(newcomer);
            h.assertTrue(count(newcomer, ModContent.FLINT_KNIFE.get()) == 1 && data.granted(newcomer.getUUID()),
                    "Re-enabling the kit must grant it exactly once");
        } finally {
            Config.CAMP_STARTER_KIT.set(enabled);
        }
        h.assertTrue(count(player, ModContent.FLINT_KNIFE.get()) == 1, "A disabled kit was granted");
        h.succeed();
    }

    static void bedrollSpawn(GameTestHelper h) {
        ServerLevel world = h.getLevel();
        FakePlayer player = survivor(world);
        player.getInventory().clearContent();
        BlockPos pos = h.absolutePos(new BlockPos(4, 2, 4));
        BlockPos head = pos.north();
        // Ground under the 2x2 footprint and beside it, where the owner stands up.
        for (BlockPos ground : BlockPos.betweenClosed(pos.offset(-1, -1, -2), pos.offset(1, -1, 1))) {
            world.setBlockAndUpdate(ground, Blocks.STONE.defaultBlockState());
        }
        placeBedroll(world, pos);
        player.setPos(Vec3.atBottomCenterOf(pos));
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);

        // By day the sleep attempt fails but, like a vanilla bed, still saves the respawn point.
        var result = ModContent.BEDROLL.get().useWithoutItem(world.getBlockState(pos), world, pos, player, hit);
        h.assertTrue(result.consumesAction(), "The bedroll did not accept the interaction");
        var config = player.getRespawnConfig();
        h.assertTrue(config != null, "The respawn point was not set");
        h.assertTrue(config.respawnData().globalPos().pos().equals(head), "The respawn point must be the bedroll's head-left cell");
        h.assertTrue(world.getBlockState(head).getRespawnPosition(EntityType.PLAYER, world, head, 0.0f).isPresent(),
                "Respawning at the bedroll must find a place to stand beside it");

        // By night it is a real bed that survives the sleep: reusable, unlike the Mattress.
        atNight(world, () -> {
            ModContent.BEDROLL.get().useWithoutItem(world.getBlockState(pos), world, pos, player, hit);
            h.assertTrue(player.isSleeping(), "The player must fall asleep in a bedroll at night");
            player.stopSleepInBed(true, true);
            h.assertTrue(world.getBlockState(head).is(ModContent.BEDROLL.get()), "The bedroll must survive a night's sleep");
        });

        // The saved point lives in player data, not in the block: destroying it must not strand anyone.
        world.removeBlock(pos, false);
        h.assertTrue(world.getBlockState(head).isAir(), "The whole 2x2 footprint must break together");
        h.assertTrue(player.getRespawnConfig() != null, "Destroying the bedroll removed the respawn point");

        // Sneak-use rolls every cell back up and leaves the saved respawn point alone.
        placeBedroll(world, pos);
        player.setShiftKeyDown(true);
        ModContent.BEDROLL.get().useWithoutItem(world.getBlockState(pos), world, pos, player, hit);
        player.setShiftKeyDown(false);
        h.assertTrue(world.getBlockState(pos).isAir() && world.getBlockState(head).isAir(), "Sneak-use did not remove the bedroll");
        h.assertTrue(count(player, ModContent.BEDROLL_ITEM.get()) == 1, "The rolled-up bedroll was not returned");
        h.assertTrue(player.getRespawnConfig() != null, "Picking the bedroll up cleared the respawn point");
        h.succeed();
    }

    /**
     * Mattress: waking up from it consumes both halves with no drop and never sets a
     * respawn point, unlike the reusable Bedroll.
     */
    static void mattressSleep(GameTestHelper h) {
        ServerLevel world = h.getLevel();
        FakePlayer player = survivor(world);
        player.getInventory().clearContent();
        BlockPos pos = h.absolutePos(new BlockPos(4, 2, 4));
        BlockPos head = pos.north();
        for (BlockPos ground : BlockPos.betweenClosed(pos.offset(-1, -1, -2), pos.offset(1, -1, 1))) {
            world.setBlockAndUpdate(ground, Blocks.STONE.defaultBlockState());
        }
        var state = ModContent.MATTRESS.get().defaultBlockState();
        world.setBlockAndUpdate(pos, state);
        world.setBlockAndUpdate(head, state.setValue(MattressBlock.PART, BedPart.HEAD));
        player.setPos(Vec3.atBottomCenterOf(pos));
        var beforeRespawn = player.getRespawnConfig();
        var hit = new BlockHitResult(Vec3.atCenterOf(head), Direction.UP, head, false);

        atNight(world, () -> {
            var result = ModContent.MATTRESS.get().useWithoutItem(world.getBlockState(head), world, head, player, hit);
            h.assertTrue(result.consumesAction(), "The mattress did not accept the interaction");
            h.assertTrue(player.isSleeping(), "The player must actually fall asleep in a mattress");
            h.assertTrue(java.util.Objects.equals(player.getRespawnConfig(), beforeRespawn),
                    "Sleeping in a mattress must never set a respawn point");

            player.stopSleepInBed(true, true);
        });

        h.assertTrue(world.getBlockState(pos).isAir() && world.getBlockState(head).isAir(),
                "The mattress must be consumed once its sleeper wakes");
        h.assertTrue(count(player, ModContent.MATTRESS_ITEM.get()) == 0, "A consumed mattress must not drop");
        h.assertTrue(java.util.Objects.equals(player.getRespawnConfig(), beforeRespawn),
                "Consuming the mattress must not touch the respawn point either");
        h.succeed();
    }

    /** Runs {@code body} at midnight (sky darkness refreshed at once) and restores the clock afterwards. */
    private static void atNight(ServerLevel world, Runnable body) {
        var clock = world.dimensionType().defaultClock().orElseThrow();
        long oldTime = world.getDefaultClockTime();
        try {
            world.clockManager().setTotalTicks(clock, 18000);
            world.updateSkyBrightness();
            body.run();
        } finally {
            world.clockManager().setTotalTicks(clock, oldTime);
            world.updateSkyBrightness();
        }
    }

    /** The four cells as the block item places them, facing north: foot-left here, the rest derived from it. */
    private static void placeBedroll(ServerLevel world, BlockPos footLeft) {
        var state = ModContent.BEDROLL.get().defaultBlockState();
        world.setBlockAndUpdate(footLeft, state);
        world.setBlockAndUpdate(footLeft.east(), state.setValue(BedrollBlock.SIDE, BedrollBlock.Side.RIGHT));
        world.setBlockAndUpdate(footLeft.north(), state.setValue(BedrollBlock.PART, BedPart.HEAD));
        world.setBlockAndUpdate(footLeft.north().east(), state.setValue(BedrollBlock.PART, BedPart.HEAD).setValue(BedrollBlock.SIDE, BedrollBlock.Side.RIGHT));
    }

    private static int count(Player player, Item item) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    private CampGameTests() {}
}
