package dev.nez.arksurvivalreturns.feature.behavior;

/** Deterministic decision model. No world access, target coordinates, navigation or damage calculation. */
public final class WildlifeMind {
    public record Observation(double signal, boolean visible, boolean prey, boolean intruding, boolean attacked,
            boolean intimidating, boolean farFromHome, boolean water, boolean forage, boolean night, double health) {}
    public record Routine(boolean enabled, boolean night, boolean sleepWanted, boolean safeToSleep,
            boolean danger, boolean defended, boolean cornered, int calmTicks, double hungerMultiplier, boolean regroup) {
        public Routine(boolean enabled, boolean night, boolean sleepWanted, boolean safeToSleep,
                boolean danger, boolean defended, boolean cornered, int calmTicks, double hungerMultiplier) {
            this(enabled, night, sleepWanted, safeToSleep, danger, defended, cornered, calmTicks, hungerMultiplier, false);
        }
        public static final Routine LEGACY = new Routine(false, false, false, true, false, false, false, 0, 1);
    }
    public record GroupRoutine(double hunger, BehaviorState state) {}
    /** The rule that chose the current state, in priority order; the showcase matrices quote these. */
    public enum Reason {
        ESCAPE("critical health, a bigger predator or a timid animal that is aware"),
        NIGHT_HERD("night herd under threat: flee, or stand once defended or cornered"),
        HOME_RANGE("left its home range or gave up a chase"),
        LOST_SIGHT("stimulus out of sight: investigate (timid animals flee)"),
        NOTICED("something sensed, awareness still building"),
        PROVOKED("attacked: retaliate"),
        HUNT_READY("hungry predator, prey in sight after the warning"),
        INTRUDER("intruder stayed close through the warning"),
        WARNING("intruder or prey in sight: warn first"),
        MEAL("a kill or a shared meal"),
        REGROUP("separated from the herd after an alarm"),
        SLEEP_TIME("scheduled sleep, safe and calm"),
        DRINK("thirsty with water at hand"),
        SEEK_WATER("thirsty, no water in reach"),
        TIRED("fatigue while calm"),
        GRAZE("hungry herbivore on grazing ground"),
        SEARCH("hungry carnivore at night"),
        ROUTINE("nothing pressing");
        private final String label;
        Reason(String label) { this.label = label; }
        public String label() { return label; }
    }
    private final boolean predator, timid, nocturnal;
    private BehaviorState state = BehaviorState.ROAM;
    private Reason reason = Reason.ROUTINE;
    private double hunger = 0.55, thirst = 0.35, fatigue = 0.15, awareness;
    private int age, memory, warning, provoked, chase, recovery, feeding;
    private int calm, flight;
    public WildlifeMind(boolean predator, boolean timid, boolean nocturnal) {
        this.predator = predator; this.timid = timid; this.nocturnal = nocturnal;
    }
    public BehaviorState state() { return state; }
    /** The rule that chose the current state on the last step. */
    public Reason reason() { return reason; }
    public int age() { return age; }
    public double hunger() { return hunger; }
    public double thirst() { return thirst; }
    public double fatigue() { return fatigue; }
    public double awareness() { return awareness; }
    public boolean remembers() { return memory > 0; }
    public int calmTicksRemaining() { return calm; }
    public void restoreCalm(int ticks) { calm = Math.clamp(ticks, 0, 1200); }
    public void interruptSleep(int ticks) {
        calm = Math.max(calm, ticks);
        if (state.sleeping()) { state = BehaviorState.ALERT; age = 0; }
    }
    public void restoreNeeds(double food, double water, double tired) {
        hunger = clamp(food); thirst = clamp(water); fatigue = clamp(tired);
    }
    public void ate() { hunger = 0.05; feeding = 100; memory = 0; awareness = 0; provoked = 0; }
    /** Water-bound bodies never thirst: they live in what they would drink. */
    public void quench() { thirst = 0; }
    /** Adopts the state a cheaper routine was showing (sleeping, roaming, grazing) when full detail resumes. */
    public void resumeAs(BehaviorState shown) { if (shown != state) { state = shown; age = 0; } }
    public void abandonChase() { recovery = 200; memory = 0; awareness = 0; chase = 0; }
    public void defendHerd() { provoked = 100; memory = 100; awareness = 1; }
    public BehaviorState step(Observation o, int ticks) {
        return step(o, ticks, Routine.LEGACY);
    }
    public BehaviorState step(Observation o, int ticks, Routine routine) {
        return step(o, ticks, routine, null);
    }
    public BehaviorState step(Observation o, int ticks, Routine routine, GroupRoutine group) {
        age += ticks;
        hunger = group == null ? clamp(hunger + ticks / 24000.0 * (routine.enabled && predator && routine.night ? routine.hungerMultiplier : 1)) : clamp(group.hunger);
        thirst = clamp(thirst + ticks / 18000.0);
        fatigue = clamp(fatigue + ticks / (state.combat() || state == BehaviorState.FLEE ? 1200.0 : 30000.0));
        provoked = Math.max(0, provoked - ticks);
        recovery = Math.max(0, recovery - ticks);
        feeding = Math.max(0, feeding - ticks);
        calm = Math.max(0, calm - ticks);
        if (routine.enabled && (o.signal > 0 || o.attacked || routine.danger || !routine.safeToSleep)) calm = routine.calmTicks;
        flight = state == BehaviorState.FLEE ? flight + ticks : 0;
        if (o.attacked) { provoked = 200; awareness = 1; }
        if (o.signal > 0 || o.attacked) {
            memory = 100;
            awareness = clamp(awareness + o.signal * ticks / 30.0);
        } else { memory = Math.max(0, memory - ticks); awareness = clamp(awareness - ticks / 100.0); }
        if (routine.enabled && routine.danger && o.visible) awareness = Math.max(0.6, awareness);
        boolean canHunt = predator && (!routine.enabled || routine.night);
        warning = o.visible && awareness >= 0.45 && (o.intruding || (canHunt && o.prey && hunger >= 0.4)) ? warning + ticks : 0;
        chase = state.combat() ? chase + ticks : 0;
        if (chase >= 300 || (o.farFromHome && state.combat())) abandonChase();
        BehaviorState next;
        Reason why;
        if ((o.health < 0.25 || o.intimidating || timid) && memory > 0 && awareness >= 0.45) { next = BehaviorState.FLEE; why = Reason.ESCAPE; }
        else if (routine.enabled && !predator && routine.night && memory > 0 && awareness >= 0.45
                && (routine.danger || state == BehaviorState.FLEE || (state == BehaviorState.DEFEND && provoked > 0))) {
            boolean stand = o.health >= 0.5 && o.visible && (routine.defended && flight >= 60
                    || routine.cornered || (routine.defended && state == BehaviorState.DEFEND));
            next = stand ? BehaviorState.DEFEND : BehaviorState.FLEE;
            why = Reason.NIGHT_HERD;
        }
        else if (recovery > 0 || o.farFromHome) { next = BehaviorState.RETURN_HOME; why = Reason.HOME_RANGE; }
        else if (memory > 0 && awareness >= 0.20) {
            if (!o.visible) { next = timid && awareness >= 0.6 ? BehaviorState.FLEE : BehaviorState.INVESTIGATE; why = Reason.LOST_SIGHT; }
            else if (awareness < 0.55) { next = BehaviorState.ALERT; why = Reason.NOTICED; }
            else if (provoked > 0) { next = BehaviorState.DEFEND; why = Reason.PROVOKED; }
            else if (canHunt && o.prey && hunger >= 0.4 && warning >= 40) { next = BehaviorState.HUNT; why = Reason.HUNT_READY; }
            else if (o.intruding && warning >= 60) { next = BehaviorState.DEFEND; why = Reason.INTRUDER; }
            else {
                next = o.intruding || (canHunt && o.prey && hunger >= 0.4) ? BehaviorState.THREATEN : BehaviorState.ALERT;
                why = next == BehaviorState.THREATEN ? Reason.WARNING : Reason.NOTICED;
            }
        } else if (feeding > 0 || group != null && group.state == BehaviorState.FEED) { next = BehaviorState.FEED; why = Reason.MEAL; }
        else if (routine.enabled && routine.regroup) { next = BehaviorState.REGROUP; why = Reason.REGROUP; }
        else if (routine.enabled && routine.sleepWanted && routine.safeToSleep && calm == 0
                && thirst < 0.9 && (predator || hunger < 0.9)
                && !(state == BehaviorState.DRINK && thirst > 0.1)
                && !(state == BehaviorState.FORAGE && hunger > 0.1)) { next = BehaviorState.SLEEP; why = Reason.SLEEP_TIME; }
        else if ((thirst >= 0.6 || (state == BehaviorState.DRINK && thirst > 0.1)) && o.water) { next = BehaviorState.DRINK; why = Reason.DRINK; }
        else if (thirst >= 0.6) { next = BehaviorState.SEEK_WATER; why = Reason.SEEK_WATER; }
        else if ((!routine.enabled || routine.safeToSleep && calm == 0) && (group != null ? group.state == BehaviorState.REST || fatigue >= 0.95 : fatigue >= 0.7
                || (!routine.enabled && o.night != nocturnal && fatigue >= 0.2)
                || (state == BehaviorState.REST && fatigue > 0.05))) { next = BehaviorState.REST; why = Reason.TIRED; }
        else if (!predator && (group != null ? group.state == BehaviorState.FORAGE : hunger >= 0.4 || (state == BehaviorState.FORAGE && hunger > 0.1)) && o.forage) { next = BehaviorState.FORAGE; why = Reason.GRAZE; }
        else {
            next = group != null ? (group.state == BehaviorState.SEARCH && canHunt ? BehaviorState.SEARCH : BehaviorState.ROAM)
                    : routine.enabled && canHunt && hunger >= 0.4 ? BehaviorState.SEARCH : BehaviorState.ROAM;
            why = next == BehaviorState.SEARCH ? Reason.SEARCH : Reason.ROUTINE;
        }
        reason = why;
        if (next != state) { state = next; age = 0; }
        if (group == null && state == BehaviorState.FORAGE) hunger = clamp(hunger - ticks / 400.0);
        if (state == BehaviorState.DRINK) thirst = clamp(thirst - ticks / 240.0);
        if (state.sleeping()) fatigue = clamp(fatigue - ticks / 1000.0);
        return state;
    }
    private static double clamp(double value) { return Double.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0.3; }
}
