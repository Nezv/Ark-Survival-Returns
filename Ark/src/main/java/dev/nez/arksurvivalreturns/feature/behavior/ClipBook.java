package dev.nez.arksurvivalreturns.feature.behavior;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * One species' runtime clips: length in ticks, the ground speed a locomotion clip was authored for
 * and the behaviour roles the rig can play. Built by the generated {@link BehaviorClips} table.
 */
public final class ClipBook {
    /** @param groundSpeed blocks per second at 1x playback, NaN when the clip has no stance phase */
    public record Clip(String name, int ticks, double groundSpeed, boolean loop) {}

    public static final ClipBook EMPTY = new Builder("").build();
    private final String species;
    private final Map<String, Clip> clips;
    private final Map<ClipRole, Clip> roles;

    private ClipBook(String species, Map<String, Clip> clips, Map<ClipRole, Clip> roles) {
        this.species = species;
        this.clips = Map.copyOf(clips);
        this.roles = roles.isEmpty() ? Map.of() : new EnumMap<>(roles);
    }

    public String species() { return species; }
    public Clip clip(String name) { return name == null ? null : clips.get(name); }
    public int ticks(String name, int fallback) {
        var clip = clip(name);
        return clip == null ? fallback : clip.ticks();
    }
    public double groundSpeed(String name) {
        var clip = clip(name);
        return clip == null ? Double.NaN : clip.groundSpeed();
    }
    public boolean has(ClipRole role) { return roles.containsKey(role); }
    public Clip role(ClipRole role) { return roles.get(role); }
    /** Clip name playing a role, or null when the rig has none. */
    public String name(ClipRole role) {
        var clip = roles.get(role);
        return clip == null ? null : clip.name();
    }
    public int roleTicks(ClipRole role, int fallback) {
        var clip = roles.get(role);
        return clip == null ? fallback : clip.ticks();
    }

    public static final class Builder {
        private final String species;
        private final Map<String, Clip> clips = new HashMap<>();
        private final Map<ClipRole, Clip> roles = new EnumMap<>(ClipRole.class);
        public Builder(String species) { this.species = species; }
        public Builder clip(String name, int ticks, double groundSpeed, boolean loop) {
            clips.put(name, new Clip(name, Math.max(1, ticks), groundSpeed, loop));
            return this;
        }
        public Builder role(ClipRole role, String name) {
            var clip = clips.get(name);
            if (clip == null) throw new IllegalArgumentException(species + ": role " + role + " names unknown clip " + name);
            roles.put(role, clip);
            return this;
        }
        public ClipBook build() { return new ClipBook(species, clips, roles); }
    }
}
