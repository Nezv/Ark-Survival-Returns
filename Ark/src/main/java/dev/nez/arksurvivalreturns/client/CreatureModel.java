package dev.nez.arksurvivalreturns.client;

import com.geckolib.model.GeoModel;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.base.GeoRenderState;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.CreatureVariants;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import net.minecraft.resources.Identifier;

final class CreatureModel extends GeoModel<CreatureEntity> {
    static final DataTicket<Integer> TEXTURE_VARIANT = DataTicket.create("arksurvivalreturns:texture_variant", Integer.class);
    /** Alert eyes: attacking, defending, threatening or fleeing (the synced behaviour state). */
    static final DataTicket<Boolean> EYE_ALERT = DataTicket.create("arksurvivalreturns:eye_alert", Boolean.class);
    /** The designed eyes of a big carnivore, as CreatureEyes left them this frame. */
    static final DataTicket<CreatureEyes> EYES = DataTicket.create("arksurvivalreturns:eyes", CreatureEyes.class);
    private final Species species;
    /** The species' own colour variants (CreatureVariants: only colourings its kind could have). */
    private final java.util.List<String> variants;
    /** Asked for every creature in every frame, so made once: per dragon, or one model and a texture per skin. */
    private final Identifier[] models, textures;
    CreatureModel(Species species) {
        this.species = species;
        variants = CreatureVariants.of(species);
        boolean dragon = species == Species.DRAGON;
        models = new Identifier[dragon ? variants.size() : 1];
        textures = new Identifier[variants.size()];
        for (int i = 0; i < models.length; i++) models[i] = ArkSurvivalReturns.id("entity/" + modelId(i));
        for (int i = 0; i < textures.length; i++)
            textures[i] = ArkSurvivalReturns.id("textures/entity/" + (dragon ? modelId(i) : species.id + "_" + variants.get(i)) + ".png");
    }
    private String modelId(int variant) {
        return species == Species.DRAGON ? "dragon_" + variants.get(Math.floorMod(variant, variants.size())) : species.id;
    }
    @Override public Identifier getModelResource(GeoRenderState state) {
        return models[Math.floorMod(state.getOrDefaultGeckolibData(TEXTURE_VARIANT, 0), models.length)];
    }
    @Override public Identifier getTextureResource(GeoRenderState state) {
        return textures[Math.floorMod(state.getOrDefaultGeckolibData(TEXTURE_VARIANT, 0), textures.length)];
    }
    @Override public Identifier getAnimationResource(CreatureEntity creature) {
        int variant = creature instanceof dev.nez.arksurvivalreturns.feature.guardian.GuardianDragonEntity dragon
                ? dragon.beaconVariant() : CreatureVariants.pick(species, creature.getUUID());
        return ArkSurvivalReturns.id("entity/" + modelId(variant));
    }
}
