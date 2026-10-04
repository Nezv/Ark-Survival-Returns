package dev.nez.arksurvivalreturns.client;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.base.*;
import com.geckolib.renderer.layer.GeoRenderLayer;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.NighttimeClientConfig;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;

/**
 * The red eyes of a hunting carnivore, drawn the way the vanilla spider's are (EyesLayer): the whole model
 * once more with the eyes render type and a texture that is transparent except for the irises
 * (textures/entity/&lt;id&gt;_eyes.png, tools/build_creature_eyes.py). Same geometry and pose as the body, so
 * the glow sits exactly on the eye set the renderer shows.
 */
final class HuntingEyesLayer<R extends EntityRenderState & GeoRenderState> extends GeoRenderLayer<CreatureEntity, Void, R> {
    private static final DataTicket<Float> GLOW = DataTicket.create("arksurvivalreturns:hunting_eyes", Float.class);
    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();
    private final RenderType eyes;
    HuntingEyesLayer(GeoRenderer<CreatureEntity, Void, R> renderer, Species species) {
        super(renderer);
        eyes = RenderTypes.eyes(ArkSurvivalReturns.id("textures/entity/" + species.id + "_eyes.png"));
    }
    @Override public void addRenderData(CreatureEntity creature, Void unused, R state, float partialTick) {
        float glow = creature.huntingEyeGlow(partialTick) * NighttimeClientConfig.EYE_GLOW.get().floatValue();
        state.addGeckolibData(GLOW, glow);
        if (NighttimeClientConfig.DEBUG_LOG.get() && glow > 0.001f && LOGGED.add(creature.species().id)) {
            ArkSurvivalReturns.LOGGER.info("Hunting eyes for {}: glow={} nightActive={} state={}",
                    creature.species().id, glow, creature.nightActive(), creature.behavior());
        }
    }
    @Override public void submitRenderTask(RenderPassInfo<R> pass, SubmitNodeCollector collector) {
        float glow = pass.getOrDefaultGeckolibData(GLOW, 0f);
        if (!pass.willRender() || pass.renderState().isInvisible || glow <= 0.001f) return;
        // The eyes fade in and out through the alpha of the pass; the red is the texture's own.
        int color = ARGB.white(glow);
        collector.order(1).submitCustomGeometry(pass.poseStack(), eyes, (pose, vertices) -> {
            var stack = pass.poseStack();
            stack.pushPose();
            stack.last().set(pose);
            pass.renderPosed(() -> pass.model().render(pass, vertices, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color));
            stack.popPose();
        });
    }
}
