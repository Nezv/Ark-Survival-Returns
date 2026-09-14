package dev.nez.arksurvivalreturns.feature.flying;

import com.mojang.serialization.MapCodec;
import dev.nez.arksurvivalreturns.feature.creature.*;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

/** Inert, non-ticking nest. The block state is the authoritative egg inventory. */
public final class NestBlock extends Block {
    public static final MapCodec<NestBlock> CODEC = simpleCodec(NestBlock::new);
    public static final BooleanProperty EGG = BooleanProperty.create("egg");
    public NestBlock(Properties properties) { super(properties); registerDefaultState(stateDefinition.any().setValue(EGG, true)); }
    @Override public MapCodec<NestBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(EGG); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return Block.box(1, 0, 1, 15, 5, 15); }
    public Species species() { return this == ModContent.NESTS.get(Species.ARGENTAVIS).get() ? Species.ARGENTAVIS : Species.PTERANODON; }
    @Override protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) { return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP); }
    @Override protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
            Direction direction, BlockPos neighbor, BlockState neighborState, RandomSource random) {
        return direction == Direction.DOWN && !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState() : state;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel world) takeEgg(world, pos, player);
        return InteractionResult.SUCCESS;
    }
    @Override protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return useWithoutItem(state, level, pos, player, hit);
    }
    public static boolean takeEgg(ServerLevel world, BlockPos pos, Player player) {
        var state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof NestBlock nest) || !state.getValue(EGG) || player.isSpectator()) return false;
        if (!world.setBlock(pos, state.setValue(EGG, false), Block.UPDATE_ALL)) return false;
        disturb(world, pos, player);
        var egg = new ItemStack(ModContent.NEST_EGGS.get(nest.species()).get());
        if (!player.getInventory().add(egg)) player.drop(egg, false);
        world.playSound(null, pos, net.minecraft.sounds.SoundEvents.TURTLE_EGG_CRACK, net.minecraft.sounds.SoundSource.BLOCKS, 0.7f, 1);
        return true;
    }
    public static void disturb(ServerLevel world, BlockPos pos, Player thief) {
        var habitat = HabitatData.get(world).atNest(pos); if (habitat == null) return;
        for (var bird : world.getEntitiesOfClass(FlyingCreatureEntity.class, new AABB(habitat.center()).inflate(96),
                b -> b.isAlive() && habitat.id().equals(b.habitatId()))) bird.defendEgg(thief);
    }
    @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (state.getValue(EGG) && level instanceof ServerLevel world) disturb(world, pos, player);
        return super.playerWillDestroy(level, pos, state, player);
    }
    @Override protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        HabitatData.get(level).removeNest(pos);
    }
}
