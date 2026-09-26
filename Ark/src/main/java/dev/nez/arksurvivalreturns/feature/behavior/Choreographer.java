package dev.nez.arksurvivalreturns.feature.behavior;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import dev.nez.arksurvivalreturns.feature.behavior.BehaviorAction.Cue;

/**
 * Turns decisions into a readable performance. When the mind changes state, the body does not snap
 * into it: an idle animal notices, faces the threat, roars or startles, and only then charges or bolts.
 * Sleepers wake before they react; a settling animal looks around before it lies down. While roaming,
 * pauses are filled with the idle beats the rig can play: looking around, sniffing, grazing, pooping.
 *
 * <p>Bridge beats last as long as their authored clip, varied per individual. Locomotion waits for
 * them; a strike on a target already in reach never does. No world access: the goal adapter feeds it
 * decisions and reads back the action and any one-shot cue to play.
 */
public final class Choreographer {
    public record Beat(BehaviorAction action, int ticks) {}

    private final BehaviorProfile profile;
    private final long seed;
    private final SplittableRandom random;
    private final ArrayDeque<Beat> queue = new ArrayDeque<>();
    private BehaviorState mode = BehaviorState.ROAM;
    private BehaviorAction action = BehaviorAction.IDLE;
    private int remaining;
    private Cue cue;
    private boolean announced;
    private int poopCooldown;
    private int beats;

    public Choreographer(BehaviorProfile profile, long seed) {
        this.profile = profile;
        this.seed = seed;
        this.random = new SplittableRandom(seed);
        this.poopCooldown = 1200 + (int) (Desync.unit(seed, 41) * 2400);
    }

    public BehaviorState mode() { return mode; }
    public BehaviorAction action() { return action; }
    public BehaviorProfile profile() { return profile; }
    public int remaining() { return remaining; }
    /** True while a bridge or idle beat holds the body in place. */
    public boolean holding() { return remaining > 0 && !action.motion().travels(); }
    /** The pending bridge, first beat first (for tests and the debug inspector). */
    public List<Beat> queued() { return List.copyOf(queue); }

    /** The one-shot cue of a beat that just started, once; null when none is pending. */
    public Cue takeCue() {
        var pending = cue;
        cue = null;
        return pending;
    }

    /**
     * The mind moved from one state to another. Queues the bridge that makes the change readable.
     *
     * @param urgent the animal was hit or the threat is at its body: reactions are reflexes, not displays
     */
    public void enter(BehaviorState from, BehaviorState to, boolean urgent) {
        mode = to;
        queue.clear();
        remaining = 0;
        cue = null;
        boolean calm = !from.alarm() && from != BehaviorState.INVESTIGATE;
        if (from.sleeping() && !to.sleeping()) {
            queue.add(beat(BehaviorAction.WAKE, urgent ? 8 : profile.ticks(Cue.WAKE, 24), urgent ? 0.2 : 0.35));
            calm = true;
        }
        switch (to) {
            case ALERT, INVESTIGATE -> { if (calm && !urgent) queue.add(beat(BehaviorAction.NOTICE, 12, 0.5)); }
            case THREATEN -> {
                if (calm) queue.add(beat(BehaviorAction.NOTICE, 10, 0.5));
                announce();
            }
            case HUNT -> {
                if (!urgent) {
                    if (calm) queue.add(beat(BehaviorAction.NOTICE, 10, 0.5));
                    if (profile.roarsBeforeCharge()) announce();
                    else if (calm && profile.stalker()) queue.add(beat(BehaviorAction.STALK, 50, 0.4));
                }
            }
            case DEFEND -> {
                if (!urgent) {
                    if (calm) queue.add(beat(BehaviorAction.NOTICE, 8, 0.5));
                    if (profile.has(Cue.WARN)) announce();
                }
            }
            case FLEE -> {
                if (from != BehaviorState.FLEE && from != BehaviorState.REGROUP) {
                    queue.add(profile.has(Cue.STARTLE)
                            ? beat(BehaviorAction.STARTLE, urgent ? Math.min(12, profile.ticks(Cue.STARTLE, 12)) : profile.ticks(Cue.STARTLE, 20), 0.15)
                            : beat(BehaviorAction.NOTICE, urgent ? 4 : 10, 0.5));
                }
            }
            case SLEEP -> {
                if (!from.sleeping()) {
                    queue.add(beat(BehaviorAction.LOOK, profile.ticks(Cue.LOOK, 30), 0.3));
                    if (profile.has(Cue.SETTLE)) queue.add(beat(BehaviorAction.SETTLE, profile.ticks(Cue.SETTLE, 40), 0.1));
                }
            }
            case REST -> { if (!from.sleeping()) queue.add(beat(BehaviorAction.IDLE, 30, 0.5)); }
            case RETURN_HOME, REGROUP, ROAM -> {
                if (from.alarm()) queue.add(beat(BehaviorAction.LOOK, profile.ticks(Cue.LOOK, 24), 0.4));
            }
            default -> {}
        }
        if (!to.alarm() && to != BehaviorState.INVESTIGATE) announced = false;
        next(false);
    }

