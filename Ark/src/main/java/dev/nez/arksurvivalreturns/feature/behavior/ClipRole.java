package dev.nez.arksurvivalreturns.feature.behavior;

/**
 * Behaviour clips beyond the species' core set (idle, walk, run, attack, food, warning, sleep).
 * Each rig offers the subset its own ARK library contains; tools/build_behavior_clips.py resolves them.
 */
public enum ClipRole {
    /** Turning in place; rigs without one reuse their walking-turn clip. */
    TURN_LEFT(true), TURN_RIGHT(true),
    LOOK(false), SNIFF(false), POOP(false), STARTLE(false), HURT(false),
    /** Threat display held while warning an intruder. */
    THREAT(true),
    /**
     * Lying down, the loop held while asleep and getting up again: a rig's own sleep or basking clips, else
     * its knock-out sequence, the only lie-down most ARK libraries have. REST is the crocodilians' basking loop.
     */
    SETTLE(false), SLEEP(true), REST(true), WAKE(false),
    TROT(true),
    FLAP(true), FLY_LEFT(true), FLY_RIGHT(true), FLY_IDLE(true), GLIDE(true),
    SWIM_LEFT(true), SWIM_RIGHT(true);

    private final boolean looping;
    ClipRole(boolean looping) { this.looping = looping; }
    /** Looping roles are held by the movement layer; the others play once as a reaction. */
    public boolean looping() { return looping; }
}
