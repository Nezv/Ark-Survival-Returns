package dev.nez.arksurvivalreturns.client.title;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;

/** A creature standing in a menu: no world and no entity, just its idle clip looping on GeckoLib's global clock. */
final class TitleCreature implements GeoAnimatable {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final RawAnimation idle;

    TitleCreature(String idleClip) { idle = RawAnimation.begin().thenLoop(idleClip); }

    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<TitleCreature>("idle", 0, state -> state.setAndContinue(idle)));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
