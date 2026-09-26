package dev.nez.arksurvivalreturns.client.title;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.jspecify.annotations.Nullable;

/**
 * One frame of a menu creature for {@link CreatureSceneRenderer}. Coordinates are GUI units; {@code scale} is
 * GUI units per block; the offsets place the feet between whole GUI pixels so parallax moves smoothly.
 */
public record CreatureSceneState(TitleScene scene, int x0, int y0, int x1, int y1, float scale, float offsetX,
                                 float offsetY, float partialTick, @Nullable ScreenRectangle bounds,
                                 @Nullable ScreenRectangle scissorArea) implements PictureInPictureRenderState {
    public CreatureSceneState(TitleScene scene, int x0, int y0, int x1, int y1, float scale, float offsetX, float offsetY,
                              float partialTick, @Nullable ScreenRectangle scissorArea) {
        this(scene, x0, y0, x1, y1, scale, offsetX, offsetY, partialTick,
                PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea), scissorArea);
    }
}
