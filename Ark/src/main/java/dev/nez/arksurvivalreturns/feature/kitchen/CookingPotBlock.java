package dev.nez.arksurvivalreturns.feature.kitchen;

import com.mojang.serialization.MapCodec;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/** Cooking pot block: opens the four-slot kitchen and drops its contents when removed. */
public final class CookingPotBlock extends BaseEntityBlock {
    public static final MapCodec<CookingPotBlock> CODEC = simpleCodec(CookingPotBlock::new);

    public CookingPotBlock(Properties properties) {
        super(properties);
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CookingPotBlockEntity(pos, state);
    }

    @Override public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, ModContent.COOKING_POT_BLOCK_ENTITY.get(), CookingPotBlockEntity::tick);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CookingPotBlockEntity)) return InteractionResult.PASS;
        if (player instanceof ServerPlayer server) {
            server.openMenu(new SimpleMenuProvider(
                            (id, inventory, actor) -> new CookingPotMenu(id, inventory, pos),
                            Component.translatable("block.arksurvivalreturns.cooking_pot")),
                    buffer -> buffer.writeBlockPos(pos));
        }
        return InteractionResult.SUCCESS;
    }
}
