package com.juicyslew.moonstation14.ms14.player_body_control.server;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GhostIntentGateTest {
    @Test
    void drainsAllIncreasingBurstUpdatesInOrderAsOneTickBatch() {
        GhostIntentGate<Long> gate = new GhostIntentGate<>();
        for (long sequence = 1; sequence <= 10; sequence++) assertTrue(gate.offer(sequence, sequence));
        assertFalse(gate.offer(10, 10L));
        assertFalse(gate.offer(9, 9L));
        assertEquals(java.util.stream.LongStream.rangeClosed(1, 10).boxed().toList(), gate.takeBatch(20));
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
        assertEquals(List.of(), gate.takeBatch(51));
    }

    @Test
    void overflowDoesNotAdvanceSequenceAndResetClearsQueueTickGuardAndExhaustion() {
        GhostIntentGate<Integer> gate = new GhostIntentGate<>();
        for (int i = 1; i <= GhostIntentGate.CAPACITY; i++) assertTrue(gate.offer(i, i));
        assertFalse(gate.offer(GhostIntentGate.CAPACITY + 1L, 100));
        List<Integer> firstBatch = gate.takeBatch(1);
        assertEquals(GhostIntentGate.CAPACITY, firstBatch.size());
        assertEquals(1, firstBatch.getFirst());
        assertEquals(GhostIntentGate.CAPACITY, firstBatch.getLast());
        assertTrue(gate.offer(GhostIntentGate.CAPACITY + 1L, 65));
        assertFalse(gate.offer(Long.MAX_VALUE, 0));
        assertTrue(gate.exhausted());
        assertTrue(gate.takeBatch(2).contains(65));
        gate.reset();
        assertFalse(gate.exhausted());
        assertEquals(List.of(), gate.takeBatch(2));
        assertTrue(gate.offer(1, 1));
        assertEquals(List.of(1), gate.takeBatch(3));
    }
}
