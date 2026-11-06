package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.ms14.movement.ContactPositionCorrectionEvidence;
import com.juicyslew.moonstation14.ms14.movement.MovementVector;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Frozen typed geometry rows for vertical-policy Y clipping alongside verified side projections. */
class OwnerCompoundContactTraceRegressionTest {
    @Test void ceilingAndSide13168CertifiesOrderedHorizontalPathWithoutChangingGeometryCorrection() {
        var root=OwnerTraceFixture.load("owner-seq13168.json");
        var frame=UUID.fromString(root.get("frameId").getAsString());
        var request=OwnerTraceFixture.vector(root.getAsJsonArray("request"));
        var nativeMotion=OwnerTraceFixture.vector(root.getAsJsonArray("nativeMotion"));
        var measured=OwnerTraceFixture.vector(root.getAsJsonArray("measured"));
        var rows=OwnerTraceFixture.rows(root.getAsJsonArray("rows"),frame);
        assertEquals(28,rows.size());
        var proof=NativeSideContactProof.validateCeilingAndSideDetailed(request,nativeMotion,measured,frame,
                rows,row->row.selectedState()==net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        assertEquals(NativeSideContactProof.Failure.NONE,proof.reason(),"row="+proof.rowIndex());
        var result=proof.result().orElseThrow();
        assertNotNull(result.horizontalContact());
        assertTrue(result.horizontalContact().matches(request,result.boundedOwn()));
        assertOriginalHorizontalReplay(request,rows,result.boundedOwn());
        var correction=ContactPositionCorrectionEvidence.validateCeilingAndSide(request,result.boundedOwn(),
                result.geometryCorrection(),result.horizontalContact()).orElseThrow();
        assertTrue(correction.matches(request,result.boundedOwn(),false));
    }

    @Test void supportedFloorAndSide15182CertifiesFloorClippedYPrefixWithoutMintingWorldGroundProof() {
        var root=OwnerTraceFixture.load("owner-seq15182.json");
        var frame=UUID.fromString(root.get("frameId").getAsString());
        var request=OwnerTraceFixture.vector(root.getAsJsonArray("request"));
        var nativeMotion=OwnerTraceFixture.vector(root.getAsJsonArray("nativeMotion"));
        var measured=OwnerTraceFixture.vector(root.getAsJsonArray("measured"));
        var rows=OwnerTraceFixture.rows(root.getAsJsonArray("rows"),frame);
        assertEquals(24,rows.size());
        var proof=NativeSideContactProof.validateSupportedFloorAndSideDetailed(request,nativeMotion,measured,
                frame,rows,row->row.selectedState()==net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),true);
        assertEquals(NativeSideContactProof.Failure.NONE,proof.reason(),"row="+proof.rowIndex());
        var result=proof.result().orElseThrow();
        assertNotNull(result.horizontalContact());
        assertTrue(result.horizontalContact().matches(request,result.boundedOwn()));
        assertOriginalHorizontalReplay(request,rows,result.boundedOwn());
        var correction=ContactPositionCorrectionEvidence.validateSupportedFloorAndSide(request,result.boundedOwn(),
                result.geometryCorrection(),result.horizontalContact()).orElseThrow();
        assertTrue(correction.matches(request,result.boundedOwn(),true),
                "this conditional pure math proof is not a live owner-world grounding claim");
    }

    @Test void supportedFloorSideKnownWorldOriginLandingUsesExistingProofAndMotorPath() {
        var root=OwnerTraceFixture.load("owner-seq15182.json");
        var frame=UUID.fromString(root.get("frameId").getAsString());
        var world=UUID.randomUUID();
        assertTrue(SableOwnedBodyMotion.groundedFloorSideOriginAdmissible(frame,null,true));
        assertTrue(SableOwnedBodyMotion.groundedFloorSideOriginAdmissible(frame,frame,true));
        assertFalse(SableOwnedBodyMotion.groundedFloorSideOriginAdmissible(frame,null,false));
        assertFalse(SableOwnedBodyMotion.groundedFloorSideOriginAdmissible(frame,world,true));
        var request=OwnerTraceFixture.vector(root.getAsJsonArray("request"));
        var nativeMotion=OwnerTraceFixture.vector(root.getAsJsonArray("nativeMotion"));
        var measured=OwnerTraceFixture.vector(root.getAsJsonArray("measured"));
        var rows=OwnerTraceFixture.rows(root.getAsJsonArray("rows"),frame);
        var resolver=(com.juicyslew.moonstation14.ms14.movement.MovementCollisionResolver)
                (p,a,g)-> {
                    assertEquals(request.x(),a.x(),Math.ulp(request.x()));
                    assertEquals(request.y(),a.y(),Math.ulp(request.y()));
                    assertEquals(request.z(),a.z(),Math.ulp(request.z()));
                    var proof=NativeSideContactProof.validateSupportedFloorAndSideDetailed(a,nativeMotion,measured,frame,
                            rows,row->row.selectedState()==net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),true);
                    assertEquals(NativeSideContactProof.Failure.NONE,proof.reason());
                    var result=proof.result().orElseThrow();
                    var position=ContactPositionCorrectionEvidence.validateSupportedFloorAndSide(a,result.boundedOwn(),
                            result.geometryCorrection(),result.horizontalContact()).orElseThrow();
                    assertTrue(position.matches(a,result.boundedOwn(),true));
                    return new com.juicyslew.moonstation14.ms14.movement.MovementCollisionResolver.CollisionResult(
                            result.boundedOwn(),true,result.horizontalContact(),position);
                };
        var environment=new com.juicyslew.moonstation14.ms14.movement.CharacterMovementEnvironment(.05,0,0,1,
                MovementVector.ZERO,0,0,resolver);
        var state=new com.juicyslew.moonstation14.ms14.movement.CharacterMovementState(MovementVector.ZERO,
                request.scale(20),false);
        var motor=new com.juicyslew.moonstation14.ms14.movement.CharacterMovementMotor(
                new com.juicyslew.moonstation14.ms14.movement.CharacterMovementPolicy(0,0,0,0,0,0));
        var stepped=motor.tick(state,new com.juicyslew.moonstation14.ms14.movement.CharacterMovementCommand(0,0,false,false),environment,false);
        assertTrue(stepped.onGround());
        assertEquals(measured.y(),stepped.position().y(),1e-6);
    }

    private static void assertOriginalHorizontalReplay(MovementVector request,
            java.util.List<CurrentFrameCollisionScope.NativeCollisionTrace> rows, MovementVector bounded) {
        var selected=new java.util.ArrayList<MovementVector>();
        for(var row:rows) selected.add(row.verticalCollision()&&row.selectedY()>0d?null:
                new MovementVector(row.selectedX(),row.selectedY(),row.selectedZ()));
        var own=NativeSideContactProof.ownResponsesAt(request,rows.getFirst().substeps(),rows,selected);
        var replay=com.juicyslew.moonstation14.ms14.movement.NativeCollisionSlop.accepted(request,request.add(own));
        assertEquals(replay.x(),bounded.x(),0d,"existing ownResponsesAt X replay remains numerically unchanged");
        assertEquals(replay.z(),bounded.z(),0d,"existing ownResponsesAt Z replay remains numerically unchanged");
    }
}
