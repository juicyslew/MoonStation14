package com.juicyslew.moonstation14.ms14.player_body_control.client;

import com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery;
import com.juicyslew.moonstation14.ms14.shuttle.sable.NativeFrameInterval;
import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ClientMassRebasePredictionTest {
    @Test void consumedNativeIntervalStaysConsumedAcrossEquivalentLargeCoordinateComRebase() {
        UUID frame = UUID.randomUUID();
        var before = pose(3_000, 300, 2_000, 20_000_000, -4_000_000, 30_000_000, .71);
        var last = pose(3_001, 300, 2_000, 20_000_000, -4_000_000, 30_000_000, .71);
        var initial = NativeFrameInterval.PosePair.capture(last, before);
        var state = new ClientFramePredictionState();
        var authority = point(frame, before);
        state.authorityProjected(authority, 11, initial);
        assertFalse(state.allowNativeCarrier(11, authority, initial));

        // SDK COM rebasing: position += rotation * delta; rotation point += delta.
        var delta = new Vec3(73, -19, 41);
        var rebasedLast = rebase(last, delta);
        var rebasedLogical = rebase(before, delta);
        Vec3 sample = new Vec3(20_000_230, -4_000_042, 30_000_117);
        assertVector(before.transformPosition(sample), rebasedLogical.transformPosition(sample), 1e-6);
        assertVector(last.transformPosition(sample), rebasedLast.transformPosition(sample), 1e-6);

        var rebased = NativeFrameInterval.PosePair.capture(rebasedLast, rebasedLogical);
        boolean carrierResult = state.allowNativeCarrier(11, authority, rebased);
        assertFalse(carrierResult,
                () -> "equivalent COM pair re-admitted; beforeLast=" + java.util.Arrays.toString(initial.last().matrix())
                        + " afterLast=" + java.util.Arrays.toString(rebased.last().matrix())
                        + " lastDiff=" + java.util.Arrays.toString(matrixDiff(initial.last().matrix(), rebased.last().matrix()))
                        + " beforeLogical=" + java.util.Arrays.toString(initial.logical().matrix())
                        + " afterLogical=" + java.util.Arrays.toString(rebased.logical().matrix())
                        + " logicalDiff=" + java.util.Arrays.toString(matrixDiff(initial.logical().matrix(), rebased.logical().matrix()))
                        + " allowNativeCarrier=" + carrierResult);
        assertFalse(state.interval().consumeIfChanged(11, frame, rebased));
    }

    @Test void equivalentRebasesDoNotReadmitButAccumulatedMotionAndRotationDo() {
        UUID frame = UUID.randomUUID();
        var base = pose(3_000, 300, 2_000, 20_000_000, -4_000_000, 30_000_000, .71);
        var initial = NativeFrameInterval.PosePair.capture(base, base);
        var state = new ClientFramePredictionState();
        var authority = point(frame, base);
        state.authorityProjected(authority, 1, initial);
        assertFalse(state.allowNativeCarrier(1, authority, initial));
        for (int i = 0; i < 12; i++) {
            var rebased = rebase(base, new Vec3(i % 2 == 0 ? 73 : -73, 0, i % 2 == 0 ? 41 : -41));
            var same = NativeFrameInterval.PosePair.capture(rebased, rebased);
            assertFalse(state.allowNativeCarrier(1, authority, same), "COM-only rebake remains consumed");
        }
        // Incremental translation is always measured from the consumed pose, not from the
        // last equivalent floating-point representation.
        var moved = pose(3_000.01, 300, 2_000, 20_000_000, -4_000_000, 30_000_000, .71);
        var interval = NativeFrameInterval.PosePair.capture(base, moved);
        assertTrue(state.allowNativeCarrier(1, authority, interval));
        assertFalse(state.allowNativeCarrier(1, authority, interval));
        var rotated = pose(3_000, 300, 2_000, 20_000_000, -4_000_000, 30_000_000, .71 + 1e-7);
        var rotationInterval = NativeFrameInterval.PosePair.capture(base, rotated);
        assertTrue(state.allowNativeCarrier(1, authority, rotationInterval));
    }

    @Test void cancellationNearZeroTranslationUsesPoseOperandScaleAfterComRebase() {
        UUID frame = UUID.randomUUID();
        var before = cancellationPose(45.0);
        var initial = NativeFrameInterval.PosePair.capture(before, before);
        var state = new ClientFramePredictionState();
        var authority = point(frame, before);
        state.authorityProjected(authority, 9, initial);
        assertFalse(state.allowNativeCarrier(9, authority, initial));

        var delta = new Vec3(113, -71, 29);
        var rebasedPose = rebase(before, delta);
        Vec3 storage = new Vec3(19_999_977, 20_000_031, -13);
        assertVector(before.transformPosition(storage), rebasedPose.transformPosition(storage), 1e-6);
        var rebased = NativeFrameInterval.PosePair.capture(rebasedPose, rebasedPose);
        double[] originalMatrix = initial.logical().matrix();
        double[] rebasedMatrix = rebased.logical().matrix();
        assertTrue(Math.abs(originalMatrix[14] - rebasedMatrix[14]) > 8 * Math.ulp(Math.max(Math.abs(originalMatrix[14]), Math.abs(rebasedMatrix[14]))),
                () -> "fixture exercises translation cancellation, not result-scaled ULPs; before="
                        + java.util.Arrays.toString(originalMatrix) + " after=" + java.util.Arrays.toString(rebasedMatrix)
                        + " translationDiff=" + (rebasedMatrix[14] - originalMatrix[14]));
        assertFalse(state.allowNativeCarrier(9, authority, rebased), "COM-only cancellation-roundoff cannot readmit carrier");

        var moved = new Pose3d(rebasedPose);
        moved.position().x += .01;
        var physical = NativeFrameInterval.PosePair.capture(rebasedPose, moved);
        assertTrue(state.allowNativeCarrier(9, authority, physical), "real physical interval above arithmetic bound is admitted");
        assertFalse(state.allowNativeCarrier(9, authority, physical), "physical interval is admitted once");

        var incrementalState = new ClientFramePredictionState();
        incrementalState.authorityProjected(authority, 10, initial);
        var accumulating = new Pose3d(before);
        boolean admitted = false;
        for (int i = 0; i < 40; i++) {
            accumulating.position().x += 2e-9;
            var step = NativeFrameInterval.PosePair.capture(before, accumulating);
            if (incrementalState.allowNativeCarrier(10, authority, step)) { admitted = true; break; }
        }
        assertTrue(admitted, "sub-bound real increments accumulate against consumed baseline and eventually admit");
    }

    private static Pose3d cancellationPose(double yaw) {
        var pose = new Pose3d();
        pose.orientation().rotateY(yaw);
        pose.rotationPoint().set(20_000_000, 20_000_000, -20_000_000);
        var rotatedPivot = pose.orientation().transform(new org.joml.Vector3d(pose.rotationPoint()));
        pose.position().set(rotatedPivot.x, rotatedPivot.y, rotatedPivot.z + 300);
        return pose;
    }

    @Test void changedPhysicalIntervalAndIdentityOrEpochRemainOneShot() {
        var state = new ClientFramePredictionState();
        UUID frame = UUID.randomUUID();
        var firstPose = pose(3_000, 300, 2_000, 20_000_000, 0, 20_000_000, .37);
        var actor = point(frame, firstPose);
        var first = NativeFrameInterval.PosePair.capture(firstPose, firstPose);
        state.authorityProjected(actor, 4, first);

        for (int i = 1; i <= 5; i++) {
            var moved = pose(3_000 + i * 2, 300 + i, 2_000 - i, 20_000_000, 0, 20_000_000, .37 + i * .08);
            var interval = NativeFrameInterval.PosePair.capture(firstPose, moved);
            assertTrue(state.allowNativeCarrier(4, actor, interval), "new physical interval " + i);
            assertFalse(state.allowNativeCarrier(4, actor, interval), "same interval cannot carry twice");
            assertFalse(state.allowNativeCarrier(4, actor, interval), "replay remains own-motion-only");
            firstPose = moved;
        }
        var terminal = NativeFrameInterval.PosePair.capture(firstPose, firstPose);
        UUID replacement = UUID.randomUUID();
        var replacementActor = point(replacement, firstPose);
        assertFalse(state.allowNativeCarrier(4, replacementActor, terminal), "identity change fails closed before authority projection");
        state.authorityProjected(replacementActor, 4, terminal);
        assertFalse(state.allowNativeCarrier(4, replacementActor, terminal), "authority projection consumes its interval");
        var nextPose = pose(3_012, 306, 1_994, 20_000_000, 0, 20_000_000, .93);
        var next = NativeFrameInterval.PosePair.capture(firstPose, nextPose);
        assertTrue(state.allowNativeCarrier(4, replacementActor, next), "new basis admits next physical interval");
        assertFalse(state.allowNativeCarrier(4, replacementActor, next), "new basis interval remains one-shot");
        state.authorityProjected(replacementActor, 5, next);
        assertFalse(state.allowNativeCarrier(5, replacementActor, next), "epoch projection consumes its interval");
        var afterEpoch = NativeFrameInterval.PosePair.capture(nextPose,
                pose(3_014, 307, 1_993, 20_000_000, 0, 20_000_000, 1.01));
        assertTrue(state.allowNativeCarrier(5, replacementActor, afterEpoch), "epoch admits next physical interval");
        assertFalse(state.allowNativeCarrier(5, replacementActor, afterEpoch), "epoch interval remains one-shot");
        state.reset();
        assertNull(state.velocityBasis());
    }

    private static Pose3d pose(double x, double y, double z, double pivotX, double pivotY, double pivotZ, double yaw) {
        var pose = new Pose3d();
        pose.position().set(x, y, z);
        pose.rotationPoint().set(pivotX, pivotY, pivotZ);
        pose.orientation().rotateY(yaw);
        return pose;
    }

    private static Pose3d rebase(Pose3d source, Vec3 delta) {
        var result = new Pose3d(source);
        var rotated = source.orientation().transform(new org.joml.Vector3d(delta.x, delta.y, delta.z));
        result.position().add(rotated.x, rotated.y, rotated.z);
        result.rotationPoint().add(delta.x, delta.y, delta.z);
        return result;
    }

    private static LoadedFrameRayQuery.ActorPoint point(UUID frame, Pose3d pose) {
        var storage = new org.joml.Vector3d(pose.rotationPoint()).add(23, 4, -17);
        var world = pose.transformPosition(new org.joml.Vector3d(storage));
        return new LoadedFrameRayQuery.ActorPoint(new Vec3(world.x, world.y, world.z),
                new Vec3(storage.x, storage.y, storage.z), frame);
    }

    private static double[] matrixDiff(double[] before, double[] after) {
        double[] diff = new double[before.length];
        for (int i = 0; i < diff.length; i++) diff[i] = after[i] - before[i];
        return diff;
    }

    private static void assertVector(Vec3 expected, Vec3 actual, double epsilon) {
        assertEquals(expected.x, actual.x, epsilon);
        assertEquals(expected.y, actual.y, epsilon);
        assertEquals(expected.z, actual.z, epsilon);
    }
}
