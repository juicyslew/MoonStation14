package com.juicyslew.moonstation14.ms14.radiation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RadiationSourceProfileTest {
    @Test void linearFalloffAndFiniteRange() {
        RadiationSourceProfile profile = new RadiationSourceProfile(10, 2);
        assertEquals(10, profile.atDistance(0));
        assertEquals(6, profile.atDistance(2));
        assertEquals(0, profile.atDistance(5));
        assertEquals(5, profile.range());
    }
    @Test void zeroSlopeUsesFiftyBlockLimit() {
        RadiationSourceProfile profile = new RadiationSourceProfile(10, 0);
        assertEquals(50, profile.range());
        assertEquals(10, profile.atDistance(50));
        assertEquals(0, profile.atDistance(50.01));
    }
    @Test void rejectsInvalidProviderValues() {
        assertThrows(IllegalArgumentException.class, () -> new RadiationSourceProfile(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> new RadiationSourceProfile(1, Double.NaN));
    }
}
