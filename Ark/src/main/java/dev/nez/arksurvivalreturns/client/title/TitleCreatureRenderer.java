package dev.nez.arksurvivalreturns.client.title;

import com.geckolib.renderer.GeoObjectRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import dev.nez.arksurvivalreturns.client.CreatureEyes;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import org.jspecify.annotations.Nullable;

/** Renders a {@link TitleCreature} where the menu scene has already placed the pose, tinted for the night. */
final class TitleCreatureRenderer extends GeoObjectRenderer<TitleCreature, Void, GeoRenderState> {
    private int color = 0xFFFFFFFF;
    private final CreatureEyes eyes = new CreatureEyes(1L);
    private final long started = System.nanoTime();

    TitleCreatureRenderer(TitleCreatureModel model) { super(model); }

    void color(int argb) { color = argb; }

    /** The scene sets the whole pose; objects would otherwise be shifted to a block centre. */
    @Override public void adjustRenderPose(RenderPassInfo<GeoRenderState> pass) {}

    @Override public int getRenderColor(TitleCreature creature, @Nullable Void unused, float partialTick) { return color; }

    /** Only the calm eyes, as a creature at rest shows in the world; designed eyes blink now and then. */
    @Override public void adjustModelBonesForRender(RenderPassInfo<GeoRenderState> pass, BoneSnapshots snapshots) {
        if (CreatureEyes.designed(pass.model())) {
            eyes.update((System.nanoTime() - started) / 1e9f, CreatureEyes.Look.CALM, false, 1.2f);
            eyes.apply(pass.model(), snapshots);
            return;
        }
        snapshots.ifPresent(Species.EYES_CALM, bone -> bone.skipRender(false).skipChildrenRender(false));
        snapshots.ifPresent(Species.EYES_ALERT, bone -> bone.skipRender(true).skipChildrenRender(true));
    }
}
