package dev.nez.arksurvivalreturns.feature.station;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A workbench-style station: the Working Station (the crafting table), the Mortar & Pestle, the Medicine Bench
 * and the Ark smithing table, plus the Armoury and the two-block Saddlery ({@link SaddleryBlock}). Each opens its
 * position-bound workstation graph.
 */
public class StationBlock extends Block {
    public enum Kind { WORKING, MEDICINE, SMITHING, MORTAR, ARMOURY, SADDLERY }

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Collision: the bench tops. The outlines below also cover the tools and bottles standing on them. */
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 15, 16);
    private static final VoxelShape WORKING_OUTLINE = Block.box(0, 0, 0, 16, 17, 16);
    private static final VoxelShape MEDICINE_OUTLINE = Block.box(0, 0, 0, 16, 18.5, 16);
    private static final VoxelShape MORTAR_SHAPE = Block.box(2.4, 0, 2.4, 13.6, 9, 13.6);
    private final Kind kind;

    public StationBlock(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public Kind kind() { return kind; }
    public String stationId() {
        return "arksurvivalreturns:" + switch (kind) {
            case WORKING -> "working_station";
            case MEDICINE -> "medicine_bench";
            case SMITHING -> "smithing_table";
            case MORTAR -> "mortar_and_pestle";
            case ARMOURY -> "armoury";
            case SADDLERY -> "saddlery";
        };
    }

    @Override protected MapCodec<? extends Block> codec() {
        return simpleCodec(properties -> new StationBlock(kind, properties));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override protected BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (kind) {
            case MORTAR -> MORTAR_SHAPE;
            case WORKING -> WORKING_OUTLINE;
            case MEDICINE -> MEDICINE_OUTLINE;
            case SMITHING, SADDLERY -> SHAPE;
            case ARMOURY -> Block.box(1, 0, 1, 15, 24, 15);
        };
    }

    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return kind == Kind.MORTAR ? MORTAR_SHAPE : SHAPE;
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer serverPlayer) {
            BlockPos at = menuPos(state, pos);
            serverPlayer.openMenu(menu(level, at), data -> { data.writeBlockPos(at); data.writeUtf(stationId(), 128); });
        }
        if (player instanceof ServerPlayer server) {
            server.awardStat(kind == Kind.SMITHING ? Stats.INTERACT_WITH_SMITHING_TABLE : Stats.INTERACT_WITH_CRAFTING_TABLE);
        }
        return InteractionResult.CONSUME;
    }

    /** Where the menu is bound: the block itself, or the lower half of a station two blocks tall. */
    public BlockPos menuPos(BlockState state, BlockPos pos) { return pos; }

    private MenuProvider menu(Level level, BlockPos pos) {
        Component title = getName();
        return new SimpleMenuProvider((id, inventory, player) -> new WorkstationMenu(id, inventory, pos, stationId()), title);
    }
}
