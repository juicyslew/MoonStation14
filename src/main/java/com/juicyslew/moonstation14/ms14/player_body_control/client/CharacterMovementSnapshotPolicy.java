package com.juicyslew.moonstation14.ms14.player_body_control.client;

import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads;
import com.juicyslew.moonstation14.ms14.player_body_control.prediction.GhostPredictionHistory;

/** Explicit authority-mode policy for CHARACTER movement prediction. */
public final class CharacterMovementSnapshotPolicy {
    private CharacterMovementSnapshotPolicy() { }

    public static boolean allowsMotor(GhostControlPayloads.Snapshot.MovementMode mode) {
        return mode == GhostControlPayloads.Snapshot.MovementMode.GROUNDED;
    }

    public static boolean requiresBarrier(GhostControlPayloads.Snapshot.MovementMode mode) {
        return !allowsMotor(mode);
    }

    public static boolean allowsReplay(GhostControlPayloads.Snapshot.MovementMode mode,
                                       GhostPredictionHistory.SnapshotStatus result, boolean predictionStopped) {
        return allowsMotor(mode) && result == GhostPredictionHistory.SnapshotStatus.REPLAY && !predictionStopped;
    }
}

