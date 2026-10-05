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
    private static final String[] DRAGONS = {"red", "white", "black"};
    private final Species species;
    /** Asked for every creature in every frame, so made once: per dragon, or one model and a texture per skin. */
    private final Identifier[] models, textures;
    CreatureModel(Species species) {
        this.species = species;
        boolean dragon = species == Species.DRAGON;
        models = new Identifier[dragon ? DRAGONS.length : 1];
        textures = new Identifier[dragon ? DRAGONS.length : VARIANTS.length];
        for (int i = 0; i < models.length; i++) models[i] = ArkSurvivalReturns.id("entity/" + modelId(i));
        for (int i = 0; i < textures.length; i++)
            textures[i] = ArkSurvivalReturns.id("textures/entity/" + (dragon ? modelId(i) : species.id + "_" + VARIANTS[i]) + ".png");
    }
    private String modelId(int variant) {
        return species == Species.DRAGON ? "dragon_" + DRAGONS[Math.floorMod(variant, 3)] : species.id;
    }
    @Override public Identifier getModelResource(GeoRenderState state) {
        return models[Math.floorMod(state.getOrDefaultGeckolibData(TEXTURE_VARIANT, 0), models.length)];
    }
    @Override public Identifier getTextureResource(GeoRenderState state) {
        return textures[Math.floorMod(state.getOrDefaultGeckolibData(TEXTURE_VARIANT, 0), textures.length)];
    }
    @Override public Identifier getAnimationResource(CreatureEntity creature) {
        int variant = creature instanceof dev.nez.arksurvivalreturns.feature.guardian.GuardianDragonEntity dragon
                ? dragon.beaconVariant() : Math.floorMod(creature.getUUID().hashCode(), 3);
        return ArkSurvivalReturns.id("entity/" + modelId(variant));
    }
}
