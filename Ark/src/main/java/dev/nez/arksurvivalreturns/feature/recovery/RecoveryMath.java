package dev.nez.arksurvivalreturns.feature.recovery;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/** Pure arithmetic and list rules for the recovery cache; no Minecraft types so unit tests can run. */
public final class RecoveryMath {
    /** How many oldest entries must fold into a new one so no more than {@code max} remain. */
    public static int mergeCount(int current, int max) {
        return Math.max(0, current - Math.max(0, max - 1));
    }

    /** Copies both lists into one, oldest first, using the supplied copy function. */
    public static <T> List<T> merge(List<T> older, List<T> newer, UnaryOperator<T> copy) {
        var merged = new ArrayList<T>(older.size() + newer.size());
        older.forEach(value -> merged.add(copy.apply(value)));
        newer.forEach(value -> merged.add(copy.apply(value)));
        return List.copyOf(merged);
    }

    private RecoveryMath() {}
}
