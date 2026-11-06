package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.ms14.movement.MovementVector;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import static org.junit.jupiter.api.Assertions.*;

class OwnerCeilingOnlyTraceRegressionTest {
    @Test void sealedWorldClipRecognizesOnlyVanillaZeroedTinyFloorAxes() {
        double epsilon=net.minecraft.world.phys.shapes.Shapes.EPSILON;
        double budget=com.juicyslew.moonstation14.ms14.movement.NativeCollisionSlop.MAX_RESIDUAL;
        var raw=observe(new Vec3(8.215491872565689e-7,-.09800053115683706,-9.558543531255253e-8),
                new Vec3(8.215491872565689e-7,-.00200000000000955,0),Vec3.ZERO,new AABB(0,0,0,1,1,1),true);
        assertTrue(raw.downwardFloorClipWithUnchangedOrVanillaZeroedXZ());
        assertNotEquals(raw.input().z,raw.output().z,"actual captured tuple has normalized Z");
        assertTrue(raw.matchesCall(raw.entity(),raw.input(),raw.output(),Vec3.ZERO,new AABB(0,0,0,1,1,1)));
        for(double sign:new double[]{-1,1}) {
            assertTrue(observe(new Vec3(sign*Math.nextDown(epsilon),-.1,0),new Vec3(0,-.01,0),
                    Vec3.ZERO,new AABB(0,0,0,1,1,1),true).downwardFloorClipWithUnchangedOrVanillaZeroedXZ());
            assertFalse(observe(new Vec3(sign*epsilon,-.1,0),new Vec3(0,-.01,0),
                    Vec3.ZERO,new AABB(0,0,0,1,1,1),true).downwardFloorClipWithUnchangedOrVanillaZeroedXZ());
            assertFalse(observe(new Vec3(sign*Math.nextUp(epsilon),-.1,0),new Vec3(0,-.01,0),
                    Vec3.ZERO,new AABB(0,0,0,1,1,1),true).downwardFloorClipWithUnchangedOrVanillaZeroedXZ());
        }
        assertTrue(observe(new Vec3(.5,-.1,0),new Vec3(.5,-.01,0),Vec3.ZERO,
                new AABB(0,0,0,1,1,1),true).downwardFloorClipWithUnchangedOrVanillaZeroedXZ());
        double each=epsilon*.70;
        assertTrue(observe(new Vec3(each,-.1,each),new Vec3(0,-.01,0),Vec3.ZERO,
                new AABB(0,0,0,1,1,1),true).downwardFloorClipWithUnchangedOrVanillaZeroedXZ());
        assertFalse(observe(new Vec3(epsilon*.5,-.1,0),new Vec3(epsilon*.25,-.01,0),Vec3.ZERO,
                new AABB(0,0,0,1,1,1),true).downwardFloorClipWithUnchangedOrVanillaZeroedXZ(),"nonzero attenuation");
        assertFalse(observe(new Vec3(epsilon*.5,-.1,0),new Vec3(-epsilon*.25,-.01,0),Vec3.ZERO,
                new AABB(0,0,0,1,1,1),true).downwardFloorClipWithUnchangedOrVanillaZeroedXZ(),"opposite-sign output");
        assertFalse(observe(new Vec3(0,-.1,0),new Vec3(0,-.1,0),Vec3.ZERO,
                new AABB(0,0,0,1,1,1),true).downwardFloorClipWithUnchangedOrVanillaZeroedXZ(),"identity Y");
        assertFalse(observe(new Vec3(0,-.1,0),new Vec3(0,.01,0),Vec3.ZERO,
                new AABB(0,0,0,1,1,1),true).downwardFloorClipWithUnchangedOrVanillaZeroedXZ(),"positive Y output");
        assertFalse(observe(new Vec3(budget*.75,-.1,budget*.75),new Vec3(0,-.01,0),Vec3.ZERO,
                new AABB(0,0,0,1,1,1),true).downwardFloorClipWithUnchangedOrVanillaZeroedXZ());
        assertFalse(observe(new Vec3(Double.NaN,-.1,0),new Vec3(0,-.01,0),Vec3.ZERO,
                new AABB(0,0,0,1,1,1),true).downwardFloorClipWithUnchangedOrVanillaZeroedXZ());
        assertFalse(observe(new Vec3(0,-.1,0),new Vec3(0,-.01,Double.POSITIVE_INFINITY),Vec3.ZERO,
                new AABB(0,0,0,1,1,1),true).downwardFloorClipWithUnchangedOrVanillaZeroedXZ());
        assertFalse(observe(new Vec3(0,-.1,0),new Vec3(0,-.1,0),Vec3.ZERO,
                new AABB(0,0,0,1,1,1),true).downwardFloorClipWithUnchangedOrVanillaZeroedXZ());
        assertFalse(observe(new Vec3(0,-.1,0),new Vec3(0,.01,0),Vec3.ZERO,
                new AABB(0,0,0,1,1,1),true).downwardFloorClipWithUnchangedOrVanillaZeroedXZ());
        assertFalse(observe(new Vec3(0,-.1,0),null,Vec3.ZERO,new AABB(0,0,0,1,1,1),false)
                .downwardFloorClipWithUnchangedOrVanillaZeroedXZ());
    }

