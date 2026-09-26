package dev.nez.arksurvivalreturns.client.accessory;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nez.arksurvivalreturns.feature.accessory.Accessory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

/**
 * Draws an Ark accessory on its wearer through Curios, and on the first-person arms for gloves, rings and
 * bracelets. Only loaded when Curios is installed.
 */
final class CuriosAccessoryRenderer implements ICurioRenderer {
    private final Accessory accessory;

    CuriosAccessoryRenderer(Accessory accessory) {
        this.accessory = accessory;
    }

    static void registerAll() {
        for (Accessory accessory : Accessory.values()) {
            ICurioRenderer.register(accessory.item(), () -> new CuriosAccessoryRenderer(accessory));
        }
    }

    @Override
    public <S extends LivingEntityRenderState, M extends EntityModel<? super S>> void render(ItemStack stack,
            SlotContext slot, PoseStack poseStack, SubmitNodeCollector collector, int light, S state,
            RenderLayerParent<S, M> parent, EntityRendererProvider.Context context, float yRotation, float xRotation) {
        if (!(state instanceof HumanoidRenderState humanoid)) return;
        WornModels.Entry entry = WornModels.get(accessory.id);
        if (entry == null) return;
        boolean slim = state instanceof AvatarRenderState avatar && avatar.skin.model() == PlayerModelType.SLIM;
        WornState worn = WornState.worn(humanoid, side(slot), slim, AccessoryClient.spread(slot.entity()));
        submit(entry, worn, stack, poseStack, collector, light, state.outlineColor);
    }

    @Override
    public void renderFirstPersonHand(ItemStack stack, SlotContext slot, HumanoidArm arm, PoseStack poseStack,
            SubmitNodeCollector collector, AvatarRenderState state, AbstractClientPlayer player, int light) {
        WornModels.Entry entry = WornModels.get(accessory.id);
        if (entry == null || !entry.model().arms) return;
        boolean slim = player.getSkin().model() == PlayerModelType.SLIM;
        String side = side(slot);
        if (side != null && !side.equals(arm == HumanoidArm.RIGHT ? "right" : "left")) return;
        submit(entry, WornState.firstPerson(arm, side, slim), stack, poseStack, collector, light, 0);
    }

    private static void submit(WornModels.Entry entry, WornState worn, ItemStack stack, PoseStack poseStack,
            SubmitNodeCollector collector, int light, int outline) {
        collector.order(1).submitModel(entry.model(), worn, poseStack, RenderTypes.armorCutoutNoCull(entry.texture()),
                light, OverlayTexture.NO_OVERLAY, -1, null, outline, null);
        if (entry.glow() != null) {
            collector.order(2).submitModel(entry.model(), worn, poseStack, RenderTypes.eyes(entry.glow()),
                    light, OverlayTexture.NO_OVERLAY, -1, null, outline, null);
        }
        if (stack.hasFoil()) {
            collector.order(3).submitModel(entry.model(), worn, poseStack, RenderTypes.armorEntityGlint(),
                    light, OverlayTexture.NO_OVERLAY, -1, null, outline, null);
        }
    }

    /** Paired slots (two rings, two bracelets) wear the first on the right hand and the second on the left. */
    private static @Nullable String side(SlotContext slot) {
        String id = slot.identifier();
        if (id.equals("ring") || id.equals("bracelet")) return slot.index() == 0 ? "right" : "left";
        if (id.equals("curio")) return "right";
        return null;
    }

    static boolean localSlim() {
        var player = Minecraft.getInstance().player;
        return player != null && player.getSkin().model() == PlayerModelType.SLIM;
    }
}
