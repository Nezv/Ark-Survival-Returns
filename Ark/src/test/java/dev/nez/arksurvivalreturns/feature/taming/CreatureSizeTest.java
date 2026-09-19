package dev.nez.arksurvivalreturns.feature.taming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Pure balance logic that needs neither a world nor the game registries.
 *
 * <p>The profile table, the generated seat table, the torpor clip table and the on-disk manifests are
 * verified in the headless game tests ({@code taming_roster}), because they reference registered entities,
 * item tags and serialization, none of which exist on this test classpath.
 */
class CreatureSizeTest {
    @Test void bandsFollowTheRegisteredHitboxHeight() {
        assertEquals(CreatureSize.SMALL, CreatureSize.of(0.5), "An Archaeopteryx is small");
        assertEquals(CreatureSize.SMALL, CreatureSize.of(1.6), "A Direwolf is still small");
        assertEquals(CreatureSize.MEDIUM, CreatureSize.of(1.61), "A Ravager is medium");
        assertEquals(CreatureSize.MEDIUM, CreatureSize.of(3.5), "A Megapithecus is still medium");
        assertEquals(CreatureSize.LARGE, CreatureSize.of(3.51), "An Ankylosaurus is large");
        assertEquals(CreatureSize.LARGE, CreatureSize.of(8.0), "A Carnotaurus is still large");
        assertEquals(CreatureSize.GIANT, CreatureSize.of(8.01), "A Brontosaurus is a giant");
        assertEquals(CreatureSize.GIANT, CreatureSize.of(42.0), "The Titanosaur is a giant");
    }

    @Test void orderingIsMonotonic() {
        double previous = -1;
        for (var size : CreatureSize.values()) {
            assertTrue(size.ordinal() > previous, "Size bands must stay ordered");
            previous = size.ordinal();
        }
    }

    @Test void onlyKnockoutNeedsAnUnconsciousCreature() {
        assertTrue(TamingMethod.KNOCKOUT.requiresUnconscious());
        assertFalse(TamingMethod.PASSIVE.requiresUnconscious());
        assertFalse(TamingMethod.AERIAL.requiresUnconscious(), "A hungry flyer can be fed while awake");
    }
}
