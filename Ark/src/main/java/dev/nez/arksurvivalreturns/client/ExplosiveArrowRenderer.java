package dev.nez.arksurvivalreturns.client;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.explosive.ExplosiveArrow;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.ArrowRenderState;
import net.minecraft.resources.Identifier;

/** Renders the explosive arrow with its own texture, on the vanilla arrow layout. */
public final class ExplosiveArrowRenderer extends ArrowRenderer<ExplosiveArrow, ArrowRenderState> {
    private static final Identifier TEXTURE = ArkSurvivalReturns.id("textures/entity/projectiles/explosive_arrow.png");

    public ExplosiveArrowRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override protected Identifier getTextureLocation(ArrowRenderState state) {
        return TEXTURE;
    }

    @Override public ArrowRenderState createRenderState() {
        return new ArrowRenderState();
    }
}
