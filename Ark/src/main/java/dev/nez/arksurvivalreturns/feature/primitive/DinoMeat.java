package dev.nez.arksurvivalreturns.feature.primitive;

import dev.nez.arksurvivalreturns.feature.creature.Species;

/**
 * Meat families. Each one is a vanilla meat sprite with its own tone (tools/build_primitive_items.py);
 * the family a creature drops follows its body plan, and big creatures add prime cuts.
 */
public enum DinoMeat {
    HERBIVORE(3, 7, 0.7f),
    CARNIVORE(3, 8, 0.8f),
    PRIME(4, 10, 1.0f),
    BIRD(2, 6, 0.6f),
    REPTILE(2, 6, 0.8f),
    GAME(3, 7, 0.8f),
    MARINE(2, 6, 0.8f);

    /** Creatures at or above this health also drop prime meat. */
    public static final double PRIME_HEALTH = 120.0;

    public final int rawNutrition, cookedNutrition;
    public final float cookedSaturation;

    DinoMeat(int rawNutrition, int cookedNutrition, float cookedSaturation) {
        this.rawNutrition = rawNutrition;
        this.cookedNutrition = cookedNutrition;
        this.cookedSaturation = cookedSaturation;
    }

    public String id() { return name().toLowerCase(java.util.Locale.ROOT); }
    public String rawId() { return "raw_" + id() + "_meat"; }
    public String cookedId() { return "cooked_" + id() + "_meat"; }

    /** The ordinary meat a species drops; null for creatures with no usable meat. */
    public static DinoMeat of(Species species) {
        if (species == Species.CNIDARIA) return null;
        if (species == Species.DRAGON) return CARNIVORE;
        if (species.aquatic()) return MARINE;
        if (species.amphibious()) return REPTILE;
        if (species.flyer() || species == Species.TERRORBIRD || species == Species.ARCHAEOPTERYX) return BIRD;
        if (mammal(species)) return GAME;
        return species.predator ? CARNIVORE : HERBIVORE;
    }

    public static boolean mammal(Species species) {
        return switch (species) {
            case MEGALOCERUS, UNICORN, MAMMOTH, DIREWOLF, SABERTOOTH, MEGAPITHECUS, PARACERATHERIUM, RAVAGER -> true;
            default -> false;
        };
    }

    /** Whether the carcass yields leather (hide); birds and sea creatures do not. */
    public static boolean hide(Species species) {
        DinoMeat meat = of(species);
        return meat != null && meat != BIRD && meat != MARINE;
    }
}
