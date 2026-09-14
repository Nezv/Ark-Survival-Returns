package dev.nez.arksurvivalreturns.client;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import net.minecraft.resources.Identifier;

final class CreatureModel extends GeoModel<CreatureEntity> {
    private final Species species;
    CreatureModel(Species species) { this.species = species; }
    @Override public Identifier getModelResource(GeoRenderState state) { return ArkSurvivalReturns.id("entity/" + species.id); }
    @Override public Identifier getTextureResource(GeoRenderState state) { return ArkSurvivalReturns.id("textures/entity/" + species.id + ".png"); }
    @Override public Identifier getAnimationResource(CreatureEntity creature) { return ArkSurvivalReturns.id("entity/" + species.id); }
}
