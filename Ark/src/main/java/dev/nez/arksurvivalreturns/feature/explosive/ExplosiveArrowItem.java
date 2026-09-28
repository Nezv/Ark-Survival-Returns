package dev.nez.arksurvivalreturns.feature.explosive;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The ammunition item: fires from bows and crossbows (it is tagged {@code #minecraft:arrows}, the tag
 * both weapons use to decide their valid ammunition) and from dispensers
 * ({@link ExplosiveContent}'s common setup registers the projectile dispense behaviour).
 */
public final class ExplosiveArrowItem extends ArrowItem {
    public ExplosiveArrowItem(Properties properties) {
        super(properties);
    }

    @Override public AbstractArrow createArrow(Level level, ItemStack itemStack, LivingEntity owner, @Nullable ItemStack firedFromWeapon) {
        return new ExplosiveArrow(level, owner, itemStack.copyWithCount(1), firedFromWeapon);
    }

    @Override public Projectile asProjectile(Level level, Position position, ItemStack itemStack, Direction direction) {
        ExplosiveArrow arrow = new ExplosiveArrow(level, position.x(), position.y(), position.z(), itemStack.copyWithCount(1), null);
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        return arrow;
    }
}
