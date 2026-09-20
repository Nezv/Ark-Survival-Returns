package dev.nez.arksurvivalreturns.gametest;

import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.recovery.RecoveryData;
import dev.nez.arksurvivalreturns.feature.recovery.RecoveryService;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/** Recovery caches: one haul per death, exact collection and a bounded backlog. */
final class RecoveryGameTests {
    private static FakePlayer survivor(ServerLevel world) {
        return FakePlayerFactory.get(world, new GameProfile(UUID.randomUUID(), "ArkRecoveryTest"));
    }

    static void recoveryCache(GameTestHelper h) {
        ServerLevel world = h.getLevel();
        FakePlayer player = survivor(world);
        player.getInventory().clearContent();
        // Stand the fake survivor on prepared ground so the cache has an obvious safe spot.
        var origin = h.absolutePos(new net.minecraft.core.BlockPos(8, 2, 8));
        world.setBlockAndUpdate(origin.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        for (int dy = 0; dy < 3; dy++) world.setBlockAndUpdate(origin.above(dy), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        player.snapTo(origin.getX() + 0.5, (double) origin.getY(), origin.getZ() + 0.5, 0f, 0f);
        RecoveryData data = RecoveryData.get(world);

        // The drops event is the only production path; the service boundary is what it hands over.
        UUID first = RecoveryService.create(player, List.of(
                new ItemStack(Items.STONE, 12), new ItemStack(ModContent.PLANT_FIBER.get(), 3)));
        h.assertTrue(first != null, "No cache was created");
        var entries = data.entries(player.getUUID());
        h.assertTrue(entries.size() == 1, "Expected exactly one cache");
        var entry = entries.getFirst();
        h.assertTrue(entry.placed(), "The cache was not placed");
        var pos = entry.pos().get();
        h.assertTrue(world.getBlockState(pos).is(ModContent.RECOVERY_CACHE.get()), "The marker block is missing");
        h.assertTrue(world.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(24)).isEmpty(),
                "Loose item entities were left behind");

        // Collecting restores the exact haul and removes both the entry and the marker.
        h.assertTrue(RecoveryService.collect(player, world, pos), "Collection failed");
        h.assertTrue(data.entries(player.getUUID()).isEmpty(), "The entry survived collection");
        h.assertTrue(world.getBlockState(pos).isAir(), "The marker survived collection");
        h.assertTrue(count(player, Items.STONE) == 12, "Stone was not restored");
        h.assertTrue(count(player, ModContent.PLANT_FIBER.get()) == 3, "Fiber was not restored");

        // The cap folds the oldest cache into the newest instead of dropping it.
        player.getInventory().clearContent();
        Config.RECOVERY_MAX_CACHES.set(2);
        try {
            RecoveryService.create(player, List.of(new ItemStack(Items.STONE, 1)));
            RecoveryService.create(player, List.of(new ItemStack(Items.STONE, 2)));
            RecoveryService.create(player, List.of(new ItemStack(Items.STONE, 4)));
            entries = data.entries(player.getUUID());
            h.assertTrue(entries.size() == 2, "The cap did not bound the caches: " + entries.size());
            var newest = entries.getLast();
            h.assertTrue(newest.items().stream().anyMatch(stack -> stack.is(Items.STONE) && stack.getCount() == 1),
                    "The oldest cache's items were not folded forward");
            h.assertTrue(newest.items().stream().anyMatch(stack -> stack.is(Items.STONE) && stack.getCount() == 4),
                    "The new haul was not stored");
            RecoveryService.clear(player, player.createCommandSourceStack());
        } finally {
            Config.RECOVERY_MAX_CACHES.set(3);
        }

        // Destroying a marker keeps the haul claimable as an unplaced entry.
        player.getInventory().clearContent();
        UUID third = RecoveryService.create(player, List.of(new ItemStack(Items.DIRT, 7)));
        var unplaced = data.entries(player.getUUID());
        h.assertTrue(unplaced.size() == 1, "Expected one cache before the removal test");
        world.removeBlock(unplaced.getFirst().pos().orElseThrow(), false);
        var afterRemoval = data.entries(player.getUUID());
        h.assertTrue(afterRemoval.getFirst().pos().isEmpty(), "Removing the marker did not unplace the entry");
        h.assertTrue(RecoveryService.claim(player, 1) == 1, "Claiming the unplaced entry failed");
        h.assertTrue(count(player, Items.DIRT) == 7, "Claimed dirt was not restored");
        h.assertTrue(data.entries(player.getUUID()).isEmpty(), "The claimed entry survived");
        h.assertTrue(third != null, "The removal test created no cache");
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

    private RecoveryGameTests() {}
}
