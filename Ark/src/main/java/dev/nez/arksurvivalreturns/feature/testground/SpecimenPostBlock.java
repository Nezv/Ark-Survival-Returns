package dev.nez.arksurvivalreturns.feature.testground;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

/** The Specimen Post block: its screen on use, a fresh specimen on a redstone pulse. */
public final class SpecimenPostBlock extends BaseEntityBlock {
    public static final MapCodec<SpecimenPostBlock> CODEC = simpleCodec(SpecimenPostBlock::new);

    public SpecimenPostBlock(Properties properties) { super(properties); }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SpecimenPostBlockEntity(pos, state);
    }

    @Override public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, TestGroundContent.SPECIMEN_POST_BLOCK_ENTITY.get(), SpecimenPostBlockEntity::tick);
    }

    /** Placed by hand, the specimen faces the one who placed it. */
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        if (by != null && level.getBlockEntity(pos) instanceof SpecimenPostBlockEntity post) post.setYaw(by.getYRot() + 180f);
    }

    @Override protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer server)) return InteractionResult.PASS;
        if (!TestGround.mayUse(server)) {
            server.sendSystemMessage(Component.literal("Specimen Posts are for creative players and operators"), true);
            return InteractionResult.SUCCESS;
        }
        PacketDistributor.sendToPlayer(server, new SpecimenPayload.Open(pos));
        return InteractionResult.SUCCESS;
    }

    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation,
            boolean movedByPiston) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof SpecimenPostBlockEntity post) post.redstone(level.hasNeighborSignal(pos));
    }
}
