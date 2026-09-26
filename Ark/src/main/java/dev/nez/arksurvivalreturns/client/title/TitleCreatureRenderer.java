package dev.nez.arksurvivalreturns.client.title;

import com.geckolib.renderer.GeoObjectRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import org.jspecify.annotations.Nullable;

/** Renders a {@link TitleCreature} where the menu scene has already placed the pose, tinted for the night. */
final class TitleCreatureRenderer extends GeoObjectRenderer<TitleCreature, Void, GeoRenderState> {
    private int color = 0xFFFFFFFF;

    TitleCreatureRenderer(TitleCreatureModel model) { super(model); }

    void color(int argb) { color = argb; }

    /** The scene sets the whole pose; objects would otherwise be shifted to a block centre. */
    @Override public void adjustRenderPose(RenderPassInfo<GeoRenderState> pass) {}

    @Override public int getRenderColor(TitleCreature creature, @Nullable Void unused, float partialTick) { return color; }

    /** Only the calm eyes, as a creature at rest shows in the world. */
    @Override public void adjustModelBonesForRender(RenderPassInfo<GeoRenderState> pass, BoneSnapshots snapshots) {
        snapshots.ifPresent(Species.EYES_CALM, bone -> bone.skipRender(false).skipChildrenRender(false));
        snapshots.ifPresent(Species.EYES_ALERT, bone -> bone.skipRender(true).skipChildrenRender(true));
    }
}
