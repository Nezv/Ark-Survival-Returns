package dev.nez.arksurvivalreturns.feature.tribe;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TribePermissionTest {
    private static final int DEFAULTS = TribePermission.mask(true, true, true, false, true);

    @Test void masksCombineAllFlags() {
        assertTrue(TribePermission.allows(TribePermission.mask(true, false, false, false), TribePermission.RIDE));
        assertFalse(TribePermission.allows(TribePermission.mask(true, false, false, false), TribePermission.CARGO));
        assertTrue(TribePermission.allows(DEFAULTS, TribePermission.COMMANDS));
        assertFalse(TribePermission.allows(DEFAULTS, TribePermission.BREEDING), "Breeding defaults to off");
        assertTrue(TribePermission.allows(DEFAULTS, TribePermission.WORK), "Work defaults on for party members");
        assertTrue(TribePermission.allows(TribePermission.mask(false, false, false, false, true), TribePermission.WORK));
        assertFalse(TribePermission.allows(TribePermission.mask(true, true, true, true), TribePermission.WORK),
                "The four-flag helper keeps work off unless asked");
    }

    @Test void unknownFlagIsRejected() {
        assertNull(TribePermission.byName("fly"), "An unknown flag must not resolve");
        assertTrue(TribePermission.byName("CARGO") == TribePermission.CARGO, "Flag names are case-insensitive");
        assertTrue(TribePermission.byName("work") == TribePermission.WORK, "The work flag must resolve");
    }
}