    /** A warning display happens once per encounter, however often the mind re-enters a threat state. */
    private void announce() {
        if (announced || !profile.has(Cue.WARN)) return;
        announced = true;
        queue.add(beat(BehaviorAction.ROAR, profile.ticks(Cue.WARN, 40), 0.1));
    }

    /**
     * Advances time. {@code travelling} says whether the body currently has somewhere to go, so a
     * roaming pause fills with idle beats and a walk is labelled as one.
     */
    public void advance(int ticks, boolean travelling) {
        poopCooldown = Math.max(0, poopCooldown - ticks);
        remaining -= ticks;
        if (remaining > 0) return;
        remaining = 0;
        next(travelling);
    }

    /** A roaming pause started: play one idle beat now instead of standing frozen. */
    public void pause() {
        if (!queue.isEmpty() || remaining > 0) return;
        queue.add(idleBeat());
        next(false);
    }

    private void next(boolean travelling) {
        var beat = queue.poll();
        if (beat != null) {
            start(beat.action(), beat.ticks());
            return;
        }
        var steady = steady(travelling);
        if (steady != action) {
            action = steady;
            cue = null;
        }
    }

    private void start(BehaviorAction next, int ticks) {
        action = next;
        remaining = Math.max(1, ticks);
        cue = next.cue() != null && profile.has(next.cue()) ? next.cue() : null;
        beats++;
    }

    /** The action a state settles into once its bridge has played. */
    private BehaviorAction steady(boolean travelling) {
        return switch (mode) {
            case HUNT, DEFEND -> BehaviorAction.CHASE;
            case FLEE -> BehaviorAction.BOLT;
            case SLEEP -> BehaviorAction.SLEEP;
            case REST -> BehaviorAction.REST;
            case FEED -> BehaviorAction.FEED;
            case DRINK -> BehaviorAction.DRINK;
            case FORAGE -> travelling ? BehaviorAction.WALK : BehaviorAction.GRAZE;
            case ALERT -> BehaviorAction.NOTICE;
            case THREATEN -> profile.clips().has(ClipRole.THREAT) ? BehaviorAction.THREAT : BehaviorAction.NOTICE;
            default -> travelling ? BehaviorAction.WALK : BehaviorAction.IDLE;
        };
    }

    /** One pause beat: mostly standing, sometimes looking, sniffing, grazing or, rarely, pooping. */
    private Beat idleBeat() {
        var options = new ArrayList<Beat>();
        var weights = new ArrayList<Double>();
        options.add(beat(BehaviorAction.IDLE, 40 + random.nextInt(80), 0.3)); weights.add(4.0);
        options.add(beat(BehaviorAction.LOOK, profile.ticks(Cue.LOOK, 30), 0.2)); weights.add(profile.has(Cue.LOOK) ? 2.5 : 1.0);
        if (profile.has(Cue.SNIFF)) { options.add(beat(BehaviorAction.SNIFF, profile.ticks(Cue.SNIFF, 40), 0.1)); weights.add(profile.predator() ? 2.5 : 1.0); }
        if (profile.grazer()) { options.add(beat(BehaviorAction.GRAZE, 60 + random.nextInt(100), 0.3)); weights.add(3.0); }
        if (profile.has(Cue.POOP) && poopCooldown == 0) { options.add(beat(BehaviorAction.POOP, profile.ticks(Cue.POOP, 30), 0.05)); weights.add(0.6); }
        double total = weights.stream().mapToDouble(Double::doubleValue).sum(), roll = random.nextDouble() * total;
        for (int i = 0; i < options.size(); i++) {
            roll -= weights.get(i);
            if (roll <= 0) {
                if (options.get(i).action() == BehaviorAction.POOP) poopCooldown = 2400 + random.nextInt(3600);
                return options.get(i);
            }
        }
        return options.getFirst();
    }

    /** A beat whose length varies per individual and per occurrence by up to {@code spread}. */
    private Beat beat(BehaviorAction action, int ticks, double spread) {
        double own = Desync.unit(seed, 53 + action.ordinal()) * 2 - 1;
        double now = random.nextDouble() * 2 - 1;
        return new Beat(action, (int) Math.max(1, Math.round(ticks * (1 + spread * (0.5 * own + 0.5 * now)))));
    }

    /** Clears any bridge, e.g. when the creature leaves the full-detail tier or is tamed. */
    public void reset(BehaviorState state) {
        queue.clear();
        remaining = 0;
        cue = null;
        announced = false;
        mode = state;
        action = steady(false);
    }

    /** Beats started so far; lets tests and the inspector see that the choreography is alive. */
    public int beatsStarted() { return beats; }
}
