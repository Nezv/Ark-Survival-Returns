package dev.nez.arksurvivalreturns.feature.spawn;

import static dev.nez.arksurvivalreturns.feature.spawn.BiomeProfile.Type.*;

import java.util.EnumMap;
import java.util.EnumSet;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.feature.spawn.BiomeProfile.Climate;
import dev.nez.arksurvivalreturns.feature.spawn.BiomeProfile.Type;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

/**
 * Where each species lives: the surface habitat types of its range, and whether it needs the cold.
 *
 * <p>A range follows what the animal is: grazers on open ground, browsers and ambushers under trees,
 * crocodilians on the banks, runners on dry scrub, fishers on the coast. Danger and level still come from
 * the area alone. Biomes of unknown type, caves, mushroom fields and sky islands hold no Ark wildlife.
 * Data packs widen a range with the biome tag {@code spawns/<species>}, or reclassify a biome with the
 * {@code ecology/<type>} tags ({@link BiomeProfile}).
 */
public final class SpeciesRange {
    private enum Snow {
        /** Basks to keep warm: never in the snow and never in a cold climate. */
        TROPICAL,
        /** Anywhere in the range that is not snow-covered. */
        NEVER,
        /** Snow-covered ground or a cold climate. */
        COLD,
        /** Snow-covered ground only. */
        SNOW
    }
    private record Range(EnumSet<Type> types, Snow snow) {}
    private static final EnumMap<Species, Range> RANGES = new EnumMap<>(Species.class);

    private static void range(Species species, Snow snow, Type first, Type... rest) {
        RANGES.put(species, new Range(EnumSet.of(first, rest), snow));
    }
    private static void warm(Species species, Type first, Type... rest) { range(species, Snow.NEVER, first, rest); }
    private static void tropical(Species species, Type first, Type... rest) { range(species, Snow.TROPICAL, first, rest); }

    static {
        // Grazers and browsers.
        warm(Species.PARASAUR, GRASSLAND, FOREST, WETLAND, RIVER, COAST, SAVANNA, JUNGLE);
        warm(Species.TRICERATOPS, GRASSLAND, SAVANNA, SHRUBLAND, FOREST);
        warm(Species.LYSTROSAURUS, DESERT, BADLANDS, SHRUBLAND, SAVANNA, COAST, VOLCANIC, GEOTHERMAL);
        warm(Species.PEGOMASTAX, FOREST, JUNGLE, TAIGA, SHRUBLAND, MOUNTAIN);
        warm(Species.ANKYLOSAURUS, MOUNTAIN, SHRUBLAND, BADLANDS, TAIGA, GRASSLAND);
        warm(Species.THERIZINOSAURUS, FOREST, TAIGA, JUNGLE);
        warm(Species.BRONTOSAURUS, GRASSLAND, SAVANNA, FOREST);
        warm(Species.TITANOSAUR, SAVANNA, GRASSLAND, SHRUBLAND);
        warm(Species.PARACERATHERIUM, SAVANNA, SHRUBLAND, GRASSLAND);
        // Hunters: runners on dry open ground, ambushers under trees, fishers on the banks.
        tropical(Species.VELOCIRAPTOR, DESERT, BADLANDS, SHRUBLAND, SAVANNA);
        warm(Species.TERRORBIRD, GRASSLAND, SAVANNA, SHRUBLAND);
        warm(Species.CARNOTAURUS, SAVANNA, GRASSLAND, SHRUBLAND, BADLANDS, DESERT);
        warm(Species.GIGANOTOSAURUS, SAVANNA, SHRUBLAND, BADLANDS, DESERT, GRASSLAND);
        warm(Species.DILOPHOSAUR, FOREST, JUNGLE, WETLAND);
        warm(Species.ALLOSAURUS, FOREST, SAVANNA, SHRUBLAND, BADLANDS);
        warm(Species.TYRANNOSAURUS, FOREST, TAIGA, GRASSLAND, SAVANNA);
        warm(Species.ACROCANTHOSAURUS, FOREST, WETLAND, SAVANNA);
        warm(Species.CERATOSAURUS, WETLAND, JUNGLE, FOREST, RIVER);
        tropical(Species.SPINOSAURUS, WETLAND, RIVER, JUNGLE, COAST);
        warm(Species.RAVAGER, MOUNTAIN, BADLANDS, VOLCANIC, GEOTHERMAL, TAIGA);
        // Banks and swamps.
        tropical(Species.KAPROSUCHUS, WETLAND, RIVER, JUNGLE);
        tropical(Species.SARCO, WETLAND, RIVER, COAST);
        tropical(Species.DEINOSUCHUS, WETLAND, RIVER, COAST);
        tropical(Species.TITANOBOA, JUNGLE, WETLAND);
        // The cold.
        range(Species.MEGALOCERUS, Snow.COLD, TAIGA, FOREST, TUNDRA, GRASSLAND, MOUNTAIN, SHRUBLAND, WETLAND, BADLANDS, RIVER);
        range(Species.DIREWOLF, Snow.COLD, TAIGA, FOREST, TUNDRA, MOUNTAIN, SHRUBLAND, BADLANDS);
        range(Species.UNICORN, Snow.COLD, GRASSLAND, TUNDRA, FOREST);
        range(Species.MAMMOTH, Snow.SNOW, TUNDRA, GRASSLAND, TAIGA, SHRUBLAND, WETLAND, COAST);
        range(Species.SABERTOOTH, Snow.SNOW, MOUNTAIN, TUNDRA, TAIGA);
        range(Species.MEGAPITHECUS, Snow.SNOW, MOUNTAIN, TAIGA);
        // The sky: fishers over the shore, gliders in the canopy, soarers over high open ground.
        tropical(Species.PTERANODON, COAST, RIVER);
        warm(Species.ARCHAEOPTERYX, FOREST, JUNGLE, TAIGA);
        warm(Species.ARGENTAVIS, MOUNTAIN, BADLANDS, SAVANNA, SHRUBLAND, DESERT);
        warm(Species.QUETZAL, SAVANNA, GRASSLAND, MOUNTAIN, DESERT);
        warm(Species.DRAGON, VOLCANIC, MOUNTAIN, BADLANDS);
        // The sea, frozen or not.
        for (var species : Species.values())
            if (species.aquatic()) RANGES.put(species, new Range(EnumSet.of(OCEAN), null));
    }

    /** Whether the species' range holds a biome of this profile. */
    public static boolean lives(Species species, BiomeProfile profile) {
        var range = RANGES.get(species);
        if (range == null || !range.types().contains(profile.type())) return false;
        if (range.snow() == null) return true;
        return switch (range.snow()) {
            case TROPICAL -> !profile.snowy() && profile.climate() != Climate.COLD;
            case NEVER -> !profile.snowy();
            case COLD -> profile.snowy() || profile.climate() == Climate.COLD;
            case SNOW -> profile.snowy();
        };
    }

    /** The range, or the data pack's own {@code spawns/<species>} tag. */
    public static boolean lives(Species species, Holder<Biome> biome) {
        return biome.is(species.biomes) || lives(species, SurfaceBiomes.profile(biome));
    }

    /** True when every species has a range; a new species without one would silently never spawn. */
    public static boolean complete() { return RANGES.keySet().containsAll(EnumSet.allOf(Species.class)); }

    private SpeciesRange() {}
}
