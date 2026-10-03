package com.juicyslew.moonstation14.ms14.hands.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BodyGrenadeRequestDispatchTest {
    @Test
    void onlyExplicitDropActionMayReachDropTransfer() {
        assertTrue(BodyHandRequestService.isDropAction(BodyHandActionRequest.Action.DROP));
        assertFalse(BodyHandRequestService.isDropAction(BodyHandActionRequest.Action.USE_HELD));
        assertFalse(BodyHandRequestService.isDropAction(BodyHandActionRequest.Action.THROW));
    }
}
