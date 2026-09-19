package dev.nez.arksurvivalreturns.feature.taming;

import dev.nez.arksurvivalreturns.Config;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * A sedative the player can eat, throw or swing.
 *
 * <p>All three routes call {@link TorporService#sedate} with a potency the server resolved from the item,
 * so there is exactly one place where torpor changes. The item is consumed only after the dose is
 * accepted.
 */
public class SedativeItem extends Item {
    public SedativeItem(Properties properties) {
        super(properties);
    }

    /** Read at use time so a configuration change needs no restart. */
    public double potency() {
        return Config.BASIC_SEDATIVE_POTENCY.get();
    }

    /** Consuming the dose sedates the user; players are knocked out but can never be tamed. */
    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!TorporService.eligible(player)) return InteractionResult.FAIL;
        TorporService.sedate(player, potency(), player, "consumed");
        consume(stack, player);
        return InteractionResult.SUCCESS_SERVER;
    }

    /** A swing delivers the dose to the target instead of the wielder. */
    @Override public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker.level().isClientSide()) return;
        if (TorporService.sedate(target, potency(), attacker, "melee")) consume(stack, attacker);
    }

    private void consume(ItemStack stack, @Nullable LivingEntity owner) {
        if (owner instanceof Player player && player.getAbilities().instabuild) return;
        stack.consume(Config.FOOD_UNITS_PER_MEAL.get(), owner);
    }
}
