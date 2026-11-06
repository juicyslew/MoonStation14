package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.ms14.movement.MovementVector;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Captured row geometry regression only; this does not prove live transient-world support admission. */
class OwnerTransientFloorSideTraceRegressionTest {
    private static final UUID FRAME=UUID.fromString("0fb05421-6f59-44d1-b337-ee121d2b1539");

    @Test void seq3725CapturedRowsReplayNativeSatFloorAndSideEvidence() {
        var root=OwnerTraceFixture.load("owner-seq3725.json");
        var request=OwnerTraceFixture.vector(root.getAsJsonArray("request"));
        var nativeMotion=OwnerTraceFixture.vector(root.getAsJsonArray("nativeMotion"));
        var rows=OwnerTraceFixture.rows(root.getAsJsonArray("rows"),FRAME);
        assertEquals(16,rows.size());
        var witness=new NativeSideCollisionWitness();
        int floor=-1,side=-1;
        MovementVector responseSum=new MovementVector(0,0,0);
        for(int i=0;i<rows.size();i++) {
            var row=rows.get(i);
            assertNotNull(row.geometry());
            MovementVector replay=witness.separation(row.geometry());
            assertNotNull(replay,"row="+i);
            assertTrue(Double.isFinite(replay.x()+replay.y()+replay.z()),"row="+i);
            assertTrue(Math.abs(replay.x())+Math.abs(replay.y())+Math.abs(replay.z())>0,"row="+i+" SAT witness is nonzero");
            responseSum=responseSum.add(new MovementVector(row.responseX(),row.responseY(),row.responseZ()));
            if(row.substep()==5 && row.verticalCollision()) floor=i;
            if(row.substep()==6 && !row.verticalCollision()) side=i;
        }
        assertEquals(13,floor);
        assertEquals(5,rows.get(floor).substep());
        assertEquals(15,side);
        assertEquals(6,rows.get(side).substep());
        assertEquals(request.x()+responseSum.x(),nativeMotion.x(),5e-18);
        assertEquals(request.y()+responseSum.y(),nativeMotion.y(),5e-18);
        assertEquals(request.z()+responseSum.z(),nativeMotion.z(),5e-18);
        var corner=rows.get(side).geometry().maxMtv();
        assertTrue(Math.abs(corner.z())>Math.abs(corner.x()) && Math.abs(corner.z())>Math.abs(corner.y()));
        assertTrue(corner.z()<0,"final corner is Z-dominant, not a floor response");
        var clip=OwnerTraceFixture.worldClip(root);
        var raw=root.getAsJsonObject("worldClip");
        assertTrue(raw.get("complete").getAsBoolean());
        assertFalse(raw.get("downwardFloorClip").getAsBoolean());
        var expectedInput=OwnerTraceFixture.vector(root.getAsJsonArray("nativeMotion"));
        var measured=OwnerTraceFixture.vector(root.getAsJsonArray("measured"));
        var marker=clip.entity();
        var cp=raw.getAsJsonArray("positionBefore"); var cb=raw.getAsJsonArray("boundsBefore");
        var before=new Vec3(cp.get(0).getAsDouble(),cp.get(1).getAsDouble(),cp.get(2).getAsDouble());
        var bounds=new AABB(new Vec3(cb.get(0).getAsJsonArray().get(0).getAsDouble(),cb.get(0).getAsJsonArray().get(1).getAsDouble(),cb.get(0).getAsJsonArray().get(2).getAsDouble()),new Vec3(cb.get(1).getAsJsonArray().get(0).getAsDouble(),cb.get(1).getAsJsonArray().get(1).getAsDouble(),cb.get(1).getAsJsonArray().get(2).getAsDouble()));
        assertTrue(clip.matchesCall(marker,new Vec3(expectedInput.x(),expectedInput.y(),expectedInput.z()),new Vec3(expectedInput.x(),expectedInput.y(),expectedInput.z()),before,bounds));
        assertEquals(expectedInput.x(),clip.input().x(),0); assertEquals(expectedInput.y(),clip.input().y(),0); assertEquals(expectedInput.z(),clip.input().z(),0);
        assertEquals(expectedInput.x(),clip.output().x(),0); assertEquals(expectedInput.y(),clip.output().y(),0); assertEquals(expectedInput.z(),clip.output().z(),0);
        assertTrue(Math.abs(measured.x()-expectedInput.x())<2e-12 && Math.abs(measured.y()-expectedInput.y())<2e-12 && Math.abs(measured.z()-expectedInput.z())<2e-12);
        assertEquals(16,rows.get(side).geometry().subLevelPose().matrix().length);
        // The clip pose is pre-move; dimensions are derived from its captured body bounds.
        var actor=root.getAsJsonObject("actor"); var feet=actor.getAsJsonArray("feet");
        assertEquals(-16.217600568000254,feet.get(0).getAsDouble(),1e-12);
        assertEquals(-59.000502931682846,feet.get(1).getAsDouble(),1e-12);
        assertEquals(40.08838333509837,feet.get(2).getAsDouble(),1e-12);
        var bodyDims=actor.getAsJsonArray("boundsDimensions");
        assertEquals(.6,actor.getAsJsonArray("dimensions").get(0).getAsDouble(),0);
        assertEquals(1.8,actor.getAsJsonArray("dimensions").get(1).getAsDouble(),0);
        double width=bodyDims.get(0).getAsDouble();
        assertEquals(1.7999999523162842,bodyDims.get(1).getAsDouble(),0);
        AABB band=new AABB(feet.get(0).getAsDouble()-width/2,feet.get(1).getAsDouble()-.001,feet.get(2).getAsDouble()-width/2,feet.get(0).getAsDouble()+width/2,feet.get(1).getAsDouble(),feet.get(2).getAsDouble()+width/2);
        assertCapturedShapesClearAtFinalFeet(rows,band);
        assertTrue(intersectsAny(rows,band.move(0,0,.2)),"translated band positive control should reach captured floor geometry");
    }

