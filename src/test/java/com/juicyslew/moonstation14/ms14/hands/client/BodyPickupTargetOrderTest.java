package com.juicyslew.moonstation14.ms14.hands.client;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BodyPickupTargetOrderTest {
    @Test void exactTieUsesUuidInEitherOrder() {
        UUID low = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID high = UUID.fromString("00000000-0000-0000-0000-000000000002");
        assertTrue(BodyPickupTargetOrder.precedes(4, low, 4, high));
        assertFalse(BodyPickupTargetOrder.precedes(4, high, 4, low));
        assertTrue(BodyPickupTargetOrder.precedes(3, high, 4, low));
        assertFalse(BodyPickupTargetOrder.precedes(Double.NaN, low, 4, high));
    }
}
