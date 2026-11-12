package dev.nez.arksurvivalreturns.feature.theme.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Leaves are no wall for a living body (A02, BUG-09): players and animals push through the canopy, slowed as in a
 * thicket and with their fall broken, so giants walk, spawn and are ridden under trees and only trunks stand in the
 * way. Leaves stay solid to everything else (falling blocks, items, projectiles) and keep their look and drops.
 */
@Mixin(BlockBehaviour.class)
abstract class PassableLeavesMixin {
    private static final Vec3 THICKET = new Vec3(0.85, 0.9, 0.85);

    @Inject(method = "getCollisionShape", at = @At("HEAD"), cancellable = true)
    private void ark$passLeaves(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context,
            CallbackInfoReturnable<VoxelShape> cir) {
        if ((Object) this instanceof LeavesBlock && context instanceof EntityCollisionContext entity
                && entity.getEntity() instanceof LivingEntity) cir.setReturnValue(Shapes.empty());
    }

    @Inject(method = "entityInside", at = @At("HEAD"))
    private void ark$thicket(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects,
            boolean precise, CallbackInfo ci) {
        if ((Object) this instanceof LeavesBlock && entity instanceof LivingEntity) entity.makeStuckInBlock(state, THICKET);
    }
}