    @Test void nativeCeilingOnlyOwnerRowsReplayWithoutSideEvidence() {
        for (long sequence : new long[]{11446, 18339, 28494}) {
            var root=OwnerTraceFixture.load("owner-seq"+sequence+".json");
            var frame=UUID.fromString(root.get("frameId").getAsString());
            var request=OwnerTraceFixture.vector(root.getAsJsonArray("request"));
            var nativeMotion=OwnerTraceFixture.vector(root.getAsJsonArray("nativeMotion"));
            var measured=OwnerTraceFixture.vector(root.getAsJsonArray("measured"));
            var rows=OwnerTraceFixture.rows(root.getAsJsonArray("rows"),frame);
            var result=NativeSideContactProof.validateCeilingOnlyDetailed(request,nativeMotion,measured,
                    frame,rows,row->true,OwnerTraceFixture.worldClip(root));
            assertTrue(result.result().isPresent(),sequence+": "+result.reason()+" row="+result.rowIndex());
            var proof=result.result().orElseThrow();
            assertEquals(request.y()<0d ? request.y() : 0d,proof.boundedOwn().y(),1e-12);
            assertEquals(request.x(),proof.boundedOwn().x(),0d);
            assertEquals(request.z(),proof.boundedOwn().z(),0d);
            assertNull(proof.horizontalContact());
            assertEquals(measured.y()-proof.boundedOwn().y(),proof.geometryCorrection().y(),1e-12);
        }
    }

    @Test void ceilingOnlyRequiresCompleteGeometryAndKnownRows() {
        var root=OwnerTraceFixture.load("owner-seq18339.json");
        var frame=UUID.fromString(root.get("frameId").getAsString());
        var result=NativeSideContactProof.validateCeilingOnlyDetailed(
                OwnerTraceFixture.vector(root.getAsJsonArray("request")),
                OwnerTraceFixture.vector(root.getAsJsonArray("nativeMotion")),
                OwnerTraceFixture.vector(root.getAsJsonArray("measured")),frame,
                OwnerTraceFixture.rows(root.getAsJsonArray("rows"),frame),row->false,
                OwnerTraceFixture.worldClip(root));
        assertTrue(result.result().isEmpty());
    }

    @Test void rejectsUnboundOrIncompleteNativeAndWorldObservations() {
        var root=OwnerTraceFixture.load("owner-seq18339.json");
        var frame=UUID.fromString(root.get("frameId").getAsString());
        var request=OwnerTraceFixture.vector(root.getAsJsonArray("request"));
        var nativeMotion=OwnerTraceFixture.vector(root.getAsJsonArray("nativeMotion"));
        var measured=OwnerTraceFixture.vector(root.getAsJsonArray("measured"));
        var rows=OwnerTraceFixture.rows(root.getAsJsonArray("rows"),frame);
        var clip=OwnerTraceFixture.worldClip(root);
        assertRejected(request,nativeMotion,measured,frame,rows,clip,"missing geometry",
                replace(rows,0,withGeometry(rows.get(0),null)),row->true);
        assertRejected(request,nativeMotion.add(new MovementVector(0,.001,0)),measured,frame,rows,clip,
                "native motion differs from clip input",rows,row->true);
        assertRejected(request,nativeMotion,measured.add(new MovementVector(0,.001,0)),frame,rows,clip,
                "measured differs from clip output",rows,row->true);
        assertRejected(request,nativeMotion,measured,frame,rows,mutatedClip(clip.input(),
                new Vec3(clip.output().x+0.01,clip.output().y,clip.output().z),clip),
                "nonidentity XZ clip",rows,row->true);
        assertRejected(request,nativeMotion,measured,frame,rows,mutatedClip(clip.input(),
                new Vec3(clip.output().x,0.1,clip.output().z),clip),"upward/nonfloor clip",rows,row->true);
        assertRejected(request,nativeMotion,measured,frame,rows,null,"missing clip",rows,row->true);
        assertRejected(request,nativeMotion,measured,frame,rows,partialClip(clip),"partial clip",rows,row->true);
        assertRejected(request,nativeMotion,measured,frame,rows,clip,"side row",replace(rows,0,
                withVertical(rows.get(0),false)),row->true);
        assertRejected(request,nativeMotion,measured,frame,rows,clip,"positive/upward-floor row",
                replace(rows,0,withVectors(rows.get(0),new MovementVector(0,0.40909313242700174,0))),row->true);
    }

