package com.juicyslew.moonstation14.ms14.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BodyViewAudioAudienceTest {
    @Test void audienceDistanceUsesViewPositionRatherThanHiddenCarrierPosition() {
        // Source is near the body-view camera even though the player's carrier may be far away.
        assertTrue(BodyViewAudioAudience.withinRange(1.5, -60, 8.5, 2, -60, 8.5, 16));
        // Conversely, a nearby carrier must not make a distant view camera hear it.
        assertFalse(BodyViewAudioAudience.withinRange(1.5, -60, 8.5, 100, -60, 8.5, 16));
    }

    @Test void rejectsInvalidCoordinatesAndRange() {
        assertFalse(BodyViewAudioAudience.withinRange(Double.NaN, 0, 0, 0, 0, 0, 16));
        assertFalse(BodyViewAudioAudience.withinRange(0, 0, 0, 0, 0, 0, -1));
    }
}
