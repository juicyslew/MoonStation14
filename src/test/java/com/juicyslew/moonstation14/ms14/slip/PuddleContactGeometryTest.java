package com.juicyslew.moonstation14.ms14.slip;

import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.api.math.LevelReusedVectors;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PuddleContactGeometryTest {
    private static final AABB BODY = new AABB(.2,.001,.2,.8,1.801,.8);
    private static final AABB SOURCE = new AABB(0,0,0,1,.0625,1);

    @Test void frameIntersectionRejectsUnsupportedScaleAsUnusablePose() {
        var pose = new Pose3d();
        assertTrue(com.juicyslew.moonstation14.ms14.shuttle.sable.FrameBoxIntersection.supportsPose(pose));
        pose.scale().set(2, 1, 1);
        assertFalse(com.juicyslew.moonstation14.ms14.shuttle.sable.FrameBoxIntersection.supportsPose(pose),
                "SAT does not claim coverage for scaled frame geometry");
    }

    @Test void sharedFrameBoxIntersectionHandlesWorldYawTiltDisjointAndTouchingBoxes() {
        var pose = new Pose3d();
        pose.orientation().rotationY(Math.PI / 4);
        var localShape = new AABB(0, 0, 0, 1, 1, 1);
        Vector3d center = pose.transformPosition(new Vector3d(.5, .5, .5));
        var overlapping = new AABB(center.x - .2, center.y - .2, center.z - .2,
                center.x + .2, center.y + .2, center.z + .2);
        assertTrue(com.juicyslew.moonstation14.ms14.shuttle.sable.FrameBoxIntersection
                .intersects(overlapping, localShape, pose), "yawed native shape intersects world-axis-aligned body");
        assertFalse(com.juicyslew.moonstation14.ms14.shuttle.sable.FrameBoxIntersection
                .intersects(new AABB(4, 4, 4, 5, 5, 5), localShape, pose), "disjoint shape does not collide");

        var tilted = new Pose3d();
        setSmallXTilt(tilted);
        Vector3d tiltedCenter = tilted.transformPosition(new Vector3d(.5, .5, .5));
        var tiltedBody = new AABB(tiltedCenter.x - .2, tiltedCenter.y - .2, tiltedCenter.z - .2,
                tiltedCenter.x + .2, tiltedCenter.y + .2, tiltedCenter.z + .2);
        assertTrue(com.juicyslew.moonstation14.ms14.shuttle.sable.FrameBoxIntersection
                .intersects(tiltedBody, localShape, tilted), "tilted native shape intersects actual world box");
        assertFalse(com.juicyslew.moonstation14.ms14.shuttle.sable.FrameBoxIntersection
                .intersects(new AABB(-.2, 1, -.2, .2, 1.2, .2), localShape, new Pose3d()),
                "mere face touching at the floor is not penetration");
    }

    @Test void growthVolumeExcludesProneFloorContactButStillRejectsLowCeiling() {
        var pose = new Pose3d();
        var floor = new AABB(0, 300, 0, 1, 301, 1);
        // A tiny native resting-point residue can make the full standing box overlap the
        // support block; only the newly occupied volume is relevant to posture growth.
        var standing = new AABB(.2, 300.999999, .2, .8, 302.8, .8);
        var proneTop = 301.6;
        var growth = new AABB(standing.minX, proneTop, standing.minZ,
                standing.maxX, standing.maxY, standing.maxZ);
        assertTrue(com.juicyslew.moonstation14.ms14.shuttle.sable.FrameBoxIntersection
                .intersects(standing, floor, pose), "full box records the resting floor residue");
        assertFalse(com.juicyslew.moonstation14.ms14.shuttle.sable.FrameBoxIntersection
                .intersects(growth, floor, pose), "growth volume excludes already occupied floor support");
        var ceiling = new AABB(0, 302, 0, 1, 303, 1);
        assertTrue(com.juicyslew.moonstation14.ms14.shuttle.sable.FrameBoxIntersection
                .intersects(growth, ceiling, pose), "growth volume still detects real overhead collision");
    }

    @Test void identityNativeContactHasFullProjectedCoverage() {
        var g=PuddleContactGeometry.capture(BODY,0,new Pose3d());
        var result=g.contact(SOURCE,BlockPos.ZERO);
        assertTrue(result.intersects());
        assertEquals(1,result.triggerRatio(),1e-10);
    }

    @Test void tiltedPoseIsSupportedAndCandidateBoundsAreOnlyBroadPhase() {
        var pose=new Pose3d();
        setSmallXTilt(pose);
        var scratch=new LevelReusedVectors();
        var body=new OrientedBoundingBox3d(new Vector3d(.5,.901,.5),new Vector3d(.6,1.8,.6),new Quaterniond(),scratch);
        var shape=new OrientedBoundingBox3d(new Vector3d(.5,.03125,.5),new Vector3d(1,.0625,1),new Quaterniond(pose.orientation()),scratch);
        Vector3d nativeMtv=new Vector3d();
        OrientedBoundingBox3d.sat(body,shape,nativeMtv);
        assertTrue(nativeMtv.lengthSquared()>0);
        var tilted=PuddleContactGeometry.capture(BODY,0,pose);
        assertTrue(tilted.contact(SOURCE,BlockPos.ZERO).intersects());

        var rotated=PuddleContactGeometry.capture(new AABB(1,0,1,1.6,1.8,1.6),Math.PI/4,new Pose3d());
        assertTrue(rotated.localCandidateBounds().intersects(new AABB(0,0,0,1,1,1)));
        assertFalse(rotated.contact(SOURCE,BlockPos.ZERO).intersects());
        assertEquals(0,rotated.contact(SOURCE,BlockPos.ZERO).triggerRatio());
    }

    @Test void partialFootprintRatioIsClippedToSourceTile() {
        var geometry=PuddleContactGeometry.capture(new AABB(.6,.001,.2,1.2,1.8,.8),0,new Pose3d());
        assertTrue(geometry.contact(SOURCE,BlockPos.ZERO).intersects());
        assertEquals(.18/.36,geometry.contact(SOURCE,BlockPos.ZERO).triggerRatio(),1e-9);
    }

    @Test void tinyPoseTiltAndAabbRoundingRemainBooleanContact() {
        var pose=new Pose3d();
        setSmallXTilt(pose);
        Quaterniond expected = new Quaterniond(pose.orientation());
        assertEquals(Math.sin(5e-6), expected.x, 1e-15);
        assertEquals(0, expected.y, 0);
        assertEquals(0, expected.z, 0);
        assertEquals(Math.cos(5e-6), expected.w, 1e-15);
        var perturbedBody=new AABB(.20000000000001,.001,.2,.80000000000001,1.801,.8);
        var geometry=PuddleContactGeometry.capture(perturbedBody,0,pose);
        pose.orientation().identity();
        var result=geometry.contact(SOURCE,BlockPos.ZERO);
        assertTrue(result.intersects(),"tiny tilt and sub-ulp-scale body offset must not turn overlap into zero-MTV noncontact");
        assertTrue(result.triggerRatio()>0);
        assertFalse(geometry.contact(new AABB(1.1,0,1.1,1.2,.0625,1.2),BlockPos.ZERO).intersects(),
                "overlapping broad-phase AABBs at a corner must not imply OBB contact");
        assertFalse(geometry.contact(new AABB(0,0,0,Double.POSITIVE_INFINITY,1,1),BlockPos.ZERO).intersects());
    }

    @Test void poseInverseTransformRoundTripPreservesOrientation() {
        var pose = new Pose3d();
        setSmallXTilt(pose);
        Quaterniond expected = new Quaterniond(pose.orientation());
        for (int i = 0; i < 12; i++) {
            Vector3d point = new Vector3d(.2 + i, 1.8, .4 - i);
            pose.transformPositionInverse(pose.transformPosition(new Vector3d(point)));
            assertQuaternionEquals(expected, pose.orientation(), "inverse round trip " + i);
        }
    }

    private static void setSmallXTilt(Pose3d pose) {
        double half = 5e-6;
        // Pinned JOML 1.10.5 Quaterniond.rotationX has incorrect component placement; set XYZW explicitly.
        pose.orientation().set(Math.sin(half), 0, 0, Math.cos(half));
    }

    private static void assertQuaternionEquals(Quaterniond expected, Quaterniond actual, String stage) {
        assertEquals(expected.x, actual.x, 1e-14, stage + " q.x");
        assertEquals(expected.y, actual.y, 1e-14, stage + " q.y");
        assertEquals(expected.z, actual.z, 1e-14, stage + " q.z");
        assertEquals(expected.w, actual.w, 1e-14, stage + " q.w");
    }

    @Test void translatedStorageShapeUsesAbsoluteBoundsAndCapturedPose() {
        var pose=new Pose3d();
        pose.position().set(100,300,200);
        pose.rotationPoint().set(20_481_032,12,20_481_032);
        pose.orientation().rotationY(.4);
        Vector3d feet=pose.transformPosition(new Vector3d(20_481_032.5,12.001,20_481_032.5));
        double yaw=.4;
        var geometry=PuddleContactGeometry.capture(new AABB(feet.x-.3,feet.y,feet.z-.3,
                feet.x+.3,feet.y+1.8,feet.z+.3),yaw,pose);
        pose.position().set(12_345,67,89);
        var result=geometry.contact(new AABB(20_481_032,12,20_481_032,
                20_481_033,12.0625,20_481_033),new BlockPos(20_481_032,12,20_481_032));
        assertTrue(result.intersects());
        assertEquals(1,result.triggerRatio(),1e-7);
    }

    @Test void scaledPoseAndNonfiniteBodyAreRefused() {
        var scaled=new Pose3d(); scaled.scale().set(2,1,1);
        assertThrows(IllegalArgumentException.class,()->PuddleContactGeometry.capture(BODY,0,scaled));
        assertThrows(IllegalArgumentException.class,()->PuddleContactGeometry.capture(
                new AABB(Double.NaN,0,0,1,1,1),0,new Pose3d()));
    }
}
