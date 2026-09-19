package dev.nez.arksurvivalreturns.client;

import dev.nez.arksurvivalreturns.feature.taming.SedativeArrow;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.ArrowRenderState;
import net.minecraft.resources.Identifier;

/** Renders the tranquilizer arrow with the vanilla tipped-arrow texture. */
public final class SedativeArrowRenderer extends ArrowRenderer<SedativeArrow, ArrowRenderState> {
    private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/entity/projectiles/arrow_tipped.png");

    public SedativeArrowRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override protected Identifier getTextureLocation(ArrowRenderState state) {
        return TEXTURE;
    }

    @Override public ArrowRenderState createRenderState() {
        return new ArrowRenderState();
    }
}
