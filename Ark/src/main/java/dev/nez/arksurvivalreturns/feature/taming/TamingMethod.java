package dev.nez.arksurvivalreturns.feature.taming;

/**
 * How a creature becomes tameable. Flying creatures always use {@link #AERIAL}; aquatic creatures are
 * assigned individually during the roster audit and never inherit the aerial rule.
 */
public enum TamingMethod {
    /** Repeat feeding while the creature is awake and willing. */
    PASSIVE,
    /** Render the creature unconscious, then feed it from its taming inventory. */
    KNOCKOUT,
    /** Hunger-based feeding: accepted food depends on appetite, not on chance. */
    AERIAL;

    /** True when the method requires an unconscious creature at the moment of feeding. */
    public boolean requiresUnconscious() {
        return this == KNOCKOUT;
    }
}
