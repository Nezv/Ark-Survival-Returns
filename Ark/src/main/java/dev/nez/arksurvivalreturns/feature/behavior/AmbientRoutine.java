package dev.nez.arksurvivalreturns.feature.behavior;

import java.util.SplittableRandom;

/**
 * The tier-2 routine: what a creature that can be seen but is not near anyone does. It walks a few
 * blocks, turns, stops, looks around, grazes, sniffs or poops, and sleeps on its daily schedule.
 * One decision every few seconds, no perception and no needs, so a distant herd stays alive for the
 * price of a timer. Movement runs through the ordinary bounded navigation.
 */
public final class AmbientRoutine {
    public enum Step { STAND, WALK, TURN, LOOK, SNIFF, POOP, GRAZE, SLEEP }

    /**
     * @param turn     signed degrees to rotate for {@link Step#TURN}; positive turns right
     * @param distance blocks to walk for {@link Step#WALK}
     */
    public record Plan(Step step, int ticks, double turn, double distance) {}

    /**
     * The next step. {@code homeward} means the creature has drifted to the edge of its home range,
     * so it walks back instead of wandering further.
     */
    public static Plan next(DailySchedule.Phase phase, BehaviorProfile profile, SplittableRandom random, boolean homeward) {
        if (phase == DailySchedule.Phase.SLEEP) return new Plan(Step.SLEEP, 200 + random.nextInt(200), 0, 0);
        if (homeward) return new Plan(Step.WALK, 200, 0, 8 + random.nextInt(8));
        boolean patrol = phase == DailySchedule.Phase.HUNT;
        double walk = patrol ? 6 : 4, turn = 2, stand = patrol ? 1.5 : 3;
        double look = profile.clips().has(ClipRole.LOOK) ? 1.5 : 0.8;
        double sniff = profile.clips().has(ClipRole.SNIFF) ? (profile.predator() ? 2 : 0.6) : 0;
        double graze = profile.grazer() && !patrol ? 3 : 0;
        double poop = profile.clips().has(ClipRole.POOP) ? 0.25 : 0;
        double roll = random.nextDouble() * (walk + turn + stand + look + sniff + graze + poop);
        if ((roll -= walk) < 0) return new Plan(Step.WALK, 200, 0, 4 + random.nextInt(patrol ? 12 : 8));
        if ((roll -= turn) < 0) {
            double degrees = (40 + random.nextInt(90)) * (random.nextBoolean() ? 1 : -1);
            return new Plan(Step.TURN, 0, degrees, 0);
        }
        if ((roll -= stand) < 0) return new Plan(Step.STAND, 40 + random.nextInt(120), 0, 0);
        if ((roll -= look) < 0) return new Plan(Step.LOOK, profile.clips().roleTicks(ClipRole.LOOK, 40), 0, 0);
        if ((roll -= sniff) < 0) return new Plan(Step.SNIFF, profile.clips().roleTicks(ClipRole.SNIFF, 40), 0, 0);
        if ((roll -= graze) < 0) return new Plan(Step.GRAZE, 80 + random.nextInt(120), 0, 0);
        return new Plan(Step.POOP, profile.clips().roleTicks(ClipRole.POOP, 30), 0, 0);
    }

    /** The state and action a step shows on the health bar and to the animation layer. */
    public static BehaviorState state(Step step) {
        return switch (step) {
            case SLEEP -> BehaviorState.SLEEP;
            case GRAZE -> BehaviorState.FORAGE;
            default -> BehaviorState.ROAM;
        };
    }

    public static BehaviorAction action(Step step) {
        return switch (step) {
            case STAND -> BehaviorAction.IDLE;
            case WALK -> BehaviorAction.WALK;
            case TURN -> BehaviorAction.TURN;
            case LOOK -> BehaviorAction.LOOK;
            case SNIFF -> BehaviorAction.SNIFF;
            case POOP -> BehaviorAction.POOP;
            case GRAZE -> BehaviorAction.GRAZE;
            case SLEEP -> BehaviorAction.SLEEP;
        };
    }

    private AmbientRoutine() {}
}
