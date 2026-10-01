package dev.nez.arksurvivalreturns.feature.spawn;

import java.util.SplittableRandom;

/**
 * Regional predator-prey abundance behind the population budget: a Rosenzweig-MacArthur model (prey
 * capped by what the land feeds, predators that can only eat so fast) on continuous densities, in units
 * of its balance, so prey and predators both settle at 1. Below the cycling threshold a disturbed region
 * swings back to balance; just above it every region follows the same stable cycle, each at its own phase.
 *
 * <p>Pure arithmetic: the ledger advances a region over the in-game days that passed since it was last
 * read, so land nobody is near still changes. Continuous densities cannot die out by chance (a literal
 * Lotka-Volterra at these sizes loses its predators within days); a small floor stands in for animals
 * wandering in from neighbouring land.
 */
public final class LedgerModel {
    /** Handling time, conversion of prey into predators and predator losses, in balance units. */
    static final double HANDLING = 0.5, CONVERSION = 0.5, LOSS = 0.25;
    /** Attack rate that puts the prey balance at 1. */
    static final double ATTACK = LOSS / (CONVERSION - LOSS * HANDLING);
    /** Carrying capacity at which the balance gives way to a cycle. */
    public static final double THRESHOLD = 2 + 1 / (ATTACK * HANDLING);
    /** Lowest density either side keeps: animals drifting in from neighbouring land. */
    public static final double FLOOR = 0.02;
    private static final double STEP = 0.1;
    private static final int CYCLE_SAMPLES = 256;
    /** Longest stretch integrated at once; past it a region is on its cycle or at balance anyway. */
    private static final int MAX_PERIODS = 20;

    public record State(double prey, double predators) {
        public State {
            prey = Double.isFinite(prey) ? Math.max(FLOOR, prey) : 1;
            predators = Double.isFinite(predators) ? Math.max(FLOOR, predators) : 1;
        }
    }

    private final boolean cycles;
    private final double capacity, growth, period, unitsPerDay;
    private final double[] cyclePrey = new double[CYCLE_SAMPLES], cyclePredators = new double[CYCLE_SAMPLES];

    /**
     * @param cycles    true for a stable cycle just above the threshold, false for a balance just below it
     * @param cycleDays in-game days of one cycle, or of one swing back to balance
     */
    public LedgerModel(boolean cycles, double cycleDays) {
        this.cycles = cycles;
        capacity = THRESHOLD * (cycles ? 1.05 : 0.8);
        // Predator balance at 1: the prey surplus at balance feeds exactly one unit of predators.
        growth = (LOSS / CONVERSION) / (1 - 1 / capacity);
        period = cycles ? measureCycle() : linearPeriod();
        unitsPerDay = period / Math.max(0.5, cycleDays);
    }

    public boolean cycles() { return cycles; }
    /** Model time of one cycle. */
    public double period() { return period; }

    /** The starting state of a region: a phase on the cycle, or a disturbance around balance, from its seed. */
    public State initial(long seed) {
        var random = new SplittableRandom(seed);
        if (cycles) {
            int i = random.nextInt(CYCLE_SAMPLES);
            return new State(cyclePrey[i], cyclePredators[i]);
        }
        return new State(1 + 0.3 * (random.nextDouble() * 2 - 1), 1 + 0.3 * (random.nextDouble() * 2 - 1));
    }

    /** The state after this many in-game days. */
    public State advance(State state, double days) {
        if (!(days > 0)) return state;
        double units = days * unitsPerDay;
        // On the cycle a whole number of periods returns to the same point; at balance it stays there.
        if (units > MAX_PERIODS * period) units = MAX_PERIODS * period + (cycles ? units % period : 0);
        int steps = (int) Math.ceil(units / STEP);
        double dt = units / steps, n = state.prey(), p = state.predators();
        for (int i = 0; i < steps; i++) {
            double[] next = rk4(n, p, dt);
            n = Math.max(FLOOR, next[0]);
            p = Math.max(FLOOR, next[1]);
        }
        return new State(n, p);
    }

    /** Removes animals of one side: hunting, taming or capture, never ordinary predation. */
    public static State remove(State state, boolean predator, double amount) {
        return predator ? new State(state.prey(), state.predators() - amount) : new State(state.prey() - amount, state.predators());
    }

    private double[] derivative(double n, double p) {
        double eaten = ATTACK * n / (1 + ATTACK * HANDLING * n);
        return new double[]{growth * n * (1 - n / capacity) - eaten * p, CONVERSION * eaten * p - LOSS * p};
    }

    private double[] rk4(double n, double p, double dt) {
        double[] k1 = derivative(n, p);
        double[] k2 = derivative(n + dt / 2 * k1[0], p + dt / 2 * k1[1]);
        double[] k3 = derivative(n + dt / 2 * k2[0], p + dt / 2 * k2[1]);
        double[] k4 = derivative(n + dt * k3[0], p + dt * k3[1]);
        return new double[]{n + dt / 6 * (k1[0] + 2 * k2[0] + 2 * k3[0] + k4[0]),
                p + dt / 6 * (k1[1] + 2 * k2[1] + 2 * k3[1] + k4[1])};
    }

    /** Period of the small swings around balance, from the linearised model. */
    private double linearPeriod() {
        double eaten = ATTACK / (1 + ATTACK * HANDLING), slope = ATTACK / Math.pow(1 + ATTACK * HANDLING, 2);
        double trace = growth * (1 - 2 / capacity) - slope;
        double omega = Math.sqrt(Math.max(1e-9, eaten * CONVERSION * slope - trace * trace / 4));
        return 2 * Math.PI / omega;
    }

    /** Runs onto the cycle, measures its period between two prey peaks and samples one period evenly. */
    private double measureCycle() {
        double n = 1.3, p = 1, dt = 0.05;
        for (int i = 0; i < 160_000; i++) { double[] next = rk4(n, p, dt); n = next[0]; p = next[1]; }
        double start = -1, end = -1, t = 0;
        boolean rising = false;
        var trace = new java.util.ArrayList<double[]>();
        while (end < 0 && t < 1000) {
            double[] next = rk4(n, p, dt);
            t += dt;
            boolean nowRising = next[0] > n;
            if (rising && !nowRising) {
                if (start < 0) start = t;
                else end = t;
            }
            rising = nowRising;
            n = next[0]; p = next[1];
            if (start >= 0) trace.add(new double[]{t - start, n, p});
        }
        double length = end - start;
        for (int i = 0, j = 0; i < CYCLE_SAMPLES; i++) {
            double at = length * i / CYCLE_SAMPLES;
            while (j < trace.size() - 1 && trace.get(j + 1)[0] < at) j++;
            cyclePrey[i] = trace.get(j)[1];
            cyclePredators[i] = trace.get(j)[2];
        }
        return length;
    }
}
