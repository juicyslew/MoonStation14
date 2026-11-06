package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.ms14.movement.MovementVector;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import net.minecraft.world.phys.AABB;
import com.juicyslew.moonstation14.ms14.movement.NativeCollisionSlop;
import com.juicyslew.moonstation14.ms14.movement.ContactPositionCorrectionEvidence;

import static org.junit.jupiter.api.Assertions.*;

class NativeSideContactProofTest {
    private static final UUID FRAME = UUID.randomUUID();

    @Test void replayProjectionCounterexampleDocumentsProducerValidatorRoundingMismatch() {
        var input = new MovementVector(Double.valueOf("0x1.95f1a38323b50p-5"),
                Double.valueOf("0x1.dd44e702c11d2p-3"), Double.valueOf("0x1.7db3a423c5a06p-2"));
        var mtv = new MovementVector(-Double.valueOf("0x1.2abf392d884d3p-33"),
                -Double.valueOf("0x1.452c61374b71bp-51"), Double.valueOf("0x1.3dba1145565b6p-36"));
        var row = traceRow(mtv, input, mtv, mtv, 1, 1, 0, false, null);
        var replay = NativeSideContactProof.replayOwnResponsesAt(input, 1, List.of(row), List.of(mtv));
        var projection = replay.projections().getFirst();
        assertEquals(5.894167742738571e-18,
                Math.hypot(replay.aggregate().x(), replay.aggregate().z()), 1e-28);
        assertEquals(-5.842763047129064e-18, projection.clipDelta().x(), 1e-31);
        assertEquals(7.7674535985382755e-19, projection.clipDelta().z(), 1e-31);
        assertEquals(.9999999999999999,
                Math.sqrt(projection.normalX()*projection.normalX() + projection.normalY()*projection.normalY()
                        + projection.normalZ()*projection.normalZ()), 0d);
        assertTrue(com.juicyslew.moonstation14.ms14.movement.HorizontalContactEvidence.validateOrdered(
                input, input.add(replay.aggregate()), 1, replay.projections()).isEmpty(),
                "current producer emits a nonzero projection whose validator rejects after renormalizing its normal");
    }

    @Test void ascendingFloorRowPreservesPositiveOwnPrefixForLaterSideProjection() {
        var request=new MovementVector(.02,.2,0);
        var floor=traceRow(new MovementVector(0,.02,0),new MovementVector(.01,.1,0),
                new MovementVector(0,.02,0),new MovementVector(0,.02,0),1,2,0,true,null);
        var side=traceRow(new MovementVector(-.1,-.01,0),new MovementVector(.02,.2,0),
                new MovementVector(-.1,-.01,0),new MovementVector(-.1,-.01,0),2,2,0,false,null);
        var response=NativeSideContactProof.ownResponsesAt(request,2,List.of(floor,side),java.util.Arrays.asList(null,
                new MovementVector(-.1,-.01,0)));
        assertEquals(-.0396039603960396,response.x(),1e-14);
        assertEquals(0,response.y(),0,"side projection is horizontal and no floor clamp changes Y");

        var descending=new MovementVector(.02,-.2,0);
        var descendingResponse=NativeSideContactProof.ownResponsesAt(descending,2,List.of(floor,side),java.util.Arrays.asList(null,
                new MovementVector(-.1,-.01,0)));
        assertEquals(-.0099009900990099,descendingResponse.x(),1e-14,
                "legacy descending own-prefix floor clipping remains unchanged");
    }

    @Test void rotatedSideThenRoofSatProofRetainsPositiveSideYGeometryResidue() {
        double angle=-1e-6, c=Math.cos(angle), s=Math.sin(angle);
        var pose=new NativeFrameInterval.PoseValue(new double[]{c,s,0,0, -s,c,0,0, 0,0,1,0, 0,0,0,1});
        var request=new MovementVector(.1,.42,0);
        var bodyOrientation=new NativeCollisionGeometry.QuaternionValue(0,0,0,1);
        var dims=new MovementVector(.6,1.8,.6);
        var sideBounds=new AABB(0,0,0,1,1,1);
        var roofBounds=new AABB(-1,2,0,0,3,1);
        var sideCenter=new MovementVector(-.199998,1.2,.5);
        var firstBefore=request.scale(.5);
        var sideTemplate=new NativeCollisionGeometry(sideCenter,dims,bodyOrientation,pose,sideBounds,
                MovementVector.ZERO,firstBefore,0,2);
        var side=new NativeSideCollisionWitness().separation(sideTemplate);
        assertNotNull(side);
        assertTrue(side.y()>0d,"actual rotated-side SAT witness: "+side);
        var sideGeometry=new NativeCollisionGeometry(sideCenter,dims,bodyOrientation,pose,sideBounds,
                side,firstBefore,0,2);
        var moved=sideCenter.add(side);
        var beforeRoof=request.add(side);
        var roofTemplate=new NativeCollisionGeometry(moved,dims,bodyOrientation,pose,roofBounds,
                MovementVector.ZERO,beforeRoof,0,2);
        var roof=new NativeSideCollisionWitness().separation(roofTemplate);
        assertNotNull(roof);
        assertTrue(roof.y()<0d,"actual roof SAT witness: "+roof);
        var roofGeometry=new NativeCollisionGeometry(moved,dims,bodyOrientation,pose,roofBounds,
                roof,beforeRoof,0,2);
        var sidePos=BlockPos.ZERO;
        var roofPos=new BlockPos(-1,2,0);
        var rows=List.of(traceRowAt(side,firstBefore,side,side,1,2,0,false,sideGeometry,sidePos),
                traceRowAt(roof,beforeRoof,roof,roof,2,2,0,true,roofGeometry,roofPos));
        var nativeMotion=request.add(side).add(roof);
        var proof=NativeSideContactProof.validateCeilingAndSideDetailed(request,nativeMotion,nativeMotion,
                FRAME,rows,row->row.frameId().equals(FRAME)
                        && (row.selectedPos().equals(sidePos)||row.selectedPos().equals(roofPos))
                        && row.selectedState()==Blocks.STONE.defaultBlockState());
        assertEquals(NativeSideContactProof.Failure.NONE,proof.reason(),"row="+proof.rowIndex());
        var result=proof.result().orElseThrow();
        assertTrue(result.geometryCorrection().y()>0d,"proof correction "+result.geometryCorrection());
        assertTrue(result.geometryCorrection().y()<=NativeCollisionSlop.MAX_RESIDUAL);
        var position=ContactPositionCorrectionEvidence.validateCeilingAndSide(request,result.boundedOwn(),
                result.geometryCorrection(),result.horizontalContact()).orElseThrow();
        assertTrue(position.matches(request,result.boundedOwn(),false));
    }


