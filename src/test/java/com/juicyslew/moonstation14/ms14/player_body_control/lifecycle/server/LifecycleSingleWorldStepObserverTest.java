package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.ms14.movement.MovementVector;
import com.juicyslew.moonstation14.ms14.shuttle.sable.NativeContactProofDiagnostic;
import com.juicyslew.moonstation14.ms14.shuttle.sable.SableOwnedBodyMotion;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class LifecycleSingleWorldStepObserverTest {
    private static final UUID FRAME=UUID.randomUUID();
    private static LifecycleSingleWorldStepObserver.NativeMoveEvidence capture(
            LifecycleSingleWorldStepObserver.NativeContactRead contact,boolean fresh,
            UUID before,UUID after,boolean grounded,boolean idleAttested,boolean idleCertified,
            boolean groundedFloorSide,NativeContactProofDiagnostic.Branch branch,
            boolean proofFresh,boolean proofTracked,NativeContactProofDiagnostic.PositionStage stage,String failure) {
        return LifecycleSingleWorldStepObserver.freezeNativeMoveFields(44,120,MovementVector.ZERO,MovementVector.ZERO,
                MovementVector.ZERO,fresh,before,after,contact,grounded,net.minecraft.world.phys.Vec3.ZERO,
                net.minecraft.world.phys.Vec3.ZERO,idleAttested,idleCertified,branch,proofFresh,proofTracked,
                groundedFloorSide,stage,failure,"proof{branch="+branch+",fresh="+proofFresh+",failure="+failure+"}");
    }
    private static LifecycleSingleWorldStepObserver.CompletedNativeStep completed(
            LifecycleSingleWorldStepObserver.NativeMoveEvidence move,boolean accepted,int count,
            boolean grounded,String failure) {
        return new LifecycleSingleWorldStepObserver.CompletedNativeStep(move,accepted,count,grounded,failure);
    }
    private static LifecycleSingleWorldStepObserver.NativeContactRead contact() {
        return new LifecycleSingleWorldStepObserver.NativeContactRead(FRAME,true,true,true,false,false);
    }
    private static LifecycleSingleWorldStepObserver.NativeMoveEvidence ordinaryIdle() {
        // Observed source contract: IDLE_FLOOR, native below contact, fresh/tracked, no correction evaluated,
        // no certification or groundedFloorSide flag, but accepted completed grounded move.
        return capture(contact(),true,FRAME,FRAME,true,true,false,false,
                NativeContactProofDiagnostic.Branch.IDLE_FLOOR,true,true,
                NativeContactProofDiagnostic.PositionStage.NOT_EVALUATED,"NONE");
    }
    private static LifecycleSingleWorldStepObserver.NativeMoveEvidence noDiagnosticGroundContact() {
        return capture(contact(),true,FRAME,FRAME,true,false,false,false,
                NativeContactProofDiagnostic.Branch.NONE,false,false,
                NativeContactProofDiagnostic.PositionStage.NOT_EVALUATED,"NONE");
    }

    @Test void frozenPostMoveEvidenceSurvivesLaterMutableSdkScratchChangesAndDuplicateReads() {
        var mutableSdkScratch=contact();
        var frozen=capture(mutableSdkScratch,true,FRAME,FRAME,true,true,true,false,
                NativeContactProofDiagnostic.Branch.IDLE_FLOOR,true,true,
                NativeContactProofDiagnostic.PositionStage.ACCEPTED,"NONE");
        mutableSdkScratch.trackingFrameId=null;
        mutableSdkScratch.verticalCollisionBelow=false;
        mutableSdkScratch.present=false;
        var step=completed(frozen,true,1,true,null);
        assertTrue(step.supportedGroundedFrame(FRAME));
        assertTrue(step.supportedGroundedFrame(FRAME),"a repeated GameTest read observes the same immutable completed-step evidence");
        assertEquals(FRAME,frozen.collisionTrackingFrameId());
        assertTrue(frozen.verticalCollisionBelow());
        assertTrue(frozen.collisionInfoPresent());
        assertTrue(frozen.proofDiagnostic().contains("IDLE_FLOOR"));

        var sourceMove=new SableOwnedBodyMotion.MoveResult(MovementVector.ZERO,MovementVector.ZERO,MovementVector.ZERO,
                MovementVector.ZERO,null,true,null,null,null,SableOwnedBodyMotion.CollisionPosePolicy.STOCK_NATIVE_INTERVAL,
                null,null,null,MovementVector.ZERO,true,true,MovementVector.ZERO,null,null);
        var movementState=new com.juicyslew.moonstation14.ms14.movement.CharacterMovementState(
                MovementVector.ZERO,MovementVector.ZERO,true);
        var attempt=new LifecycleSingleWorldStepObserver.Attempt(44,120,0,0,false,false,0f,Optional.empty(),
                net.minecraft.world.phys.Vec3.ZERO,net.minecraft.world.phys.Vec3.ZERO,true,
                net.minecraft.world.phys.Vec3.ZERO,net.minecraft.world.phys.Vec3.ZERO,true,Optional.of(movementState),
                List.of(sourceMove),List.of(frozen),null);
        assertSame(attempt,LifecycleSingleWorldStepObserver.attemptsAtServerTick(List.of(attempt),44).getFirst());
        assertTrue(attempt.completedNativeStep().supportedGroundedFrame(FRAME));
        assertTrue(attempt.completedNativeStep().supportedGroundedFrame(FRAME),
                "duplicate reads do not reinterpret unchanged SDK scratch as a new step");
    }

    @Test void nativeIdleAndCertifiedCorrectionBranchesRetainTheirDistinctProductionContracts() {
        assertTrue(completed(noDiagnosticGroundContact(),true,1,true,null).supportedGroundedFrame(FRAME),
                "default NativeContactProofDiagnostic.none() does not negate the primary fresh native floor contact");
        assertTrue(completed(ordinaryIdle(),true,1,true,null).supportedGroundedFrame(FRAME),
                "ordinary fresh IDLE_FLOOR contact does not require a correction-stage ACCEPTED marker");

        var certifiedIdle=capture(contact(),true,FRAME,FRAME,true,true,true,false,
                NativeContactProofDiagnostic.Branch.IDLE_FLOOR,true,true,
                NativeContactProofDiagnostic.PositionStage.ACCEPTED,"NONE");
        assertTrue(completed(certifiedIdle,true,1,true,null).supportedGroundedFrame(FRAME),
                "production-certified idle correction is allowed");
        var rejectedIdle=capture(contact(),true,FRAME,FRAME,true,true,false,false,
                NativeContactProofDiagnostic.Branch.IDLE_FLOOR,true,true,
                NativeContactProofDiagnostic.PositionStage.REJECTED,"NONE");
        assertFalse(completed(rejectedIdle,true,1,true,null).supportedGroundedFrame(FRAME),"rejected correction is not floor proof");
        var uncertifiedCorrection=capture(contact(),true,FRAME,FRAME,true,true,false,false,
                NativeContactProofDiagnostic.Branch.IDLE_FLOOR,true,true,
                NativeContactProofDiagnostic.PositionStage.ACCEPTED,"NONE");
        assertFalse(completed(uncertifiedCorrection,true,1,true,null).supportedGroundedFrame(FRAME),
                "ACCEPTED correction requires the production idle-support certification");

        var side=capture(contact(),true,FRAME,FRAME,true,false,false,true,
                NativeContactProofDiagnostic.Branch.SUPPORTED_FLOOR_SIDE,true,true,
                NativeContactProofDiagnostic.PositionStage.ACCEPTED,"NONE");
        assertTrue(completed(side,true,1,true,null).supportedGroundedFrame(FRAME),
                "nonidle supported floor-side branch requires its grounded proof and accepted correction");
        var unprovedSide=capture(contact(),true,FRAME,FRAME,true,false,false,false,
                NativeContactProofDiagnostic.Branch.SUPPORTED_FLOOR_SIDE,true,true,
                NativeContactProofDiagnostic.PositionStage.ACCEPTED,"NONE");
        assertFalse(completed(unprovedSide,true,1,true,null).supportedGroundedFrame(FRAME));
    }

    @Test void staleUnmatchedUnsupportedAndRejectedCompletedStepsNeverPass() {
        var good=ordinaryIdle();
        assertFalse(completed(good,false,1,true,null).supportedGroundedFrame(FRAME),"rejected world step");
        assertFalse(completed(good,true,0,true,null).supportedGroundedFrame(FRAME),"no native move callback");
        assertFalse(completed(good,true,2,true,null).supportedGroundedFrame(FRAME),"duplicate native callbacks");
        assertFalse(completed(good,true,1,false,null).supportedGroundedFrame(FRAME),"ungrounded completed attempt");
        assertFalse(completed(good,true,1,true,"step exception").supportedGroundedFrame(FRAME),"step failure");
        assertFalse(capture(contact(),false,FRAME,FRAME,true,true,false,false,
                NativeContactProofDiagnostic.Branch.IDLE_FLOOR,true,true,
                NativeContactProofDiagnostic.PositionStage.NOT_EVALUATED,"NONE").nativeGroundSupportFor(FRAME),"stale info");
        assertFalse(capture(new LifecycleSingleWorldStepObserver.NativeContactRead(FRAME,false,true,true,false,false),
                true,FRAME,FRAME,true,true,false,false,NativeContactProofDiagnostic.Branch.IDLE_FLOOR,true,true,
                NativeContactProofDiagnostic.PositionStage.NOT_EVALUATED,"NONE").nativeGroundSupportFor(FRAME),"missing info");
        assertFalse(capture(new LifecycleSingleWorldStepObserver.NativeContactRead(FRAME,true,true,false,false,false),
                true,FRAME,FRAME,true,true,false,false,NativeContactProofDiagnostic.Branch.IDLE_FLOOR,true,true,
                NativeContactProofDiagnostic.PositionStage.NOT_EVALUATED,"NONE").nativeGroundSupportFor(FRAME),"missing below contact");
        assertFalse(capture(new LifecycleSingleWorldStepObserver.NativeContactRead(FRAME,true,false,true,false,false),
                true,FRAME,FRAME,true,true,false,false,NativeContactProofDiagnostic.Branch.NONE,false,false,
                NativeContactProofDiagnostic.PositionStage.NOT_EVALUATED,"NONE").nativeGroundSupportFor(FRAME),"missing vertical collision");
        assertFalse(capture(new LifecycleSingleWorldStepObserver.NativeContactRead(FRAME,true,true,true,true,false),
                true,FRAME,FRAME,true,true,false,false,NativeContactProofDiagnostic.Branch.NONE,false,false,
                NativeContactProofDiagnostic.PositionStage.NOT_EVALUATED,"NONE").nativeGroundSupportFor(FRAME),"forbidden horizontal collision");
        assertFalse(capture(contact(),true,UUID.randomUUID(),FRAME,true,true,false,false,
                NativeContactProofDiagnostic.Branch.NONE,false,false,
                NativeContactProofDiagnostic.PositionStage.NOT_EVALUATED,"NONE").nativeGroundSupportFor(FRAME),"before point mismatch");
        assertFalse(capture(new LifecycleSingleWorldStepObserver.NativeContactRead(UUID.randomUUID(),true,true,true,false,false),
                true,FRAME,FRAME,true,true,false,false,NativeContactProofDiagnostic.Branch.IDLE_FLOOR,true,true,
                NativeContactProofDiagnostic.PositionStage.NOT_EVALUATED,"NONE").nativeGroundSupportFor(FRAME),"unmatched native frame");
        assertFalse(capture(contact(),true,FRAME,null,true,true,false,false,
                NativeContactProofDiagnostic.Branch.IDLE_FLOOR,true,true,
                NativeContactProofDiagnostic.PositionStage.NOT_EVALUATED,"NONE").nativeGroundSupportFor(FRAME),"after point left frame");
        assertFalse(capture(contact(),true,FRAME,FRAME,true,true,false,false,
                NativeContactProofDiagnostic.Branch.IDLE_FLOOR,false,true,
                NativeContactProofDiagnostic.PositionStage.NOT_EVALUATED,"NONE").nativeGroundSupportFor(FRAME),"proof not fresh");
        assertFalse(capture(contact(),true,FRAME,FRAME,true,true,false,false,
                NativeContactProofDiagnostic.Branch.IDLE_FLOOR,true,false,
                NativeContactProofDiagnostic.PositionStage.NOT_EVALUATED,"NONE").nativeGroundSupportFor(FRAME),"proof not tracked");
        assertFalse(capture(contact(),true,FRAME,FRAME,true,true,false,false,
                NativeContactProofDiagnostic.Branch.IDLE_FLOOR,true,true,
                NativeContactProofDiagnostic.PositionStage.NOT_EVALUATED,"CONTACT_EVIDENCE_REJECTED").nativeGroundSupportFor(FRAME),"proof failure");
        assertFalse(capture(contact(),true,FRAME,FRAME,true,true,false,false,
                NativeContactProofDiagnostic.Branch.UNSUPPORTED_MIXED,true,true,
                NativeContactProofDiagnostic.PositionStage.NOT_EVALUATED,"NONE").nativeGroundSupportFor(FRAME),"unsupported branch");
        assertFalse(capture(contact(),true,FRAME,FRAME,true,true,false,true,
                NativeContactProofDiagnostic.Branch.NONE,false,false,
                NativeContactProofDiagnostic.PositionStage.NOT_EVALUATED,"NONE").nativeGroundSupportFor(FRAME),
                "NONE sentinel cannot claim grounded floor-side proof");
        assertFalse(capture(contact(),true,FRAME,FRAME,true,true,true,false,
                NativeContactProofDiagnostic.Branch.NONE,false,false,
                NativeContactProofDiagnostic.PositionStage.NOT_EVALUATED,"NONE").nativeGroundSupportFor(FRAME),
                "NONE sentinel cannot claim certified correction");
        assertFalse(capture(contact(),true,FRAME,FRAME,true,true,false,false,
                NativeContactProofDiagnostic.Branch.NONE,false,false,
                NativeContactProofDiagnostic.PositionStage.ACCEPTED,"NONE").nativeGroundSupportFor(FRAME),
                "NONE with a claimed correction stage is malformed");
        assertFalse(capture(contact(),true,FRAME,FRAME,true,true,false,false,
                NativeContactProofDiagnostic.Branch.NONE,false,false,
                NativeContactProofDiagnostic.PositionStage.NOT_EVALUATED,"INVALID_INPUT").nativeGroundSupportFor(FRAME),
                "NONE with proof failure is malformed");
        assertFalse(capture(contact(),true,FRAME,FRAME,true,true,false,false,
                NativeContactProofDiagnostic.Branch.NONE,true,true,
                NativeContactProofDiagnostic.PositionStage.NOT_EVALUATED,"NONE").nativeGroundSupportFor(FRAME),
                "NONE cannot claim that an optional proof diagnostic ran");
    }
}
