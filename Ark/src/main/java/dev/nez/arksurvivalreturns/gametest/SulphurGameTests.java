package dev.nez.arksurvivalreturns.gametest;

import java.util.Optional;
import dev.nez.arksurvivalreturns.feature.explosive.ExplosiveArrow;
import dev.nez.arksurvivalreturns.feature.station.CrusherBlockEntity;
import dev.nez.arksurvivalreturns.feature.station.StationContent;
import dev.nez.arksurvivalreturns.feature.sulphur.SulphurClusterBlock;
import dev.nez.arksurvivalreturns.feature.sulphur.SulphurContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Sulphur crystals, the cave patch that grows them, the Crusher's gunpowder recipe and the explosive arrow. */
final class SulphurGameTests {

    /** Budding sulphur grows a bud on a random tick, the same way vanilla budding amethyst does. */
    static void growth(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos rel = new BlockPos(8, 3, 8);
        h.setBlock(rel, SulphurContent.BUDDING_SULPHUR.get().defaultBlockState());
        for (var side : Direction.values()) h.setBlock(rel.relative(side), Blocks.AIR.defaultBlockState());
        BlockPos pos = h.absolutePos(rel);
        BlockState budding = level.getBlockState(pos);
        boolean grew = false;
        for (int i = 0; i < 500 && !grew; i++) {
            budding.randomTick(level, pos, level.getRandom());
            for (var side : Direction.values()) {
                if (level.getBlockState(pos.relative(side)).is(SulphurContent.SMALL_SULPHUR_BUD.get())) grew = true;
            }
        }
        h.assertTrue(grew, "Budding sulphur must eventually grow a small bud on a free face");
        h.succeed();
    }

    /** The cluster drops sulphur: four with a pickaxe (fortune applies), two by hand, only silk touch keeps it whole. */
    static void clusterDrop(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(8, 3, 8));
        BlockState cluster = SulphurContent.SULPHUR_CLUSTER.get().defaultBlockState()
                .setValue(SulphurClusterBlock.FACING, Direction.UP).setValue(SulphurClusterBlock.WATERLOGGED, false);

        var pickaxeDrops = Block.getDrops(cluster, level, pos, null, null, new ItemStack(Items.IRON_PICKAXE));
        int pickaxeCount = pickaxeDrops.stream().filter(s -> s.is(SulphurContent.SULPHUR.get())).mapToInt(ItemStack::getCount).sum();
        h.assertTrue(pickaxeCount == 4, "A pickaxe must yield four sulphur, got " + pickaxeCount);

        var handDrops = Block.getDrops(cluster, level, pos, null, null, ItemStack.EMPTY);
        int handCount = handDrops.stream().filter(s -> s.is(SulphurContent.SULPHUR.get())).mapToInt(ItemStack::getCount).sum();
        h.assertTrue(handCount == 2, "Bare hands must yield two sulphur, got " + handCount);

        var enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        ItemStack silkPickaxe = new ItemStack(Items.IRON_PICKAXE);
        silkPickaxe.enchant(enchantments.getOrThrow(Enchantments.SILK_TOUCH), 1);
        var silkDrops = Block.getDrops(cluster, level, pos, null, null, silkPickaxe);
        h.assertTrue(silkDrops.size() == 1 && silkDrops.get(0).is(SulphurContent.SULPHUR_CLUSTER_ITEM.get()) && silkDrops.get(0).getCount() == 1,
                "Silk touch must keep the cluster itself");

