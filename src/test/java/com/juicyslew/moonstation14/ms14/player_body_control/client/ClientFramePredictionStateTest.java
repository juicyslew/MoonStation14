package com.juicyslew.moonstation14.ms14.player_body_control.client;

import com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind;
import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads;
import com.juicyslew.moonstation14.ms14.player_body_control.prediction.GhostPredictionHistory;
import com.juicyslew.moonstation14.ms14.shuttle.sable.NativeFrameInterval;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ClientFramePredictionStateTest {
    @Test void snapshotBasisOverridesStaleSdkIdentityAndIntervalIsConsumedOnce() {
        var state = new ClientFramePredictionState();
        UUID authorityFrame = UUID.randomUUID(), staleNativeFrame = UUID.randomUUID();
        var value = identityPose(4);
        var poses = new NativeFrameInterval.PosePair(value, value);
        var authority = new LoadedFrameRayQuery.ActorPoint(new Vec3(100, 20, 100), new Vec3(2, 3, 4), authorityFrame);
        var staleNative = new LoadedFrameRayQuery.ActorPoint(new Vec3(100, 20, 100), new Vec3(8, 3, 9), staleNativeFrame);

        state.authorityProjected(authority, 7, poses);
        assertEquals(authorityFrame, state.velocityBasisFrame());
        assertNotEquals(staleNative.frameId(), state.velocityBasisFrame(), "SDK's stale A identity cannot replace snapshot B basis");
        assertFalse(state.interval().consumeIfChanged(7, authorityFrame, poses), "snapshot projection consumed this interval");
        assertFalse(state.interval().consumeIfChanged(7, authorityFrame, poses), "same interval is already consumed");
        assertFalse(state.interval().consumeIfChanged(7, authorityFrame, poses), "same interval remains one-shot");
        assertFalse(state.allowNativeCarrier(7, staleNative, poses), "stale native A cannot authorize B inheritedMotion");
        assertFalse(state.allowNativeCarrier(7, authority, poses), "authority interval was already consumed");

        var changed = new NativeFrameInterval.PosePair(identityPose(5), value);
        assertTrue(state.allowNativeCarrier(7, authority, changed), "matching replay/live interval admits fresh stock carrier once");
        assertFalse(state.allowNativeCarrier(7, authority, changed), "changed interval remains one-shot");
        state.ordinaryWorldAuthority(new LoadedFrameRayQuery.ActorPoint(new Vec3(100, 20, 100),
                new Vec3(100, 20, 100), null));
        assertNull(state.velocityBasisFrame(), "world authority explicitly replaces stale SDK frame basis");
        assertTrue(state.allowNativeCarrier(7,
                new LoadedFrameRayQuery.ActorPoint(Vec3.ZERO, Vec3.ZERO, null), null), "aligned ordinary-world basis may admit stock frame entry");
    }

    @Test void unknownFramePausesAndSkippedInputResumesOnlyOnAnewAcceptedSnapshot() {
        var state = new ClientFramePredictionState();
        var history = new GhostPredictionHistory(3, 8, MobHarnessKind.CHARACTER);
        var one = intent(3, 1); var two = intent(3, 2); var three = intent(3, 3);
        assertEquals(GhostPredictionHistory.RecordResult.RECORDED, history.record(one));
        state.pause(); history.onAuthorityBarrier();
        assertTrue(state.paused());
        assertEquals(GhostPredictionHistory.RecordResult.RECORDED, history.skip(two));
        assertEquals(0, history.pendingCount());
        assertEquals(GhostPredictionHistory.SnapshotStatus.REPLAY,
                history.reconcile(snapshot(3, 8, 2, 2)).status());
        state.ordinaryWorldAuthority(new LoadedFrameRayQuery.ActorPoint(Vec3.ZERO, Vec3.ZERO, null));
        assertFalse(state.paused());
        assertEquals(GhostPredictionHistory.RecordResult.RECORDED, history.record(three));
        var resumed = history.reconcile(snapshot(3, 8, 3, 2));
        assertEquals(GhostPredictionHistory.SnapshotStatus.REPLAY, resumed.status());
        assertEquals(java.util.List.of(three), resumed.replayFrames());
        state.reset();
        assertNull(state.velocityBasis());
        assertFalse(state.paused());
    }

    private static GhostControlPayloads.Intent intent(long epoch, long seq) {
        return new GhostControlPayloads.Intent(epoch, seq, (short) 0, (short) 0, (byte) 0, 0, 0, 0);
    }
    private static GhostControlPayloads.Snapshot snapshot(long epoch, int id, long tick, long ack) {
        return new GhostControlPayloads.Snapshot(epoch, id, MobHarnessKind.CHARACTER, tick, ack,
                0, 0, 0, 0, 0, 0, 0, 0, false, 1, 1, false,
                GhostControlPayloads.Snapshot.MovementMode.GROUNDED);
    }
    private static NativeFrameInterval.PoseValue identityPose(double tx) {
        double[] values = new double[16]; values[0] = values[5] = values[10] = values[15] = 1; values[12] = tx;
        return new NativeFrameInterval.PoseValue(values);
    }
}
