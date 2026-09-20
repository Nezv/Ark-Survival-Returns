package dev.nez.arksurvivalreturns.feature.journal;

import org.jspecify.annotations.Nullable;

/** Client-installed hook that opens the journal screen; a no-op on the dedicated server. */
public final class JournalOpener {
    private static @Nullable Runnable opener;

    public static void set(@Nullable Runnable value) {
        opener = value;
    }

    public static void open() {
        if (opener != null) opener.run();
    }

    private JournalOpener() {}
}
