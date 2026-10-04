package dev.nez.arksurvivalreturns.feature.behavior;

import java.util.SplittableRandom;

/**
 * What an undisturbed animal does with its time: a chain of bouts, each held for a while, the way a watched
 * animal spends a morning. A grazer keeps its head down, takes a few steps to fresh grass, lifts its head to
 * look, and only now and then walks off to another patch. An animal with nothing to do stands far more than
 * it walks. A hunter on its rounds walks a leg, then stops to scent and listen.
 *
 * <p>One planner serves the near, full-detail routine and the far, cheap one, so a herd behaves the same at
 * any distance; only senses and needs differ. No world access: the goal adapter finds the ground to walk on.
 */
public final class CalmRoutine {
    /** What the animal is about while nothing presses it. */
    public enum Mode {
        /** On its feeding ground during feeding hours. */
        GRAZE,
        /** Awake with nothing to do. */
        LOAF,
        /** A hunter going round its range at night. */
        PATROL,
        /** Lying up: the midday rest of a herd, the lazy part of a hunter's day. Far routine only; near, the mind rests. */
        REST
    }

    public enum Step {
        STAND, LOOK, SNIFF, POOP, GRAZE,
        /** A few slow paces to fresh grass, head barely up. */
        STEP,
        /** A walk to somewhere else in the range. */
        WALK,
        /** Turning the body to face another way. */
        TURN,
        /** Lying down, awake. */
        LIE,
        SLEEP;

        /** A step that moves the body across the ground. */
        public boolean travels() { return this == STEP || this == WALK; }
    }

    /**
     * @param ticks    how long a held step lasts; the time limit of a walked one
     * @param turn     signed degrees to rotate for {@link Step#TURN}; positive turns right
     * @param distance blocks to walk for {@link Step#STEP} and {@link Step#WALK}
     * @param pace     share of the animal's wandering speed
     */
    public record Plan(Step step, int ticks, double turn, double distance, double pace) {}

    /** The scheduled sleep: held until the schedule or the shelter says otherwise. */
    public static Plan sleep(SplittableRandom random) {
        return new Plan(Step.SLEEP, 200 + random.nextInt(200), 0, 0, 0);
    }

    /** Back toward the middle of the home range, from its edge. */
    public static Plan homeward(BehaviorProfile profile, SplittableRandom random) {
        return new Plan(Step.WALK, 300, 0, (8 + random.nextInt(8)) * stride(profile), 1);
    }

    /**
     * The next bout. What came before decides what can follow: a walk always ends in a pause, grazing
     * alternates with a few steps and a look around, and nobody strings walks together.
     */
    public static Plan next(Mode mode, Step previous, BehaviorProfile profile, SplittableRandom random) {
        boolean moved = previous != null && previous.travels();
        double stand = 0, look = 0, sniff = 0, poop = 0, graze = 0, step = 0, walk = 0, turn = 0, lie = 0;
        boolean canSniff = profile.clips().has(ClipRole.SNIFF), canPoop = profile.clips().has(ClipRole.POOP);
        switch (mode) {
            case GRAZE -> {
                if (previous == Step.GRAZE) { step = 50; look = 18; graze = 14; stand = 8; turn = 5; walk = 3; poop = canPoop ? 2 : 0; }
                else if (moved) { graze = 80; look = 14; stand = 6; }
                else { graze = 72; step = 16; stand = 8; turn = 4; }
            }
            case LOAF -> {
                if (moved) { stand = 45; look = 25; turn = 8; sniff = canSniff ? 12 : 0; graze = profile.grazer() ? 18 : 0; }
                else {
                    walk = 34; stand = 24; look = 14; turn = 10; sniff = canSniff ? 6 : 0;
                    graze = profile.grazer() ? 14 : 0; poop = canPoop ? 1 : 0;
                }
            }
            case PATROL -> {
                if (moved) { stand = 35; look = 30; turn = 10; sniff = canSniff ? 35 : 0; }
                else { walk = 72; stand = 12; look = 10; sniff = canSniff ? 6 : 0; }
            }
            case REST -> {
                if (previous == Step.LIE) { lie = 82; stand = 12; look = 6; }
                else if (moved) { stand = 40; look = 20; lie = 40; }
                else { lie = 62; stand = 18; look = 12; turn = 8; }
            }
        }
        double slow = slow(profile), stride = stride(profile);
        double roll = random.nextDouble() * (stand + look + sniff + poop + graze + step + walk + turn + lie);
        if ((roll -= graze) < 0) return held(Step.GRAZE, 160 + random.nextInt(mode == Mode.GRAZE ? 280 : 120), slow);
        if ((roll -= step) < 0) return new Plan(Step.STEP, 200, 0, (2 + random.nextInt(4)) * stride, 0.6);
        if ((roll -= stand) < 0) return held(Step.STAND, mode == Mode.GRAZE ? 40 + random.nextInt(60) : 80 + random.nextInt(160), slow);
        if ((roll -= look) < 0) return new Plan(Step.LOOK, profile.clips().has(ClipRole.LOOK)
                ? profile.clips().roleTicks(ClipRole.LOOK, 60) : 50 + random.nextInt(50), 0, 0, 0);
        if ((roll -= sniff) < 0) return new Plan(Step.SNIFF, profile.clips().roleTicks(ClipRole.SNIFF, 40), 0, 0, 0);
        if ((roll -= turn) < 0) return new Plan(Step.TURN, 200, (30 + random.nextInt(70)) * (random.nextBoolean() ? 1 : -1), 0, 0);
        if ((roll -= lie) < 0) return held(Step.LIE, 400 + random.nextInt(800), 1);
        if ((roll -= walk) < 0) {
            double blocks = mode == Mode.PATROL ? 12 + random.nextInt(17) : mode == Mode.GRAZE ? 8 + random.nextInt(9) : 6 + random.nextInt(11);
            return new Plan(Step.WALK, 400, 0, blocks * stride, 1);
        }
        return new Plan(Step.POOP, profile.clips().roleTicks(ClipRole.POOP, 30), 0, 0, 0);
    }

    private static Plan held(Step step, int ticks, double slow) {
        return new Plan(step, (int) Math.round(ticks * slow), 0, 0, 0);
    }

    /** Big animals do everything for longer: up to twice the bout of a small one. */
    static double slow(BehaviorProfile profile) { return Math.clamp(0.85 + 0.09 * profile.height(), 1, 2); }

    /** And cover more ground with it: a Brontosaurus' few steps are a small animal's walk. */
    static double stride(BehaviorProfile profile) { return Math.clamp(0.8 + 0.1 * profile.height(), 1, 2.2); }

    /** The state a step shows on the health bar and to the animation layer in the far routine. */
    public static BehaviorState state(Step step) {
        return switch (step) {
            case SLEEP -> BehaviorState.SLEEP;
            case LIE -> BehaviorState.REST;
            case GRAZE, STEP -> BehaviorState.FORAGE;
            default -> BehaviorState.ROAM;
        };
    }

    public static BehaviorAction action(Step step) {
        return switch (step) {
            case STAND -> BehaviorAction.IDLE;
            case STEP, WALK -> BehaviorAction.WALK;
            case TURN -> BehaviorAction.TURN;
            case LOOK -> BehaviorAction.LOOK;
            case SNIFF -> BehaviorAction.SNIFF;
            case POOP -> BehaviorAction.POOP;
            case GRAZE -> BehaviorAction.GRAZE;
            case LIE -> BehaviorAction.REST;
            case SLEEP -> BehaviorAction.SLEEP;
        };
    }

    private CalmRoutine() {}
}
