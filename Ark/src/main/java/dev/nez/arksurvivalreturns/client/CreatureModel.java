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
    /** Alert eyes: attacking, defending, threatening or fleeing (the synced behaviour state). */
    static final DataTicket<Boolean> EYE_ALERT = DataTicket.create("arksurvivalreturns:eye_alert", Boolean.class);
    private static final String[] VARIANTS = {"ivory", "darken", "emerald", "midnight", "burgundy"};
    private final Species species;
    CreatureModel(Species species) { this.species = species; }
    private static final String[] DRAGONS = {"red", "white", "black"};
    private String modelId(int variant) {
        return species == Species.DRAGON ? "dragon_" + DRAGONS[Math.floorMod(variant, 3)] : species.id;
    }
    @Override public Identifier getModelResource(GeoRenderState state) {
        return ArkSurvivalReturns.id("entity/" + modelId(state.getOrDefaultGeckolibData(TEXTURE_VARIANT, 0)));
    }
    @Override public Identifier getTextureResource(GeoRenderState state) {
        if (species == Species.DRAGON)
            return ArkSurvivalReturns.id("textures/entity/" + modelId(state.getOrDefaultGeckolibData(TEXTURE_VARIANT, 0)) + ".png");
        int index = Math.floorMod(state.getOrDefaultGeckolibData(TEXTURE_VARIANT, 0), VARIANTS.length);
        return ArkSurvivalReturns.id("textures/entity/" + species.id + "_" + VARIANTS[index] + ".png");
    }
    @Override public Identifier getAnimationResource(CreatureEntity creature) {
        int variant = creature instanceof dev.nez.arksurvivalreturns.feature.guardian.GuardianDragonEntity dragon
                ? dragon.beaconVariant() : Math.floorMod(creature.getUUID().hashCode(), 3);
        return ArkSurvivalReturns.id("entity/" + modelId(variant));
    }
}