        // Budding sulphur has no loot table entry at all: it never drops, not even with silk touch.
        var buddingDrops = Block.getDrops(SulphurContent.BUDDING_SULPHUR.get().defaultBlockState(), level, pos, null, null, silkPickaxe);
        h.assertTrue(buddingDrops.isEmpty(), "Budding sulphur must never drop, even with silk touch");
        h.succeed();
    }

    /** The cave feature: a guaranteed budding-sulphur centre topped with a bud, refused on the wrong ground. */
    static void worldgen(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        var feature = SulphurContent.SULPHUR_PATCH_FEATURE.get();

        BlockPos floorRel = new BlockPos(4, 2, 4);
        h.setBlock(floorRel, Blocks.STONE.defaultBlockState());
        h.setBlock(floorRel.above(), Blocks.AIR.defaultBlockState());
        BlockPos originPos = h.absolutePos(floorRel.above());
        boolean placed = feature.place(new FeaturePlaceContext<>(Optional.empty(), level, level.getChunkSource().getGenerator(),
                level.getRandom(), originPos, NoneFeatureConfiguration.INSTANCE));
        h.assertTrue(placed, "A sulphur patch must generate on a cave-stone floor");
        BlockPos floorPos = h.absolutePos(floorRel);
        h.assertTrue(level.getBlockState(floorPos).is(SulphurContent.BUDDING_SULPHUR.get()), "The patch centre must be budding sulphur");
        BlockState above = level.getBlockState(floorPos.above());
        h.assertTrue(above.getBlock() instanceof SulphurClusterBlock, "A bud stage must grow directly on the budding centre");

        BlockPos dirtRel = new BlockPos(6, 2, 4);
        h.setBlock(dirtRel, Blocks.DIRT.defaultBlockState());
        h.setBlock(dirtRel.above(), Blocks.AIR.defaultBlockState());
        boolean onDirt = feature.place(new FeaturePlaceContext<>(Optional.empty(), level, level.getChunkSource().getGenerator(),
                level.getRandom(), h.absolutePos(dirtRel.above()), NoneFeatureConfiguration.INSTANCE));
        h.assertFalse(onDirt, "Sulphur must never generate outside cave stone");
        h.succeed();
    }

    /** 2 coal + 2 sulphur -> gunpowder in the Crusher, and it still turns cobblestone into gravel. */
    static void crusherGunpowder(GameTestHelper h) {
        ServerLevel level = h.getLevel();

        BlockPos stoneRel = new BlockPos(2, 2, 2);
        h.setBlock(stoneRel, StationContent.CRUSHER.get().defaultBlockState());
        BlockPos stonePos = h.absolutePos(stoneRel);
        if (!(level.getBlockEntity(stonePos) instanceof CrusherBlockEntity stoneCrusher)) throw h.assertionException("No crusher");
        stoneCrusher.insert(new ItemStack(Items.COBBLESTONE, 4));
        for (int i = 0; i < 120; i++) CrusherBlockEntity.tick(level, stonePos, level.getBlockState(stonePos), stoneCrusher);
        ItemStack gravel = stoneCrusher.takeOutput();
        h.assertTrue(gravel.is(Items.GRAVEL) && gravel.getCount() >= 1, "The crusher must still turn cobblestone into gravel");
        stoneCrusher.clearContent();
        h.setBlock(stoneRel, Blocks.AIR.defaultBlockState());

        BlockPos rel = new BlockPos(6, 2, 2);
        h.setBlock(rel, StationContent.CRUSHER.get().defaultBlockState());
        BlockPos pos = h.absolutePos(rel);
        if (!(level.getBlockEntity(pos) instanceof CrusherBlockEntity crusher)) throw h.assertionException("No crusher");
        int movedCoal = crusher.insert(new ItemStack(Items.COAL, 2));
        int movedSulphur = crusher.insert(new ItemStack(SulphurContent.SULPHUR.get(), 2));
        h.assertTrue(movedCoal == 2 && movedSulphur == 2, "Coal and sulphur must both load into the crusher");
        h.assertTrue(crusher.getItem(CrusherBlockEntity.INPUT).is(Items.COAL), "Coal must sit in the hopper slot");
        h.assertTrue(crusher.getItem(CrusherBlockEntity.INPUT_B).is(SulphurContent.SULPHUR.get()), "Sulphur must sit in the second slot");
        for (int i = 0; i < 150; i++) CrusherBlockEntity.tick(level, pos, level.getBlockState(pos), crusher);
        ItemStack out = crusher.takeOutput();
        h.assertTrue(out.is(Items.GUNPOWDER) && out.getCount() == 2, "Two coal and two sulphur must grind into two gunpowder, got " + out);
        h.assertTrue(crusher.getItem(CrusherBlockEntity.INPUT).isEmpty() && crusher.getItem(CrusherBlockEntity.INPUT_B).isEmpty(),
                "Both ingredient slots must be fully consumed");
        crusher.clearContent();
        h.setBlock(rel, Blocks.AIR.defaultBlockState());
        h.succeed();
    }

    /** The explosive arrow blows up and discards on impact, with block damage governed by the tnt gamerule. */
    static void explosiveArrow(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos wallRel = new BlockPos(8, 2, 10);
        h.setBlock(wallRel, Blocks.STONE.defaultBlockState());
        BlockPos wallPos = h.absolutePos(wallRel);
        BlockPos startPos = h.absolutePos(new BlockPos(8, 2, 8));

        var arrow = new ExplosiveArrow(level, startPos.getX() + 0.5, startPos.getY() + 0.5, startPos.getZ() + 0.5, ItemStack.EMPTY, null);
        arrow.shoot(0, 0, 1, 2.0f, 0f);
        level.addFreshEntity(arrow);
        h.runAfterDelay(20, () -> {
            h.assertFalse(arrow.isAlive(), "The explosive arrow must discard once it detonates");
            h.assertFalse(level.getBlockState(wallPos).is(Blocks.STONE), "The blast must damage the block it hit under the tnt gamerule");
            h.succeed();
        });
    }

    private SulphurGameTests() {}
}
