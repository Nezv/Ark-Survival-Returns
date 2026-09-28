package dev.nez.arksurvivalreturns.feature.explosive;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * An arrow that never sticks: on hitting a block or an entity it detonates in place and discards, so
 * there is nothing left standing to pick back up. The blast leaves block damage up to the {@code tnt}
 * game rule, exactly like a stray TNT explosion, because it reports itself as a
 * {@link Level.ExplosionInteraction#TNT} explosion.
 */
public final class ExplosiveArrow extends AbstractArrow {
    /** Comparable to a thrown TNT primed close by: enough to break stone, never a giant crater. */
    public static final float EXPLOSION_POWER = 2.0f;

    public ExplosiveArrow(EntityType<? extends ExplosiveArrow> type, Level level) {
        super(type, level);
    }

    public ExplosiveArrow(Level level, double x, double y, double z, ItemStack pickup, @Nullable ItemStack weapon) {
        super(ExplosiveContent.EXPLOSIVE_ARROW.get(), x, y, z, level, pickup, weapon);
    }

    public ExplosiveArrow(Level level, LivingEntity owner, ItemStack pickup, @Nullable ItemStack weapon) {
        super(ExplosiveContent.EXPLOSIVE_ARROW.get(), owner, level, pickup, weapon);
    }

    @Override protected void onHitEntity(EntityHitResult hitResult) {
        detonate(hitResult.getLocation());
    }

    @Override protected void onHitBlock(BlockHitResult hitResult) {
        detonate(hitResult.getLocation());
    }

    private void detonate(Vec3 at) {
        if (isRemoved()) return;
        level().explode(this, at.x, at.y, at.z, EXPLOSION_POWER, Level.ExplosionInteraction.TNT);
        discard();
    }

    /** Nothing survives the blast: this is never reached in practice since the arrow always discards on hit. */
    @Override protected ItemStack getDefaultPickupItem() { return ItemStack.EMPTY; }
}
