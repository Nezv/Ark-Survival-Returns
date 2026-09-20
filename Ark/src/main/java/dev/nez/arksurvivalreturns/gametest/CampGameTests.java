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
import net.minecraft.world.level.block.Blocks;
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
        h.assertTrue(count(player, ModContent.BEDROLL_ITEM.get()) == 1, "The kit did not include a bedroll");
        h.assertTrue(count(player, ModContent.FIBER_BANDAGE.get()) == 2, "The kit did not include two bandages");
        h.assertTrue(count(player, ModContent.PLANT_FIBER.get()) == 8, "The kit did not include eight fiber");
        h.assertTrue(count(player, ModContent.FLINT_KNIFE.get()) == 1, "The kit did not include a flint knife");
        h.assertTrue(data.granted(player.getUUID()), "The kit grant was not recorded");
        // Rejoining never duplicates the kit.
        StarterKitService.onLogin(player);
        h.assertTrue(count(player, ModContent.BEDROLL_ITEM.get()) == 1, "Rejoining duplicated the kit");
        h.assertTrue(count(player, ModContent.PLANT_FIBER.get()) == 8, "Rejoining duplicated the fiber");
        // The config switch blocks new kits but does not retract a recorded grant.
        Config.CAMP_STARTER_KIT.set(false);
        try {
            StarterKitService.onLogin(player);
        } finally {
            Config.CAMP_STARTER_KIT.set(true);
        }
        h.assertTrue(count(player, ModContent.PLANT_FIBER.get()) == 8, "A disabled kit was granted");
        h.succeed();
    }

    static void bedrollSpawn(GameTestHelper h) {
        ServerLevel world = h.getLevel();
        FakePlayer player = survivor(world);
        player.getInventory().clearContent();
        BlockPos pos = h.absolutePos(new BlockPos(4, 2, 4));
        world.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        world.setBlockAndUpdate(pos, ModContent.BEDROLL.get().defaultBlockState());
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);

        var result = ModContent.BEDROLL.get().useWithoutItem(world.getBlockState(pos), world, pos, player, hit);
        h.assertTrue(result.consumesAction(), "The bedroll did not accept the interaction");
        var config = player.getRespawnConfig();
        h.assertTrue(config != null, "The respawn point was not set");
        h.assertTrue(config.respawnData().globalPos().pos().equals(pos), "The respawn point is at the wrong position");

        // The saved point lives in player data, not in the block: destroying it must not strand anyone.
        world.removeBlock(pos, false);
        h.assertTrue(player.getRespawnConfig() != null, "Destroying the bedroll removed the respawn point");

        // Sneak-use rolls it back up and leaves the saved respawn point alone.
        world.setBlockAndUpdate(pos, ModContent.BEDROLL.get().defaultBlockState());
        player.setShiftKeyDown(true);
        ModContent.BEDROLL.get().useWithoutItem(world.getBlockState(pos), world, pos, player, hit);
        player.setShiftKeyDown(false);
        h.assertTrue(world.getBlockState(pos).isAir(), "Sneak-use did not remove the bedroll");
        h.assertTrue(count(player, ModContent.BEDROLL_ITEM.get()) == 1, "The rolled-up bedroll was not returned");
        h.assertTrue(player.getRespawnConfig() != null, "Picking the bedroll up cleared the respawn point");
        h.succeed();
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
