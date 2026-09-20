package dev.nez.arksurvivalreturns.feature.forge;

import dev.nez.arksurvivalreturns.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** Raw ore, sand, cobblestone and clay process without fuel over the configured batches. */
public final class PrimitiveForgeBlockEntity extends BatchStationBlockEntity {
    public PrimitiveForgeBlockEntity(BlockPos pos, BlockState state) {
        super(dev.nez.arksurvivalreturns.registry.ModContent.PRIMITIVE_FORGE_BLOCK_ENTITY.get(), pos, state);
    }

    @Override protected ItemStack result(ItemStack input) {
        return ForgeRecipes.forgeResult(input);
    }

    @Override protected int batches() {
        return Config.FORGE_BATCHES.get();
    }
}
