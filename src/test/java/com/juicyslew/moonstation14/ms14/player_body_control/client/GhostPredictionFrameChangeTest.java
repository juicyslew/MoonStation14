package com.juicyslew.moonstation14.ms14.player_body_control.client;

import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind;
import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads;
import com.juicyslew.moonstation14.ms14.player_body_control.prediction.GhostPredictionHistory;
import com.juicyslew.moonstation14.ms14.shuttle.sable.NativeFrameInterval;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GhostPredictionFrameChangeTest {
    private static final long EPOCH = 7;
    private static final int BODY = 23;
    private static final UUID FRAME = UUID.fromString("c7ae448e-f64a-4962-88ec-4f315b7d6a20");

    @Test
    void frameGeometryChangesReconcilePendingInputsWithoutFalseBarrier() {
        GhostPredictionHistory history = new GhostPredictionHistory(EPOCH, BODY, MobHarnessKind.CHARACTER);
        ClientFramePredictionState frame = new ClientFramePredictionState();
        var initialPose = pose(3000, 300, 2000, 20_000_000, 128, 20_000_000, .37);
        var actor = point(FRAME, initialPose);
        var beforePoses = NativeFrameInterval.PosePair.capture(initialPose, initialPose);
        frame.authorityProjected(actor, EPOCH, beforePoses);
        List<GhostControlPayloads.Intent> intents = record(history, 1, 2, 3, 4, 5);

        var rebased = history.reconcile(snapshot(1, 2, FRAME, 0, 0, 0));
        assertEquals(GhostPredictionHistory.SnapshotStatus.REPLAY, rebased.status());
        assertEquals(intents.subList(2, 5), rebased.replayFrames());
        assertTrue(CharacterMovementRebasePolicy.decide(0, 0, false, 0, 2, true, true)
                == CharacterMovementRebasePolicy.Decision.NORMAL);
        assertEquals(GhostPredictionHistory.SnapshotStatus.REJECTED,
                history.reconcile(snapshot(1, 2, FRAME, 0, 0, 0)).status(), "duplicate tick is rejected");
        assertEquals(GhostPredictionHistory.SnapshotStatus.REJECTED,
                history.reconcile(snapshot(0, 2, FRAME, 0, 0, 0)).status(), "stale tick is rejected");

        // Pivot is large while composed world translation remains modest, as in a COM rebake.
        var rebakedPose = rebase(initialPose, 73, -19, 41);
        var rebakedActor = point(FRAME, rebakedPose, actor.storagePoint());
        assertVector(actor.worldPoint(), rebakedActor.worldPoint(), 1e-5);
        assertVector(actor.storagePoint(), rebakedActor.storagePoint(), 0);
        var comRebake = NativeFrameInterval.PosePair.capture(rebakedPose, rebakedPose);
        frame.authorityProjected(rebakedActor, EPOCH, comRebake);
        assertFalse(frame.allowNativeCarrier(EPOCH, rebakedActor, comRebake), "authority projection consumes COM interval");
        var impactPose = pose(3008, 302, 1995, 20_000_000, 128, 20_000_000, .51);
        var impactActor = point(FRAME, impactPose);
        var impactInterval = NativeFrameInterval.PosePair.capture(rebakedPose, impactPose);
        assertTrue(frame.allowNativeCarrier(EPOCH, rebakedActor, impactInterval), "physical pose advance admitted once");
        assertFalse(frame.allowNativeCarrier(EPOCH, rebakedActor, impactInterval), "same physical interval not admitted twice");
        var impact = history.reconcile(snapshot(2, 3, FRAME, impactActor.storagePoint().x,
                impactActor.storagePoint().y, impactActor.storagePoint().z));
        assertEquals(List.of(intents.get(3), intents.get(4)), impact.replayFrames());
        frame.authorityProjected(impactActor, EPOCH, impactInterval);
        assertFalse(frame.allowNativeCarrier(EPOCH, impactActor, impactInterval), "authority commit consumes interval");
        assertEquals(GhostPredictionHistory.SnapshotStatus.REJECTED,
                history.reconcile(snapshot(2, 3, FRAME, 1, 0, 0)).status());

        UUID nextFrame = UUID.fromString("7a1b0874-e414-4088-9fe0-5d85522f24b9");
        var replacementRotation = new org.joml.Quaterniond().rotationY(-.28);
        var replacementStorageOffset = replacementRotation.transform(new org.joml.Vector3d(23, 4, -17));
        var replacementPose = pose(impactActor.worldPoint().x - replacementStorageOffset.x,
                impactActor.worldPoint().y - replacementStorageOffset.y,
                impactActor.worldPoint().z - replacementStorageOffset.z, 0, 0, 0, -.28);
        var replacementActor = point(nextFrame, replacementPose);
        assertVector(impactActor.worldPoint(), replacementActor.worldPoint(), 1e-5);
        assertFalse(frame.allowNativeCarrier(EPOCH, replacementActor, impactInterval), "replacement identity fails closed");
        var next = history.reconcile(snapshot(3, 4, nextFrame, replacementActor.storagePoint().x,
                replacementActor.storagePoint().y, replacementActor.storagePoint().z));
        assertEquals(List.of(intents.get(4)), next.replayFrames());
        var replacementInterval = NativeFrameInterval.PosePair.capture(replacementPose, replacementPose);
        frame.authorityProjected(replacementActor, EPOCH, replacementInterval);
        assertFalse(frame.allowNativeCarrier(EPOCH, replacementActor, replacementInterval));
        assertEquals(nextFrame, frame.velocityBasisFrame());
        assertEquals(GhostPredictionHistory.SnapshotStatus.REJECTED,
                history.reconcile(snapshot(4, 3, FRAME, 2, 0, 0)).status(), "old carrier identity cannot regain admission");
        assertEquals(1, history.pendingCount());
        assertEquals(GhostPredictionHistory.RecordResult.REJECTED, history.record(intent(EPOCH + 1, 6)));
    }

    private static List<GhostControlPayloads.Intent> record(GhostPredictionHistory history, long... seqs) {
        var result = new java.util.ArrayList<GhostControlPayloads.Intent>();
        for (long seq : seqs) { var input = intent(EPOCH, seq); history.record(input); result.add(input); }
        return List.copyOf(result);
    }
    private static GhostControlPayloads.Intent intent(long epoch, long seq) {
        return new GhostControlPayloads.Intent(epoch, seq, (short) 125, (short) -250, (byte) 1,
                GhostControlPayloads.BUTTON_JUMP, 30f, -5f);
    }
    private static GhostControlPayloads.Snapshot snapshot(long tick, long ack, UUID id, double x, double y, double z) {
        return new GhostControlPayloads.Snapshot(EPOCH, BODY, MobHarnessKind.CHARACTER, tick, ack,
                x, y, z, 0, 0, 0, 0, 0, false, 1, 1, false, 0,
                GhostControlPayloads.Snapshot.MovementMode.GROUNDED, Optional.empty(),
                Optional.of(new GhostControlPayloads.Snapshot.FrameState(id, 0, 0, 0)), 0);
    }
    private static dev.ryanhcode.sable.companion.math.Pose3d pose(double x, double y, double z,
            double px, double py, double pz, double yaw) {
        var pose = new dev.ryanhcode.sable.companion.math.Pose3d();
        pose.position().set(x, y, z); pose.rotationPoint().set(px, py, pz); pose.orientation().rotateY(yaw);
        return pose;
    }
    private static dev.ryanhcode.sable.companion.math.Pose3d rebase(
            dev.ryanhcode.sable.companion.math.Pose3d source, double x, double y, double z) {
        var result = new dev.ryanhcode.sable.companion.math.Pose3d(source);
        var rotated = source.orientation().transform(new org.joml.Vector3d(x, y, z));
        result.position().add(rotated.x, rotated.y, rotated.z); result.rotationPoint().add(x, y, z);
        return result;
    }
    private static com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.ActorPoint point(UUID id,
            dev.ryanhcode.sable.companion.math.Pose3d pose) {
        var storage = new org.joml.Vector3d(pose.rotationPoint()).add(23, 4, -17);
        return point(id, pose, new net.minecraft.world.phys.Vec3(storage.x, storage.y, storage.z));
    }
    private static com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.ActorPoint point(UUID id,
            dev.ryanhcode.sable.companion.math.Pose3d pose, net.minecraft.world.phys.Vec3 storagePoint) {
        var storage = new org.joml.Vector3d(storagePoint.x, storagePoint.y, storagePoint.z);
        var world = pose.transformPosition(new org.joml.Vector3d(storage));
        return new com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.ActorPoint(
                new net.minecraft.world.phys.Vec3(world.x, world.y, world.z),
                new net.minecraft.world.phys.Vec3(storage.x, storage.y, storage.z), id);
    }
    private static void assertVector(net.minecraft.world.phys.Vec3 a, net.minecraft.world.phys.Vec3 b, double epsilon) {
        assertEquals(a.x, b.x, epsilon); assertEquals(a.y, b.y, epsilon); assertEquals(a.z, b.z, epsilon);
    }
}
