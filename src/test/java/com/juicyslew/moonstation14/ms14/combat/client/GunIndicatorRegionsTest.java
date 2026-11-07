package com.juicyslew.moonstation14.ms14.combat.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GunIndicatorRegionsTest {
    @Test void regionsAreSelectiveAndInclusive() {
        var charge = new GunIndicatorRegions.Region(.63671875, .00390625, .7109375, .01171875);
        var shotgun = new GunIndicatorRegions.Region(.27734375, .13671875, .28125, .14453125);
        assertTrue(charge.matches(.63671875, .00390625));
        assertTrue(charge.matches(.7109375, .01171875));
        assertFalse(charge.matches(.5, .5));
        assertTrue(shotgun.matches(.28, .14));
        assertFalse(shotgun.matches(.28, .2));
    }
}
