package com.juicyslew.moonstation14.ms14.player_body_control.client;

import com.juicyslew.moonstation14.ms14.movement.MovementVector;
import com.juicyslew.moonstation14.ms14.player_body_control.prediction.GhostPredictionHistory;
import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CharacterPredictionFailurePolicyTest {
    @Test
    void postMoveRejectionIsNotRetriedAndDropsReplayHistory() {
        GhostPredictionHistory history = new GhostPredictionHistory(1, 7);
        history.record(new GhostControlPayloads.Intent(1, 1, (short) 0, (short) 0,
                (byte) 0, 0, 0f, 0f));
        AtomicInteger moves = new AtomicInteger();
        assertNull(CharacterPredictionFailurePolicy.run(() -> {
            moves.incrementAndGet();
            throw new IllegalArgumentException("collision resolution must be finite and bounded by requested displacement");
        }, history));
        assertEquals(1, moves.get());
        assertEquals(0, history.pendingCount());
        assertEquals(1, history.lastRecordedSequence());
    }

    @Test
    void unrelatedArgumentErrorsAreNotSwallowed() {
        GhostPredictionHistory history = new GhostPredictionHistory(1, 7);
        assertThrows(IllegalArgumentException.class, () -> CharacterPredictionFailurePolicy.run(() -> {
            throw new IllegalArgumentException("invalid character movement policy");
        }, history));
    }

    @Test
    void nonfiniteActualDisplacementAfterMoveStopsWithoutRetryAndDropsReplay() {
        GhostPredictionHistory history = new GhostPredictionHistory(1, 7);
        history.record(new GhostControlPayloads.Intent(1, 1, (short) 0, (short) 0,
                (byte) 0, 0, 0f, 0f));
        AtomicInteger moves = new AtomicInteger();
        assertNull(CharacterPredictionFailurePolicy.run(() -> {
            moves.incrementAndGet();
            return CharacterPredictionFailurePolicy.postMoveDisplacement(Double.NaN, 0d, 0d);
        }, history));
        assertEquals(1, moves.get());
        assertEquals(0, history.pendingCount());
        assertEquals(1, history.lastRecordedSequence());
    }

    @Test
    void preMoveNonfiniteVectorStillPropagatesWithoutClearingReplay() {
        GhostPredictionHistory history = new GhostPredictionHistory(1, 7);
        history.record(new GhostControlPayloads.Intent(1, 1, (short) 0, (short) 0,
                (byte) 0, 0, 0f, 0f));
        assertThrows(IllegalArgumentException.class, () -> CharacterPredictionFailurePolicy.run(
                () -> new MovementVector(Double.POSITIVE_INFINITY, 0d, 0d), history));
        assertEquals(1, history.pendingCount());
    }
}
