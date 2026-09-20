package dev.nez.arksurvivalreturns.feature.forge;

import com.mojang.serialization.MapCodec;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/** Primitive forge block: raw ore and stone process without fuel, so the vanilla furnace stays usable. */
public final class PrimitiveForgeBlock extends BaseEntityBlock {
    public static final MapCodec<PrimitiveForgeBlock> CODEC = simpleCodec(PrimitiveForgeBlock::new);

    public PrimitiveForgeBlock(Properties properties) {
        super(properties);
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PrimitiveForgeBlockEntity(pos, state);
    }

    @Override public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, ModContent.PRIMITIVE_FORGE_BLOCK_ENTITY.get(), BatchStationBlockEntity::tick);
    }

    @Override protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof BatchStationBlockEntity station)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        return station.insert(stack) > 0 ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof BatchStationBlockEntity station)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        ItemStack taken = station.extractAny();
        if (taken.isEmpty()) return InteractionResult.SUCCESS;
        if (!player.getInventory().add(taken)) player.drop(taken, false);
        return InteractionResult.SUCCESS;
    }
}
