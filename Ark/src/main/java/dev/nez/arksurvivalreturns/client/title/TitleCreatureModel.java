package dev.nez.arksurvivalreturns.client.title;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.resources.Identifier;

/** The same geometry, clips and skins the creature uses in the world (client/CreatureModel), one skin per model. */
final class TitleCreatureModel extends GeoModel<TitleCreature> {
    private final Identifier model;
    private final Identifier texture;

    TitleCreatureModel(String species, String variant) {
        model = ArkSurvivalReturns.id("entity/" + species);
        texture = ArkSurvivalReturns.id("textures/entity/" + species + (variant.isEmpty() ? "" : "_" + variant) + ".png");
    }

    @Override public Identifier getModelResource(GeoRenderState state) { return model; }
    @Override public Identifier getTextureResource(GeoRenderState state) { return texture; }
    @Override public Identifier getAnimationResource(TitleCreature creature) { return model; }
}