    @Test void oct24OwnerFirstServerStepCannotEnterWorldFloorSideWithoutCapturedWorldClip() {
        // Captured from run/logs/latest.log:154 (02:03:32.271). The server row reports
        // nativeTracking=null, so this is not a supported-floor proof candidate. The log
        // includes no WorldClipObservation; do not synthesize one from the measured result.
        var request = new MovementVector(0.046487906332098025, -0.07544406705017148,
                0.27738139278772483);
        var nativeMotion = new MovementVector(0.04648796183841629, -0.09747288105692777,
                0.1941669926871425);
        var measuredOwn = new MovementVector(0.04648796183841597, -0.0974728810569303,
                0.19416699268714233);

        var result = NativeSideContactProof.validateWorldFloorSideDetailed(request, nativeMotion,
                measuredOwn, FRAME, List.of(), ignored -> false, null);

        assertEquals(NativeSideContactProof.Failure.INVALID_CONTEXT, result.reason());
        assertEquals(-1, result.rowIndex());
        assertTrue(result.result().isEmpty());
        assertEquals(-0.02202881400675882, measuredOwn.y() - request.y(), 1e-15,
                "the measured-own Y discrepancy is materially larger than the accepted-own/request delta");
    }

    @Test void detailedOutcomeReportsFirstFailureWithoutLeakingAcrossCalls() {
        var missing = NativeSideContactProof.validateDetailed(MovementVector.ZERO,MovementVector.ZERO,
                MovementVector.ZERO,FRAME,List.of(),ignored->true);
        assertEquals(NativeSideContactProof.Failure.INVALID_CONTEXT,missing.reason());
        assertEquals(-1,missing.rowIndex());
        var request=new MovementVector(.1,0,0);
        var selected=new MovementVector(-.100002,0,0);
        var row=row(selected,request,geometry(new MovementVector(-.199998,.9,.5),selected,request),1,1,0);
        var good=NativeSideContactProof.validateDetailed(request,new MovementVector(-.000002,0,0),
                new MovementVector(-.000002,0,0),FRAME,List.of(row),ignored->true);
        assertEquals(NativeSideContactProof.Failure.NONE,good.reason());
        var optional=NativeSideContactProof.validate(request,new MovementVector(-.000002,0,0),
                new MovementVector(-.000002,0,0),FRAME,List.of(row),ignored->true);
        assertTrue(optional.isPresent());
        assertEquals(optional.orElseThrow().boundedOwn(),good.result().orElseThrow().boundedOwn());
        assertEquals(optional.orElseThrow().geometryCorrection(),good.result().orElseThrow().geometryCorrection());
        assertEquals(optional.orElseThrow().horizontalContact() == null,
                good.result().orElseThrow().horizontalContact() == null);
        assertThrows(UnsupportedOperationException.class,()->good.result().stream().toList().clear());
    }

    @Test void headOnNativePushIsSplitIntoOwnedClipAndGeometryCorrection() {
        var selected = new MovementVector(-.100002, 0, 0);
        var geometry = geometry(new MovementVector(-.199998, .9, .5), selected, new MovementVector(.1, 0, 0));
        var row = row(selected, new MovementVector(.1, 0, 0), geometry, 1, 1, 0);
        var result = NativeSideContactProof.validate(new MovementVector(.1, 0, 0),
                new MovementVector(-.000002, 0, 0), new MovementVector(-.000002, 0, 0),
                FRAME, List.of(row), ignored -> true).orElseThrow();
        assertEquals(0, result.boundedOwn().x(), NativeCollisionSlop.MAX_RESIDUAL);
        assertEquals(-.000002, result.geometryCorrection().x(), NativeCollisionSlop.MAX_RESIDUAL);
        assertNotNull(result.horizontalContact());
    }

    @Test void outgoingContactIsPureGeometryCorrection() {
        var selected = new MovementVector(-.002, 0, 0);
        var request = new MovementVector(-.1, 0, 0);
        var geometry = geometry(new MovementVector(-.298, .9, .5), selected, request);
        var row = row(selected, request, geometry, 1, 1, 0);
        var result = NativeSideContactProof.validate(request, new MovementVector(-.102, 0, 0),
                new MovementVector(-.102, 0, 0), FRAME, List.of(row), ignored -> true).orElseThrow();
        assertEquals(request, result.boundedOwn());
        assertNull(result.horizontalContact());
        assertEquals(-.002, result.geometryCorrection().x(), NativeCollisionSlop.MAX_RESIDUAL);
    }

    @Test void pureSideProofPreservesSubSlopUpwardAndDescendingYAsPositionOnlyCorrection() {
        for (double y : new double[]{.42, -.08}) {
            var request = new MovementVector(.1, y, 0);
            var nativeMotion = new MovementVector(-.000002, y, 0);
            var measured = new MovementVector(-.000002, y-8.0e-8, 0);
            var selected = new MovementVector(-.100002, 0, 0);
            var geometry = geometry(new MovementVector(-.199998, .9, .5), selected, request);
            var row = row(selected, request, geometry, 1, 1, 0);

            var proof = NativeSideContactProof.validate(request, nativeMotion, measured,
                    FRAME, List.of(row), ignored -> true).orElseThrow();
            assertEquals(y, proof.boundedOwn().y(), 0d);
            assertEquals(-8.0e-8, proof.geometryCorrection().y(), 1e-15);
            var correction = ContactPositionCorrectionEvidence.validateSide(request, proof.boundedOwn(),
                    proof.geometryCorrection(), proof.horizontalContact()).orElseThrow();
            assertTrue(correction.matches(request, proof.boundedOwn(), false),
                    "side position correction does not depend on grounded state or rewrite velocity");
        }
    }

