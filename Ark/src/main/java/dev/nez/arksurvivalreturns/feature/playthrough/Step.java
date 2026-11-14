package dev.nez.arksurvivalreturns.feature.playthrough;

/** One thing the playthrough does; ticked every server tick until it says it is done or runs out of time. */
public interface Step {
    String name();

    /** Ticks this step may take before it is written off as timed out. */
    default int timeout() { return 20 * 60; }

    /** @param ticks how many times this step has been ticked before */
    boolean tick(PlayRun run, int ticks);

    /**
     * Called once when the step ran out of time: it writes what it saw so far and clears up what it put in the world
     * (a creature, a hook), so a timeout never loses a measurement or leaves anything behind.
     */
    default void abandon(PlayRun run) {}

    static Step of(String name, java.util.function.Consumer<PlayRun> once) {
        return new Step() {
            @Override public String name() { return name; }
            @Override public boolean tick(PlayRun run, int ticks) { once.accept(run); return true; }
        };
    }

    /** Ticks a step the way the run does: true once it is done, timed out (abandoned, written down) or failed. */
    static boolean drive(PlayRun run, Step step, int ticks) {
        boolean done;
        try { done = step.tick(run, ticks); }
        catch (RuntimeException e) {
            dev.nez.arksurvivalreturns.ArkSurvivalReturns.LOGGER.error("Playthrough: step {} failed", step.name(), e);
            var failed = run.event("step_error");
            failed.addProperty("inner", step.name());
            failed.addProperty("error", String.valueOf(e));
            run.write(failed);
            return true;
        }
        if (!done && ticks >= step.timeout()) {
            var timeout = run.event("step_timeout");
            timeout.addProperty("inner", step.name());
            run.write(timeout);
            try { step.abandon(run); }
            catch (RuntimeException e) { dev.nez.arksurvivalreturns.ArkSurvivalReturns.LOGGER.error("Playthrough: abandoning {} failed", step.name(), e); }
            return true;
        }
        return done;
    }
}
