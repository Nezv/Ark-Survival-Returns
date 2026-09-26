package dev.nez.arksurvivalreturns.feature.behavior;

/**
 * What the choreography needs to know about a species, without touching the game registry.
 *
 * @param stalker  ambush hunters creep toward prey instead of announcing the charge
 * @param grazer   eats plants in place, so roaming pauses include grazing
 * @param warning  the species' warning clip (roar, call, howl or startle display), or null
 */
public record BehaviorProfile(String species, boolean predator, boolean timid, boolean herd, boolean stalker,
        boolean grazer, double height, String warning, ClipBook clips) {

    public boolean has(BehaviorAction.Cue cue) {
        return cue.role() == null ? warning != null && clips.clip(warning) != null : clips.has(cue.role());
    }

    /** Authored length of a cue's clip in ticks, or the fallback when the rig has none. */
    public int ticks(BehaviorAction.Cue cue, int fallback) {
        if (cue.role() == null) return warning == null ? fallback : clips.ticks(warning, fallback);
        return clips.roleTicks(cue.role(), fallback);
    }

    /** Big predators announce a charge with their roar; stalkers and prey never do. */
    public boolean roarsBeforeCharge() {
        return predator && !stalker && has(BehaviorAction.Cue.WARN);
    }
}