    @Test void signedPureSideYCorrectionRemainsBoundedAndCannotMintOwnYClipping() {
        var request = new MovementVector(.1, .42, 0);
        var selected = new MovementVector(-.100002, 0, 0);
        var geometry = geometry(new MovementVector(-.199998, .9, .5), selected, request);
        var row = row(selected, request, geometry, 1, 1, 0);
        assertTrue(NativeSideContactProof.validate(request, new MovementVector(-.000002, .42, 0),
                new MovementVector(-.000002, .4199989, 0), FRAME, List.of(row), ignored -> true).isEmpty(),
                "Y discrepancy beyond the existing 1e-6 residual is not certified");
        var contact = com.juicyslew.moonstation14.ms14.movement.HorizontalContactEvidence
                .validate(request, new MovementVector(0, .41, 0), -1, 0, 0).orElse(null);
        assertTrue(ContactPositionCorrectionEvidence.validateSide(request,
                new MovementVector(0, .41, 0), new MovementVector(0, 8e-8, 0), contact).isEmpty(),
                "nonzero geometric Y cannot accompany a clipped own-Y request");
        assertTrue(ContactPositionCorrectionEvidence.validateSide(request,
                new MovementVector(0, .42, 0), new MovementVector(0, 1.1e-6, 0), contact).isEmpty(),
                "position correction cannot exceed existing residual budget");
    }

    @Test void authenticatedWorldFloorClipCanBoundDownwardOwnYWithPureSideGeometry() {
        var request=new MovementVector(.1,-.426909648,0);
        var nativeMotion=new MovementVector(-.000002,-.426909648,0);
        var measured=new MovementVector(-.000002,-.0443874444,0);
        var selected=new MovementVector(-.100002,0,0);
        var geometry=geometry(new MovementVector(-.199998,.9,.5),selected,request);
        var row=row(selected,request,geometry,1,1,0);
        var observation=worldClip(nativeMotion,measured);
        var proof=NativeSideContactProof.validateWorldFloorSide(request,nativeMotion,measured,FRAME,
                List.of(row),ignored->true,observation).orElseThrow();
        assertEquals(measured.y(),proof.boundedOwn().y(),0d,"actual partial world-floor displacement remains bounded own motion");
        assertEquals(0d,proof.geometryCorrection().y(),0d,"authentic floor response adds no Y position correction");
        var correction=ContactPositionCorrectionEvidence.validateSide(request,proof.boundedOwn(),
                proof.geometryCorrection(),proof.horizontalContact()).orElseThrow();
        assertTrue(correction.matches(request,proof.boundedOwn(),true));
    }

    @Test void worldFloorMayClipDownwardWishPartiallyOrToZeroButNeverBeyondOrUpward() {
        var request=new MovementVector(0,-.426909648,0);
        var nativeMotion=new MovementVector(0,-.426909648,0);
        var partial=new MovementVector(0,-.0443874444,0);
        assertTrue(worldClip(nativeMotion,partial).downwardFloorClip());
        assertTrue(worldClip(nativeMotion,MovementVector.ZERO).downwardFloorClip());
        assertFalse(worldClip(nativeMotion,nativeMotion).downwardFloorClip(),"no actual vertical clipping");
        assertFalse(worldClip(nativeMotion,new MovementVector(0,-.5,0)).downwardFloorClip(),
                "world response cannot extend the negative request");
        assertFalse(worldClip(nativeMotion,new MovementVector(0,1e-12,0)).downwardFloorClip(),
                "upward world movement is not a floor clip");
        assertFalse(worldClip(MovementVector.ZERO,MovementVector.ZERO).downwardFloorClip(),
                "stationary/step-up movement does not authenticate a downward floor clip");
    }

    @Test void worldFloorProofRetainsOutwardOwnNormalRequestAndCertifiesOnlyPositionMtv() {
        var request=new MovementVector(-7.97017e-7,-.001,.15625);
        var selected=new MovementVector(-1.051449999e-6,-1.23202e-13,0);
        var nativeMotion=request.add(selected);
        var measured=new MovementVector(nativeMotion.x(),0,nativeMotion.z());
        var geometry=geometry(new MovementVector(-.29999894855,.9,.5),selected,request);
        var row=row(selected,request,geometry,1,1,0);
        var matchingNormal=new org.joml.Vector3d(selected.x(),selected.y(),selected.z()).normalize();
        var contact=new dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision.FirstCollisionInfo(
                new org.joml.Vector3d(),matchingNormal,true,false,Blocks.STONE.defaultBlockState());
        assertTrue(SableOwnedBodyMotion.nativeFirstContactMatches(FRAME,contact,List.of(row)));
        assertFalse(SableOwnedBodyMotion.nativeFirstContactMatches(FRAME,
                new dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision.FirstCollisionInfo(
                        new org.joml.Vector3d(),new org.joml.Vector3d(0,0,1),true,false,
                        Blocks.STONE.defaultBlockState()),List.of(row)),
                "a valid floor token alone cannot substitute for a mismatched first side plane");
        assertFalse(SableOwnedBodyMotion.nativeFirstContactMatches(UUID.randomUUID(),contact,List.of(row)),
                "native first-contact frame must match traced side geometry");
        var proof=NativeSideContactProof.validateWorldFloorSide(request,nativeMotion,measured,FRAME,
                List.of(row),ignored->true,worldClip(nativeMotion,measured)).orElseThrow();
        assertEquals(request.x(),proof.boundedOwn().x(),0d,
                "an outward request aligned with the MTV remains own motion rather than being cancelled");
        assertEquals(request.z(),proof.boundedOwn().z(),0d);
        assertEquals(0d,proof.boundedOwn().y(),0d,"world-floor clipping bounds downward own Y");
        assertEquals(selected.x(),proof.geometryCorrection().x(),1e-12,
                "native separation remains position-only geometry correction");
        assertEquals(0d,proof.geometryCorrection().y(),0d);
        assertNull(proof.horizontalContact(),"the outward request was not clipped by the side normal");
        assertTrue(ContactPositionCorrectionEvidence.validateSide(request,proof.boundedOwn(),
                proof.geometryCorrection(),proof.horizontalContact()).isPresent());
    }

