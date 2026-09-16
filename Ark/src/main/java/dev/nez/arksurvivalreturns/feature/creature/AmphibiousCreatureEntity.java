package dev.nez.arksurvivalreturns.feature.creature;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Bank dweller: keeps the ordinary ground routine, saved land habitat and shared group satiation,
 * and swaps to its own swim clip set while it is in water. Used by the semi-aquatic swamp species.
 */
public final class AmphibiousCreatureEntity extends CreatureEntity {
    public AmphibiousCreatureEntity(EntityType<? extends CreatureEntity> type, Level level, Species species) {
        super(type, level, species);
    }
    @Override protected boolean swimming() { return isInWater(); }
}
