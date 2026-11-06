package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.ms14.movement.MovementVector;
import com.juicyslew.moonstation14.ms14.movement.HorizontalContactEvidence;
import com.juicyslew.moonstation14.ms14.movement.ContactPositionCorrectionEvidence;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementMotor;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementPolicy;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementState;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementCommand;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementEnvironment;
import com.juicyslew.moonstation14.ms14.movement.MovementCollisionResolver;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TransientFloorSideAdmissionTest {
    @Test void ascendingAndNegativePoliciesDifferOnlyForKnownWorldBeforePoint() {
        var frame = java.util.UUID.randomUUID();
        assertTrue(SableOwnedBodyMotion.transientBeforePointAdmissible(
                SableOwnedBodyMotion.TransientPolicy.ASCENDING, frame, null, true));
        assertTrue(SableOwnedBodyMotion.transientBeforePointAdmissible(
                SableOwnedBodyMotion.TransientPolicy.ASCENDING, frame, frame, true));
        assertFalse(SableOwnedBodyMotion.transientBeforePointAdmissible(
                SableOwnedBodyMotion.TransientPolicy.ASCENDING, frame, java.util.UUID.randomUUID(), true));
        assertFalse(SableOwnedBodyMotion.transientBeforePointAdmissible(
                SableOwnedBodyMotion.TransientPolicy.ASCENDING, frame, null, false));
        assertFalse(SableOwnedBodyMotion.transientBeforePointAdmissible(
                SableOwnedBodyMotion.TransientPolicy.NEGATIVE, frame, null, true));
        assertTrue(SableOwnedBodyMotion.transientBeforePointAdmissible(
                SableOwnedBodyMotion.TransientPolicy.NEGATIVE, frame, frame, true));
        assertFalse(SableOwnedBodyMotion.transientBeforePointAdmissible(
                SableOwnedBodyMotion.TransientPolicy.NEGATIVE, frame, java.util.UUID.randomUUID(), true));
    }

    @Test void ascendingCandidateRowsPermitSideContactsButRequireACompatibleFloorRow() {
        var frame = java.util.UUID.randomUUID();
        var side = trace(frame, false, -1, 0, 0);
        var floor = trace(frame, true, .6, .8, 0);
        var ceiling = trace(frame, true, 0, -1, 0);
        var horizontalVertical = trace(frame, true, 1, .5, 0);
        assertTrue(SableOwnedBodyMotion.rowsHaveAscendingFloor(List.of(side, floor)));
        assertTrue(SableOwnedBodyMotion.rowsHaveAscendingFloor(List.of(floor, side)));
        assertTrue(SableOwnedBodyMotion.rowsHaveAscendingFloor(List.of(floor)), "floor-only ascending contacts are valid candidates");
        assertFalse(SableOwnedBodyMotion.rowsHaveAscendingFloor(List.of(side)));
        assertFalse(SableOwnedBodyMotion.rowsHaveAscendingFloor(List.of(ceiling)));
        assertFalse(SableOwnedBodyMotion.rowsHaveAscendingFloor(List.of(floor, ceiling)),
                "incompatible vertical rows are refused before full proof");
        assertFalse(SableOwnedBodyMotion.rowsHaveAscendingFloor(java.util.Arrays.asList(floor, null)));
        assertFalse(SableOwnedBodyMotion.rowsHaveAscendingFloor(List.of(horizontalVertical)));
    }

    @Test void ascendingFirstSelectedRowAttestsNativeFirstContactWithoutFallback() {
        var frame = java.util.UUID.randomUUID();
        var contact = new dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision.FirstCollisionInfo(
                new org.joml.Vector3d(), new org.joml.Vector3d(.6, .8, 0), false, true,
                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        // Native SDK normalizes selected MTV before the floor redirect; for this tilted floor
        // the applied response may be vertical, but the first-contact direction remains tilted.
        assertTrue(SableOwnedBodyMotion.ascendingFirstContactMatches(frame, contact,
                List.of(trace(frame, true, .6, .8, 0))));
        var sideContact = new dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision.FirstCollisionInfo(
                new org.joml.Vector3d(), new org.joml.Vector3d(.6, .8, 0), true, false,
                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        assertTrue(SableOwnedBodyMotion.ascendingFirstContactMatches(frame, sideContact,
                List.of(trace(frame, false, .6, .8, 0))));
        assertFalse(SableOwnedBodyMotion.ascendingFirstContactMatches(frame, contact,
                List.of(trace(java.util.UUID.randomUUID(), true, .6, .8, 0))));
        var wrongHorizontal = new dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision.FirstCollisionInfo(
                new org.joml.Vector3d(), new org.joml.Vector3d(.6, .8, 0), true, true,
                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        assertFalse(SableOwnedBodyMotion.ascendingFirstContactMatches(frame, wrongHorizontal,
                List.of(trace(frame, true, .6, .8, 0))));
        assertFalse(SableOwnedBodyMotion.ascendingFirstContactMatches(frame, contact,
                List.of(trace(frame, true, 0, 0, 0), trace(frame, true, .6, .8, 0))),
                "malformed first selected row must reject rather than searching later rows");
        var nonfinite = new dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision.FirstCollisionInfo(
                new org.joml.Vector3d(), new org.joml.Vector3d(Double.NaN, 0, 0), true, false,
                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        assertFalse(SableOwnedBodyMotion.ascendingFirstContactMatches(frame, nonfinite,
                List.of(trace(frame, true, .6, .8, 0), trace(frame, true, .6, .8, 0))));
        var nonunit = new dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision.FirstCollisionInfo(
                new org.joml.Vector3d(), new org.joml.Vector3d(0, 1, 0), true, false,
                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        assertFalse(SableOwnedBodyMotion.ascendingFirstContactMatches(frame, nonunit,
                List.of(trace(frame, true, .6, .8, 0))));
    }

    private static CurrentFrameCollisionScope.NativeCollisionTrace trace(java.util.UUID frame, boolean vertical,
            double x, double y, double z) {
        return new CurrentFrameCollisionScope.NativeCollisionTrace(frame, 0, 1, 0, null, null, null, null,
                x, y, z, 0, 0, 0, 0, 0, 0, 0, 0, 0, null, vertical);
    }

    @Test void ascendingPolicyUsesSameExactIdentityAndTwoEndpointClearanceAuthorization() {
        Object info = new Object(), stale = new Object();
        var proof = new NativeSideContactProof.Result(MovementVector.ZERO, MovementVector.ZERO, null);
        var endpoint = Vec3.ZERO;
        var bounds = new AABB(0, 0, 0, 1, 1, 1);
        var candidate = new SableOwnedBodyMotion.TransientContactCandidate(proof, null, info, endpoint, bounds, null,
                NativeFinalFootClearance.Result.CLEAR, SableOwnedBodyMotion.TransientPolicy.ASCENDING);
        assertTrue(SableOwnedBodyMotion.transientCandidateAuthorizes(candidate, info, NativeFinalFootClearance.Result.CLEAR));
        assertFalse(SableOwnedBodyMotion.transientCandidateAuthorizes(candidate, stale, NativeFinalFootClearance.Result.CLEAR));
        assertFalse(SableOwnedBodyMotion.transientCandidateAuthorizes(candidate, info, NativeFinalFootClearance.Result.UNKNOWN));
        assertFalse(SableOwnedBodyMotion.transientCandidateAuthorizes(candidate, info, NativeFinalFootClearance.Result.OBSTRUCTED));
        assertEquals(NativeFinalFootClearance.Result.UNKNOWN,
                SableOwnedBodyMotion.transientFinalClearance(candidate, false, NativeFinalFootClearance.Result.UNKNOWN));
        assertEquals(NativeFinalFootClearance.Result.OBSTRUCTED,
                SableOwnedBodyMotion.transientFinalClearance(candidate, false, NativeFinalFootClearance.Result.OBSTRUCTED));
        assertFalse(SableOwnedBodyMotion.transientEndpointUnchanged(candidate, info, endpoint.add(0, 1e-9, 0), bounds));
    }

    @Test void optionalWitnessIsBoundToCandidateButNeverChangesClearanceAuthorization() {
        Object info = new Object();
        var proof = new NativeSideContactProof.Result(MovementVector.ZERO, MovementVector.ZERO, null);
        var endpoint = Vec3.ZERO;
        var bounds = new AABB(0, 0, 0, 1, 1, 1);
        var detail = new NativeFinalFootClearance.QueryObservation("SUPPORT_BAND", bounds, bounds, bounds, bounds,
                .25, 42L, NativeFinalFootClearance.Result.CLEAR, "clear", null, null, null, null, null, null);
        var candidate = new SableOwnedBodyMotion.TransientContactCandidate(proof, null, info, endpoint, bounds,
                null, NativeFinalFootClearance.Result.CLEAR, SableOwnedBodyMotion.TransientPolicy.NEGATIVE, detail);
        assertSame(detail, candidate.clearanceObservation());
        assertEquals(NativeFinalFootClearance.Result.CLEAR,
                SableOwnedBodyMotion.transientFinalClearance(candidate, true, NativeFinalFootClearance.Result.OBSTRUCTED));
        assertTrue(SableOwnedBodyMotion.transientEndpointUnchanged(candidate, info, endpoint, bounds));
        assertTrue(SableOwnedBodyMotion.transientCandidateAuthorizes(candidate, info,
                NativeFinalFootClearance.Result.CLEAR));
        assertFalse(SableOwnedBodyMotion.transientCandidateAuthorizes(candidate, info,
                NativeFinalFootClearance.Result.OBSTRUCTED));
        assertFalse(SableOwnedBodyMotion.transientCandidateAuthorizes(candidate, info,
                NativeFinalFootClearance.Result.UNKNOWN));
        var diagnostic = new NativeContactProofDiagnostic.ClearanceObservation(
                NativeContactProofDiagnostic.QueryMode.SUPPORT_BAND, candidate.clearance(), true,
                candidate.clearance(), NativeContactProofDiagnostic.Authorization.AUTHORIZED, detail, detail);
        assertSame(detail, diagnostic.capturedDetail());
        assertSame(detail, diagnostic.finalDetail(), "same endpoint reuses the captured witness");
    }

    @Test void candidateAdmissionBindsExactMoveAndRequiresClearPreAndPostCarryEndpoints() {
        Object info = new Object(), staleInfo = new Object();
        var request = new MovementVector(.1, -.001, .2);
        var bounded = new MovementVector(0, -.0005, .1);
        var proof = new NativeSideContactProof.Result(bounded, new MovementVector(0, -2e-8, 0), null);
        var endpoint = new Vec3(10, 20, 30);
        var bounds = new AABB(9.7, 20, 29.7, 10.3, 21.8, 30.3);
        var candidate = new SableOwnedBodyMotion.TransientContactCandidate(proof, null, info, endpoint, bounds, null,
                NativeFinalFootClearance.Result.CLEAR);

        assertEquals(NativeFinalFootClearance.Result.CLEAR,
                SableOwnedBodyMotion.transientFinalClearance(candidate, true, NativeFinalFootClearance.Result.UNKNOWN),
                "unchanged own/carry endpoint reuses its captured pre-carry query");
        assertTrue(SableOwnedBodyMotion.transientEndpointUnchanged(candidate, info, endpoint, bounds));
        assertFalse(SableOwnedBodyMotion.transientEndpointUnchanged(candidate, staleInfo, endpoint, bounds));
        assertFalse(SableOwnedBodyMotion.transientEndpointUnchanged(candidate, info,
                endpoint.add(0, 1e-9, 0), bounds), "even tiny carry requires current-endpoint clearance");
        assertFalse(SableOwnedBodyMotion.transientEndpointUnchanged(candidate, info, endpoint,
                bounds.move(0, 1e-9, 0)));
        assertTrue(SableOwnedBodyMotion.transientCandidateAuthorizes(candidate, info,
                NativeFinalFootClearance.Result.CLEAR));
        assertFalse(SableOwnedBodyMotion.transientCandidateAuthorizes(candidate, staleInfo,
                NativeFinalFootClearance.Result.CLEAR), "candidate cannot cross collision-info identity");
        assertFalse(SableOwnedBodyMotion.transientCandidateAuthorizes(candidate, info,
                NativeFinalFootClearance.Result.OBSTRUCTED));
        assertFalse(SableOwnedBodyMotion.transientCandidateAuthorizes(candidate, info,
                NativeFinalFootClearance.Result.UNKNOWN));
        assertFalse(SableOwnedBodyMotion.transientCandidateAuthorizes(candidate, info,
                NativeFinalFootClearance.Result.CLEAR) && candidate.clearance() != NativeFinalFootClearance.Result.CLEAR);
        var blockedPreclear = new SableOwnedBodyMotion.TransientContactCandidate(proof, null, info, endpoint, bounds, null,
                NativeFinalFootClearance.Result.OBSTRUCTED);
        assertFalse(SableOwnedBodyMotion.transientCandidateAuthorizes(blockedPreclear, info,
                NativeFinalFootClearance.Result.CLEAR), "post-carry clear cannot erase pre-carry obstruction");
        assertEquals(NativeFinalFootClearance.Result.OBSTRUCTED,
                SableOwnedBodyMotion.transientFinalClearance(candidate, false, NativeFinalFootClearance.Result.OBSTRUCTED),
                "changed post-carry endpoint requires its own query");
        assertEquals(NativeFinalFootClearance.Result.UNKNOWN,
                SableOwnedBodyMotion.transientFinalClearance(null, false, NativeFinalFootClearance.Result.CLEAR));
        assertTrue(request.y() < bounded.y() && bounded.y() < 0d);
    }

    @Test void clearIdentityBoundNegativeCandidateOwnsGroundedFloorSideDispatchOnlyWhenPreclearIsClear() {
        Object info = new Object(), stale = new Object();
        var proof = new NativeSideContactProof.Result(new MovementVector(0,-.0005,0),
                MovementVector.ZERO, null);
        var endpoint = Vec3.ZERO;
        var bounds = new AABB(-.3,0,-.3,.3,1.8,.3);
        var clear = new SableOwnedBodyMotion.TransientContactCandidate(proof, null, info, endpoint, bounds,
                null, NativeFinalFootClearance.Result.CLEAR);
        assertTrue(SableOwnedBodyMotion.transientCandidateOwnsSupportedFloorSide(clear, info),
                "a grounded body with a valid floor witness must dispatch through the already-clear transient proof");
        assertFalse(SableOwnedBodyMotion.transientCandidateOwnsSupportedFloorSide(clear, stale));
        assertFalse(SableOwnedBodyMotion.transientCandidateOwnsSupportedFloorSide(null, info));

        var obstructed = new SableOwnedBodyMotion.TransientContactCandidate(proof, null, info, endpoint, bounds,
                null, NativeFinalFootClearance.Result.OBSTRUCTED);
        var unknown = new SableOwnedBodyMotion.TransientContactCandidate(proof, null, info, endpoint, bounds,
                null, NativeFinalFootClearance.Result.UNKNOWN);
        assertFalse(SableOwnedBodyMotion.transientCandidateOwnsSupportedFloorSide(obstructed, info),
                "blocked preclear leaves the grounded supported-floor branch available");
        assertFalse(SableOwnedBodyMotion.transientCandidateOwnsSupportedFloorSide(unknown, info),
                "unknown is never clear or support authorization");
        assertFalse(SableOwnedBodyMotion.transientCandidateAuthorizes(clear, info, NativeFinalFootClearance.Result.UNKNOWN));
        assertFalse(SableOwnedBodyMotion.transientCandidateAuthorizes(clear, info, NativeFinalFootClearance.Result.OBSTRUCTED));
        assertFalse(SableOwnedBodyMotion.transientCandidateAuthorizes(obstructed, info, NativeFinalFootClearance.Result.CLEAR),
                "post-carry CLEAR cannot upgrade an obstructed preclear");
        assertEquals(NativeFinalFootClearance.Result.UNKNOWN,
                SableOwnedBodyMotion.transientFinalClearance(clear, false, NativeFinalFootClearance.Result.UNKNOWN),
                "changed endpoint UNKNOWN stays UNKNOWN, so existing transient rollback remains mandatory");
        assertEquals(NativeFinalFootClearance.Result.OBSTRUCTED,
                SableOwnedBodyMotion.transientFinalClearance(clear, false, NativeFinalFootClearance.Result.OBSTRUCTED));
    }

    @Test void endpointSnapshotIsExactAndGeometryEvidenceCannotChangeTheBoundedVelocity() {
        var before = new Vec3(1, 2, 3);
        var moved = new Vec3(1, 2.0000001, 3);
        var beforeBounds = new AABB(.7, 2, 2.7, 1.3, 3.8, 3.3);
        var movedBounds = beforeBounds.move(0, .0000001, 0);
        Object info = new Object();
        var proof = new NativeSideContactProof.Result(MovementVector.ZERO, MovementVector.ZERO, null);
        var candidate = new SableOwnedBodyMotion.TransientContactCandidate(proof, null, info, before, beforeBounds, null,
                NativeFinalFootClearance.Result.CLEAR);
        assertTrue(SableOwnedBodyMotion.transientEndpointUnchanged(candidate, info, before, beforeBounds));
        assertFalse(SableOwnedBodyMotion.transientEndpointUnchanged(candidate, info, moved, movedBounds),
                "tiny carry still changes the exact endpoint and requires a fresh query");
    }

    @Test void transientPositionEvidenceBindsExactOwnMovementAndNeverGrantsGrounding() {
        var request = new MovementVector(.1, -.001, 0);
        var bounded = new MovementVector(0, -.0005, 0);
        var correction = new MovementVector(0, -2.4931e-8, 0);
        var contact = HorizontalContactEvidence.validate(request, bounded, -1, 0).orElseThrow();
        var evidence = ContactPositionCorrectionEvidence.validateTransientFloorAndSide(request, bounded,
                correction, contact).orElseThrow();
        assertTrue(evidence.matches(request, bounded, false));
        assertFalse(evidence.matches(request, bounded, true));
        assertFalse(evidence.matches(request, bounded.add(new MovementVector(0, 1e-5, 0)), false));
        assertTrue(ContactPositionCorrectionEvidence.validateTransientFloorAndSide(request,
                new MovementVector(0, -.0011, 0), correction, contact).isEmpty());
        assertTrue(ContactPositionCorrectionEvidence.validateTransientFloorAndSide(request, bounded,
                new MovementVector(0, -1.1e-6, 0), contact).isEmpty());
    }

    @Test void motorVelocityUsesBoundedOwnMovementAndIgnoresOnlyPositionGeometryCorrection() {
        var request = new MovementVector(.1, -.0005, 0);
        var bounded = new MovementVector(0, request.y(), 0);
        var contact = HorizontalContactEvidence.validate(request, bounded, -1, 0).orElseThrow();
        var geometryWithoutOffset = MovementVector.ZERO;
        var geometryWithOffset = new MovementVector(0, -2.4931e-8, 0);
        var without = motorStep(request, bounded, contact, geometryWithoutOffset);
        var with = motorStep(request, bounded, contact, geometryWithOffset);
        assertEquals(without.velocity(), with.velocity(), "position correction must never become own velocity");
        assertEquals(geometryWithOffset.y(), with.position().y() - without.position().y(), 1e-15,
                "the only position difference is the certified geometry correction");
    }

    private static CharacterMovementState motorStep(MovementVector request, MovementVector bounded,
            HorizontalContactEvidence contact, MovementVector geometry) {
        var correction = ContactPositionCorrectionEvidence.validateTransientFloorAndSide(request, bounded,
                geometry, contact).orElseThrow();
        MovementCollisionResolver resolver = (position, asked, grounded) -> {
            assertEquals(request, asked);
            return new MovementCollisionResolver.CollisionResult(bounded, false, contact, correction);
        };
        var environment = new CharacterMovementEnvironment(.05, 0, 0, 1, MovementVector.ZERO,
                0, 0, resolver);
        var state = new CharacterMovementState(MovementVector.ZERO, request.scale(20), false);
        return new CharacterMovementMotor(new CharacterMovementPolicy(0,0,0,0,0,0)).tick(state,
                new CharacterMovementCommand(0,0,false,false), environment, false);
    }
}