    @Test void worldFloorProofRejectsMissingMismatchUpwardOrNativeVerticalRows() {
        var request=new MovementVector(.1,-.001,0);
        var nativeMotion=new MovementVector(-.000002,-.001,0);
        var measured=new MovementVector(-.000002,0,0);
        var selected=new MovementVector(-.100002,0,0);
        var geometry=geometry(new MovementVector(-.199998,.9,.5),selected,request);
        var row=row(selected,request,geometry,1,1,0);
        var valid=worldClip(nativeMotion,measured);
        assertTrue(NativeSideContactProof.validateWorldFloorSide(request,nativeMotion,measured,FRAME,
                List.of(row),ignored->true,null).isEmpty());
        assertTrue(NativeSideContactProof.validateWorldFloorSide(request,nativeMotion,measured,FRAME,
                List.of(row),ignored->true,worldClip(nativeMotion,new MovementVector(-.000002,0,.01))).isEmpty());
        assertTrue(NativeSideContactProof.validateWorldFloorSide(request,nativeMotion,measured,FRAME,
                List.of(row),ignored->true,worldClip(nativeMotion,new MovementVector(-.000002,0,.0000011))).isEmpty(),
                "world terrain may not silently clip tangent X/Z beyond the existing residual allowance");
        assertTrue(NativeSideContactProof.validateWorldFloorSide(request,nativeMotion,measured,FRAME,
                List.of(row),ignored->false,valid).isEmpty(),"unknown/replaced selected state");
        assertTrue(NativeSideContactProof.validateWorldFloorSide(request,nativeMotion,measured,FRAME,
                List.of(),ignored->true,valid).isEmpty(),"missing native geometry rows");
        assertTrue(NativeSideContactProof.validateWorldFloorSide(request,nativeMotion,
                new MovementVector(-.000002,0,0),UUID.randomUUID(),List.of(row),ignored->true,valid).isEmpty(),
                "selected native frame must be the proved frame");
        assertTrue(NativeSideContactProof.validateWorldFloorSide(request,
                new MovementVector(-.000002,-.0012,0),measured,FRAME,List.of(row),ignored->true,
                worldClip(new MovementVector(-.000002,-.0012,0),measured)).isEmpty(),
                "complete request-plus-response budget cannot be bypassed by world-floor authority");
        assertTrue(NativeSideContactProof.validateWorldFloorSide(new MovementVector(.1,.001,0),nativeMotion,
                measured,FRAME,List.of(row),ignored->true,valid).isEmpty());
        var verticalRow=new CurrentFrameCollisionScope.NativeCollisionTrace(FRAME,1,1,1,BlockPos.ZERO,
                Blocks.STONE.defaultBlockState(),"stone","stone",0,.01,0,-.000002,-.001,0,
                0,1,0,0,1,0,null,true);
        assertTrue(NativeSideContactProof.validateWorldFloorSide(request,nativeMotion,measured,FRAME,
                List.of(row,verticalRow),ignored->true,valid).isEmpty());
    }

    private static CurrentFrameCollisionScope.WorldClipObservation worldClip(MovementVector input,MovementVector output) {
        return worldClipForTest(input,output,new net.minecraft.world.phys.Vec3(4,5,6),new AABB(3.7,5,5.7,4.3,6.8,6.3));
    }

    static CurrentFrameCollisionScope.WorldClipObservation worldClipForTest(MovementVector input,
            net.minecraft.world.phys.Vec3 position,AABB bounds) {
        return worldClipForTest(input,input,position,bounds);
    }

    private static CurrentFrameCollisionScope.WorldClipObservation worldClipForTest(MovementVector input,MovementVector output,
            net.minecraft.world.phys.Vec3 position,AABB bounds) {
        var entity=blankPlayer();
        var in=new net.minecraft.world.phys.Vec3(input.x(),input.y(),input.z());
        var out=new net.minecraft.world.phys.Vec3(output.x(),output.y(),output.z());
        return CurrentFrameCollisionScope.withTrackedCharacterPose(entity,false,()->{
            CurrentFrameCollisionScope.recordVanillaWorldCollisionInput(entity,in,position,bounds);
            CurrentFrameCollisionScope.recordVanillaWorldCollisionOutput(entity,out);
            return null;
        }).worldClipObservation();
    }

