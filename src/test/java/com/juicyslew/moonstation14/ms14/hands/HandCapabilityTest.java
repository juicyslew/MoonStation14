package com.juicyslew.moonstation14.ms14.hands;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class HandCapabilityTest {
    @Test
    void compatibilityRequiresExactPrototypeIdsAndOrderWithoutDroppingTokens() {
        HandComponent existing = HandComponent.from(HandState.create(List.of("left", "right"), "right")
                .place("left", new ItemToken("held-token")).state());

        assertTrue(HandCapability.isCompatible(existing, List.of("left", "right")));
        assertFalse(HandCapability.isCompatible(existing, List.of("right", "left")));
        assertFalse(HandCapability.isCompatible(existing, List.of("left")));
        assertFalse(HandCapability.isCompatible(existing, List.of("left", "right", "extra")));
        assertEquals(Optional.of("held-token"), existing.hands().getFirst().token());
        assertThrows(UnsupportedOperationException.class,
                () -> existing.hands().clear());
    }
}
