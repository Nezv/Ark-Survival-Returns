package dev.nez.arksurvivalreturns.gametest;

import dev.nez.arksurvivalreturns.feature.storage.StorageCrateBlockEntity;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The storage crate: 27 fixed slots, contents preserved and dropped on removal. */
final class StorageGameTests {
    static void slots(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        int chunks = level.getChunkSource().getLoadedChunksCount();
        BlockPos crateRel = new BlockPos(8, 3, 8);
        h.setBlock(crateRel, ModContent.STORAGE_CRATE.get().defaultBlockState());
        BlockPos cratePos = h.absolutePos(crateRel);
        StorageCrateBlockEntity crate = (StorageCrateBlockEntity) level.getBlockEntity(cratePos);
        h.assertTrue(crate != null, "The storage crate block entity is missing");
        h.assertTrue(crate.getContainerSize() == 27, "The crate must expose 27 slots");

        for (int slot = 0; slot < 27; slot++) {
            crate.setItem(slot, new ItemStack(Items.STONE, slot + 1));
        }
        h.assertTrue(crate.getItem(26).getCount() == 27, "The last crate slot must hold its stack");

        var output = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess());
        crate.saveCustomOnly(output);
        StorageCrateBlockEntity restored = new StorageCrateBlockEntity(cratePos, crate.getBlockState());
        restored.loadCustomOnly(net.minecraft.world.level.storage.TagValueInput.create(
                net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), output.buildResult()));
        h.assertTrue(restored.getItem(7).is(Items.STONE) && restored.getItem(7).getCount() == 8,
                "Crate contents must survive a save/load round trip");

        h.assertTrue(level.getChunkSource().getLoadedChunksCount() == chunks, "The crate must not load chunks");
        h.succeed();
    }

    private StorageGameTests() {}
}
