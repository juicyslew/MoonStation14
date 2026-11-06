package com.juicyslew.moonstation14.ms14.doors.client;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ClientAirlockPredictionSystemTest {
    @Test void ownerLeaseRequiresEpochEntityAndUniqueIdentity() {
        UUID body = UUID.randomUUID();
        var lease = new ClientAirlockPredictionSystem.OwnerKey(12, 44, body);
        assertTrue(ClientAirlockPredictionSystem.sameOwner(lease, 12, 44, body));
        assertFalse(ClientAirlockPredictionSystem.sameOwner(lease, 13, 44, body));
        assertFalse(ClientAirlockPredictionSystem.sameOwner(lease, 12, 45, body));
        assertFalse(ClientAirlockPredictionSystem.sameOwner(lease, 12, 44, UUID.randomUUID()));
        assertFalse(ClientAirlockPredictionSystem.sameOwner(null, 12, 44, body));
    }
}
