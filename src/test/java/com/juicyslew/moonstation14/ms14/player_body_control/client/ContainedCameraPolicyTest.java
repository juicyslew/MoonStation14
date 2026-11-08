package com.juicyslew.moonstation14.ms14.player_body_control.client;

import net.minecraft.client.CameraType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ContainedCameraPolicyTest {
    @Test void carrierRelativeCameraRemovesBodySnapshotNoiseAtEveryRenderFraction() {
        var offset = new net.minecraft.world.phys.Vec3(0, .3, 0);
        for (float partial : new float[]{0, .25f, .5f, .75f, 1}) {
            var renderedParent = new net.minecraft.world.phys.Vec3(10 + partial, 64, -2);
            var independentlyPinnedBody = new net.minecraft.world.phys.Vec3(15 + partial * 2, 71, 9);
            var delta = ContainedCameraTranslation.delta(renderedParent, offset, independentlyPinnedBody);
            var relative = independentlyPinnedBody.add(delta).subtract(renderedParent);
            assertEquals(offset.x, relative.x, 1.0e-9);
            assertEquals(offset.y, relative.y, 1.0e-9);
            assertEquals(offset.z, relative.z, 1.0e-9);
        }
        var noisyBody = new net.minecraft.world.phys.Vec3(-500, 300, 200);
        var parent = new net.minecraft.world.phys.Vec3(3, 4, 5);
        var relative = noisyBody.add(ContainedCameraTranslation.delta(parent, offset, noisyBody)).subtract(parent);
        assertEquals(offset.x, relative.x, 1.0e-9);
        assertEquals(offset.y, relative.y, 1.0e-9);
        assertEquals(offset.z, relative.z, 1.0e-9);
    }

    @Test void unitViewpointAlignsDifferentNativeEyeHeightsWithoutChangingTransitPolicy() {
        var unitPosition = new net.minecraft.world.phys.Vec3(4.5, 64, 8.5);
        var humanFeet = com.juicyslew.moonstation14.ms14.containment.ContainedBodyOwner
                .positionForViewpoint(unitPosition, net.minecraft.world.entity.player.Player.DEFAULT_EYE_HEIGHT);
        var mouseFeet = com.juicyslew.moonstation14.ms14.containment.ContainedBodyOwner
                .positionForViewpoint(unitPosition, .255);
        assertEquals(unitPosition.add(0, net.minecraft.world.entity.player.Player.DEFAULT_EYE_HEIGHT, 0),
                humanFeet.add(0, net.minecraft.world.entity.player.Player.DEFAULT_EYE_HEIGHT, 0));
        assertEquals(humanFeet.add(0, net.minecraft.world.entity.player.Player.DEFAULT_EYE_HEIGHT, 0), mouseFeet.add(0, .255, 0));
        // Transit retains its own bore-aligned positionFor implementation and does not use this unit adapter.
        var transitEyeAlignedFeet = unitPosition.subtract(0, .255, 0);
        assertEquals(unitPosition, transitEyeAlignedFeet.add(0, .255, 0));
        assertNotEquals(mouseFeet, transitEyeAlignedFeet);
    }

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