    private static net.minecraft.server.level.ServerPlayer blankPlayer() {
        try {
            Class<?> unsafeType=Class.forName("sun.misc.Unsafe");
            var field=unsafeType.getDeclaredField("theUnsafe");field.setAccessible(true);
            Object unsafe=field.get(null);
            return (net.minecraft.server.level.ServerPlayer)unsafeType.getMethod("allocateInstance",Class.class)
                    .invoke(unsafe,net.minecraft.server.level.ServerPlayer.class);
        } catch(ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    @Test void emptyOrIncompleteTraceCannotProveNativeSideContact() {
        UUID frame = UUID.randomUUID();
        assertTrue(NativeSideContactProof.validate(MovementVector.ZERO, MovementVector.ZERO,
                MovementVector.ZERO, frame, List.of(), row -> true).isEmpty());
        var row = new CurrentFrameCollisionScope.NativeCollisionTrace(frame, 1, 1, 0,
                BlockPos.ZERO, Blocks.STONE.defaultBlockState(), "stone", "stone",
                -1, 0, 0, 0, 0, 0, -1, 0, 0, -1, 0, 0, null, false);
        assertTrue(NativeSideContactProof.validate(MovementVector.ZERO, MovementVector.ZERO,
                MovementVector.ZERO, frame, List.of(row), ignored -> true).isEmpty());
    }

    @Test void rejectsWrongFrameUnknownStateAndVerticalCollision() {
        UUID frame = UUID.randomUUID();
        var row = new CurrentFrameCollisionScope.NativeCollisionTrace(UUID.randomUUID(), 1, 1, 0,
                BlockPos.ZERO, Blocks.STONE.defaultBlockState(), "stone", "stone",
                0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 1, 0, null, true);
        assertTrue(NativeSideContactProof.validate(MovementVector.ZERO, MovementVector.ZERO,
                MovementVector.ZERO, frame, List.of(row), ignored -> true).isEmpty());
        assertTrue(NativeSideContactProof.validate(MovementVector.ZERO, MovementVector.ZERO,
                MovementVector.ZERO, row.frameId(), List.of(row), ignored -> false).isEmpty());
    }

    @Test void appliedVectorUsesWholeVectorTolerance() {
        var selected = new MovementVector(-.100002, 0, 0);
        var request = new MovementVector(.1, 0, 0);
        var geometry = geometry(new MovementVector(-.199998, .9, .5), selected, request);
        assertTrue(validateAppliedOffset(selected, request, geometry, .7e-6).isEmpty(),
                "three individually sub-epsilon differences exceed the vector budget");
        assertTrue(validateAppliedOffset(selected, request, geometry, .4e-6).isPresent(),
                "a vector residual within the existing budget remains admissible");
    }

    @Test void supportedFloorRowsAndExactSideGeometryShareCompleteResponseBudget() {
        var request = new MovementVector(.1, -.02, 0);
        var floorOne = new MovementVector(0, .01002, 0);
        var beforeSide = new MovementVector(.1, .00002, 0);
        var side = new MovementVector(-.100002, 0, 0);
        var floorRowOne = traceRow(floorOne, new MovementVector(.05, -.01, 0), floorOne,
                floorOne, 1, 2, 0, true, null);
        var floorRowTwo = traceRow(new MovementVector(0, .01, 0), new MovementVector(.1, -.00998, 0),
                new MovementVector(0, .01, 0), new MovementVector(0, .01, 0), 2, 2, 0, true, null);
        var sideGeometry = geometry(new MovementVector(-.199998, .9, .5), side, beforeSide, 1, 2);
        var witnessedSide = new NativeSideCollisionWitness().separation(sideGeometry);
        assertNotNull(witnessedSide);
        assertEquals(side.x(), witnessedSide.x(), NativeCollisionSlop.MAX_RESIDUAL);
        assertEquals(side.y(), witnessedSide.y(), NativeCollisionSlop.MAX_RESIDUAL);
        assertEquals(side.z(), witnessedSide.z(), NativeCollisionSlop.MAX_RESIDUAL);
        var sideRow = traceRow(side, beforeSide, side, side, 2, 2, 1, false, sideGeometry);
        var result = NativeSideContactProof.validateSupportedFloorAndSide(request,
                new MovementVector(-.000002, .00002, 0), new MovementVector(-.000002, .00002, 0), FRAME,
                List.of(floorRowOne, floorRowTwo, sideRow), ignored -> true, true).orElseThrow();
        assertEquals(MovementVector.ZERO, result.boundedOwn());
        assertEquals(.00002, result.geometryCorrection().y(), 1e-12);
        assertNotNull(result.horizontalContact());
        var position = ContactPositionCorrectionEvidence.validateSupportedFloorAndSide(request,
                result.boundedOwn(), result.geometryCorrection(), result.horizontalContact()).orElseThrow();
        assertTrue(position.matches(request, result.boundedOwn(), true));
        assertFalse(position.matches(request, result.boundedOwn(), false));
        assertTrue(NativeSideContactProof.validateSupportedFloorAndSide(request,
                new MovementVector(-.000002, .00003, 0), new MovementVector(-.000002, .00003, 0), FRAME,
                List.of(floorRowOne, floorRowTwo, sideRow), ignored -> true, true).isEmpty(),
                "native motion must equal the complete floor-plus-side response budget");
    }

    @Test void mixedProofRejectsFloorOnlyUnknownFloorAndUnprovenSideGeometry() {
        var request = new MovementVector(-.1, -.02, 0);
        var floor = new MovementVector(0, .02, 0);
        var floorRow = traceRow(floor, new MovementVector(-.1, -.02, 0), floor,
                MovementVector.ZERO, 1, 1, 0, true, null);
        assertTrue(NativeSideContactProof.validateSupportedFloorAndSide(request, MovementVector.ZERO,
                MovementVector.ZERO, FRAME, List.of(floorRow), ignored -> true, true).isEmpty());
        assertTrue(NativeSideContactProof.validateSupportedFloorAndSide(request, MovementVector.ZERO,
                MovementVector.ZERO, FRAME, List.of(floorRow), ignored -> false, true).isEmpty());
        var side = new MovementVector(.1, 0, 0);
        var before = new MovementVector(-.1, 0, 0);
        var sideRow = traceRow(side, before, side, side, 1, 1, 1, false, null);
        assertTrue(NativeSideContactProof.validateSupportedFloorAndSide(request, MovementVector.ZERO,
                MovementVector.ZERO, FRAME, List.of(floorRow, sideRow), ignored -> true, true).isEmpty());
        assertTrue(NativeSideContactProof.validateSupportedFloorAndSide(request, MovementVector.ZERO,
                MovementVector.ZERO, FRAME, List.of(floorRow, sideRow), ignored -> true, false).isEmpty());
    }

    @Test void mixedProofRejectsBadFloorAxisRedirectAndCeilingRows() {
        var request = new MovementVector(-.1, -.02, 0);
        var floor = new MovementVector(.001, .01, 0);
        var before = new MovementVector(-.1, -.02, 0);
        var wronglyUnredirected = traceRow(floor, before, floor, MovementVector.ZERO,
                1, 1, 0, true, null);
        assertTrue(NativeSideContactProof.validateSupportedFloorAndSide(request, MovementVector.ZERO,
                MovementVector.ZERO, FRAME, List.of(wronglyUnredirected), ignored -> true, true).isEmpty());
        var ceiling = traceRow(new MovementVector(0, -.02, 0), before,
                new MovementVector(0, -.02, 0), MovementVector.ZERO, 1, 1, 0, true, null);
        assertTrue(NativeSideContactProof.validateSupportedFloorAndSide(request, MovementVector.ZERO,
                MovementVector.ZERO, FRAME, List.of(ceiling), ignored -> true, true).isEmpty());
    }

    @Test void mixedSupportedSideVolumePushAllowsGeometryOnlyCorrectionWithoutContactPlane() {
        var request = new MovementVector(0, -.02, 0);
        var floorOne = new MovementVector(0, .01002, 0);
        var floorTwo = new MovementVector(0, .0099636924603187, 0);
        var beforeSide = new MovementVector(0, -.0000163075396813, 0);
        var side = new MovementVector(-.100002, 0, 0);
        var rows = List.of(
                traceRow(floorOne, new MovementVector(0, -.01, 0), floorOne, floorOne, 1, 2, 0, true, null),
                traceRow(floorTwo, new MovementVector(0, -.00998, 0), floorTwo, floorTwo, 2, 2, 0, true, null),
                traceRow(side, beforeSide, side, side, 2, 2, 1, false,
                        geometry(new MovementVector(-.199998, .9, .5), side, beforeSide, 1, 2)));
        var nativeMotion = new MovementVector(-.100002, -.0000163075396813, 0);
        var proof = NativeSideContactProof.validateSupportedFloorAndSide(request, nativeMotion, nativeMotion,
                FRAME, rows, ignored -> true, true).orElseThrow();
        assertEquals(new MovementVector(0, nativeMotion.y(), 0), proof.boundedOwn(),
                "supported negative own-Y clipping is preserved while horizontal own request stays zero");
        assertNull(proof.horizontalContact(), "geometry-only volume push needs no invented plane certificate");
        assertTrue(proof.geometryCorrection().x() < 0d);
        assertEquals(0d, proof.geometryCorrection().y(), 0d,
                "real negative floor displacement is not replaced by positive geometry correction");
        var correction = ContactPositionCorrectionEvidence.validateSupportedFloorAndSide(request,
                proof.boundedOwn(), proof.geometryCorrection(), proof.horizontalContact()).orElseThrow();
        assertTrue(correction.matches(request, proof.boundedOwn(), true));
        assertFalse(correction.matches(request, proof.boundedOwn(), false));
    }

    @Test void supportedMixedProofRetainsSubSlopPositiveNativeYOnlyAsPositionCorrection() {
        var request = new MovementVector(0, -.02, 0);
        var floorOne = new MovementVector(0, .01002, 0);
        var floorTwo = new MovementVector(0, .0099808297882346, 0);
        var side = new MovementVector(-.100002, 0, 0);
        var nativeMotion = new MovementVector(-.100002, .0000008297882346, 0);
        var rows = List.of(
                traceRow(floorOne, new MovementVector(0, -.01, 0), floorOne, floorOne, 1, 2, 0, true, null),
                traceRow(floorTwo, new MovementVector(0, -.00998, 0), floorTwo, floorTwo, 2, 2, 0, true, null),
                traceRow(side, new MovementVector(0, nativeMotion.y(), 0), side, side, 2, 2, 1, false,
                        geometry(new MovementVector(-.199998, .9, .5), side,
                                new MovementVector(0, nativeMotion.y(), 0), 1, 2)));
        var proof = NativeSideContactProof.validateSupportedFloorAndSide(request, nativeMotion, nativeMotion,
                FRAME, rows, ignored -> true, true).orElseThrow();
        assertEquals(MovementVector.ZERO, proof.boundedOwn());
        assertEquals(nativeMotion.y(), proof.geometryCorrection().y(), 1e-15);
        assertTrue(ContactPositionCorrectionEvidence.validateSupportedFloorAndSide(request, proof.boundedOwn(),
                proof.geometryCorrection(), proof.horizontalContact()).isPresent());
    }

    @Test void ownReplayClipsFloorWishBeforeUsingCapturedObliqueSideRow() {
        // R5 WEST yaw request and first measured steel-wall side MTV (trace line 1365).
        var request = new MovementVector(-.1526740635279334, -.02, -.033236919620635234);
        var floor = new MovementVector(0, .02000065032151997, 0);
        var side = new MovementVector(.010633872694861097, -2.5465424915242032e-8,
                -.00032576026823043247);
        // Keep the request below this MTV's length so the tiny native floor residual would
        // otherwise perturb the side projection instead of being hidden by the MTV cap.
        var projectionRequest = new MovementVector(-.0001, -.02, 0);
        var rows = List.of(
                traceRow(floor, projectionRequest,
                        floor, floor, 1, 1, 0, true, null),
                traceRow(side, new MovementVector(projectionRequest.x(), .00000065032151997,
                        projectionRequest.z()), side, side, 1, 1, 1, false, null));
        var replayed = NativeSideContactProof.ownResponsesAt(projectionRequest, 1, rows,
                java.util.Arrays.asList(null, side));
        double length = Math.sqrt(side.x()*side.x() + side.y()*side.y() + side.z()*side.z());
        double lambda = Math.min(length, Math.max(0d,
                -(projectionRequest.x()*side.x() + projectionRequest.z()*side.z())/length));
        var expected = new MovementVector(side.x()/length*lambda, 0, side.z()/length*lambda);
        var unclippedReplay = NativeSideContactProof.ownResponsesAt(projectionRequest, 1,
                List.of(rows.get(1)), List.of(side));
        assertEquals(expected.x(), replayed.x(), NativeCollisionSlop.MAX_RESIDUAL,
                "floor-positive geometry Y must not add a spurious side-projection X response");
        assertEquals(expected.z(), replayed.z(), NativeCollisionSlop.MAX_RESIDUAL);
        assertEquals(0d, replayed.y(), 0d, "side-plane replay never authorizes vertical own response");
        assertTrue(Math.abs(unclippedReplay.x()-replayed.x()) > 1.0e-8,
                "regression input exposes the positive floor-geometry residue leaking into the side projection");
    }

    @Test void sdkFloorRedirectUsesExistingWholeVectorAppliedUpRule() {
        var selected = new MovementVector(.001, .01, 0);
        double length = Math.sqrt(.001 * .001 + .01 * .01);
        assertTrue(SableOwnedBodyMotion.nativeFloorAppliedResponseAdmissible(selected,
                new MovementVector(0, length, 0), new MovementVector(0, length, 0)));
        assertFalse(SableOwnedBodyMotion.nativeFloorAppliedResponseAdmissible(selected,
                selected, selected), "SDK floor redirect cannot be represented as unredirected selected MTV");
    }

    @Test void ceilingAndSideProofUsesNativeSatWitnessAndBoundsOnlyOwnUpwardMotion() {
        for (var sample : List.of(
                new CeilingCase(.42, -.2, .3, .22, 0),
                new CeilingCase(-.07544406705, -.1, .2, -.07544406705, -.1))) {
            var request = new MovementVector(.1, sample.requestY(), 0);
            var side = new MovementVector(-.100002, 0, 0);
            var ceiling = new MovementVector(0, sample.ceilingMtv(), 0);
            var sideGeo = geometry(new MovementVector(-.199998, sample.centerY(), .5), side, request);
            var ceilingBefore = request.add(side);
            var ceilingGeo = geometry(new MovementVector(-.300000, sample.centerY(), .5), ceiling, ceilingBefore,
                    1, 1, new BlockPos(-1, 1, 0), new AABB(-1, 1, 0, 0, 2, 1));
            assertEquals(side.x(), new NativeSideCollisionWitness().separation(sideGeo).x(), NativeCollisionSlop.MAX_RESIDUAL);
            assertEquals(ceiling.y(), new NativeSideCollisionWitness().separation(ceilingGeo).y(), NativeCollisionSlop.MAX_RESIDUAL);
            var rows = List.of(row(side, request, sideGeo, 1, 1, 0),
                    traceRowAt(ceiling, ceilingBefore, ceiling, ceiling, 1, 1, 1, true, ceilingGeo,
                            new BlockPos(-1, 1, 0)));
            var nativeMotion = request.add(side).add(ceiling);
            var result = NativeSideContactProof.validateCeilingAndSideDetailed(request, nativeMotion,
                    nativeMotion, FRAME, rows, ignored -> ignored.selectedState() == Blocks.STONE.defaultBlockState());
            assertEquals(NativeSideContactProof.Failure.NONE, result.reason(), "row " + result.rowIndex() + " case " + sample);
            var proof = result.result().orElseThrow();
            assertEquals(0, proof.boundedOwn().x(), NativeCollisionSlop.MAX_RESIDUAL);
            assertEquals(sample.boundedY(), proof.boundedOwn().y(), NativeCollisionSlop.MAX_RESIDUAL);
            assertEquals(-.000002, proof.geometryCorrection().x(), NativeCollisionSlop.MAX_RESIDUAL);
            assertEquals(sample.geometryY(), proof.geometryCorrection().y(), NativeCollisionSlop.MAX_RESIDUAL);
        }
    }

    @Test void nativeCeilingSatWitnessBoundsUpwardOwnMotionAndKeepsGeometryCorrectionSeparate() {
        var request = new MovementVector(.1, .42, 0);
        var side = new MovementVector(-.100002, 0, 0);
        var ceiling = new MovementVector(0, -.5, 0);
        var sideGeo = geometry(new MovementVector(-.199998, .6, .5), side, request);
        var beforeCeiling = request.add(side);
        var roofPos = new BlockPos(-1, 1, 0);
        var roofBounds = new AABB(-1, 1, 0, 0, 2, 1);
        var roofGeo = geometry(new MovementVector(-.300000, .6, .5), ceiling, beforeCeiling,
                1, 1, roofPos, roofBounds);
        var witness = new NativeSideCollisionWitness().separation(roofGeo);
        assertNotNull(witness);
        assertEquals(0, witness.x(), 0d);
        assertEquals(-.5, witness.y(), 0d);
        assertEquals(0, witness.z(), 0d);

        var rows = List.of(row(side, request, sideGeo, 1, 1, 0),
                traceRowAt(ceiling, beforeCeiling, ceiling, ceiling, 1, 1, 1,
                        true, roofGeo, roofPos));
        var nativeMotion = request.add(side).add(ceiling);
        var result = NativeSideContactProof.validateCeilingAndSideDetailed(request, nativeMotion,
                nativeMotion, FRAME, rows,
                ignored -> ignored.selectedState() == Blocks.STONE.defaultBlockState());
        assertEquals(NativeSideContactProof.Failure.NONE, result.reason());
        var proof = result.result().orElseThrow();
        assertEquals(0, proof.boundedOwn().x(), NativeCollisionSlop.MAX_RESIDUAL);
        assertEquals(0, proof.boundedOwn().y(), NativeCollisionSlop.MAX_RESIDUAL,
                "the witnessed ceiling MTV exceeds the upward request and bounds own Y to zero");
        assertEquals(-.000002, proof.geometryCorrection().x(), NativeCollisionSlop.MAX_RESIDUAL);
        assertEquals(-.08, proof.geometryCorrection().y(), NativeCollisionSlop.MAX_RESIDUAL);
    }

    @Test void ceilingProofRejectsMissingBadRoofMismatchUnknownStateAndOrder() {
        var request = new MovementVector(.1, -.07544406705, 0);
        var side = new MovementVector(-.100002, 0, 0);
        var ceiling = new MovementVector(0, -.1, 0);
        var sideGeo = geometry(new MovementVector(-.199998, .2, .5), side, request);
        var beforeRoof = request.add(side);
        var roofPos = new BlockPos(-1, 1, 0);
        var roofGeo = geometry(new MovementVector(-.3, .2, .5), ceiling, beforeRoof, 1, 1,
                roofPos, new AABB(-1, 1, 0, 0, 2, 1));
        var sideRow = row(side, request, sideGeo, 1, 1, 0);
        var roofRow = traceRowAt(ceiling, beforeRoof, ceiling, ceiling, 1, 1, 1, true, roofGeo, roofPos);
        var nativeMotion = request.add(side).add(ceiling);
        java.util.function.BiPredicate<List<CurrentFrameCollisionScope.NativeCollisionTrace>, MovementVector> rejected =
                (rows, measured) -> NativeSideContactProof.validateCeilingAndSideDetailed(request, nativeMotion,
                        measured, FRAME, rows, ignored -> true).result().isEmpty();
        assertTrue(rejected.test(List.of(sideRow), nativeMotion), "missing ceiling");
        var wrongRoof = traceRowAt(ceiling, beforeRoof, ceiling, ceiling, 1, 1, 1, true,
                geometry(new MovementVector(-.3, .25, .5), ceiling, beforeRoof, -1, 1, roofPos,
                        new AABB(-1, 1, 0, 0, 2, 1)), roofPos);
        assertTrue(rejected.test(List.of(sideRow, wrongRoof), nativeMotion), "SAT does not witness claimed roof MTV");
        assertTrue(rejected.test(List.of(sideRow, roofRow), nativeMotion.add(new MovementVector(0, 1e-4, 0))), "measured/native mismatch");
        var unknown = traceRowAt(ceiling, beforeRoof, ceiling, ceiling, 1, 1, 1, true, roofGeo, roofPos);
        assertTrue(NativeSideContactProof.validateCeilingAndSideDetailed(request, nativeMotion, nativeMotion,
                FRAME, List.of(sideRow, unknown), ignored -> false).result().isEmpty(), "unknown source state");
        assertTrue(rejected.test(List.of(roofRow, sideRow), nativeMotion), "row iteration order");
    }

    private static java.util.Optional<NativeSideContactProof.Result> validateAppliedOffset(MovementVector selected,
            MovementVector request, NativeCollisionGeometry geometry, double delta) {
        var row = new CurrentFrameCollisionScope.NativeCollisionTrace(FRAME, 1, 1, 0,
                BlockPos.ZERO, Blocks.STONE.defaultBlockState(), "stone", "stone",
                selected.x(), selected.y(), selected.z(), request.x(), request.y(), request.z(),
                selected.x()+delta, selected.y()+delta, selected.z()+delta,
                selected.x(), selected.y(), selected.z(), geometry, false);
        return NativeSideContactProof.validate(request, new MovementVector(-.000002, 0, 0),
                new MovementVector(-.000002, 0, 0), FRAME, List.of(row), ignored -> true);
    }

    private static NativeCollisionGeometry geometry(MovementVector center, MovementVector selected, MovementVector motion) {
        return geometry(center, selected, motion, 0, 1);
    }

    private static NativeCollisionGeometry geometry(MovementVector center, MovementVector selected,
            MovementVector motion, int iteration, int substeps) {
        return new NativeCollisionGeometry(center, new MovementVector(.6, 1.8, .6),
                new NativeCollisionGeometry.QuaternionValue(0, 0, 0, 1),
                new NativeFrameInterval.PoseValue(new double[]{1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1}),
                new AABB(0, 0, 0, 1, 1, 1), selected, motion, iteration, substeps);
    }

    private static NativeCollisionGeometry geometry(MovementVector center, MovementVector selected,
            MovementVector motion, int iteration, int substeps, BlockPos pos, AABB bounds) {
        return new NativeCollisionGeometry(center, new MovementVector(.6, 1.8, .6),
                new NativeCollisionGeometry.QuaternionValue(0, 0, 0, 1),
                new NativeFrameInterval.PoseValue(new double[]{1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1}),
                bounds, selected, motion, iteration, substeps);
    }

    private static CurrentFrameCollisionScope.NativeCollisionTrace traceRowAt(MovementVector selected,
            MovementVector before, MovementVector applied, MovementVector response, int substep, int substeps,
            int iteration, boolean vertical, NativeCollisionGeometry geometry, BlockPos pos) {
        return new CurrentFrameCollisionScope.NativeCollisionTrace(FRAME, substep, substeps, iteration,
                pos, Blocks.STONE.defaultBlockState(), "stone", "stone", selected.x(), selected.y(), selected.z(),
                before.x(), before.y(), before.z(), applied.x(), applied.y(), applied.z(), response.x(), response.y(),
                response.z(), geometry, vertical);
    }

    private record CeilingCase(double requestY, double ceilingMtv, double centerY, double boundedY, double geometryY) { }

    private static CurrentFrameCollisionScope.NativeCollisionTrace row(MovementVector selected,
            MovementVector before, NativeCollisionGeometry geometry, int substep, int substeps, int iteration) {
        return new CurrentFrameCollisionScope.NativeCollisionTrace(FRAME, substep, substeps, iteration,
                BlockPos.ZERO, Blocks.STONE.defaultBlockState(), "stone", "stone",
                selected.x(), selected.y(), selected.z(), before.x(), before.y(), before.z(),
                selected.x(), selected.y(), selected.z(), selected.x(), selected.y(), selected.z(), geometry, false);
    }

    private static CurrentFrameCollisionScope.NativeCollisionTrace traceRow(MovementVector selected,
            MovementVector before, MovementVector applied, MovementVector response,
            int substep, int substeps, int iteration, boolean vertical, NativeCollisionGeometry geometry) {
        return new CurrentFrameCollisionScope.NativeCollisionTrace(FRAME, substep, substeps, iteration,
                BlockPos.ZERO, Blocks.STONE.defaultBlockState(), "stone", "stone",
                selected.x(), selected.y(), selected.z(), before.x(), before.y(), before.z(),
                applied.x(), applied.y(), applied.z(), response.x(), response.y(), response.z(), geometry, vertical);
    }
}
