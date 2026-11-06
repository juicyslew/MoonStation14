package com.juicyslew.moonstation14.ms14.movement;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContactPositionCorrectionEvidenceTest {
    @Test void ascendingFloorGeometryNeverCertifiesGroundAndPreservesOwnY() {
        var request = new MovementVector(-.04159305857396045, .2481360000000001, .27815747334641777);
        var bounded = request;
        var correction = new MovementVector(0, .02347557973781278, 0);
        var proof = ContactPositionCorrectionEvidence.validateAscendingFloor(request, bounded, correction, null).orElseThrow();
        assertTrue(proof.matches(request, bounded, false));
        assertFalse(proof.matches(request, bounded, true));
        assertTrue(ContactPositionCorrectionEvidence.validateAscendingFloor(request,
                new MovementVector(request.x(), request.y() + 1e-8, request.z()), correction, null).isEmpty());
        assertTrue(ContactPositionCorrectionEvidence.validateAscendingFloor(request, bounded,
                new MovementVector(0, -1e-8, 0), null).isEmpty());
    }

    @Test void ceilingSideDomainMatchesWithoutGroundAndNeverCertifiesFloor() {
        var request = new MovementVector(-1, .5, 0);
        var own = new MovementVector(-.5, .25, 0);
        var contact = HorizontalContactEvidence.validate(request, own, 1, 0, 0).orElseThrow();
        var proof = ContactPositionCorrectionEvidence.validateCeilingAndSide(request, own,
                new MovementVector(0, -.1, 0), contact).orElseThrow();
        assertTrue(proof.matches(request, own, false));
        assertFalse(proof.matches(new MovementVector(0, -1, 0), own, true));
        assertTrue(ContactPositionCorrectionEvidence.validateSupportedFloor(request, own,
                MovementVector.ZERO).isEmpty());
    }

    @Test void ceilingSideRejectsInvalidOwnGeometryAndContact() {
        var request = new MovementVector(-1, .5, 0);
        var own = new MovementVector(-.5, .25, 0);
        var contact = HorizontalContactEvidence.validate(request, own, 1, 0, 0).orElseThrow();
        assertTrue(ContactPositionCorrectionEvidence.validateCeilingAndSide(request,
                new MovementVector(-.5, -.01, 0), MovementVector.ZERO, contact).isEmpty());
        assertTrue(ContactPositionCorrectionEvidence.validateCeilingAndSide(request, own,
                new MovementVector(0, .01, 0), contact).isEmpty());
        assertTrue(ContactPositionCorrectionEvidence.validateCeilingAndSide(request, own,
                MovementVector.ZERO, null).isEmpty());
    }

    @Test void ceilingSideAllowsOnlyExistingSmallPositiveGeometryResidual() {
        var request = new MovementVector(-1, .5, 0);
        var own = new MovementVector(-.5, .25, 0);
        var contact = HorizontalContactEvidence.validate(request, own, 1, 0, 0).orElseThrow();
        assertTrue(ContactPositionCorrectionEvidence.validateCeilingAndSide(request, own,
                new MovementVector(0, NativeCollisionSlop.MAX_RESIDUAL, 0), contact).isPresent());
        assertTrue(ContactPositionCorrectionEvidence.validateCeilingAndSide(request, own,
                new MovementVector(0, NativeCollisionSlop.MAX_RESIDUAL + 1e-12, 0), contact).isEmpty());
    }
}
