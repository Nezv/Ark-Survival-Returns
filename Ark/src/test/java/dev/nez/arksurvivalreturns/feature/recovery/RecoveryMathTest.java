package dev.nez.arksurvivalreturns.feature.recovery;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class RecoveryMathTest {
    @Test void mergeKeepsOldestFirst() {
        var merged = RecoveryMath.merge(List.of("old"), List.of("new"), value -> value);
        assertEquals(List.of("old", "new"), merged, "Folding must keep every value, oldest first");
        assertEquals(0, RecoveryMath.merge(List.of(), List.of(), value -> value).size());
    }

    @Test void mergeCopiesInsteadOfSharing() {
        var source = new StringBuilder("old");
        var merged = RecoveryMath.merge(List.of(source), List.of(), StringBuilder::new);
        source.append("!");
        assertEquals("old", merged.getFirst().toString(), "Stored entries must be copies");
    }

    @Test void mergeCountOnlyFoldsAtTheCap() {
        assertEquals(0, RecoveryMath.mergeCount(0, 3));
        assertEquals(0, RecoveryMath.mergeCount(2, 3), "Below the cap nothing folds");
        assertEquals(1, RecoveryMath.mergeCount(3, 3), "The third cache folds the oldest");
        assertEquals(3, RecoveryMath.mergeCount(5, 3), "Oversized backlogs fold down to the cap");
        assertEquals(4, RecoveryMath.mergeCount(4, 0), "A zero cap folds everything already stored into the new entry");
    }
}
