package dev.nez.arksurvivalreturns.feature.primitive;

import dev.nez.arksurvivalreturns.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Bow-drill fire starter: every use costs durability and only sometimes catches. It lights a fueled
 * stone fire (handled by the fire block) or an unlit vanilla campfire.
 */
public final class FireStarterItem extends Item {
    public FireStarterItem(Properties properties) {
        super(properties);
    }

    /** One attempt; true when the ember caught. Durability is spent either way. */
    public static boolean attempt(Level level, BlockPos pos, ItemStack stack, net.minecraft.world.entity.player.Player player,
            net.minecraft.world.InteractionHand hand) {
        level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 0.6f, 0.6f + level.getRandom().nextFloat() * 0.2f);
        if (!player.getAbilities().instabuild) stack.hurtAndBreak(1, player, hand.asEquipmentSlot());
        boolean caught = level.getRandom().nextDouble() < Config.PRIMITIVE_FIRE_STARTER_CHANCE.get();
        if (level instanceof ServerLevel server) {
            server.sendParticles(caught ? ParticleTypes.FLAME : ParticleTypes.SMOKE,
                    pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5, caught ? 6 : 3, 0.15, 0.05, 0.15, 0.01);
        }
        return caught;
    }

    @Override public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (!CampfireBlock.canLight(state) || context.getPlayer() == null) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (attempt(level, pos, context.getItemInHand(), context.getPlayer(), context.getHand())) {
            BlockState lit = state.setValue(CampfireBlock.LIT, true);
            level.setBlock(pos, lit, 11);
            if (context.getPlayer() instanceof ServerPlayer player) {
                dev.nez.arksurvivalreturns.feature.tech.TechService.notify(player,
                        dev.nez.arksurvivalreturns.feature.tech.TechEvent.place(player, lit));
            }
        }
        return InteractionResult.SUCCESS;
    }
}
