package dev.nez.arksurvivalreturns.client.title;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * Draws a menu creature into its own texture, which the GUI then blits in draw order: GUI rendering is
 * deferred since 1.21.6, so 3D models reach a screen only as a picture-in-picture. The texture origin is the
 * bottom centre, where the creature's feet stand.
 */
public final class CreatureSceneRenderer extends PictureInPictureRenderer<CreatureSceneState> {
    public CreatureSceneRenderer(MultiBufferSource.BufferSource buffers) { super(buffers); }

    @Override public Class<CreatureSceneState> getRenderStateClass() { return CreatureSceneState.class; }

    @Override protected float getTranslateY(int height, int guiScale) { return height; }

    @Override protected void renderToTexture(CreatureSceneState state, PoseStack pose) { state.scene().render(state, pose); }

    @Override protected String getTextureLabel() { return "ark menu creature"; }
}
