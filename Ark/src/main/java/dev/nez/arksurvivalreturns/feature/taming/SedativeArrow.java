package dev.nez.arksurvivalreturns.feature.taming;

import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.jspecify.annotations.Nullable;

/**
 * Ranged sedative delivery. Damage and torpor stay separate, so taking a creature down is never a race
 * against its last hit point: the arrow hurts it exactly like an arrow and adds one bounded dose.
 */
public final class SedativeArrow extends AbstractArrow {
    private double potency;

    public SedativeArrow(EntityType<? extends SedativeArrow> type, Level level) {
        super(type, level);
        this.potency = Config.TRANQUILIZER_ARROW_POTENCY.get();
    }

    public SedativeArrow(Level level, double x, double y, double z, ItemStack pickup, @Nullable ItemStack weapon,
            double potency) {
        super(ModContent.TRANQUILIZER_ARROW.get(), x, y, z, level, pickup, weapon);
        this.potency = potency;
    }

    public SedativeArrow(Level level, LivingEntity owner, ItemStack pickup, @Nullable ItemStack weapon, double potency) {
        super(ModContent.TRANQUILIZER_ARROW.get(), owner, level, pickup, weapon);
        this.potency = potency;
    }

    @Override protected void doPostHurtEffects(LivingEntity target) {
        super.doPostHurtEffects(target);
        Entity owner = this.getOwner();
        TorporService.sedate(target, potency, owner, owner instanceof LivingEntity living
                ? "arrow:" + living.getName().getString()
                : "arrow");
    }

    @Override protected ItemStack getDefaultPickupItem() {
        return new ItemStack(ModContent.TRANQUILIZER_ARROW_ITEM.get());
    }

    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putDouble("SedativePotency", potency);
    }

    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        potency = input.getDoubleOr("SedativePotency", Config.TRANQUILIZER_ARROW_POTENCY.get());
    }
}
