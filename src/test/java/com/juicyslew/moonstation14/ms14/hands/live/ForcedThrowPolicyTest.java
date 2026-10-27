package com.juicyslew.moonstation14.ms14.hands.live;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ForcedThrowPolicyTest {
    @Test void voluntaryIntentRequiresActionAndCooldownWhileForcedDoesNot() {
        assertTrue(BodyLooseThrow.intentPermits(false, true, true));
        assertFalse(BodyLooseThrow.intentPermits(false, false, true));
        assertFalse(BodyLooseThrow.intentPermits(false, true, false));
        assertTrue(BodyLooseThrow.intentPermits(true, false, false));
        assertTrue(BodyLooseThrow.usesVoluntaryCooldown(false));
        assertFalse(BodyLooseThrow.usesVoluntaryCooldown(true));
    }

    @Test void trustedOffsetIsIndependentOfBodyLookAndBoundedToDisarmSourceRange() {
        Vec3 bodyLook = new Vec3(0, 0, 1);
        Vec3 supplied = new Vec3(1.25, 0, 0);
        assertNotEquals(bodyLook, supplied.normalize());
        assertEquals(new Vec3(1, 0, 0), supplied.normalize());
        assertTrue(supplied.horizontalDistance() >= 1 && supplied.horizontalDistance() <= 1.5);
        assertEquals(1.25, BodyLooseThrow.aimRange(true, 8.0, supplied));
        assertEquals(1.5, BodyLooseThrow.aimRange(true, 8.0, new Vec3(0, 0, 1.5)));
        assertEquals(8.0, BodyLooseThrow.aimRange(false, 8.0, supplied));
        assertFalse(Double.isFinite(new Vec3(Double.NaN, 0, 0).x));
    }
}
