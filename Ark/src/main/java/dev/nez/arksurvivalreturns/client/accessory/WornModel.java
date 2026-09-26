package dev.nez.arksurvivalreturns.client.accessory;

import java.util.List;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

/**
 * An accessory baked onto an empty humanoid skeleton. The skeleton is posed exactly like the wearer
 * (HumanoidModel#setupAnim with the wearer's render state), so gear follows walking, sneaking, swimming and
 * riding the way armour does. On top of the pose it fits over armour, picks the slot side and arm model,
 * spreads wings, swings capes and lets pendants sway.
 */
public final class WornModel extends Model<WornState> {
    record Bone(ModelPart part, String humanoid, String side, String arms, String anim, float[] rest, float[] open) {}

    record Fit(ModelPart wrapper, String humanoid, String fit) {}

    private static final float[] ONE = {1, 1, 1};
    private final HumanoidModel<HumanoidRenderState> poser;
    private final List<Bone> bones;
    private final List<Fit> fits;
    final boolean arms;

    WornModel(ModelPart root, List<Bone> bones, List<Fit> fits) {
        super(root, RenderTypes::armorCutoutNoCull);
        this.poser = new HumanoidModel<>(root);
        this.bones = bones;
        this.fits = fits;
        this.arms = bones.stream().anyMatch(b -> b.humanoid().endsWith("_arm"));
    }

    @Override
    public void setupAnim(WornState state) {
        if (state.firstPerson == null) {
            poser.setupAnim(state.humanoid);
            for (ModelPart part : List.of(poser.head, poser.body, poser.rightArm, poser.leftArm, poser.rightLeg, poser.leftLeg))
                part.visible = true;
        } else {
            resetPose();
            boolean right = state.firstPerson == HumanoidArm.RIGHT;
            for (ModelPart part : List.of(poser.head, poser.body, poser.rightLeg, poser.leftLeg)) part.visible = false;
            poser.rightArm.visible = right;
            poser.leftArm.visible = !right;
            poser.rightArm.zRot = 0.1f;
            poser.leftArm.zRot = -0.1f;
        }
        for (Fit fit : fits) {
            float[] scale = state.fitted(fit.fit()) ? scale(fit.humanoid(), fit.fit()) : ONE;
            fit.wrapper().xScale = scale[0];
            fit.wrapper().yScale = scale[1];
            fit.wrapper().zScale = scale[2];
        }
        float walk = state.humanoid == null ? 0 : state.humanoid.walkAnimationPos;
        float walkSpeed = state.humanoid == null ? 0 : state.humanoid.walkAnimationSpeed;
        for (Bone bone : bones) {
            ModelPart part = bone.part();
            part.visible = (bone.side() == null || state.side == null || bone.side().equals(state.side))
                    && (bone.arms() == null || bone.arms().equals(state.slim ? "slim" : "wide"));
            if (!part.visible || bone.anim() == null) continue;
            switch (bone.anim()) {
                case "wing" -> {
                    float t = state.spread;
                    part.xRot = Mth.lerp(t, bone.rest()[0], bone.open()[0]);
                    part.yRot = Mth.lerp(t, bone.rest()[1], bone.open()[1]);
                    part.zRot = Mth.lerp(t, bone.rest()[2], bone.open()[2]);
                }
                case "cape" -> {
                    part.xRot = bone.rest()[0] + (6.0f + state.capeLean / 2.0f + state.capeFlap) * Mth.DEG_TO_RAD;
                    part.zRot = bone.rest()[2] + state.capeLean2 / 2.0f * Mth.DEG_TO_RAD;
                }
                case "sway" -> {
                    part.xRot = bone.rest()[0] + Mth.cos(walk * 0.6662f) * walkSpeed * 0.35f;
                    part.zRot = bone.rest()[2] + Mth.sin(walk * 0.3331f) * walkSpeed * 0.12f;
                }
                default -> { }
            }
        }
    }

    /** Pushes gear out over the armour piece that would otherwise swallow it (same numbers as the previews). */
    static float[] scale(String humanoid, String fit) {
        if (humanoid.endsWith("_arm")) return fit.equals("chest") ? new float[]{1.45f, 1.12f, 1.45f} : ONE;
        return switch (fit) {
            case "helmet" -> new float[]{1.22f, 1.22f, 1.22f};
            case "chest" -> new float[]{1.25f, 1.12f, 1.5f};
            case "legs" -> new float[]{1.25f, 1.06f, 1.3f};
            case "boots" -> new float[]{1.3f, 1.1f, 1.3f};
            default -> ONE;
        };
    }
}
