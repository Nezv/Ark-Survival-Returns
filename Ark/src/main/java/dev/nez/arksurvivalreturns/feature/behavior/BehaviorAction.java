package dev.nez.arksurvivalreturns.feature.behavior;

/**
 * The concrete, animation-timed step a creature is performing inside its current {@link BehaviorState}.
 * The state says what the animal wants (hunt, flee, sleep); the action says what its body is doing right
 * now (noticing, roaring, bolting). Actions are synchronized so the client picks clips from them.
 */
public enum BehaviorAction {
    IDLE(Motion.HOLD, null),
    WALK(Motion.WALK, null),
    RUN(Motion.RUN, null),
    /** Rotating in place toward a new heading. */
    TURN(Motion.TURN, null),
    LOOK(Motion.HOLD, Cue.LOOK),
    SNIFF(Motion.HOLD, Cue.SNIFF),
    POOP(Motion.HOLD, Cue.POOP),
    GRAZE(Motion.HOLD, null),
    DRINK(Motion.HOLD, null),
    /** Head up and facing the stimulus before deciding. */
    NOTICE(Motion.FACE, null),
    ROAR(Motion.FACE, Cue.WARN),
    /** Held threat display toward an intruder. */
    THREAT(Motion.FACE, null),
    STARTLE(Motion.FACE, Cue.STARTLE),
    /** Slow, low approach before a charge. */
    STALK(Motion.CREEP, null),
    CHASE(Motion.RUN, null),
    BOLT(Motion.RUN, null),
    FEED(Motion.HOLD, null),
    SETTLE(Motion.HOLD, Cue.SETTLE),
    SLEEP(Motion.HOLD, null),
    WAKE(Motion.HOLD, Cue.WAKE),
    REST(Motion.HOLD, null);

    /** How the body may move while the action lasts. */
    public enum Motion {
        /** Stand still. */ HOLD,
        /** Stand and turn toward the stimulus. */ FACE,
        /** Turn in place toward a heading. */ TURN,
        /** Slow approach. */ CREEP,
        WALK, RUN;
        public boolean travels() { return this == CREEP || this == WALK || this == RUN; }
    }

    /** One-shot reaction clips an action starts with. */
    public enum Cue {
        /** The species' warning clip: roar, call, howl or startle display. */
        WARN(null), STARTLE(ClipRole.STARTLE), LOOK(ClipRole.LOOK), SNIFF(ClipRole.SNIFF), POOP(ClipRole.POOP),
        SETTLE(ClipRole.SETTLE), WAKE(ClipRole.WAKE);
        private final ClipRole role;
        Cue(ClipRole role) { this.role = role; }
        /** The behaviour role that plays this cue, or null for the species' own warning clip. */
        public ClipRole role() { return role; }
        public String key() { return name().toLowerCase(java.util.Locale.ROOT); }
    }

    private final Motion motion;
    private final Cue cue;
    BehaviorAction(Motion motion, Cue cue) { this.motion = motion; this.cue = cue; }
    public Motion motion() { return motion; }
    public Cue cue() { return cue; }
    public String key() { return "action.arksurvivalreturns." + name().toLowerCase(java.util.Locale.ROOT); }
}
