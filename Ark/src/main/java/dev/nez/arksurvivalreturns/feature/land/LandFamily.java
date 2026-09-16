package dev.nez.arksurvivalreturns.feature.land;

/**
 * Shared horizontal habitat ranges and group defaults for land wildlife.
 * This includes the semi-aquatic and cold profiles; water-bound species use AQUATIC
 * but take their roaming distance from the same family table.
 */
public enum LandFamily {
    BIG_CARNIVORE(1, 1, 32, 96, 96, 160),
    SMALL_CARNIVORE(4, 6, 24, 80, 80, 128),
    TITANOSAUR(1, 1, 24, 64, 64, 112),
    BIG_HERBIVORE(2, 4, 16, 48, 48, 96),
    SMALL_HERBIVORE(4, 6, 12, 32, 32, 64),
    /** Water-bound solo roamers; the water distances are unused because the body is the habitat. */
    AQUATIC(1, 1, 0, 16, 64, 96),
    /** Solitary bank dweller that hunts from water and basks on land. */
    AMPHIBIOUS(1, 1, 16, 48, 48, 96),
    /** Small crocodilian bask that shares one shoreline home. */
    SWAMP_PACK(2, 3, 16, 48, 48, 96),
    /** Cold pack hunter; the widest cold range so a wolf pack can follow a prey corridor. */
    COLD_PREDATOR(4, 6, 64, 112, 96, 144),
    /** Vigilant cold grazer around a sheltered forest edge. */
    COLD_GRAZER(4, 6, 40, 96, 40, 80),
    /** Heavy cold browser that needs a broad valley and a wide herd footprint. */
    COLD_BROWSER(2, 4, 48, 96, 48, 96),
    /** Solitary or paired ambusher with a long return leash. */
    COLD_STALKER(1, 2, 64, 112, 64, 112),
    /** Rare territorial guardian: a compact defended core with a short leash. */
    GUARDIAN(1, 1, 24, 64, 48, 80),
    /** Rare elusive grazer kept as a quiet discovery encounter. */
    RARE_GRAZER(1, 1, 40, 96, 40, 80);

    public final int minGroup, maxGroup, preferredWater, maximumWater, roam, leash;
    LandFamily(int min, int max, int preferred, int maximum, int roam, int leash) {
        minGroup = min; maxGroup = max; preferredWater = preferred; maximumWater = maximum;
        this.roam = roam; this.leash = leash;
    }
    public static double waterWeight(double distance, double preferred, double maximum) {
        if (!Double.isFinite(distance) || distance < 0 || maximum <= 0 || distance >= maximum) return 0;
        preferred = Math.clamp(preferred, 0, maximum);
        return distance <= preferred ? 1 : (maximum - distance) / (maximum - preferred);
    }
}
