package dev.nez.arksurvivalreturns.feature.camp;

import com.mojang.serialization.MapCodec;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Field bedroll: a low, walkable camp marker that sets the personal respawn point.
 *
 * <p>The respawn position lives in the player's own saved data, so destroying the bedroll never
 * strands the owner: the fallback remains the world spawn. Sneak-use rolls it back up.
 */
public final class BedrollBlock extends Block {
    public static final MapCodec<BedrollBlock> CODEC = simpleCodec(BedrollBlock::new);

    public BedrollBlock(Properties properties) {
        super(properties);
    }

    @Override public MapCodec<BedrollBlock> codec() {
        return CODEC;
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Block.box(0, 0, 0, 16, 2, 16);
    }

    @Override protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    @Override protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
            Direction direction, BlockPos neighbor, BlockState neighborState, RandomSource random) {
        return direction == Direction.DOWN && !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState() : state;
    }

    @Override public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel world) || !(player instanceof ServerPlayer server)) {
            return InteractionResult.SUCCESS;
        }
        if (player.isSecondaryUseActive() && Config.CAMP_BEDROLL_PICKUP.get()) return rollUp(world, pos, player);
        if (!Config.CAMP_BEDROLL_SETS_SPAWN.get()) {
            server.sendSystemMessage(Component.translatable("camp.arksurvivalreturns.bedroll_disabled"), true);
            return InteractionResult.SUCCESS;
        }
        server.setRespawnPosition(new ServerPlayer.RespawnConfig(
                LevelData.RespawnData.of(level.dimension(), pos, server.getYRot(), server.getXRot()), false), true);
        server.sendSystemMessage(Component.translatable("camp.arksurvivalreturns.bedroll_set"), false);
        return InteractionResult.SUCCESS;
    }

    @Override protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        return useWithoutItem(state, level, pos, player, hit);
    }

    private static InteractionResult rollUp(net.minecraft.server.level.ServerLevel world, BlockPos pos, Player player) {
        if (player.isSpectator()) return InteractionResult.SUCCESS;
        if (!world.removeBlock(pos, false)) return InteractionResult.SUCCESS;
        var roll = new ItemStack(ModContent.BEDROLL.get());
        if (!player.getInventory().add(roll)) player.drop(roll, false);
        Component message = Component.translatable("camp.arksurvivalreturns.bedroll_picked");
        if (player instanceof ServerPlayer server) server.sendSystemMessage(message, true);
        else player.sendSystemMessage(message);
        return InteractionResult.SUCCESS;
    }
}
