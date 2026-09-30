package com.juicyslew.moonstation14.ms14.hands.quarantine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParkedInventoryPacketPolicyTest {
    @Test
    void connectedAccountCannotMutateWithValidOrInvalidMarker() {
        assertTrue(ParkedInventoryPacketPolicy.deny(true, true, true));
        assertTrue(ParkedInventoryPacketPolicy.deny(true, true, false), "mismatched/corrupt marker fails closed");
        assertTrue(ParkedInventoryPacketPolicy.deny(true, false, true), "owned park takes precedence");
    }

    @Test
    void absenceAndUnrelatedPlayersKeepVanillaBehavior() {
        assertFalse(ParkedInventoryPacketPolicy.deny(true, false, false), "ordinary creative/survival/spectator");
        assertFalse(ParkedInventoryPacketPolicy.deny(false, false, false));
        assertFalse(ParkedInventoryPacketPolicy.deny(false, true, false), "unregistered or fake holder");
        assertFalse(ParkedInventoryPacketPolicy.deny(false, true, true), "not the connected account");
    }
}
