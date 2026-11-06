package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.ms14.movement.ContactPositionCorrectionEvidence;
import com.juicyslew.moonstation14.ms14.movement.MovementVector;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Frozen owner seq3936 geometry replay; this is not live caller or grounding evidence. */
class OwnerAscendingFloorTraceRegressionTest {
    private static final UUID FRAME=UUID.fromString("0fb05421-6f59-44d1-b337-ee121d2b1539");

    @Test void controlledSyntheticFloorOnlyAscendingGeometryIsAdmissibleWithoutSideEvidence() {
        var request=new MovementVector(0,.1,0);
        var selected=new MovementVector(0,.02,0);
        var before=new MovementVector(0,.05,0);
        var bounds=new net.minecraft.world.phys.AABB(0,0,0,1,1,1);
        var geometry=new NativeCollisionGeometry(new MovementVector(.5,1.88,.5),new MovementVector(.6,1.8,.6),
                new NativeCollisionGeometry.QuaternionValue(0,0,0,1),
                new NativeFrameInterval.PoseValue(new double[]{1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1}),
                bounds,selected,before,0,2);
        var row=new CurrentFrameCollisionScope.NativeCollisionTrace(FRAME,1,2,0,new net.minecraft.core.BlockPos(0,0,0),
                Blocks.STONE.defaultBlockState(),"stone","stone",selected.x(),selected.y(),selected.z(),before.x(),before.y(),before.z(),
                selected.x(),selected.y(),selected.z(),selected.x(),selected.y(),selected.z(),geometry,true);
        var motion=new MovementVector(0,.12,0);
        var clip=NativeSideContactProofTest.worldClipForTest(motion,
                new net.minecraft.world.phys.Vec3(.5,.93,.5),new net.minecraft.world.phys.AABB(.2,.93,.2,.8,2.73,.8));
        var result=NativeSideContactProof.validateAscendingFloorDetailed(request,motion,motion,FRAME,List.of(row),
                r->r.selectedState()==Blocks.STONE.defaultBlockState(),clip);
        assertEquals(NativeSideContactProof.Failure.NONE,result.reason());
        var proof=result.result().orElseThrow();
        assertEquals(request.y(),proof.boundedOwn().y(),0);
        assertEquals(.02,proof.geometryCorrection().y(),1e-8);
        assertNull(proof.horizontalContact());
        assertTrue(com.juicyslew.moonstation14.ms14.movement.ContactPositionCorrectionEvidence
                .validateAscendingFloor(request,proof.boundedOwn(),proof.geometryCorrection(),null).orElseThrow()
                .matches(request,proof.boundedOwn(),false));
    }

    @Test void seq3936AscendingFloorAndSideRowsReplayGeometryOnlyPositionEvidence() {
        var root=OwnerTraceFixture.load("owner-seq3936.json");
        var request=OwnerTraceFixture.vector(root.getAsJsonArray("request"));
        var nativeMotion=OwnerTraceFixture.vector(root.getAsJsonArray("nativeMotion"));
        var measured=OwnerTraceFixture.vector(root.getAsJsonArray("measured"));
        var rows=OwnerTraceFixture.rows(root.getAsJsonArray("rows"),FRAME);
        assertEquals(11,rows.size());
        java.util.function.Predicate<CurrentFrameCollisionScope.NativeCollisionTrace> known=
                row->row.selectedState()==Blocks.STONE.defaultBlockState();
        var clip=OwnerTraceFixture.worldClip(root);
        var proof=NativeSideContactProof.validateAscendingFloorDetailed(request,nativeMotion,measured,FRAME,rows,known,clip);
        assertEquals(NativeSideContactProof.Failure.NONE,proof.reason(),"row="+proof.rowIndex());
        var result=proof.result().orElseThrow();
        assertEquals(request.y(),result.boundedOwn().y(),0d);
        assertEquals(.2481360000000001,result.boundedOwn().y(),0d);
        assertTrue(result.geometryCorrection().y()>0d);
        assertNotNull(result.horizontalContact(),"captured XZ clipping retains ordered horizontal evidence");
        var position=ContactPositionCorrectionEvidence.validateAscendingFloor(request,result.boundedOwn(),
                result.geometryCorrection(),result.horizontalContact()).orElseThrow();
        assertTrue(position.matches(request,result.boundedOwn(),false));
        assertFalse(position.matches(request,result.boundedOwn(),true));
        assertEquals(measured.x(),result.boundedOwn().x()+result.geometryCorrection().x(),1e-6);
        assertEquals(measured.y(),result.boundedOwn().y()+result.geometryCorrection().y(),1e-6);
        assertEquals(measured.z(),result.boundedOwn().z()+result.geometryCorrection().z(),1e-6);
        MovementVector response=MovementVector.ZERO;
        for(var row:rows) response=response.add(new MovementVector(row.responseX(),row.responseY(),row.responseZ()));
        assertEquals(request.x()+response.x(),nativeMotion.x(),1e-6);
        assertEquals(request.y()+response.y(),nativeMotion.y(),1e-6);
        assertEquals(request.z()+response.z(),nativeMotion.z(),1e-6);
        assertTrue(NativeSideContactProof.validateDetailed(request,nativeMotion,measured,FRAME,rows,known).result().isEmpty());
        assertTrue(NativeSideContactProof.validateCeilingOnlyDetailed(request,nativeMotion,measured,FRAME,rows,known,clip).result().isEmpty());
        assertTrue(NativeSideContactProof.validateTransientFloorAndSideDetailed(request,nativeMotion,measured,FRAME,rows,known,clip).result().isEmpty());
        assertTrue(NativeSideContactProof.validateSupportedFloorAndSideDetailed(request,nativeMotion,measured,FRAME,rows,known,true).result().isEmpty());

        assertRejected(new MovementVector(request.x(),-request.y(),request.z()),nativeMotion,measured,rows,known,clip);
        assertRejected(request,nativeMotion,measured,withoutGeometry(rows),known,clip);
        assertRejected(request,nativeMotion,measured,rows,row->false,clip);
        var reversed=new ArrayList<>(rows); Collections.swap(reversed,0,1);
        assertRejected(request,nativeMotion,measured,reversed,known,clip);
        assertRejected(request,nativeMotion.add(new MovementVector(.001,0,0)),measured,rows,known,clip);
        assertRejected(request,nativeMotion,measured,rows,known,null);
    }

