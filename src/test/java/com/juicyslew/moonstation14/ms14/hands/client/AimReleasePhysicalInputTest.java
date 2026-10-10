package com.juicyslew.moonstation14.ms14.hands.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AimReleasePhysicalInputTest {
    @Test void configuredMouseReleaseUsesCapturedPhysicalButtonAfterRebinding() {
        var input = new AimReleasePhysicalInput();
        input.beginMouse(2, false); // user-rebound Alt+right mouse mapping
        assertFalse(input.matchesMouseRelease(0));
        assertTrue(input.matchesMouseRelease(2));
    }
    @Test void cancellationRetainsPhysicalReleaseIdentityAndOnlyDefaultAltLeftFallsBack() {
        var input = new AimReleasePhysicalInput();
        var gesture = new AimReleaseWorldDump();
        var source = java.util.UUID.randomUUID();
        gesture.press(true, false, true, source, java.util.UUID.randomUUID(), 2);
        input.beginMouse(0, true);
        gesture.escape();
        // Gesture cancellation does not clear the input latch; its exact physical up is still consumed.
        assertTrue(input.matchesMouseRelease(0));
        assertTrue(gesture.consumeMouseUp());
        assertTrue(input.legacyFallback());
        input.beginMouse(2, false);
        assertFalse(input.legacyFallback());
    }
}
