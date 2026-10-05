package dev.nez.arksurvivalreturns.client;

import java.util.EnumMap;
import java.util.List;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.drop.DropContent;
import dev.nez.arksurvivalreturns.feature.drop.LootCrateBlock;
import dev.nez.arksurvivalreturns.feature.drop.SupplyDropEntity;
import dev.nez.arksurvivalreturns.feature.drop.SupplyTier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;
import org.jspecify.annotations.Nullable;

/**
 * A falling supply drop: the tier's loot crate as the block will stand, the tier's parachute over it (a standalone
 * block model whose y -16 is the crate's lid) and the beam the crate will keep. The whole thing sways a little.
 */
public final class SupplyDropRenderer extends EntityRenderer<SupplyDropEntity, SupplyDropRenderer.State> {
    public static final EnumMap<SupplyTier, StandaloneModelKey<BlockStateModelPart>> PARACHUTES = new EnumMap<>(SupplyTier.class);
    private static final BlockDisplayContext DISPLAY = BlockDisplayContext.create();
    static {
        for (SupplyTier tier : SupplyTier.values())
            PARACHUTES.put(tier, new StandaloneModelKey<>(() -> ArkSurvivalReturns.MOD_ID + ":parachute_" + tier.id));
    }

    private final BlockModelResolver blocks;

    public SupplyDropRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.8f;
        blocks = context.getBlockModelResolver();
    }

    @Override public State createRenderState() { return new State(); }

    /** The beam reaches far above the crate's own box. */
    @Override protected boolean affectedByCulling(SupplyDropEntity entity) { return false; }

    @Override public void extractRenderState(SupplyDropEntity entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        SupplyTier tier = entity.tier();
        state.colour = 0xFF000000 | tier.colour;
        blocks.update(state.crate, DropContent.LOOT_CRATE.get().defaultBlockState().setValue(LootCrateBlock.TIER, tier), DISPLAY);
        state.parachute = Minecraft.getInstance().getModelManager().getStandaloneModel(PARACHUTES.get(tier));
        state.beamTime = Math.floorMod(entity.level().getGameTime(), 40) + partialTicks;
        // As the beacon does: the beam thickens with distance so it stays visible.
        state.beamScale = Math.max(1f, (float)Math.sqrt(state.distanceToCameraSq) / 96f);
    }

    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose();
        pose.translate(-.5f, 0f, -.5f);
        BeaconRenderer.submitBeaconBeam(pose, collector, BeaconRenderer.BEAM_LOCATION, 1f, state.beamTime, 0,
                BeaconRenderer.MAX_RENDER_Y, state.colour, .2f * state.beamScale, .25f * state.beamScale);
        pose.popPose();
        pose.pushPose();
        // The sway turns about the canopy, three blocks up, so the crate swings under it.
        pose.translate(0f, 3f, 0f);
        pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(state.ageInTicks * .06f) * 4f));
        pose.mulPose(Axis.XP.rotationDegrees(Mth.cos(state.ageInTicks * .045f) * 3f));
        pose.translate(-.5f, -3f, -.5f);
        state.crate.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        if (state.parachute != null) {
            pose.translate(0f, 2f, 0f);
            collector.submitBlockModel(pose, Sheets.cutoutBlockSheet(), List.of(state.parachute), BlockModelRenderState.EMPTY_TINTS,
                    state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        }
        pose.popPose();
        super.submit(state, pose, collector, camera);
    }

    public static final class State extends EntityRenderState {
        final BlockModelRenderState crate = new BlockModelRenderState();
        @Nullable BlockStateModelPart parachute;
        int colour;
        float beamTime, beamScale = 1f;
    }
}
