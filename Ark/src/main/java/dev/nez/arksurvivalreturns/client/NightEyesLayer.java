package dev.nez.arksurvivalreturns.client;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.base.*;
import com.geckolib.renderer.layer.GeoRenderLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.NighttimeClientConfig;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** Two eye bones only, using a depth-tested emissive pipeline. */
final class NightEyesLayer<R extends EntityRenderState & GeoRenderState> extends GeoRenderLayer<CreatureEntity, Void, R> {
    private static final DataTicket<Float> GLOW = DataTicket.create("arksurvivalreturns:night_eyes", Float.class);
    /** The eyeball cubes sit inside the eye socket; a small inflation keeps them out of the eyelids. */
    private static final float EYE_INFLATE = 1.15f;
    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();
    private final String[] eyes;
    NightEyesLayer(GeoRenderer<CreatureEntity, Void, R> renderer, Species species) {
        super(renderer);
        eyes = species.eyeBones();
    }
    @Override public void addRenderData(CreatureEntity creature, Void unused, R state, float partialTick) {
        float glow = creature.nightEyeGlow(partialTick) * NighttimeClientConfig.EYE_GLOW.get().floatValue();
        state.addGeckolibData(GLOW, glow);
        if (NighttimeClientConfig.DEBUG_LOG.get() && glow > 0.001f && LOGGED.add(creature.species().id)) {
            ArkSurvivalReturns.LOGGER.info("Night eyes for {}: glow={} bones={} nightActive={}",
                    creature.species().id, glow, eyes.length, creature.nightActive());
        }
    }
    @Override public void addPerBoneRender(RenderPassInfo<R> pass, BiConsumer<GeoBone, PerBoneRender<R>> consumer) {
        float glow = pass.getOrDefaultGeckolibData(GLOW, 0f);
        if (!pass.willRender() || pass.renderState().isInvisible || glow <= 0.001f) return;
        // The original eye palette is amber. Red-only modulation produces red without a duplicate texture.
        int color = 0xFF000000 | Math.round(255 * glow) << 16;
        var type = RenderTypes.entityTranslucentEmissive(getTextureResource(pass.renderState()));
        for (String eye : eyes) pass.model().getBone(eye).ifPresent(bone -> consumer.accept(bone, (p, b, collector) -> {
            b.translateAwayFromPivotPoint(p.poseStack());
            collector.order(1).submitCustomGeometry(p.poseStack(), type, (pose, vertices) -> {
                var stack = new PoseStack();
                stack.last().set(pose);
                // Similarity around the bone pivot: grow the eyeball outward without moving it.
                float px = b.pivotX() / 16f, py = b.pivotY() / 16f, pz = b.pivotZ() / 16f;
                stack.translate(px, py, pz);
                stack.scale(EYE_INFLATE, EYE_INFLATE, EYE_INFLATE);
                stack.translate(-px, -py, -pz);
                p.renderPosed(() -> b.render(p, stack, vertices, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color));
            });
        }));
    }
}
