package dev.nez.arksurvivalreturns.feature.accessory.mixin;

import dev.nez.arksurvivalreturns.feature.accessory.Accessory;
import dev.nez.arksurvivalreturns.feature.accessory.Worn;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Climbing Claws: a wall counts as a ladder. Walking into it climbs, letting go slides down slowly and
 * sneaking clings, all through vanilla ladder physics on both sides. On the ground the wall only counts
 * while the wearer pushes into it, so walking along a cliff keeps its normal speed.
 */
@Mixin(LivingEntity.class)
public abstract class ClimbingClawsMixin {
    @Inject(method = "onClimbable", at = @At("RETURN"), cancellable = true)
    private void ark$climbWalls(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() || !((Object) this instanceof Player player)) return;
        if (player.isSpectator() || player.getAbilities().flying || player.isPassenger() || player.isInWater()) return;
        if (player.onGround() && !player.horizontalCollision) return;
        if (!Worn.has(player, Accessory.CLIMBING_CLAWS)) return;
        AABB reach = player.getBoundingBox().inflate(0.12, 0, 0.12).deflate(0, 0.2, 0);
        if (player.level().getBlockCollisions(player, reach).iterator().hasNext()) cir.setReturnValue(true);
    }
}
