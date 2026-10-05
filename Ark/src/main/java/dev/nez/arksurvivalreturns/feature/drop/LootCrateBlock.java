package dev.nez.arksurvivalreturns.feature.drop;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * What a supply drop leaves: the Storage Crate's model in the tier's paint, with 3, 5, 7 or 9 slots of loot and
 * a beam over it. It goes when it is emptied or when its time runs out, and breaking it spills what it holds.
 */
public final class LootCrateBlock extends BaseEntityBlock {
    public static final MapCodec<LootCrateBlock> CODEC = simpleCodec(LootCrateBlock::new);
    public static final EnumProperty<SupplyTier> TIER = EnumProperty.create("tier", SupplyTier.class);

    public LootCrateBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(TIER, SupplyTier.WHITE));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TIER);
    }

    @Override public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LootCrateBlockEntity(pos, state);
    }

    @Override public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, DropContent.LOOT_CRATE_BLOCK_ENTITY.get(), LootCrateBlockEntity::serverTick);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof LootCrateBlockEntity crate) {
            player.openMenu(crate, data -> data.writeVarInt(crate.getContainerSize()));
            level.playSound(null, pos, SoundEvents.BARREL_OPEN, SoundSource.BLOCKS, 0.5f,
                    level.getRandom().nextFloat() * 0.1f + 0.9f);
        }
        return InteractionResult.CONSUME;
    }
}
