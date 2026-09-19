package dev.nez.arksurvivalreturns.feature.taming;

import dev.nez.arksurvivalreturns.Config;
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
 * The ammunition item. Both bow firing and dispenser firing route through the same arrow entity, and the
 * dose comes from the configured item potency rather than from the client.
 */
public final class SedativeArrowItem extends ArrowItem {
    public SedativeArrowItem(Properties properties) {
        super(properties);
    }

    public double potency() {
        return Config.TRANQUILIZER_ARROW_POTENCY.get();
    }

    @Override public AbstractArrow createArrow(Level level, ItemStack itemStack, LivingEntity owner,
            @Nullable ItemStack firedFromWeapon) {
        return new SedativeArrow(level, owner, itemStack.copyWithCount(1), firedFromWeapon, potency());
    }

    @Override public Projectile asProjectile(Level level, Position position, ItemStack itemStack, Direction direction) {
        SedativeArrow arrow = new SedativeArrow(level, position.x(), position.y(), position.z(),
                itemStack.copyWithCount(1), null, potency());
        arrow.pickup = AbstractArrow.Pickup.ALLOWED;
        return arrow;
    }
}
