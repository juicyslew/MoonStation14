package com.juicyslew.moonstation14.ms14.standing;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GunProneAimPolicyTest {
    private final PostureData down = new PostureData(true, true, 0, 0, 2, 1, false, true, false, 30);

    @Test void admissionRequiresEveryOwnerCondition() {
        assertTrue(GunProneAimPolicy.admitted(true, true, true, down, true));
        assertFalse(GunProneAimPolicy.admitted(false, true, true, down, true));
        assertFalse(GunProneAimPolicy.admitted(true, false, true, down, true));
        assertFalse(GunProneAimPolicy.admitted(true, true, false, down, true));
        assertFalse(GunProneAimPolicy.admitted(true, true, true, PostureData.STANDING, true));
        assertFalse(GunProneAimPolicy.admitted(true, true, true, down, false));
    }

    @Test void normalizationForcesFaceDownAndAimYawWithoutDroppingOtherState() {
        PostureData normalized = GunProneAimPolicy.normalize(down, 120);
        assertFalse(normalized.faceUp());
        assertEquals(120, normalized.movementFacingYaw());
        assertEquals(down.totalHands(), normalized.totalHands());
        assertEquals(down.knockdownUntil(), normalized.knockdownUntil());
    }

    @Test void admittedNormalizationOverridesSupineStateAndFailedAdmissionClearsPhysicalAimFlag() {
        PostureData admitted = GunProneAimPolicy.normalize(down.withSupportedGunAim(false), 45);
        assertFalse(admitted.faceUp());
        assertTrue(admitted.supportedGunAim());
        PostureData medical = down.withSupportedGunAim(true).forceDown(20);
        assertFalse(GunProneAimPolicy.admitted(false, false, true, medical, true));
        assertFalse(medical.withSupportedGunAim(false).supportedGunAim());
        assertFalse(PostureData.STANDING.withSupportedGunAim(false).supportedGunAim());
    }
}
