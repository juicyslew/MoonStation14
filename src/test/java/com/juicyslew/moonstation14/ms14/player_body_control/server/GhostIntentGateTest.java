package com.juicyslew.moonstation14.ms14.player_body_control.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GhostIntentGateTest {
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
    void sequenceExhaustionIsExplicitAndDoesNotWrap() {
        GhostIntentGate<String> gate = new GhostIntentGate<>();
        assertFalse(gate.offer(Long.MAX_VALUE, 10, "exhausted"));
        assertTrue(gate.exhausted());
        assertFalse(gate.offer(1, 11, "wrapped"));
        assertNull(gate.take());
    }
}