    private static void assertRejected(MovementVector request,MovementVector nativeMotion,MovementVector measured,
            UUID frame,List<CurrentFrameCollisionScope.NativeCollisionTrace> source,
            CurrentFrameCollisionScope.WorldClipObservation clip,String label,
            List<CurrentFrameCollisionScope.NativeCollisionTrace> rows,
            java.util.function.Predicate<CurrentFrameCollisionScope.NativeCollisionTrace> known) {
        var result=NativeSideContactProof.validateCeilingOnlyDetailed(request,nativeMotion,measured,frame,rows,known,clip);
        assertTrue(result.result().isEmpty(),label+" unexpectedly accepted");
    }
    private static List<CurrentFrameCollisionScope.NativeCollisionTrace> replace(
            List<CurrentFrameCollisionScope.NativeCollisionTrace> rows,int index,
            CurrentFrameCollisionScope.NativeCollisionTrace value) {
        var copy=new ArrayList<>(rows); copy.set(index,value); return List.copyOf(copy);
    }
    private static CurrentFrameCollisionScope.NativeCollisionTrace withGeometry(
            CurrentFrameCollisionScope.NativeCollisionTrace r,NativeCollisionGeometry g) {
        return new CurrentFrameCollisionScope.NativeCollisionTrace(r.frameId(),r.substep(),r.substeps(),r.iteration(),
                r.selectedPos(),r.selectedState(),r.block(),r.blockState(),r.selectedX(),r.selectedY(),r.selectedZ(),
                r.collisionMotionBeforeX(),r.collisionMotionBeforeY(),r.collisionMotionBeforeZ(),r.appliedX(),r.appliedY(),
                r.appliedZ(),r.responseX(),r.responseY(),r.responseZ(),g,r.verticalCollision());
    }
    private static CurrentFrameCollisionScope.NativeCollisionTrace withVertical(
            CurrentFrameCollisionScope.NativeCollisionTrace r,boolean vertical) {
        return new CurrentFrameCollisionScope.NativeCollisionTrace(r.frameId(),r.substep(),r.substeps(),r.iteration(),
                r.selectedPos(),r.selectedState(),r.block(),r.blockState(),r.selectedX(),r.selectedY(),r.selectedZ(),
                r.collisionMotionBeforeX(),r.collisionMotionBeforeY(),r.collisionMotionBeforeZ(),r.appliedX(),r.appliedY(),
                r.appliedZ(),r.responseX(),r.responseY(),r.responseZ(),r.geometry(),vertical);
    }
    private static CurrentFrameCollisionScope.NativeCollisionTrace withVectors(
            CurrentFrameCollisionScope.NativeCollisionTrace r,MovementVector v) {
        return new CurrentFrameCollisionScope.NativeCollisionTrace(r.frameId(),r.substep(),r.substeps(),r.iteration(),
                r.selectedPos(),r.selectedState(),r.block(),r.blockState(),v.x(),v.y(),v.z(),
                r.collisionMotionBeforeX(),r.collisionMotionBeforeY(),r.collisionMotionBeforeZ(),v.x(),v.y(),v.z(),
                v.x(),v.y(),v.z(),r.geometry(),true);
    }
    private static CurrentFrameCollisionScope.WorldClipObservation mutatedClip(Vec3 input,Vec3 output,
            CurrentFrameCollisionScope.WorldClipObservation original) {
        return observe(input,output,original.positionBefore(),original.boundsBefore(),true);
    }
    private static CurrentFrameCollisionScope.WorldClipObservation partialClip(
            CurrentFrameCollisionScope.WorldClipObservation original) {
        return observe(original.input(),null,original.positionBefore(),original.boundsBefore(),false);
    }
    static CurrentFrameCollisionScope.WorldClipObservation observe(Vec3 input,Vec3 output,Vec3 position,
            AABB bounds,boolean complete) {
        var marker=new Marker(EntityType.MARKER,null);
        return CurrentFrameCollisionScope.withTrackedCharacterPose(marker,false,()->{
            CurrentFrameCollisionScope.recordVanillaWorldCollisionInput(marker,input,position,bounds);
            if(complete) CurrentFrameCollisionScope.recordVanillaWorldCollisionOutput(marker,output);
            return null;
        }).worldClipObservation();
    }
}
