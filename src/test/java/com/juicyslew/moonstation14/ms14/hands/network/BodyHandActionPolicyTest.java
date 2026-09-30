package com.juicyslew.moonstation14.ms14.hands.network;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Synthetic input/pure sequence policy only; does not claim real ServerPlayer packet authorization. */
class BodyHandActionPolicyTest {
    @Test void rejectsMalformedIntent() {
        assertThrows(IllegalArgumentException.class, () -> new BodyHandActionRequest(
                BodyHandActionRequest.Action.PICKUP, "a", 0, 1, 0, UUID.randomUUID(), null));
        assertThrows(IllegalArgumentException.class, () -> new BodyHandActionRequest(
                BodyHandActionRequest.Action.DROP, "a", 1, 0, 0, null, "token"));
        assertThrows(IllegalArgumentException.class, () -> new BodyHandActionRequest(
                BodyHandActionRequest.Action.DROP, "a".repeat(65), 1, 1, 0, null, "token"));
        assertThrows(IllegalArgumentException.class, () -> new BodyHandActionRequest(
                BodyHandActionRequest.Action.DROP, "a", 1, 1, -1, null, "token"));
        assertThrows(IllegalArgumentException.class, () -> new BodyHandActionRequest(
                BodyHandActionRequest.Action.PICKUP, "a", 1, 1, 0, null, null));
        assertThrows(IllegalArgumentException.class, () -> new BodyHandActionRequest(
                BodyHandActionRequest.Action.DROP, "a", 1, 1, 0, UUID.randomUUID(), "token"));
    }

    @Test void rejectsReplayAndBoundsRatePerTick() {
        var gate = new BodyHandRequestService.SequenceGate();
        assertEquals(BodyHandActionResult.Reason.WRONG_EPOCH, gate.admit(7, 6, 1, 100));
        assertNull(gate.admit(7, 7, 1, 100));
        assertEquals(BodyHandActionResult.Reason.REPLAY, gate.admit(7, 7, 1, 100));
        for (int seq = 2; seq <= 6; seq++) assertNull(gate.admit(7, 7, seq, 100));
        assertEquals(BodyHandActionResult.Reason.RATE_LIMITED, gate.admit(7, 7, 7, 100));
        assertNull(gate.admit(7, 7, 7, 101));
        assertEquals(BodyHandActionResult.Reason.REPLAY, gate.admit(7, 7, 6, 102));
        assertNull(gate.admit(8, 8, 1, 102));
    }
}
