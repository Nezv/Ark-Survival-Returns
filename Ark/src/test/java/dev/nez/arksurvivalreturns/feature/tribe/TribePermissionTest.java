package dev.nez.arksurvivalreturns.feature.tribe;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TribePermissionTest {
    private static final int DEFAULTS = TribePermission.mask(true, true, true, false);

    @Test void masksCombineAllFlags() {
        assertTrue(TribePermission.allows(TribePermission.mask(true, false, false, false), TribePermission.RIDE));
        assertFalse(TribePermission.allows(TribePermission.mask(true, false, false, false), TribePermission.CARGO));
        assertTrue(TribePermission.allows(DEFAULTS, TribePermission.COMMANDS));
        assertFalse(TribePermission.allows(DEFAULTS, TribePermission.BREEDING), "Breeding defaults to off");
    }

    @Test void unknownFlagIsRejected() {
        assertNull(TribePermission.byName("fly"), "An unknown flag must not resolve");
        assertTrue(TribePermission.byName("CARGO") == TribePermission.CARGO, "Flag names are case-insensitive");
    }
}
