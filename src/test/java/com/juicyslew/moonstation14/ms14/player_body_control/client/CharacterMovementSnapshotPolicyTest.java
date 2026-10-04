package com.juicyslew.moonstation14.ms14.player_body_control.client;

import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind;
import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads;
import com.juicyslew.moonstation14.ms14.player_body_control.prediction.GhostPredictionHistory;
import com.juicyslew.moonstation14.ms14.player_body_control.network.CharacterMovementModeResolver;
import com.juicyslew.moonstation14.ms14.disposal.containment.DisposalContainmentOwner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CharacterMovementSnapshotPolicyTest {
    @Test void serverModeMappingUsesExactOwnerStage() {
        assertEquals(GhostControlPayloads.Snapshot.MovementMode.GROUNDED,
                CharacterMovementModeResolver.fromExactOwner(null));
        assertEquals(GhostControlPayloads.Snapshot.MovementMode.UNIT,
                CharacterMovementModeResolver.fromExactOwner(owner(DisposalContainmentOwner.Stage.UNIT)));
        assertEquals(GhostControlPayloads.Snapshot.MovementMode.TRANSIT,
                CharacterMovementModeResolver.fromExactOwner(owner(DisposalContainmentOwner.Stage.TRANSIT)));
    }

    @Test void containedModesBarrierReplayAndOnlyGroundedCanResumeMotor() {
        var history = new GhostPredictionHistory(4, 10, MobHarnessKind.CHARACTER);
        assertEquals(GhostPredictionHistory.RecordResult.RECORDED, history.record(intent(1)));
        var transit = history.reconcile(snapshot(1, 0, GhostControlPayloads.Snapshot.MovementMode.TRANSIT));
        assertTrue(CharacterMovementSnapshotPolicy.requiresBarrier(GhostControlPayloads.Snapshot.MovementMode.TRANSIT));
        assertFalse(CharacterMovementSnapshotPolicy.allowsMotor(GhostControlPayloads.Snapshot.MovementMode.TRANSIT));
        if (CharacterMovementSnapshotPolicy.requiresBarrier(GhostControlPayloads.Snapshot.MovementMode.TRANSIT))
            history.onAuthorityBarrier();
        assertEquals(0, history.pendingCount(), "contained snapshot discards outstanding movement replay");
        assertFalse(CharacterMovementSnapshotPolicy.allowsReplay(GhostControlPayloads.Snapshot.MovementMode.TRANSIT,
                transit.status(), false));

        assertEquals(GhostPredictionHistory.RecordResult.RECORDED, history.record(intent(2)));
        var grounded = history.reconcile(snapshot(2, 1, GhostControlPayloads.Snapshot.MovementMode.GROUNDED));
        assertTrue(CharacterMovementSnapshotPolicy.allowsMotor(GhostControlPayloads.Snapshot.MovementMode.GROUNDED));
        assertTrue(CharacterMovementSnapshotPolicy.allowsReplay(GhostControlPayloads.Snapshot.MovementMode.GROUNDED,
                grounded.status(), false));
    }

    private static GhostControlPayloads.Intent intent(long sequence) {
        return new GhostControlPayloads.Intent(4, sequence, (short) 1, (short) 0, (byte) 0, 0, 0, 0);
    }

    private static DisposalContainmentOwner owner(DisposalContainmentOwner.Stage stage) {
        return new DisposalContainmentOwner() {
            public java.util.UUID identity() { return new java.util.UUID(0, 1); }
            public net.minecraft.server.level.ServerLevel level() { return null; }
            public net.minecraft.world.phys.Vec3 position() { return net.minecraft.world.phys.Vec3.ZERO; }
            public Stage stage() { return stage; }
            public boolean isValid() { return true; }
            public boolean tryEscape(net.minecraft.world.entity.LivingEntity body) { return false; }
        };
    }

    private static GhostControlPayloads.Snapshot snapshot(long tick, long acknowledged,
                                                           GhostControlPayloads.Snapshot.MovementMode mode) {
        return new GhostControlPayloads.Snapshot(4, 10, MobHarnessKind.CHARACTER, tick, acknowledged,
                1, 2, 3, 0, 0, 0, 0, 0, true, 1, 1, false, mode);
    }
}
