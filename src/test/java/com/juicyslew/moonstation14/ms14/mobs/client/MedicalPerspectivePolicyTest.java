package com.juicyslew.moonstation14.ms14.mobs.client;

import org.junit.jupiter.api.Test;
import com.juicyslew.moonstation14.ms14.eye.client.AngularRecoveryComfortCameraPolicy;
import com.juicyslew.moonstation14.ms14.character.AngularRecoveryModel;
import com.juicyslew.moonstation14.ms14.character.AngularRecoveryLook;
import static org.junit.jupiter.api.Assertions.*;

class MedicalPerspectivePolicyTest {
    @Test void optingOutFinishesComfortLookAndRestoresMedicalPerspectiveLease() {
        var camera = new AngularRecoveryComfortCameraPolicy();
        var q = AngularRecoveryModel.Quat.fromAxisAngle(new AngularRecoveryModel.Vec3(0, 0, 1), .65);
        var initial = AngularRecoveryModel.departure(q, new AngularRecoveryModel.Vec3(0, 2, 0));
        var current = AngularRecoveryModel.departure(q, new AngularRecoveryModel.Vec3(0, 2, 0));
        camera.start(initial, 20, -10);
        camera.update(31, -4);
        var stable = camera.worldAngles().orElseThrow();
        var lease = new MedicalPerspectivePolicy<String>();
        assertTrue(lease.state(true, "first").pushRear()); // comfort owns the perspective lease

        boolean comfort = MedicalPerspectivePolicy.comfortActive(false, true, false, false, false);
        assertFalse(comfort);
        var handoff = comfort ? null : camera.finish(current).orElseThrow();
        assertFalse(camera.active()); // clear() would lose handoff and fail the world-look check below
        var returnedWorldLook = AngularRecoveryLook.worldLook(current, handoff.yaw(), handoff.pitch());
        var expectedWorldLook = AngularRecoveryLook.worldLook(null, stable.yaw(), stable.pitch());
        assertEquals(expectedWorldLook.x(), returnedWorldLook.x(), 1e-6);
        assertEquals(expectedWorldLook.y(), returnedWorldLook.y(), 1e-6);
        assertEquals(expectedWorldLook.z(), returnedWorldLook.z(), 1e-6);
        assertEquals("first", lease.state(false, "rear").restore());
    }

    @Test void medicalAndContainmentTakeoversRemainIndependentOfComfortOptIn() {
        assertTrue(MedicalPerspectivePolicy.comfortActive(false, true, true, false, false) == false);
        var lease = new MedicalPerspectivePolicy<String>();
        assertTrue(lease.state(true, "first").pushRear()); // medical state remains active with config off
        assertEquals("first", lease.observationChanged().restore()); // containment takeover releases lease

        var camera = new com.juicyslew.moonstation14.ms14.eye.client.AngularRecoveryComfortCameraPolicy();
        camera.start(null, 40, 12);
        camera.clear(); // contained/medical takeover deliberately does not inverse-rebase body look
        assertTrue(camera.finish(null).isEmpty());
    }

    @Test void disabledComfortCannotRequestCameraButMedicalLeaseStillWorks() {
        assertFalse(MedicalPerspectivePolicy.comfortActive(false, true, false, false, false));
        assertTrue(MedicalPerspectivePolicy.comfortActive(true, true, false, false, false));
        assertFalse(MedicalPerspectivePolicy.comfortActive(true, true, false, true, false));
        assertFalse(MedicalPerspectivePolicy.comfortActive(true, true, false, false, true));
        assertFalse(MedicalPerspectivePolicy.comfortActive(true, false, false, false, false));

        var policy = new MedicalPerspectivePolicy<String>();
        assertTrue(policy.state(true, "first-person").pushRear());
        assertEquals("first-person", policy.state(false, "rear").restore());
    }

    @Test void restoresOriginalUnlessUserCyclesPerspective() {
        var policy = new MedicalPerspectivePolicy<String>();
        assertTrue(policy.state(true, "first").pushRear());
        assertEquals("first", policy.state(false, "rear").restore());
    }

    @Test void manualCriticalPerspectiveBecomesDeadBaseline() {
        var policy = new MedicalPerspectivePolicy<String>();
        policy.state(true, "first");
        policy.manualChange();
        assertTrue(policy.dead("front").pushRear());
        assertEquals("front", policy.state(false, "rear").restore());
    }

    @Test void untouchedCriticalBaselineSurvivesDeadAndManualDeadChangeBlocksRestore() {
        var policy = new MedicalPerspectivePolicy<String>();
        policy.state(true, "first");
        policy.dead("rear");
        assertEquals("first", policy.state(false, "rear").restore());

        policy.state(true, "first");
        policy.dead("rear");
        policy.manualChange();
        assertNull(policy.state(false, "front").restore());
    }

    @Test void manualCycleEvenBackToSamePerspectivePreventsRestore() {
        var policy = new MedicalPerspectivePolicy<String>();
        policy.state(true, "first");
        policy.manualChange();
        policy.manualChange();
        assertNull(policy.state(false, "rear").restore());
    }

    @Test void observationEndRestoresWithoutMedicalMutation() {
        var policy = new MedicalPerspectivePolicy<String>();
        policy.state(true, "third");
        assertEquals("third", policy.observationChanged().restore());
        assertNull(policy.observationChanged().restore());
    }
}
