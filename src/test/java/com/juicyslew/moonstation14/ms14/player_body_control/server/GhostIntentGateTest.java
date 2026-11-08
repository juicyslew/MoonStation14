package com.juicyslew.moonstation14.ms14.player_body_control.server;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GhostIntentGateTest {
    @Test
    void drainingDoesNotClaimApplicationUntilExplicitlyMarked() {
        GhostIntentGate<Long> gate = new GhostIntentGate<>();
        for (long sequence = 1; sequence <= 10; sequence++) assertTrue(gate.offer(sequence, sequence, sequence));
        assertFalse(gate.offer(10, 10L));
        assertFalse(gate.offer(9, 9L));
        assertEquals(java.util.stream.LongStream.rangeClosed(1, 10).boxed().toList(), gate.takeBatch(20));
        assertEquals(0, gate.acknowledgedAppliedSequence());
        assertEquals(0, gate.settledThroughSequence());
        gate.markApplied(10);
        assertEquals(10, gate.acknowledgedAppliedSequence());
        assertEquals(10, gate.settledThroughSequence());
        gate.markApplied(4);
        assertEquals(10, gate.acknowledgedAppliedSequence());
        assertEquals(List.of(), gate.takeBatch(20));
        assertEquals(List.of(), gate.takeBatch(19));
    }

    @Test
    void emptyBatchConsumesTickAndLaterOfferWaitsForNextTick() {
        GhostIntentGate<String> gate = new GhostIntentGate<>();
        assertEquals(List.of(), gate.takeBatch(50));
        assertTrue(gate.offer(1, "late"));
        assertEquals(List.of(), gate.takeBatch(50));
        assertEquals(List.of("late"), gate.takeBatch(51));
    }

    @Test
    void discardSettlesButNeverClaimsSkippedFramesWereApplied() {
        GhostIntentGate<String> gate = new GhostIntentGate<>();
        assertTrue(gate.offerQueued(1, "forward", 2));
        assertTrue(gate.offerQueued(2, "release", 2));
        assertEquals(2, gate.discardQueuedThroughSequence());
        assertEquals(0, gate.acknowledgedAppliedSequence());
        assertEquals(2, gate.settledThroughSequence());
        assertFalse(gate.offerQueued(2, "duplicate", 2));
        assertTrue(gate.offerQueued(3, "fresh", 2));
        assertEquals("fresh", gate.takeQueued());
        assertEquals(0, gate.acknowledgedAppliedSequence());
        assertEquals(2, gate.settledThroughSequence());
        gate.settleSkippedThroughSequence(3);
        assertEquals(0, gate.acknowledgedAppliedSequence());
        assertEquals(3, gate.settledThroughSequence());
    }

    @Test
    void boundedOverflowDoesNotAdvanceSequenceAndExhaustionFailsClosed() {
        GhostIntentGate<String> gate = new GhostIntentGate<>();
        for (int i = 1; i <= GhostIntentGate.CAPACITY; i++) assertTrue(gate.offer(i, "frame"));
        assertFalse(gate.offer(GhostIntentGate.CAPACITY + 1L, "overflow"));
        assertFalse(gate.exhausted());
        assertEquals(GhostIntentGate.CAPACITY, gate.takeBatch(1).size());
        assertTrue(gate.offer(GhostIntentGate.CAPACITY + 1L, "fresh"));
        gate.reset();
        assertFalse(gate.offer(Long.MAX_VALUE, "exhaust"));
        assertTrue(gate.exhausted());
        assertFalse(gate.offer(1, "wrap"));
    }
}
