package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CombatMeleeTargetProbeTest {
    @Test void measuresNearestPhysicalBoundsInThreeDimensions() {
        AABB attacker = new AABB(0, 0, 0, 1, 1, 1);
        assertEquals(0, CombatMeleeTargetProbe.BodyBounds.distance(attacker, new AABB(.5, .5, .5, 1.5, 1.5, 1.5)));
        assertEquals(1.5, CombatMeleeTargetProbe.BodyBounds.distance(attacker, new AABB(2.5, 0, 0, 3, 1, 1)), 1e-12);
        assertTrue(CombatMeleeTargetProbe.BodyBounds.distance(attacker, new AABB(2.5001, 0, 0, 3, 1, 1)) > 1.5);
        double authoredLongRange = CombatMeleeTargetProbe.BodyBounds.distance(attacker, new AABB(4, 0, 0, 5, 1, 1));
        assertTrue(CombatMeleeTargetProbe.BodyBounds.withinRange(authoredLongRange, 4f));
        assertFalse(CombatMeleeTargetProbe.BodyBounds.withinRange(authoredLongRange, 1.5f));
        assertEquals(2, CombatMeleeTargetProbe.BodyBounds.distance(attacker, new AABB(0, 3, 0, 1, 4, 1)), 1e-12);
    }

    @Test void rejectsNonFiniteBounds() {
        assertTrue(Double.isNaN(CombatMeleeTargetProbe.BodyBounds.distance(
                new AABB(0, 0, 0, 1, 1, 1), new AABB(Double.NaN, 0, 0, 1, 1, 1))));
    }
}
