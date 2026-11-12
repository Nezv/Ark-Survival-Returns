package dev.nez.arksurvivalreturns.feature.taming;

/**
 * How a creature becomes tameable. Flyers are knock-out tames too (BUG-17): a sedated bird glides to the ground and is
 * fed there, and an unconscious sea animal floats up to the surface.
 */
public enum TamingMethod {
    /** Repeat feeding while the creature is awake and willing. */
    PASSIVE,
    /** Render the creature unconscious, then feed it from its taming inventory. */
    KNOCKOUT;

    /** True when the method requires an unconscious creature at the moment of feeding. */
    public boolean requiresUnconscious() {
        return this == KNOCKOUT;
    }
}
