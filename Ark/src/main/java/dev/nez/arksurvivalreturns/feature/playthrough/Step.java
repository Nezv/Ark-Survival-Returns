package dev.nez.arksurvivalreturns.feature.playthrough;

/** One thing the playthrough does; ticked every server tick until it says it is done or runs out of time. */
public interface Step {
    String name();

    /** Ticks this step may take before it is written off as timed out. */
    default int timeout() { return 20 * 60; }

    /** @param ticks how many times this step has been ticked before */
    boolean tick(PlayRun run, int ticks);

    static Step of(String name, java.util.function.Consumer<PlayRun> once) {
        return new Step() {
            @Override public String name() { return name; }
            @Override public boolean tick(PlayRun run, int ticks) { once.accept(run); return true; }
        };
    }
}
