package dev.nez.arksurvivalreturns.gametest;

import com.mojang.authlib.GameProfile;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.drop.*;
import dev.nez.arksurvivalreturns.feature.theme.ThemePolicy;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

final class SupplyDropGameTests {
    /** A drop already low over the test, so it lands within it; a real one starts supplyDrops.fallHeight up. */
    private static SupplyDropEntity release(net.minecraft.server.level.ServerLevel world, BlockPos from, SupplyTier tier) {
        var drop = new SupplyDropEntity(world, from.getX() + .5, from.getY(), from.getZ() + .5, tier);
        world.addFreshEntity(drop);
        return drop;
    }

    /** Four tiers of 3, 5, 7 and 9 slots; each loot table always gives something, never more stacks than its crate holds. */
    static void loot(GameTestHelper h) {
        var world = h.getLevel();
        int[] slots = {3, 5, 7, 9};
        for (SupplyTier tier : SupplyTier.values()) {
            h.assertTrue(tier.slots == slots[tier.ordinal()], tier.id + " crate has " + tier.slots + " slots");
            LootTable table = world.getServer().reloadableRegistries().getLootTable(tier.loot);
            h.assertTrue(table != LootTable.EMPTY, "No loot table for the " + tier.id + " drop");
            var params = new LootParams.Builder(world).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(h.absolutePos(BlockPos.ZERO)))
                    .create(LootContextParamSets.CHEST);
            for (long seed = 1; seed <= 40; seed++) {
                var stacks = table.getRandomItems(params, seed);
                h.assertTrue(!stacks.isEmpty() && stacks.size() <= tier.slots,
                        "The " + tier.id + " table gave " + stacks.size() + " stacks for " + tier.slots + " slots");
                for (var stack : stacks) {
                    h.assertTrue(!stack.isEmpty() && !ThemePolicy.removed(stack),
                            "The " + tier.id + " table gives a removed item: " + BuiltInRegistries.ITEM.getKey(stack.getItem()));
                }
            }
        }
        h.assertTrue(SupplyDrops.due(3, 5, 2) && !SupplyDrops.due(3, 4, 2) && !SupplyDrops.due(SupplyDropData.UNSET, 9, 2)
                && !SupplyDrops.due(7, 3, 2), "A drop is due two days after the last one, and only then");
        String line = SupplyDrops.announcement(SupplyTier.BLUE, new BlockPos(120, 71, -340)).getString();
        h.assertTrue(line.contains("120, 71, -340") && line.contains("blue"), "The chat line does not give the coordinates: " + line);
        h.succeed();
    }

    /** The drop comes down through leaves, leaves only the crate, and the crate goes once it is emptied. */
    static void landing(GameTestHelper h) {
        var world = h.getLevel();
        BlockPos floor = new BlockPos(4, 1, 4), crateAt = h.absolutePos(floor.above());
        h.setBlock(floor, Blocks.STONE.defaultBlockState());
        h.setBlock(floor.above(4), Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
        h.setBlock(floor.above(), Blocks.SHORT_GRASS.defaultBlockState());
        var drop = release(world, crateAt.above(8), SupplyTier.GREEN);
        h.assertTrue(drop.isAlive() && drop.tier() == SupplyTier.GREEN && world.getBlockState(crateAt).is(Blocks.SHORT_GRASS),
                "The drop did not start in the air");
        h.runAfterDelay(120, () -> {
            h.assertTrue(drop.isRemoved() && world.getEntitiesOfClass(SupplyDropEntity.class, new AABB(crateAt).inflate(12)).isEmpty(),
                    "The parachute is still there after landing");
            var state = world.getBlockState(crateAt);
            h.assertTrue(state.is(DropContent.LOOT_CRATE.get()) && state.getValue(LootCrateBlock.TIER) == SupplyTier.GREEN,
                    "No green loot crate where the drop landed: " + state);
            h.assertTrue(world.getBlockState(crateAt.above(3)).is(Blocks.OAK_LEAVES), "The drop stopped on the leaves or tore them down");
            var crate = (LootCrateBlockEntity)world.getBlockEntity(crateAt);
            long left = crate.expiresAt() - world.getGameTime();
            h.assertTrue(SupplyTier.GREEN.loot.equals(crate.getLootTable()) && left > 0 && left <= Config.DROP_LIFETIME.get(),
                    "The crate has no loot or no hour to go: " + left);
            h.assertTrue(crate.getBeamSections().size() == 1 && (crate.getBeamSections().getFirst().getColor() & 0xFFFFFF) == SupplyTier.GREEN.colour,
                    "The crate's beam is not green");
            // Opening rolls the loot into five slots; nothing goes in, everything comes out, and the crate goes.
            var player = FakePlayerFactory.get(world, new GameProfile(UUID.randomUUID(), "DropLooter"));
            player.getInventory().clearContent();
            player.snapTo(crateAt.getX() + .5, crateAt.getY() + 1, crateAt.getZ() + .5);
            var menu = (LootCrateMenu)crate.createMenu(31, player.getInventory(), player);
            var remote = new LootCrateMenu(31, player.getInventory(), SupplyTier.GREEN.slots);
            h.assertTrue(menu.slots.size() == 41 && remote.slots.size() == 41 && menu.crateSlots() == 5, "A green crate opens with " + menu.crateSlots() + " slots");
            for (int i = 0; i < 41; i++)
                h.assertTrue(menu.slots.get(i).x == remote.slots.get(i).x && menu.slots.get(i).y == remote.slots.get(i).y, "Slot layouts diverged");
            h.assertTrue(crate.getLootTable() == null && !crate.isEmpty(), "Opening the crate did not roll its loot");
            h.assertFalse(menu.slots.get(0).mayPlace(new net.minecraft.world.item.ItemStack(Blocks.DIRT)), "Items can be put into a loot crate");
            for (int i = 0; i < 5; i++) menu.quickMoveStack(player, i);
            h.assertTrue(crate.isEmpty() && !player.getInventory().isEmpty(), "The loot did not move to the player");
            menu.removed(player);
            h.assertTrue(world.getBlockState(crateAt).isAir(), "An emptied crate stays");
            player.discard();
            h.setBlock(floor.above(4), Blocks.AIR.defaultBlockState());
            h.succeed();
        });
    }

    /** A crate whose hour has passed goes with its loot; one that floats takes the top block of the water. */
    static void expiry(GameTestHelper h) {
        var world = h.getLevel();
        BlockPos floor = new BlockPos(8, 1, 8), crateAt = h.absolutePos(floor.above());
        h.setBlock(floor, Blocks.STONE.defaultBlockState());
        h.setBlock(floor.above(), DropContent.LOOT_CRATE.get().defaultBlockState().setValue(LootCrateBlock.TIER, SupplyTier.PURPLE));
        var crate = (LootCrateBlockEntity)world.getBlockEntity(crateAt);
        h.assertTrue(crate.getContainerSize() == 9, "A purple crate has " + crate.getContainerSize() + " slots");
        crate.arm(world.getGameTime() + 30, 7L);
        // Water held in a glass basin: the drop stops in it instead of sinking to the bottom.
        BlockPos pool = new BlockPos(12, 1, 12);
        for (BlockPos pos : BlockPos.betweenClosed(pool.offset(-1, 0, -1), pool.offset(1, 2, 1))) h.setBlock(pos, Blocks.GLASS.defaultBlockState());
        h.setBlock(pool.above(), Blocks.WATER.defaultBlockState());
        h.setBlock(pool.above(2), Blocks.WATER.defaultBlockState());
        var drop = release(world, h.absolutePos(pool.above(2)).above(4), SupplyTier.WHITE);
        h.runAfterDelay(80, () -> {
            h.assertTrue(world.getBlockState(crateAt).isAir(), "The crate outlived its hour");
            h.assertTrue(world.getEntitiesOfClass(ItemEntity.class, new AABB(crateAt).inflate(3)).isEmpty(), "An expired crate spilled its loot");
            h.assertTrue(drop.isRemoved() && world.getBlockState(h.absolutePos(pool.above(2))).is(DropContent.LOOT_CRATE.get())
                    && world.getBlockState(h.absolutePos(pool.above())).is(Blocks.WATER), "The drop did not come to rest on the water");
            for (BlockPos pos : BlockPos.betweenClosed(pool.offset(-1, 0, -1), pool.offset(1, 2, 1))) h.setBlock(pos, Blocks.AIR.defaultBlockState());
            h.succeed();
        });
    }

    /** A landing place is only ever taken from loaded, ticking chunks, on something to stand on, within the set distance. */
    static void aim(GameTestHelper h) {
        var world = h.getLevel();
        int min = Config.DROP_MIN_DISTANCE.get(), max = Config.DROP_MAX_DISTANCE.get();
        GameTestCleanup.onFinish(h, () -> { Config.DROP_MIN_DISTANCE.set(min); Config.DROP_MAX_DISTANCE.set(max); });
        Config.DROP_MIN_DISTANCE.set(2);
        Config.DROP_MAX_DISTANCE.set(8);
        BlockPos centre = h.absolutePos(new BlockPos(8, 2, 8));
        int found = 0;
        for (int i = 0; i < 20; i++) {
            var landing = SupplyDrops.landing(world, centre, world.getRandom());
            if (landing.isEmpty()) continue;
            found++;
            BlockPos pos = landing.get();
            double away = Math.hypot(pos.getX() - centre.getX(), pos.getZ() - centre.getZ());
            h.assertTrue(away >= 1 && away <= 9, "A drop was aimed " + away + " blocks away");
            h.assertTrue(world.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null && world.isPositionEntityTicking(pos),
                    "A drop was aimed at a chunk that is not ticking");
            h.assertTrue(!world.getFluidState(pos).isEmpty() || SupplyDropEntity.ground(world, pos.below()) || world.getBlockState(pos.below()).is(net.minecraft.tags.BlockTags.LEAVES),
                    "A drop was aimed at thin air: " + world.getBlockState(pos.below()));
        }
        h.assertTrue(found > 0, "No landing place beside the test, in loaded chunks");
        var sent = SupplyDrops.launch(world, centre, SupplyTier.PURPLE);
        h.assertTrue(sent.isAlive() && sent.tier() == SupplyTier.PURPLE && sent.getY() - centre.getY() == Config.DROP_FALL_HEIGHT.get(),
                "A drop does not start its fall height over the landing place");
        sent.discard();
        // Far outside the loaded world nothing is found, and nothing is loaded to look.
        BlockPos nowhere = centre.offset(40000, 0, 40000);
        h.assertTrue(SupplyDrops.landing(world, nowhere, world.getRandom()).isEmpty()
                && world.getChunkSource().getChunkNow(nowhere.getX() >> 4, nowhere.getZ() >> 4) == null, "A drop was aimed into unloaded chunks");
        h.succeed();
    }
}
