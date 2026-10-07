package dev.nez.arksurvivalreturns.feature.spawn;

import java.util.HashMap;
import java.util.Map;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import org.jspecify.annotations.Nullable;

/**
 * The kinds of wildlife a biome region keeps count of, each with its own quota of groups ({@link LandRegister}).
 * A first cut, to be settled with the user: plant eaters, hunters, the giants, flyers and the animals of the sea.
 */
public enum WildClass {
    GRAZER(0.65), HUNTER(0.20), APEX(0.05), FLYER(0.10), SEA(1.0);

    /** The share of a region's groups this class has: of a land region for the first four, of a sea region for the last. */
    public final double share;
    private static final Map<String, Species> SPECIES = new HashMap<>();

    static {
        for (Species species : Species.values()) SPECIES.put(species.id, species);
    }

    WildClass(double share) { this.share = share; }

    public static WildClass of(Species species) {
        return species.aquatic() ? SEA : species.flyer() ? FLYER : species.apex() ? APEX : species.predator ? HUNTER : GRAZER;
    }

    /** The species a register entry names, or null for one this build no longer has. */
    public static @Nullable Species species(String id) { return SPECIES.get(id); }
}
