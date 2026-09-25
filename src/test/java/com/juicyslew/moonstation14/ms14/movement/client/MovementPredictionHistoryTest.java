package com.juicyslew.moonstation14.ms14.movement.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovementPredictionHistoryTest {
    private static final double SPRINT_TICK = 4.5d / 20d;

    @Test
    void acknowledgementPrunesAckedSamplesAndProjectsPendingDisplacement() {
        MovementPredictionHistory history = new MovementPredictionHistory();
        history.record(1, position(1d), true);
        history.record(2, position(2d), true);
        history.record(3, position(3d), true);

        MovementPredictionHistory.Acknowledgement ack = history.acknowledge(2);

        assertEquals(2L, ack.predictedEnd().sequence());
        assertTrue(ack.hasPending());
        assertEquals(1, history.size());
        assertEquals(position(3.05d), MovementPredictionHistory.project(position(2.05d), position(3d),
                true, ack.predictedEnd(), true, ack.hasPending(), SPRINT_TICK));
    }

    @Test
    void staleAndMissingAcknowledgementsDoNotInventPredictionAndSequenceDoesNotWrap() {
        MovementPredictionHistory history = new MovementPredictionHistory();
        history.record(Long.MAX_VALUE, position(1d), true);
        assertThrows(IllegalArgumentException.class, () -> history.record(Long.MAX_VALUE, position(2d), true));
        assertEquals(Long.MAX_VALUE, history.acknowledge(Long.MAX_VALUE).predictedEnd().sequence());
        assertNull(history.acknowledge(Long.MAX_VALUE).predictedEnd());
        assertThrows(IllegalArgumentException.class, () -> history.record(1, position(0d), true));
    }

    @Test
    void historyMemoryIsBoundedAndOverflowDropsOldestAcknowledgement() {
        MovementPredictionHistory history = new MovementPredictionHistory();
        for (long sequence = 1; sequence <= MovementPredictionHistory.CAPACITY + 1L; sequence++)
            history.record(sequence, position(sequence), true);

        assertEquals(MovementPredictionHistory.CAPACITY, history.size());
        assertNull(history.acknowledge(1).predictedEnd());
        assertEquals(MovementPredictionHistory.CAPACITY, history.size());
    }

    @Test
    void oneSprintTickCorrectionIsAllowedButLargeCorrectionDefersToAuthority() {
        MovementPredictionHistory.Entry ack = new MovementPredictionHistory.Entry(4, position(10d), true);

        MovementPredictionHistory.Position oneStep = MovementPredictionHistory.project(position(10.225d),
                position(10.4d), true, ack, true, true, SPRINT_TICK);
        assertEquals(10.625d, oneStep.x(), 1e-12);

        assertNull(MovementPredictionHistory.project(position(10.226d), position(10.4d), true,
                ack, true, true, SPRINT_TICK));
        assertNull(MovementPredictionHistory.project(position(10.01d), position(10.4d), false,
                ack, true, true, SPRINT_TICK));
    }

    private static MovementPredictionHistory.Position position(double x) {
        return new MovementPredictionHistory.Position(x, 0d, 0d);
    }
}
