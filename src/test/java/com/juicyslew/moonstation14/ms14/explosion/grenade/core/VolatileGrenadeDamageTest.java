package com.juicyslew.moonstation14.ms14.explosion.grenade.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VolatileGrenadeDamageTest {
    private static VolatileGrenadeDamage.Transition hit(VolatileGrenadeDamage state, double amount) {
        return state.apply(amount, 10.0, 45.0);
    }

    @Test void respectsExactThresholdBoundaries() {
        assertEquals(VolatileGrenadeDamage.Trigger.NONE, hit(VolatileGrenadeDamage.safe(), 9.99).trigger());
        assertEquals(VolatileGrenadeDamage.Trigger.ARM, hit(VolatileGrenadeDamage.safe(), 10).trigger());
        var belowImmediate = hit(VolatileGrenadeDamage.safe(), 44.99);
        assertEquals(VolatileGrenadeDamage.Trigger.ARM, belowImmediate.trigger());
        assertFalse(belowImmediate.state().terminal());
        assertEquals(VolatileGrenadeDamage.Trigger.NONE,
                hit(belowImmediate.state(), 0).trigger());
        var immediate = hit(VolatileGrenadeDamage.safe(), 45);
        assertEquals(VolatileGrenadeDamage.Trigger.IMMEDIATE, immediate.trigger());
        assertTrue(immediate.state().terminal());
    }

    @Test void accumulatesIncrementalDamageAndImmediateWinsAcrossBothThresholds() {
        var first = hit(VolatileGrenadeDamage.safe(), 6);
        var second = hit(first.state(), 4);
        assertEquals(VolatileGrenadeDamage.Trigger.ARM, second.trigger());
        var third = hit(second.state(), 35);
        assertEquals(VolatileGrenadeDamage.Trigger.IMMEDIATE, third.trigger());
        assertTrue(third.state().armed());
    }

    @Test void armedStateDoesNotRequestAnotherArmAndTerminalIsIgnored() {
        var alreadyArmed = new VolatileGrenadeDamage(9, true, false);
        var crossing = hit(alreadyArmed, 1);
        assertEquals(VolatileGrenadeDamage.Trigger.NONE, crossing.trigger());
        assertTrue(crossing.state().armed()); // Caller preserves the existing fuse deadline.
        var terminal = hit(crossing.state(), 35);
        assertEquals(VolatileGrenadeDamage.Trigger.IMMEDIATE, terminal.trigger());
        assertFalse(hit(terminal.state(), 100).accepted());
        assertSame(terminal.state(), hit(terminal.state(), 100).state());
    }

    @Test void invalidValuesAndOverflowDoNotMutateState() {
        var initial = new VolatileGrenadeDamage(Double.MAX_VALUE, false, false);
        for (double invalid : new double[] { -1, Double.NaN, Double.POSITIVE_INFINITY }) {
            var result = initial.apply(invalid, 10, 45);
            assertFalse(result.accepted());
            assertSame(initial, result.state());
        }
        var overflow = initial.apply(Double.MAX_VALUE, 10, 45);
        assertFalse(overflow.accepted());
        assertSame(initial, overflow.state());
        assertThrows(IllegalArgumentException.class, () -> new VolatileGrenadeDamage(-1, false, false));
    }
}
