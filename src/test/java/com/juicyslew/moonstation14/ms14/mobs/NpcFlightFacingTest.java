package com.juicyslew.moonstation14.ms14.mobs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NpcFlightFacingTest {
    @Test
    void presentationTracksTravelDuringCruiseAndBraking() {
        assertEquals(0f, NpcBehaviorSystem.flightPresentationYaw(0, 1, 0, 1, 90), .001f);
        // Opposing target acceleration brakes forward travel; facing remains with travel, not command.
        assertEquals(0f, NpcBehaviorSystem.flightPresentationYaw(0, 1, 0, -1, 90), .001f);
    }

    @Test
    void nearZeroVelocityUsesTargetAndHoverPreservesFacing() {
        assertEquals(90f, NpcBehaviorSystem.flightPresentationYaw(0, 0, -1, 0, 15), .001f);
        assertEquals(15f, NpcBehaviorSystem.flightPresentationYaw(0, 0, 0, 0, 15), .001f);
    }
}
