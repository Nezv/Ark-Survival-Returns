package dev.nez.arksurvivalreturns.client;

import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.RenderPassInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

public final class CreatureRenderer extends GeoEntityRenderer<CreatureEntity, EntityRenderState> {
    private final Species species;
    @SuppressWarnings({"rawtypes", "unchecked"}) // EntityRenderState implements GeoRenderState through GeckoLib's mixin.
    public CreatureRenderer(EntityRendererProvider.Context context, Species species) {
        super(context, new CreatureModel(species));
        this.species = species;
        shadowRadius = species.width * 0.45f;
        if (species.glowingEyes()) withRenderLayer(new NightEyesLayer((com.geckolib.renderer.base.GeoRenderer)this, species));
    }

    @Override
    public void captureDefaultRenderState(CreatureEntity creature, Void unused, EntityRenderState state, float partialTick) {
        super.captureDefaultRenderState(creature, unused, state, partialTick);
        ((com.geckolib.renderer.base.GeoRenderState) state).addGeckolibData(
                CreatureModel.TEXTURE_VARIANT, Math.floorMod(creature.getUUID().hashCode(), 5));
        ((com.geckolib.renderer.base.GeoRenderState) state).addGeckolibData(
                CreatureModel.EYE_ALERT, creature.behavior().alarm() || creature.isAggressive());
    }

    /** One eye set at a time: calm (predatory for carnivores) or alert. */
    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void adjustModelBonesForRender(RenderPassInfo pass, BoneSnapshots snapshots) {
        super.adjustModelBonesForRender(pass, snapshots);
        boolean alert = Boolean.TRUE.equals(pass.getOrDefaultGeckolibData(CreatureModel.EYE_ALERT, false));
        snapshots.ifPresent(Species.EYES_CALM, bone -> bone.skipRender(alert).skipChildrenRender(alert));
        snapshots.ifPresent(Species.EYES_ALERT, bone -> bone.skipRender(!alert).skipChildrenRender(!alert));
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"}) // GeckoLib adds GeoRenderState to EntityRenderState through a runtime mixin.
    protected void applyRotations(RenderPassInfo pass, PoseStack pose, float nativeScale) {
        super.applyRotations(pass, pose, nativeScale);
        // All nine imported skeletons face +Z; GeckoLib's entity convention faces -Z.
        // Correct the model space once, keeping movement, aiming and animation timing intact.
        if (species.flyer() && pass.renderState() instanceof net.minecraft.client.renderer.entity.state.LivingEntityRenderState living)
            pose.mulPose(Axis.XP.rotationDegrees(living.xRot));
        pose.mulPose(Axis.YP.rotationDegrees(180f));
    }
}
