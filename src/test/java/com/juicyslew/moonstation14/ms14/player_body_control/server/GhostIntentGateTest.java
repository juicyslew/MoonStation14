package com.juicyslew.moonstation14.ms14.player_body_control.server;

import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GhostIntentGateTest {
    private static final long EPOCH = 7;

    @Test
    void acceptsMonotonicSequenceAndAtMostOneFramePerTick() {
        GhostIntentGate<String> gate = new GhostIntentGate<>();
        assertTrue(gate.offer(1, 10, "first"));
        assertFalse(gate.offer(2, 10, "same tick"));
        assertEquals("first", gate.take());
        assertFalse(gate.offer(1, 11, "replay"));
        assertTrue(gate.offer(3, 11, "next"));
        assertFalse(gate.offer(4, 12, "pending frame cannot be overwritten"));
        assertEquals("next", gate.take());
        assertNull(gate.take());
    }

    @Test
    void permanentlySkipsSameTickSequencesAndRejectsThemAfterHigherSequenceIsAccepted() {
        GhostIntentGate<GhostControlPayloads.Intent> gate = new GhostIntentGate<>();
        GhostControlPayloads.Intent first = intent(1);
        GhostControlPayloads.Intent skippedTwo = intent(2);
        GhostControlPayloads.Intent skippedThree = intent(3);
        GhostControlPayloads.Intent fourth = intent(4);

        assertTrue(gate.offer(first.sequence(), 1, first));
        assertFalse(gate.offer(skippedTwo.sequence(), 1, skippedTwo));
        assertFalse(gate.offer(skippedThree.sequence(), 1, skippedThree));
        assertEquals(first, gate.take());

        assertTrue(gate.offer(fourth.sequence(), 2, fourth));
        assertEquals(fourth, gate.take());
        assertFalse(gate.offer(skippedTwo.sequence(), 3, skippedTwo));
        assertFalse(gate.offer(skippedThree.sequence(), 3, skippedThree));
    }

    @Test
    void boundedFifoKeepsOrderAndSequenceAcrossBarrierDiscard() {
        GhostIntentGate<String> gate = new GhostIntentGate<>();
        assertTrue(gate.offerQueued(1, "forward", 2));
        assertTrue(gate.offerQueued(2, "release", 2));
        assertFalse(gate.offerQueued(2, "duplicate", 2));
        assertFalse(gate.offerQueued(1, "stale", 2));
        assertEquals("forward", gate.takeQueued());
        assertEquals(2, gate.discardQueuedThroughSequence());
        assertFalse(gate.offerQueued(2, "duplicate after discard", 2));
        assertTrue(gate.offerQueued(3, "fresh after barrier", 2));
        assertEquals("fresh after barrier", gate.takeQueued());
        assertEquals(-1, gate.discardQueuedThroughSequence());
    }

    @Test
    void boundedFifoOverflowAndSequenceExhaustionFailClosed() {
        GhostIntentGate<String> gate = new GhostIntentGate<>();
        assertTrue(gate.offerQueued(1, "first", 1));
        assertFalse(gate.offerQueued(2, "overflow", 1));
        assertTrue(gate.exhausted());
        assertEquals("first", gate.takeQueued());
        assertFalse(gate.offerQueued(3, "after overflow", 1));

        gate.reset();
        assertFalse(gate.offerQueued(Long.MAX_VALUE, "exhaust", 1));
        assertTrue(gate.exhausted());
        assertFalse(gate.offerQueued(1, "wrap", 1));
    }

    @Test
    void sequenceExhaustionIsExplicitAndDoesNotWrap() {
        GhostIntentGate<String> gate = new GhostIntentGate<>();
        assertFalse(gate.offer(Long.MAX_VALUE, 10, "exhausted"));
        assertTrue(gate.exhausted());
        assertFalse(gate.offer(1, 11, "wrapped"));
        assertNull(gate.take());
    }

    private static GhostControlPayloads.Intent intent(long sequence) {
        return new GhostControlPayloads.Intent(EPOCH, sequence, (short) 0, (short) 0,
                (byte) 0, 0, 0, 0);
    }
}
