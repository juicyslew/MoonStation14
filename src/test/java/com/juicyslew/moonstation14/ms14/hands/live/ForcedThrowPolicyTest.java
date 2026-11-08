package com.juicyslew.moonstation14.ms14.hands.live;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ForcedThrowPolicyTest {
    @Test void handOriginUsesOneDoublePrecisionCalculationForPlacementAndRevalidation() {
        assertOldHandOriginDiffers(Vec3.ZERO);
        assertOldHandOriginDiffers(new Vec3(12_126_056.5, -59, -11_654_903.5));
        assertEquals(1.125, BodyLooseThrow.handOrigin(Vec3.ZERO, 1.8f, .25f).y);
        assertEquals(.725, BodyLooseThrow.handOrigin(Vec3.ZERO, .9f, .1f).y, 1e-7);
        assertEquals(.575, BodyLooseThrow.handOrigin(Vec3.ZERO, .9f, .25f).y, 1e-7);
    }

    private static void assertOldHandOriginDiffers(Vec3 bodyPosition) {
        double oldHeight = Math.min(1.125, Math.max(0.0, (.9f - .08f) - .075));
        Vec3 oldOrigin = new Vec3(bodyPosition.x, bodyPosition.y + oldHeight, bodyPosition.z);
        Vec3 sharedOrigin = BodyLooseThrow.handOrigin(bodyPosition, .9f, .08f);
        assertNotEquals(oldOrigin, sharedOrigin);
        assertEquals(-1.4901161193847656e-8, sharedOrigin.y - oldOrigin.y, 1e-12);
    }
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