    @Test void ascendingMixedContactPassesOuterGateAndKeepsCorrectionPositionOnly() {
        var root=OwnerTraceFixture.load("owner-seq3936.json");
        var request=OwnerTraceFixture.vector(root.getAsJsonArray("request"));
        var nativeMotion=OwnerTraceFixture.vector(root.getAsJsonArray("nativeMotion"));
        var measured=OwnerTraceFixture.vector(root.getAsJsonArray("measured"));
        var rows=OwnerTraceFixture.rows(root.getAsJsonArray("rows"),FRAME);
        var proof=NativeSideContactProof.validateAscendingFloorDetailed(request,nativeMotion,measured,FRAME,rows,
                row->row.selectedState()==Blocks.STONE.defaultBlockState(),OwnerTraceFixture.worldClip(root)).result().orElseThrow();
        assertNotNull(proof.horizontalContact());
        var info=new Object();
        var candidate=new SableOwnedBodyMotion.TransientContactCandidate(proof,null,info,
                net.minecraft.world.phys.Vec3.ZERO,new net.minecraft.world.phys.AABB(0,0,0,1,1,1),null,
                NativeFinalFootClearance.Result.CLEAR,SableOwnedBodyMotion.TransientPolicy.ASCENDING);
        assertTrue(SableOwnedBodyMotion.outerPositionProofAdmissible(proof.horizontalContact(),false,false,false,false,candidate,info));
        assertFalse(SableOwnedBodyMotion.outerPositionProofAdmissible(proof.horizontalContact(),false,false,false,false,candidate,new Object()));
        var position=ContactPositionCorrectionEvidence.validateAscendingFloor(request,proof.boundedOwn(),
                proof.geometryCorrection(),proof.horizontalContact()).orElseThrow();
        var collision=new com.juicyslew.moonstation14.ms14.movement.MovementCollisionResolver.CollisionResult(
                proof.boundedOwn(),false,proof.horizontalContact(),position);
        var environment=new com.juicyslew.moonstation14.ms14.movement.CharacterMovementEnvironment(.05,0,0,1,
                MovementVector.ZERO,0,0,(p,a,g)->collision);
        var state=new com.juicyslew.moonstation14.ms14.movement.CharacterMovementState(MovementVector.ZERO,
                request.scale(20),false);
        var result=new com.juicyslew.moonstation14.ms14.movement.CharacterMovementMotor(
                new com.juicyslew.moonstation14.ms14.movement.CharacterMovementPolicy(0,0,0,0,0,0)).tick(state,
                new com.juicyslew.moonstation14.ms14.movement.CharacterMovementCommand(0,0,false,false),environment,false);
        assertEquals(request.y()/.05,result.velocity().y(),1e-12);
        assertFalse(result.onGround());
        assertEquals(measured.y(),result.position().y(),1e-6);
    }

    private static void assertRejected(MovementVector request,MovementVector nativeMotion,MovementVector measured,
            List<CurrentFrameCollisionScope.NativeCollisionTrace> rows,
            java.util.function.Predicate<CurrentFrameCollisionScope.NativeCollisionTrace> known,
            CurrentFrameCollisionScope.WorldClipObservation clip) {
        assertTrue(NativeSideContactProof.validateAscendingFloorDetailed(request,nativeMotion,measured,FRAME,rows,known,clip).result().isEmpty());
    }

    private static List<CurrentFrameCollisionScope.NativeCollisionTrace> withoutGeometry(List<CurrentFrameCollisionScope.NativeCollisionTrace> source) {
        var copy=new ArrayList<>(source); var r=copy.get(0);
        copy.set(0,new CurrentFrameCollisionScope.NativeCollisionTrace(r.frameId(),r.substep(),r.substeps(),r.iteration(),
                r.selectedPos(),r.selectedState(),r.block(),r.blockState(),r.selectedX(),r.selectedY(),r.selectedZ(),
                r.collisionMotionBeforeX(),r.collisionMotionBeforeY(),r.collisionMotionBeforeZ(),r.appliedX(),r.appliedY(),r.appliedZ(),
                r.responseX(),r.responseY(),r.responseZ(),null,r.verticalCollision()));
        return List.copyOf(copy);
    }
}
