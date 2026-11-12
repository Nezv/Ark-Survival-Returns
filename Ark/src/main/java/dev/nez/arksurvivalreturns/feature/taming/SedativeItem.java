package dev.nez.arksurvivalreturns.feature.taming;

import dev.nez.arksurvivalreturns.Config;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * A sedative the player can give, swing, deposit in a taming hold or take.
 *
 * <p>Every route calls {@link TorporService#sedate} with a potency the server resolved from the item,
 * so there is exactly one place where torpor changes. The item is consumed only after the dose is
 * accepted. Taking it yourself is deliberate: only while sneaking, and it takes as long as eating (BUG-11).
 */
public class SedativeItem extends Item {
    public SedativeItem(Properties properties) {
        super(properties);
    }

    /** Read at use time so a configuration change needs no restart. */
    public double potency() {
        return Config.BASIC_SEDATIVE_POTENCY.get();
    }

    /** Ticks of eating before a dose a player takes lands, as long as a meal. */
    public static final int SELF_USE_TICKS = 32;

    /**
     * A plain right-click does nothing: this is the item a player holds while taming, and one dose of Narcotics
     * is a collapse. Sneaking starts eating it; the dose lands when the eating finishes.
     */
    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!player.isSecondaryUseActive()) return InteractionResult.PASS;
        if (!TorporService.eligible(player)) return InteractionResult.FAIL;
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override public int getUseDuration(ItemStack stack, LivingEntity user) { return SELF_USE_TICKS; }

    @Override public ItemUseAnimation getUseAnimation(ItemStack stack) { return ItemUseAnimation.EAT; }

    /** The eating finished: the dose sedates the user; players are knocked out but can never be tamed. */
    @Override public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
        if (!level.isClientSide() && TorporService.sedate(user, potency(), user, "consumed")) consume(stack, user);
        return stack;
    }

    /**
     * The hand route of taming: a dose given to another creature by a right-click, with no blow struck, so it
     * never costs taming progress. Server side; true when the dose was accepted and one item spent.
     */
    public boolean dose(ItemStack stack, Player giver, LivingEntity target) {
        if (giver.level().isClientSide() || target == giver) return false;
        if (!TorporService.sedate(target, potency(), giver, "dosed")) return false;
        consume(stack, giver);
        return true;
    }

    /** A swing delivers the dose to the target instead of the wielder. */
    @Override public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker.level().isClientSide()) return;
        if (TorporService.sedate(target, potency(), attacker, "melee")) consume(stack, attacker);
    }

    private void consume(ItemStack stack, @Nullable LivingEntity owner) {
        if (owner instanceof Player player && player.getAbilities().instabuild) return;
        // One item per dose: the meal-size config belongs to feeding, not to sedation.
        stack.consume(1, owner);
    }
}
