package dev.nez.arksurvivalreturns.client.draw;

import com.geckolib.animation.object.EasingType;
import com.geckolib.animation.state.AnimationPoint;
import com.geckolib.animation.state.ControllerState;
import com.geckolib.cache.animation.BoneAnimation;
import com.geckolib.cache.animation.Keyframe;
import com.geckolib.cache.animation.KeyframeStack;
import com.geckolib.loading.math.MathValue;
import net.minecraft.util.Mth;

/**
 * What one channel of a bone is worth at the animation's moment, for the keys clips are made of: linear ones
 * and catmull-rom ones. GeckoLib asks an easing object for every channel of every bone in every frame (a record
 * made for the call, a function fetched from it, the share of the way boxed in and out); this is the same sum on
 * the same doubles in the same order, AnimationProcessor.findAnimationPointValue without the detour, so the
 * value is GeckoLib's to the last bit. It answers for an animation that is not blending into another; any other
 * easing it leaves to GeckoLib. The mixin on AnimationProcessor asks here first (client/draw/mixin).
 */
public final class PlainKeys {
    /** From the client setting creatureFastAnimation, once a frame; render thread. */
    public static boolean enabled = true;
    /** Channels answered here and channels left to GeckoLib's own code, since the client started. */
    public static long read, left;

    /** The value, or NaN where the keys are not plain: GeckoLib works those out. Not for a state in transition. */
    public static float value(ControllerState state, AnimationPoint animation, int boneIndex, AnimationPoint.Transform transform,
                              AnimationPoint.Axis axis, EasingType easingOverride) {
        BoneAnimation bone = animation.animation().boneAnimations()[boneIndex];
        KeyframeStack stack = transform == AnimationPoint.Transform.ROTATION ? bone.rotationKeyFrames()
                : transform == AnimationPoint.Transform.TRANSLATION ? bone.positionKeyFrames() : bone.scaleKeyFrames();
        Keyframe[] keys = axis == AnimationPoint.Axis.X ? stack.xKeyframes() : axis == AnimationPoint.Axis.Y ? stack.yKeyframes() : stack.zKeyframes();
        int point = animation.keyFramePoints()[boneIndex][transform.index][axis.index];
        if (keys.length == 0 || point == AnimationPoint.NO_KEYFRAME) return transform.defaultValue;
        int last = keys.length - 1;
        Keyframe from = keys[Mth.clamp(point, 0, last)], to = keys[Mth.clamp(point + 1, 0, last)];
        EasingType easing = easingOverride != null ? easingOverride : to.easingType();
        MathValue[] arguments = to.easingArgs();
        boolean spline = easing == EasingType.CATMULLROM && arguments.length >= 2;
        if (easing != EasingType.LINEAR && !spline) return Float.NaN;
        double start = from.endValue().get(state), end = to.endValue().get(state);
        double delta = to.length() == 0.0 ? 0.0 : (animation.animTime() - from.startTime()) / to.length();
        if (delta >= 1.0) return (float) end;
        return (float) (spline ? EasingType.CatmullRomEasing.getPointOnSpline(delta, arguments[0].get(state), start, end, arguments[1].get(state))
                : Mth.lerp(delta, start, end));
    }

    private PlainKeys() {}
}
