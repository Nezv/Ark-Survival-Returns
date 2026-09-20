package dev.nez.arksurvivalreturns.feature.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Logs, planks and saplings reduce to charcoal over the configured batches. */
public final class CharcoalKilnBlockEntity extends BatchStationBlockEntity {
    public CharcoalKilnBlockEntity(BlockPos pos, BlockState state) {
        this(dev.nez.arksurvivalreturns.registry.ModContent.CHARCOAL_KILN_BLOCK_ENTITY.get(), pos, state);
    }

    public CharcoalKilnBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override protected ItemStack result(ItemStack input) {
        return ForgeRecipes.kilnResult(input);
    }

    @Override protected int batches() {
        return dev.nez.arksurvivalreturns.Config.KILN_BATCHES.get();
    }
}
