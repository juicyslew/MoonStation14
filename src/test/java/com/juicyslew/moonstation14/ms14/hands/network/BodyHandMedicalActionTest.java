package com.juicyslew.moonstation14.ms14.hands.network;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class BodyHandMedicalActionTest {
    @Test void useBodyRequiresExactTargetAndCurrentHandProof() {
        UUID target = UUID.randomUUID();
        var request = new BodyHandActionRequest(BodyHandActionRequest.Action.USE_BODY, "right", 1, 1, 0,
                target, "token");
        assertEquals(target, request.pickupTarget());
        assertEquals("token", request.expectedToken());
        assertThrows(IllegalArgumentException.class, () -> new BodyHandActionRequest(
                BodyHandActionRequest.Action.USE_BODY, "right", 1, 1, 0, null, "token"));
        assertThrows(IllegalArgumentException.class, () -> new BodyHandActionRequest(
                BodyHandActionRequest.Action.USE_BODY, "right", 1, 1, 0, target, null));
        assertThrows(IllegalArgumentException.class, () -> new BodyHandActionRequest(
                BodyHandActionRequest.Action.USE_HELD, "right", 1, 1, 0, target, "token"));
    }
}
