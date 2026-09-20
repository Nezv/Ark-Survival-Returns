package dev.nez.arksurvivalreturns.feature.camp;

import dev.nez.arksurvivalreturns.Config;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Fiber bandage: field medicine from plant fiber.
 *
 * <p>The revive interaction for downed tribe members arrives with the downed state; this item
 * already owns the healing and cooldown so there is one place that consumes it.
 */
public final class FiberBandageItem extends Item {
    public FiberBandageItem(Properties properties) {
        super(properties);
    }

    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (player.getHealth() >= player.getMaxHealth()) return InteractionResult.FAIL;
        if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.FAIL;
        float heal = (float) (double) Config.CAMP_BANDAGE_HEAL.get();
        player.heal(heal);
        player.getCooldowns().addCooldown(stack, Config.CAMP_BANDAGE_COOLDOWN.get());
        if (!player.getAbilities().instabuild) stack.consume(1, player);
        Component message = Component.translatable("camp.arksurvivalreturns.bandaged", Math.round(heal));
        if (player instanceof net.minecraft.server.level.ServerPlayer server) server.sendSystemMessage(message, true);
        else player.sendSystemMessage(message);
        return InteractionResult.SUCCESS_SERVER;
    }
}
