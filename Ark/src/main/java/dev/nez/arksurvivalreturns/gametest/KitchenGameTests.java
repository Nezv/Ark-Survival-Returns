package dev.nez.arksurvivalreturns.gametest;

import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.kitchen.CookingPotBlockEntity;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The kitchen: fixed meal matching, one item per slot consumed, and no chunk loading. */
final class KitchenGameTests {
    static void cook(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos potRel = new BlockPos(8, 3, 8);
        h.setBlock(potRel, ModContent.COOKING_POT.get().defaultBlockState());
        BlockPos potPos = h.absolutePos(potRel);
        CookingPotBlockEntity pot = (CookingPotBlockEntity) level.getBlockEntity(potPos);
        h.assertTrue(pot != null, "The cooking pot block entity is missing");
        int chunks = level.getChunkSource().getLoadedChunksCount();

        // An incomplete or unknown set never advances.
        h.assertTrue(!pot.advance(), "An empty pot must not cook");
        pot.setItem(0, new ItemStack(ModContent.DRIED_RATION.get()));
        h.assertTrue(!pot.advance(), "A partial recipe must not cook");

        // Hearty stew: two dried rations, one meat, one carrot.
        pot.setItem(1, new ItemStack(ModContent.DRIED_RATION.get()));
        pot.setItem(2, new ItemStack(Items.COOKED_BEEF));
        pot.setItem(3, new ItemStack(Items.CARROT));
        h.assertTrue(!pot.advance(), "A complete recipe must wait for a lit campfire");
        h.assertTrue(pot.getItem(0).getCount() == 1, "A cold pot consumed ingredients");
        h.setBlock(potRel.below(), net.minecraft.world.level.block.Blocks.CAMPFIRE.defaultBlockState());
        h.assertTrue(level.getBlockState(potPos).getValue(dev.nez.arksurvivalreturns.feature.kitchen.CookingPotBlock.ON_CAMPFIRE),
                "The pot did not adopt its campfire trivet");
        h.assertTrue(pot.isHeated(), "The lit campfire must heat the pot");
        h.assertTrue(pot.advance(), "Heating must start cooking");
        int paused = pot.get(0);
        h.setBlock(potRel.below(), net.minecraft.world.level.block.Blocks.CAMPFIRE.defaultBlockState()
                .setValue(net.minecraft.world.level.block.CampfireBlock.LIT, false));
        h.assertTrue(!pot.advance() && pot.get(0) == paused, "Extinguishing must pause cooking without losing progress");
        h.setBlock(potRel.below(), net.minecraft.world.level.block.Blocks.CAMPFIRE.defaultBlockState());
        for (int i = 1; i < Config.KITCHEN_COOK_BATCHES.get() - 1; i++) {
            h.assertTrue(pot.advance(), "A valid recipe must advance each batch");
        }
        h.assertTrue(pot.getItem(CookingPotBlockEntity.OUTPUT_SLOT).isEmpty(), "The meal must wait for the last batch");
        h.assertTrue(pot.advance(), "The final batch must finish the meal");
        h.assertTrue(pot.getItem(CookingPotBlockEntity.OUTPUT_SLOT).is(ModContent.HEARTY_STEW.get()),
                "The pot must produce a hearty stew");
        for (int slot = 0; slot < CookingPotBlockEntity.INPUT_SLOTS; slot++) {
            h.assertTrue(pot.getItem(slot).isEmpty(), "Every input slot must consume exactly one item");
        }

        // Trail mix: two dried rations and two Ark berries.
        pot.clearContent();
        pot.setItem(0, new ItemStack(ModContent.DRIED_RATION.get()));
        pot.setItem(1, new ItemStack(ModContent.DRIED_RATION.get()));
        pot.setItem(2, new ItemStack(ModContent.BERRIES.get("azulberry").get()));
        pot.setItem(3, new ItemStack(ModContent.BERRIES.get("narcoberry").get()));
        for (int i = 0; i < Config.KITCHEN_COOK_BATCHES.get(); i++) {
            h.assertTrue(pot.advance(), "The trail mix recipe must cook");
        }
        h.assertTrue(pot.getItem(CookingPotBlockEntity.OUTPUT_SLOT).is(ModContent.TRAIL_MIX.get()),
                "The pot must produce trail mix");

        h.setBlock(potRel.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        h.assertTrue(!pot.isHeated(), "Removing the fire must remove heat");
        h.assertTrue(!level.getBlockState(potPos).getValue(dev.nez.arksurvivalreturns.feature.kitchen.CookingPotBlock.ON_CAMPFIRE),
                "The pot must return to the short feet on a solid surface");
        h.assertTrue(level.getChunkSource().getLoadedChunksCount() == chunks, "Cooking must not load chunks");
        h.succeed();
    }

    private KitchenGameTests() {}
}
