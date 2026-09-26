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
 * A workbench-style station: the Working Station (the crafting table), the Medicine Bench and the Ark
 * smithing table. Each opens a vanilla menu bound to itself, so the recipe book, JEI transfer and other
 * mods' crafting recipes keep working.
 */
public final class StationBlock extends Block {
    public enum Kind { WORKING, MEDICINE, SMITHING }

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 15, 16);
    private final Kind kind;

    public StationBlock(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public Kind kind() { return kind; }

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
        return SHAPE;
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        player.openMenu(menu(level, pos));
        if (player instanceof ServerPlayer server) {
            server.awardStat(kind == Kind.SMITHING ? Stats.INTERACT_WITH_SMITHING_TABLE : Stats.INTERACT_WITH_CRAFTING_TABLE);
        }
        return InteractionResult.CONSUME;
    }

    private MenuProvider menu(Level level, BlockPos pos) {
        var access = ContainerLevelAccess.create(level, pos);
        Component title = getName();
        return switch (kind) {
            case WORKING -> new SimpleMenuProvider((id, inventory, player) ->
                    new StationCraftingMenu(id, inventory, access, this, stack -> !StationContent.medicine(stack)), title);
            case MEDICINE -> new SimpleMenuProvider((id, inventory, player) ->
                    new StationCraftingMenu(id, inventory, access, this, StationContent::medicine), title);
            case SMITHING -> new SimpleMenuProvider((id, inventory, player) ->
                    new StationSmithingMenu(id, inventory, access), title);
        };
    }
}
