package dev.nez.arksurvivalreturns.feature.taming;

import dev.nez.arksurvivalreturns.Config;

/**
 * Size band of a registered creature. The band decides the torpor ceiling a sedative has to fill, so
 * a dose that drops a Lystrosaurus leaves a Titanosaur walking.
 *
 * <p>The bands are derived from the registered hitbox height, not from a hand-written list, so a new
 * species cannot silently inherit another group's balance.
 */
public enum CreatureSize {
    /** Under 1.6 blocks: Lystrosaurus up to a Direwolf. */
    SMALL,
    /** Up to 3.5 blocks: Kaprosuchus up to a Megapithecus. */
    MEDIUM,
    /** Up to 8 blocks: a Mammoth up to a Carnotaurus. */
    LARGE,
    /** 8 blocks and above: Brontosaurus, the large carnivores and the Titanosaur. */
    GIANT;

    public double maxTorpor() {
        return Config.MAX_TORPOR.get(this).get();
    }

    public static CreatureSize of(double height) {
        if (height <= 1.6) return SMALL;
        if (height <= 3.5) return MEDIUM;
        if (height <= 8.0) return LARGE;
        return GIANT;
    }
}
