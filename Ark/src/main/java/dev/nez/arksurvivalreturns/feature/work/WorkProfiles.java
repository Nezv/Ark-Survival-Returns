package dev.nez.arksurvivalreturns.feature.work;

import java.util.EnumMap;
import java.util.Map;
import dev.nez.arksurvivalreturns.feature.creature.Species;

/** Which harvest job a species can run when given the WORK order. */
public final class WorkProfiles {
    public enum Job { NONE, FORAGE, MINERAL }

    public record Profile(Job job) {}

    private static final Profile NONE = new Profile(Job.NONE);
    private static final Map<Species, Profile> PROFILES = new EnumMap<>(Species.class);

    static {
        // Triceratops for berries and thatch; Ankylosaurus for stone and ores, as the roadmap assigns.
        PROFILES.put(Species.TRICERATOPS, new Profile(Job.FORAGE));
        PROFILES.put(Species.ANKYLOSAURUS, new Profile(Job.MINERAL));
    }

    public static Profile of(Species species) {
        return PROFILES.getOrDefault(species, NONE);
    }

    private WorkProfiles() {}
}
