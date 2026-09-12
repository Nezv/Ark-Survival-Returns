package dev.nez.arksurvivalreturns.feature.land;

/** Shared horizontal habitat ranges and group defaults for land wildlife. */
public enum LandFamily {
    BIG_CARNIVORE(1, 1, 32, 96, 96, 160),
    SMALL_CARNIVORE(4, 6, 24, 80, 80, 128),
    TITANOSAUR(1, 1, 24, 64, 64, 112),
    BIG_HERBIVORE(2, 4, 16, 48, 48, 96),
    SMALL_HERBIVORE(4, 6, 12, 32, 32, 64);

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
