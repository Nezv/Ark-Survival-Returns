package dev.nez.arksurvivalreturns.client.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Grassier Grass builds up to four grass sections a frame on the render thread, nearest first; in flight that was a
 * quarter of the thread and most of the slow frames (A01 OPT-03). While the camera moves faster than eight blocks a
 * second only one is built a frame, and the land fills in as soon as it slows. Optional: does nothing without the mod.
 */
@Pseudo
@Mixin(targets = "com.leonardoinc22.shortgrass.client.render.GrassSectionCache", remap = false)
abstract class GrassBuildCapMixin {
    private static final double FAST_BLOCKS_PER_SECOND = 8.0;

    @ModifyConstant(method = "buildBudgeted", constant = @Constant(intValue = 4), require = 0)
    private static int ark$sectionsPerFrame(int budget) {
        var camera = Minecraft.getInstance().getCameraEntity();
        if (camera == null) return budget;
        var moving = camera.getVehicle() != null ? camera.getVehicle() : camera;
        double speed = Math.sqrt(moving.getDeltaMovement().horizontalDistanceSqr()) * 20;
        return speed > FAST_BLOCKS_PER_SECOND ? 1 : budget;
    }
}