    @Test void transientFloorAndSideProofBoundsOnlyOwnYAndKeepsCornerCorrectionPositionOnly() {
        var root=OwnerTraceFixture.load("owner-seq3725.json");
        var request=OwnerTraceFixture.vector(root.getAsJsonArray("request"));
        var nativeMotion=OwnerTraceFixture.vector(root.getAsJsonArray("nativeMotion"));
        var measured=OwnerTraceFixture.vector(root.getAsJsonArray("measured"));
        var rows=OwnerTraceFixture.rows(root.getAsJsonArray("rows"),FRAME);
        var clip=OwnerTraceFixture.worldClip(root);
        var proof=NativeSideContactProof.validateTransientFloorAndSideDetailed(request,nativeMotion,measured,
                FRAME,rows,row->row.selectedState()==net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),clip);
        assertEquals(NativeSideContactProof.Failure.NONE,proof.reason(),"row="+proof.rowIndex());
        var result=proof.result().orElseThrow();
        assertEquals(-.0005,result.boundedOwn().y(),1e-12);
        assertTrue(result.geometryCorrection().y()<0d);
        assertTrue(Math.abs(result.geometryCorrection().y())<=com.juicyslew.moonstation14.ms14.movement.NativeCollisionSlop.MAX_RESIDUAL);
        assertTrue(result.horizontalContact()!=null,"corner clipped horizontal own request needs ordered contact evidence");
        assertTrue(com.juicyslew.moonstation14.ms14.movement.ContactPositionCorrectionEvidence
                .validateTransientFloorAndSide(request,result.boundedOwn(),result.geometryCorrection(),result.horizontalContact()).isPresent());
        var position=com.juicyslew.moonstation14.ms14.movement.ContactPositionCorrectionEvidence
                .validateTransientFloorAndSide(request,result.boundedOwn(),result.geometryCorrection(),result.horizontalContact()).orElseThrow();
        assertTrue(position.matches(request,result.boundedOwn(),false));
        assertFalse(position.matches(request,result.boundedOwn(),true));
        assertTrue(NativeSideContactProof.validateTransientFloorAndSideDetailed(request,nativeMotion,measured,
                FRAME,rows,row->true,null).result().isEmpty(),"raw identity world clip is mandatory");
        var nonIdentityClip=clipWithOutput(root,nativeMotion,new MovementVector(Math.nextUp(nativeMotion.x()),nativeMotion.y(),nativeMotion.z()));
        assertTrue(Math.abs(nonIdentityClip.output().x()-nativeMotion.x())<com.juicyslew.moonstation14.ms14.movement.NativeCollisionSlop.MAX_RESIDUAL,
                "one-ULP adversarial raw clip remains within legacy movement slop");
        assertTrue(NativeSideContactProof.validateTransientFloorAndSideDetailed(request,nativeMotion,measured,
                FRAME,rows,row->true,nonIdentityClip).result().isEmpty(),"raw one-ULP nonidentity clip is rejected exactly");
        assertTrue(NativeSideContactProof.validateTransientFloorAndSideDetailed(new MovementVector(.1,.01,0),nativeMotion,measured,
                FRAME,rows,row->true,clip).result().isEmpty(),"upward request cannot use transient floor policy");
        assertTrue(NativeSideContactProof.validateTransientFloorAndSideDetailed(request,nativeMotion,measured,
                FRAME,rows,row->false,clip).result().isEmpty(),"unknown selected state cannot certify geometry");
        var floorOnly=rows.stream().filter(r->r.verticalCollision()).toList();
        var sideOnly=rows.stream().filter(r->!r.verticalCollision()).toList();
        assertTrue(NativeSideContactProof.validateTransientFloorAndSideDetailed(request,nativeMotion,measured,
                FRAME,floorOnly,row->true,clip).result().isEmpty(),"floor-only trace does not certify side contact");
        assertTrue(NativeSideContactProof.validateTransientFloorAndSideDetailed(request,nativeMotion,measured,
                FRAME,sideOnly,row->true,clip).result().isEmpty(),"side-only trace does not certify transient floor clip");
        var missingGeometry=new java.util.ArrayList<>(rows);
        var r=rows.get(0);
        missingGeometry.set(0,new CurrentFrameCollisionScope.NativeCollisionTrace(r.frameId(),r.substep(),r.substeps(),r.iteration(),
                r.selectedPos(),r.selectedState(),r.block(),r.blockState(),r.selectedX(),r.selectedY(),r.selectedZ(),
                r.collisionMotionBeforeX(),r.collisionMotionBeforeY(),r.collisionMotionBeforeZ(),r.appliedX(),r.appliedY(),r.appliedZ(),
                r.responseX(),r.responseY(),r.responseZ(),null,r.verticalCollision()));
        assertTrue(NativeSideContactProof.validateTransientFloorAndSideDetailed(request,nativeMotion,measured,
                FRAME,missingGeometry,row->true,clip).result().isEmpty(),"missing SAT geometry cannot authorize response");
    }

    private static CurrentFrameCollisionScope.WorldClipObservation clipWithOutput(com.google.gson.JsonObject root,
            MovementVector input, MovementVector output) {
        var entity=new net.minecraft.world.entity.Marker(net.minecraft.world.entity.EntityType.MARKER,null);
        var raw=root.getAsJsonObject("worldClip"); var p=raw.getAsJsonArray("positionBefore"); var b=raw.getAsJsonArray("boundsBefore");
        var position=new Vec3(p.get(0).getAsDouble(),p.get(1).getAsDouble(),p.get(2).getAsDouble());
        var bounds=new AABB(new Vec3(b.get(0).getAsJsonArray().get(0).getAsDouble(),b.get(0).getAsJsonArray().get(1).getAsDouble(),b.get(0).getAsJsonArray().get(2).getAsDouble()),
                new Vec3(b.get(1).getAsJsonArray().get(0).getAsDouble(),b.get(1).getAsJsonArray().get(1).getAsDouble(),b.get(1).getAsJsonArray().get(2).getAsDouble()));
        return CurrentFrameCollisionScope.withTrackedCharacterPose(entity,false,()->{
            CurrentFrameCollisionScope.recordVanillaWorldCollisionInput(entity,new Vec3(input.x(),input.y(),input.z()),position,bounds);
            CurrentFrameCollisionScope.recordVanillaWorldCollisionOutput(entity,new Vec3(output.x(),output.y(),output.z())); return null;
        }).worldClipObservation();
    }

    private static void assertCapturedShapesClearAtFinalFeet(java.util.List<CurrentFrameCollisionScope.NativeCollisionTrace> rows,AABB band) {
        var distinct=rows.stream().map(r->r.geometry()).filter(java.util.Objects::nonNull).map(NativeCollisionGeometry::selectedStorageBounds).distinct().toList();
        assertEquals(2,distinct.size());
        for(var box:distinct) {
            double[] m=rows.stream().map(r->r.geometry()).filter(g->g!=null&&g.selectedStorageBounds().equals(box)).findFirst().orElseThrow().subLevelPose().matrix();
            var pose=pose(m);
            assertFalse(FrameBoxIntersection.intersects(band,box,pose),"captured shape overlaps final foot band");
        }
    }
    private static boolean intersectsAny(java.util.List<CurrentFrameCollisionScope.NativeCollisionTrace> rows,AABB band) {
        for(var row:rows) if(row.geometry()!=null) {
            var g=row.geometry(); var pose=pose(g.subLevelPose().matrix());
            if(FrameBoxIntersection.intersects(band,g.selectedStorageBounds(),pose)) return true;
        }
        return false;
    }
    private static dev.ryanhcode.sable.companion.math.Pose3d pose(double[] matrix) {
        var m=new Matrix4d().set(matrix); var pose=new dev.ryanhcode.sable.companion.math.Pose3d();
        pose.position().set(m.m30(),m.m31(),m.m32());
        pose.orientation().setFromUnnormalized(m);
        return pose;
    }
}
