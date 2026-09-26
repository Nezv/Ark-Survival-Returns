package dev.nez.arksurvivalreturns.feature.station;

import java.util.Map;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * The only chest: every wooden chest variant falls back to this crate. Neighbouring crates join visually
 * like Create casings (the frame between them disappears), but each crate keeps its own 27 slots and
 * exposes only those, so Tom's Storage connectors, hoppers and tame cargo never count a group twice.
 */
public final class StorageCrateBlock extends BaseEntityBlock {
    public static final MapCodec<StorageCrateBlock> CODEC = simpleCodec(StorageCrateBlock::new);
    public static final Map<Direction, BooleanProperty> CONNECTED = PipeBlock.PROPERTY_BY_DIRECTION;

    public StorageCrateBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any();
        for (BooleanProperty property : CONNECTED.values()) state = state.setValue(property, false);
        registerDefaultState(state);
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        CONNECTED.values().forEach(builder::add);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        for (Direction direction : Direction.values()) {
            BlockState neighbor = context.getLevel().getBlockState(context.getClickedPos().relative(direction));
            state = state.setValue(CONNECTED.get(direction), neighbor.is(this));
        }
        return state;
    }

    @Override protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks,
            BlockPos pos, Direction direction, BlockPos neighbor, BlockState neighborState, RandomSource random) {
        return state.setValue(CONNECTED.get(direction), neighborState.is(this));
    }

    @Override public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StorageCrateBlockEntity(pos, state);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof StorageCrateBlockEntity crate) {
            player.openMenu(crate);
            player.awardStat(Stats.OPEN_BARREL);
            level.playSound(null, pos, SoundEvents.BARREL_OPEN, SoundSource.BLOCKS, 0.5f,
                    level.getRandom().nextFloat() * 0.1f + 0.9f);
        }
        return InteractionResult.CONSUME;
    }
}
