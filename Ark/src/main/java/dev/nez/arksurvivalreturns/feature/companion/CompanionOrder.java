package dev.nez.arksurvivalreturns.feature.companion;

/** Standing orders for a tamed creature. Whistle use cycles them in this order. */
public enum CompanionOrder {
    FOLLOW, STAY, WANDER;

    public CompanionOrder next() { return values()[(ordinal() + 1) % values().length]; }

    public static CompanionOrder byOrdinal(int value) {
        return values()[Math.clamp(value, 0, values().length - 1)];
    }
}
