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
    /** The designed eyes of each big carnivore drawn lately; render thread only. */
    private final java.util.Map<CreatureEntity, CreatureEyes> eyes = new java.util.WeakHashMap<>();
    @SuppressWarnings({"rawtypes", "unchecked"}) // EntityRenderState implements GeoRenderState through GeckoLib's mixin.
    public CreatureRenderer(EntityRendererProvider.Context context, Species species) {
        super(context, new CreatureModel(species));
        this.species = species;
        shadowRadius = species.width * 0.45f;
        if (species.glowingEyes()) withRenderLayer(new HuntingEyesLayer((com.geckolib.renderer.base.GeoRenderer)this, species));
    }

    @Override
    public void captureDefaultRenderState(CreatureEntity creature, Void unused, EntityRenderState state, float partialTick) {
        super.captureDefaultRenderState(creature, unused, state, partialTick);
        ((com.geckolib.renderer.base.GeoRenderState) state).addGeckolibData(
                CreatureModel.TEXTURE_VARIANT, creature instanceof dev.nez.arksurvivalreturns.feature.guardian.GuardianDragonEntity dragon
                        ? dragon.beaconVariant() : Math.floorMod(creature.getUUID().hashCode(), species == Species.DRAGON ? 3 : 5));
        ((com.geckolib.renderer.base.GeoRenderState) state).addGeckolibData(
                CreatureModel.EYE_ALERT, creature.behavior().alarm() || creature.isAggressive());
        if (species.designedEyes()) {
            CreatureEyes look = eyes.computeIfAbsent(creature, entity -> new CreatureEyes(entity.getUUID().getLeastSignificantBits()));
            look.update(creature, partialTick);
            ((com.geckolib.renderer.base.GeoRenderState) state).addGeckolibData(CreatureModel.EYES, look);
        }
    }

    /** Designed eyes move (CreatureEyes); the generated sets show one at a time, calm (predatory for carnivores) or alert. */
    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void adjustModelBonesForRender(RenderPassInfo pass, BoneSnapshots snapshots) {
        super.adjustModelBonesForRender(pass, snapshots);
        CreatureEyes look = (CreatureEyes) pass.getGeckolibData(CreatureModel.EYES);
        if (look != null && CreatureEyes.designed(pass.model())) {
            look.apply(pass.model(), snapshots);
            return;
        }
        boolean alert = Boolean.TRUE.equals(pass.getOrDefaultGeckolibData(CreatureModel.EYE_ALERT, false));
        snapshots.ifPresent(Species.EYES_CALM, bone -> bone.skipRender(alert).skipChildrenRender(alert));
        snapshots.ifPresent(Species.EYES_ALERT, bone -> bone.skipRender(!alert).skipChildrenRender(!alert));
    }

    /** The cubes are written by Ark's own writer (client/draw); GeckoLib's stays for what that one declines. */
    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void submitRenderTasks(RenderPassInfo pass, net.minecraft.client.renderer.OrderedSubmitNodeCollector renderTasks,
                                  net.minecraft.client.renderer.rendertype.RenderType renderType) {
        if (renderType == null) return;
        if (!dev.nez.arksurvivalreturns.client.draw.CreatureDraw.submit(pass, renderTasks, renderType,
                getTextureLocation((EntityRenderState) pass.renderState()), pass.packedLight(), pass.packedOverlay(), pass.renderColor(), false))
            super.submitRenderTasks(pass, renderTasks, renderType);
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"}) // GeckoLib adds GeoRenderState to EntityRenderState through a runtime mixin.
    protected void applyRotations(RenderPassInfo pass, PoseStack pose, float nativeScale) {
        super.applyRotations(pass, pose, nativeScale);
        // All nine imported skeletons face +Z; GeckoLib's entity convention faces -Z.
        // Correct the model space once, keeping movement, aiming and animation timing intact.
        if (species.flyer() && pass.renderState() instanceof net.minecraft.client.renderer.entity.state.LivingEntityRenderState living)
            pose.mulPose(Axis.XP.rotationDegrees(living.xRot));
        // The authored wyverns already face -Z; the older imported rigs face +Z.
        if (species != Species.DRAGON) pose.mulPose(Axis.YP.rotationDegrees(180f));
    }
}
