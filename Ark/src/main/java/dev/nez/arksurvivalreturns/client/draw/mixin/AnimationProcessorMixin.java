package dev.nez.arksurvivalreturns.client.draw.mixin;

import com.geckolib.animation.AnimationProcessor;
import com.geckolib.animation.object.EasingType;
import com.geckolib.animation.state.AnimationPoint;
import com.geckolib.animation.state.BoneSnapshot;
import com.geckolib.animation.state.ControllerState;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.nez.arksurvivalreturns.client.draw.PlainKeys;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

/**
 * GeckoLib 5.5 reads every channel of every animated bone through an easing object. Where the keys are plain
 * ({@link PlainKeys}) the value is worked out directly, the same to the last bit; everything else, and every
 * blend from one animation into the next, runs GeckoLib's code. It serves whatever GeckoLib animates, Ark's
 * creatures above all. Not required: on a GeckoLib that keeps this elsewhere nothing is wrapped.
 */
@Mixin(value = AnimationProcessor.class, remap = false)
public abstract class AnimationProcessorMixin {
    @WrapMethod(method = "findAnimationPointValue", require = 0)
    private static float ark$plainKeys(BoneSnapshot boneSnapshot, ControllerState controllerState, AnimationPoint animation, @Nullable AnimationPoint prevAnimation,
                                       int boneIndex, AnimationPoint.Transform transform, AnimationPoint.Axis axis, @Nullable EasingType easingOverride,
                                       Operation<Float> original) {
        if (PlainKeys.enabled && controllerState.transitionTime() < 0.0) {
            float value = PlainKeys.value(controllerState, animation, boneIndex, transform, axis, easingOverride);
            if (value == value) {
                PlainKeys.read++;
                return value;
            }
        }
        PlainKeys.left++;
        return original.call(boneSnapshot, controllerState, animation, prevAnimation, boneIndex, transform, axis, easingOverride);
    }
}
