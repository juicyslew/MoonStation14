package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.ms14.doors.NativeAirlockContactAccess;

import dev.ryanhcode.sable.api.math.LevelReusedVectors;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NativeCollisionGeometryTest {
    // R47 result, line 1376: bound raw 128-byte record SHA-1
    // fc98215d016f9215f9ad64e597c0e7b9310c9c732213b7d21804e8527e4b24b371128.
    // These arithmetic guards do not reproduce live Sable timing/solver/native continuity,
    // prove correct hydration phase, or attribute the entire final 129.545 displacement.
    private static final double[] R47_MATRIX = {
            0.9999999999961735,9.900980193419963E-7,2.5832514031735917E-6,0,
            -9.90101366451571E-7,0.9999999999986707,1.295695455064145E-6,0,
            -2.5832501203046534E-6,-1.295698012739931E-6,0.9999999999958242,0,
            -2.047862859205548E7,180.5277342322554,-2.0479384407719307E7,1};

    @Test
    void collisionSelectionIdentityIncludesIteration() {
        var components = NativeAirlockContactAccess.Selection.class.getRecordComponents();
        assertNotNull(components);
        assertTrue(java.util.Arrays.stream(components).anyMatch(component -> component.getName().equals("iteration")));
    }

    @Test
    void snapshotCopiesBorrowedSdkGeometryAndPoseData() {
        var sink = new LevelReusedVectors();
        var obb = new OrientedBoundingBox3d(new Vector3d(2, 3, 4), new Vector3d(.5, 1.8, .5),
                new Quaterniond().rotationY(.25), sink);
        var pose = new Pose3d();
        pose.position().set(10, 20, 30);
        pose.orientation().rotationY(.5);
        var storageBounds = new BoundingBox3d(1, 2, 3, 4, 5, 6);
        var maxMtv = new Vector3d(.01, 0, 0);
        var collisionMotion = new Vector3d(.2, -.1, .3);

        var snapshot = NativeCollisionGeometry.capture(obb, pose, storageBounds,
                maxMtv, collisionMotion, 2, 5);
        assertNotNull(snapshot);
        var capturedPose = snapshot.subLevelPose();
        double[] capturedMatrix = capturedPose.matrix();
        var capturedOrientation = snapshot.entityBoundsOrientation();

        obb.getPosition().set(99, 99, 99);
        obb.getDimensions().set(99, 99, 99);
        obb.getOrientation().identity();
        pose.position().set(99, 99, 99);
        pose.orientation().identity();
        storageBounds.set(90, 90, 90, 99, 99, 99);
        maxMtv.set(99, 99, 99);
        collisionMotion.set(99, 99, 99);
        capturedMatrix[0] = -123;

        assertEquals(new com.juicyslew.moonstation14.ms14.movement.MovementVector(2, 3, 4),
                snapshot.entityBoundsCenter());
        assertEquals(new com.juicyslew.moonstation14.ms14.movement.MovementVector(.5, 1.8, .5),
                snapshot.entityBoundsDimensions());
        assertEquals(new com.juicyslew.moonstation14.ms14.movement.MovementVector(.01, 0, 0), snapshot.maxMtv());
        assertEquals(new com.juicyslew.moonstation14.ms14.movement.MovementVector(.2, -.1, .3),
                snapshot.collisionMotion());
        assertEquals(Math.sin(.125), capturedOrientation.y(), 1e-12);
        assertEquals(1, snapshot.selectedStorageBounds().minX);
        assertEquals(6, snapshot.selectedStorageBounds().maxZ);
        assertArrayEquals(capturedPose.matrix(), snapshot.subLevelPose().matrix());
        assertTrue(snapshot.toString().contains("subLevelPose=["));
    }

    @Test
    void invalidBorrowedGeometryOmitsOnlyDiagnosticSnapshot() {
        var sink = new LevelReusedVectors();
        var obb = new OrientedBoundingBox3d(new Vector3d(), new Vector3d(1, 1, 1), new Quaterniond(), sink);
        var snapshot = NativeCollisionGeometry.capture(obb, new Pose3d(),
                new BoundingBox3d(), new Vector3d(Double.NaN, 0, 0), new Vector3d(), 0, 1);
        assertNull(snapshot);
    }

    @Test
    void nonFiniteBorrowedOrientationOmitsDiagnosticSnapshot() {
        var sink = new LevelReusedVectors();
        var orientation = new Quaterniond(Double.NaN, 0, 0, 1);
        var obb = new OrientedBoundingBox3d(new Vector3d(), new Vector3d(1, 1, 1), orientation, sink);
        assertNull(NativeCollisionGeometry.capture(obb, new Pose3d(), new BoundingBox3d(),
                new Vector3d(), new Vector3d(), 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new NativeCollisionGeometry.QuaternionValue(0, 0, Double.POSITIVE_INFINITY, 1));
    }

    @Test
    void invalidDimensionsAndStorageBoundsAreRejected() {
        var sink = new LevelReusedVectors();
        var negativeDimensions = new OrientedBoundingBox3d(new Vector3d(), new Vector3d(-1, 1, 1),
                new Quaterniond(), sink);
        assertNull(NativeCollisionGeometry.capture(negativeDimensions, new Pose3d(), new BoundingBox3d(),
                new Vector3d(), new Vector3d(), 0, 1));
        var body = new OrientedBoundingBox3d(new Vector3d(), new Vector3d(1, 1, 1), new Quaterniond(), sink);
        var v = new com.juicyslew.moonstation14.ms14.movement.MovementVector(0, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> new NativeCollisionGeometry(v, v,
                new NativeCollisionGeometry.QuaternionValue(0, 0, 0, 1), NativeFrameInterval.PoseValue.capture(new Pose3d()),
                new net.minecraft.world.phys.AABB(Double.NaN, 0, 0, 1, 1, 1), v, v, 0, 1));
    }

    @Test
    void canonicalSnapshotRejectsNullComponents() {
        var v = new com.juicyslew.moonstation14.ms14.movement.MovementVector(0, 0, 0);
        assertThrows(NullPointerException.class, () -> new NativeCollisionGeometry(v, v, null,
                NativeFrameInterval.PoseValue.capture(new Pose3d()), new net.minecraft.world.phys.AABB(0, 0, 0, 1, 1, 1), v, v, 0, 1));
    }

    @Test
    void r47RecordedPoseAndStorageBoundsProjectionAreConsistent() {
        double[] latest = R47_MATRIX.clone();
        latest[13] = 181.02433457405232;
        var anchor = new org.joml.Vector3d(20481034.5, 129, 20481034.5);
        var originalPose = new NativeFrameInterval.PoseValue(R47_MATRIX);
        var latestPose = new NativeFrameInterval.PoseValue(latest);
        assertEquals(303.26873022410103, project(originalPose, anchor).y, 1e-8);
        assertEquals(303.76533056589795, project(latestPose, anchor).y, 1e-8);
        assertEquals(20481034.5, anchor.x, 0);
        assertEquals(129, anchor.y, 0);
        assertEquals(20481034.5, anchor.z, 0);
        assertEquals(0.4966003417969205, project(latestPose, anchor).y - project(originalPose, anchor).y, 1e-8);
        assertEquals(180.5277342322554, originalPose.matrix()[13]); // PoseValue retained immutable source data

        var geometry = new NativeCollisionGeometry(new com.juicyslew.moonstation14.ms14.movement.MovementVector(
                2353.000103592123,304.96566463658735,1703.0000234354807),
                new com.juicyslew.moonstation14.ms14.movement.MovementVector(.6000000238418579,1.7999999523162842,.6000000238418579),
                new NativeCollisionGeometry.QuaternionValue(0,-1.2916253808714967E-6,0,.9999999999991658), originalPose,
                new net.minecraft.world.phys.AABB(20481034,131,20481034,20481035,132,20481035),
                new com.juicyslew.moonstation14.ms14.movement.MovementVector(5.910262328263807E-7,-.5969350743790778,-7.734460628423766E-7),
                new com.juicyslew.moonstation14.ms14.movement.MovementVector(0,-.030732800000000005,0),0,10);
        double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
        var b = geometry.selectedStorageBounds();
        for (double x : new double[]{b.minX,b.maxX}) for (double y : new double[]{b.minY,b.maxY})
            for (double z : new double[]{b.minZ,b.maxZ}) {
                double worldY = project(geometry.subLevelPose(), new org.joml.Vector3d(x,y,z)).y;
                lo = Math.min(lo, worldY); hi = Math.max(hi, worldY);
            }
        assertEquals(305.2687290812004, lo, 1e-8);
        assertEquals(306.2687313669951, hi, 1e-8);
        double entityTop = geometry.entityBoundsCenter().y() + geometry.entityBoundsDimensions().y() / 2;
        double overlap = entityTop - lo;
        assertEquals(.5969355315451139, overlap, 1e-6);
        assertEquals(-overlap, geometry.maxMtv().y(), 1e-6);
        assertEquals(.19999890478305815,
                lo + (latestPose.matrix()[13] - originalPose.matrix()[13])
                        - (303.76533056589795 + 1.7999999523162842), 1e-8);
    }

    @Test
    void r47FractionalCenterIsCompatibleWithCarrierContribution() {
        double before = 303.76533056589795, height = 1.7999999523162842;
        double inherited = .8276672363281818, ownRequest = -.07683200000000001, fraction = .4;
        double centerY = 304.96566463658735;
        assertEquals(centerY, before + height / 2 + fraction * (inherited + ownRequest), 1e-10);
        assertNotEquals(centerY, before + height / 2 + fraction * ownRequest, .01);
        assertEquals(.3310668945312727, fraction * inherited, 1e-10);
        assertEquals(.4966003417969205, .6 * inherited, 1e-10);
    }

    @Test
    void r47OwnRequestAndInheritedDisplacementReconcileSeparately() {
        double response = -.5969350743790778, request = -.07683200000000001;
        double measuredOwn = -.6737670743791;
        double inherited = .8276672363281818, before = 303.76533056589795, after = 303.91923072784704;
        assertEquals(measuredOwn, request + response, 1e-10);
        assertEquals(after - before, measuredOwn + inherited, 1e-10);
        assertNotEquals(measuredOwn, -.030732800000000005 + response, 1e-10);
    }

    private static org.joml.Vector3d project(NativeFrameInterval.PoseValue pose, org.joml.Vector3d point) {
        return new org.joml.Matrix4d().set(pose.matrix()).transformPosition(new org.joml.Vector3d(point));
    }
}
