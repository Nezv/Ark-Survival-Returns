package dev.nez.arksurvivalreturns.gametest;

import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.forge.BatchStationBlockEntity;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The forge stations: kiln charcoal, forge ingots, rejected inputs and chunk safety. */
final class ForgeGameTests {
    static void batch(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        int chunks = level.getChunkSource().getLoadedChunksCount();

        BlockPos kilnRel = new BlockPos(8, 3, 8);
        h.setBlock(kilnRel, ModContent.CHARCOAL_KILN.get().defaultBlockState());
        BatchStationBlockEntity kiln = (BatchStationBlockEntity) level.getBlockEntity(h.absolutePos(kilnRel));
        h.assertTrue(kiln != null, "The charcoal kiln block entity is missing");
        h.assertTrue(!kiln.advance(), "An empty kiln must not process");
        h.assertTrue(kiln.insert(new ItemStack(Items.DIRT)) == 0, "The kiln must reject non-wood input");
        h.assertTrue(kiln.insert(new ItemStack(Items.OAK_LOG, 4)) == 4, "The kiln must accept logs");
        for (int i = 0; i < Config.KILN_BATCHES.get() - 1; i++) {
            h.assertTrue(kiln.advance(), "The kiln must advance each batch");
        }
        h.assertTrue(kiln.getItem(BatchStationBlockEntity.OUTPUT).isEmpty(), "Charcoal must wait for the last batch");
        h.assertTrue(kiln.advance(), "The final kiln batch must finish");
        h.assertTrue(kiln.getItem(BatchStationBlockEntity.OUTPUT).is(Items.CHARCOAL), "The kiln must produce charcoal");
        h.assertTrue(kiln.getItem(BatchStationBlockEntity.INPUT).getCount() == 3, "One log is consumed per item");

        BlockPos forgeRel = new BlockPos(10, 3, 8);
        h.setBlock(forgeRel, ModContent.PRIMITIVE_FORGE.get().defaultBlockState());
        BatchStationBlockEntity forge = (BatchStationBlockEntity) level.getBlockEntity(h.absolutePos(forgeRel));
        h.assertTrue(forge != null, "The primitive forge block entity is missing");
        h.assertTrue(forge.insert(new ItemStack(Items.OAK_LOG)) == 0, "The forge must reject non-ore input");
        h.assertTrue(forge.insert(new ItemStack(Items.RAW_IRON, 2)) == 2, "The forge must accept raw iron");
        for (int i = 0; i < Config.FORGE_BATCHES.get(); i++) {
            h.assertTrue(forge.advance(), "The forge must advance each batch");
        }
        h.assertTrue(forge.getItem(BatchStationBlockEntity.OUTPUT).is(Items.IRON_INGOT), "The forge must produce an ingot");
        h.assertTrue(forge.getItem(BatchStationBlockEntity.INPUT).getCount() == 1, "One ore is consumed per item");

        h.assertTrue(level.getChunkSource().getLoadedChunksCount() == chunks, "Forge batches must not load chunks");
        h.succeed();
    }

    private ForgeGameTests() {}
}
