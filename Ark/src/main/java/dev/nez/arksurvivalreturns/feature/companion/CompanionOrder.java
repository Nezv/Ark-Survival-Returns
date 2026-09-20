package dev.nez.arksurvivalreturns.feature.companion;

/** Standing orders for a tamed creature. Whistle use cycles them in this order. */
public enum CompanionOrder {
    FOLLOW, STAY, WANDER, WORK;

    /** Cycles the order; WORK is skipped for species without a work profile. */
    public CompanionOrder next(boolean workCapable) {
        CompanionOrder candidate = values()[(ordinal() + 1) % values().length];
        return candidate == WORK && !workCapable ? FOLLOW : candidate;
    }

    public static CompanionOrder byOrdinal(int value) {
        return values()[Math.clamp(value, 0, values().length - 1)];
    }
}
