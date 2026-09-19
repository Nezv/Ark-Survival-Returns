package dev.nez.arksurvivalreturns.feature.taming;

import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import org.jspecify.annotations.Nullable;

/**
 * Maps synchronized state and real movement to the imported animation clips.
 *
 * <p>The clip set comes from the runtime animation files, so a name reported here always exists in the
 * shipped asset; {@code docs/taming-animation-matrix.json} lists the species whose source library has no
 * torpor sequence, and those fall back to the authored standing sleep pose rather than to a borrowed rig.
 *
 * <p>Unconsciousness has priority over locomotion and attack, and it is driven by the same synced state
 * the server uses for the physical restrictions, so a spectator and the rider see the same phase.
 */
public final class CreatureAnimationBridge {
    /** Movement clip for the current sedation phase, or null when the caller should pick its normal clip. */
    public static @Nullable String sedationClip(CreatureEntity creature, float partialTick) {
        var torpor = creature.torpor();
        if (!torpor.restricted()) return null;
        var clips = CreatureTorporClips.of(creature.species());
        String fallback = creature.species().sleepClip();
        return switch (torpor.phase()) {
            case COLLAPSING -> orElse(clips == null ? null : clips.in(), fallback);
            case TORPID -> {
                if (torpor.feedTicks() > 0 && clips != null && clips.eat() != null) yield clips.eat();
                yield orElse(clips == null ? null : clips.loop(), fallback);
            }
            case WAKING_TAMED -> orElse(clips == null ? null : clips.outTamed(), fallback);
            case WAKING_WILD -> orElse(clips == null ? null : clips.outWild(), fallback);
            default -> null;
        };
    }

    /** True when the sedation phase owns the pose and no locomotion or attack clip may be selected. */
    public static boolean sedationOwnsPose(CreatureEntity creature) {
        return creature.torpor().restricted();
    }

    /** One-shot phases are forced to play once; the unconscious loop is held. */
    public static boolean isOneShot(CreatureEntity creature, String clip) {
        var clips = CreatureTorporClips.of(creature.species());
        if (clips == null) return false;
        return clip != null && (clip.equals(clips.in()) || clip.equals(clips.outTamed()) || clip.equals(clips.outWild()));
    }

    private static String orElse(@Nullable String clip, String fallback) {
        return clip == null ? fallback : clip;
    }

    private CreatureAnimationBridge() {}
}
