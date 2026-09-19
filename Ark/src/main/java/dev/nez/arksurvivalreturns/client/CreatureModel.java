package dev.nez.arksurvivalreturns.client;

import com.geckolib.model.GeoModel;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.base.GeoRenderState;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import net.minecraft.resources.Identifier;

final class CreatureModel extends GeoModel<CreatureEntity> {
    static final DataTicket<Integer> TEXTURE_VARIANT = DataTicket.create("arksurvivalreturns:texture_variant", Integer.class);
    private static final String[] VARIANTS = {"ivory", "darken", "emerald", "midnight", "burgundy"};
    private final Species species;
    CreatureModel(Species species) { this.species = species; }
    @Override public Identifier getModelResource(GeoRenderState state) { return ArkSurvivalReturns.id("entity/" + species.id); }
    @Override public Identifier getTextureResource(GeoRenderState state) {
        int index = Math.floorMod(state.getOrDefaultGeckolibData(TEXTURE_VARIANT, 0), VARIANTS.length);
        return ArkSurvivalReturns.id("textures/entity/" + species.id + "_" + VARIANTS[index] + ".png");
    }
    @Override public Identifier getAnimationResource(CreatureEntity creature) { return ArkSurvivalReturns.id("entity/" + species.id); }
}
