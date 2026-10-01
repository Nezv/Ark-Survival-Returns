package dev.nez.arksurvivalreturns.feature.station;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorkstationPaymentTest {
    @Test void overlappingSelectorsNeedSeparateUnits() {
        // Two oak planks cannot simultaneously pay for 2 oak planks and 2 #planks.
        assertNull(WorkstationPayment.plan(new int[]{2}, new int[]{2, 2}, new boolean[][]{{true}, {true}}, 1));
        assertEquals(0, WorkstationPayment.maxCrafts(new int[]{2}, new int[]{2, 2}, new boolean[][]{{true}, {true}}));
    }
    @Test void flowReassignsBroadTagToPreserveExactItem() {
        // The broad tag comes first and initially takes oak; the exact cost must move it to spruce.
        var plan = WorkstationPayment.plan(new int[]{2, 2}, new int[]{2, 2}, new boolean[][]{{true, true}, {true, false}}, 1);
        assertNotNull(plan); assertArrayEquals(new int[]{2, 2}, plan.consumed());
        assertArrayEquals(new int[]{0, 2}, plan.allocation()[0]);
        assertArrayEquals(new int[]{2, 0}, plan.allocation()[1]);
    }
    @Test void shiftCraftFindsActualAffordableMaximum() {
        int[] available = {5, 8}; int[] cost = {2, 1}; boolean[][] matches = {{true, true}, {true, false}};
        assertEquals(4, WorkstationPayment.maxCrafts(available, cost, matches));
        assertNotNull(WorkstationPayment.plan(available, cost, matches, 4));
        assertNull(WorkstationPayment.plan(available, cost, matches, 5));
        assertArrayEquals(new int[]{5, 8}, available, "Planning is read-only");
    }
    @Test void hostileCountsAndEmptyCostCannotMintItems() {
        assertNull(WorkstationPayment.plan(new int[]{64}, new int[]{2}, new boolean[][]{{true}}, Integer.MAX_VALUE));
        assertNull(WorkstationPayment.plan(new int[]{64}, new int[]{1}, new boolean[][]{{true}}, -1));
        assertEquals(0, WorkstationPayment.maxCrafts(new int[]{64}, new int[]{}, new boolean[][]{}));
    }
}
