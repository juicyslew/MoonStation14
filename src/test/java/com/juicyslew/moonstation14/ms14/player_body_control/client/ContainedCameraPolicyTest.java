package com.juicyslew.moonstation14.ms14.player_body_control.client;

import net.minecraft.client.CameraType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ContainedCameraPolicyTest {
    @Test void holdsRearPerspectiveAcrossOwnerTransferAndRestoresExactPreviousType() {
        var policy = new ContainedCameraPolicy();
        UUID body = UUID.randomUUID();
        UUID ownerA = UUID.randomUUID();
        UUID ownerB = UUID.randomUUID();
        assertEquals(CameraType.THIRD_PERSON_BACK,
                policy.reconcile(body, 12, true, CameraType.THIRD_PERSON_FRONT));
        // Owner identity is intentionally not part of the camera lease: a Unit -> Holder update
        // for the same body and epoch cannot recapture the forced rear perspective.
        assertEquals(CameraType.THIRD_PERSON_BACK,
                policy.reconcile(body, 12, true, CameraType.THIRD_PERSON_BACK));
        assertNotEquals(ownerA, ownerB);
        assertEquals(CameraType.THIRD_PERSON_FRONT,
                policy.reconcile(body, 12, false, CameraType.THIRD_PERSON_BACK));
    }

    @Test void aNewBodyOrEpochDoesNotInheritTheForcedPerspective() {
        var policy = new ContainedCameraPolicy();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        assertEquals(CameraType.THIRD_PERSON_BACK, policy.reconcile(first, 4, true, CameraType.FIRST_PERSON));
        assertEquals(CameraType.THIRD_PERSON_BACK, policy.reconcile(second, 5, true, CameraType.THIRD_PERSON_BACK));
        assertEquals(CameraType.FIRST_PERSON,
                policy.reconcile(second, 5, false, CameraType.THIRD_PERSON_BACK));
    }

    @Test void unclipRequiresExactCameraAndOwnedBodyAndEpochAndDisabledCollision() {
        var policy = new ContainedCameraPolicy();
        UUID body = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        policy.reconcile(body, 8, true, CameraType.FIRST_PERSON);
        assertTrue(policy.matches(body, body, 8, true));
        assertFalse(policy.matches(other, body, 8, true));
        assertFalse(policy.matches(body, other, 8, true));
        assertFalse(policy.matches(body, body, 9, true));
        assertFalse(policy.matches(body, body, 8, false));
    }

    @Test void sessionClearRestoresPriorModeOnNextReconciliation() {
        var policy = new ContainedCameraPolicy();
        UUID body = UUID.randomUUID();
        policy.reconcile(body, 1, true, CameraType.THIRD_PERSON_FRONT);
        assertEquals(CameraType.THIRD_PERSON_FRONT,
                policy.reconcile(null, 0, false, CameraType.THIRD_PERSON_BACK));
    }
}
