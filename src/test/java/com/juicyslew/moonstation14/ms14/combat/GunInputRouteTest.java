package com.juicyslew.moonstation14.ms14.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GunInputRouteTest {
    @Test void claimsOnlyMatchingGunButtonsInCombat() {
        assertFalse(GunInputRoute.claims(false, true, true));
        assertFalse(GunInputRoute.claims(false, false, true));
        assertTrue(GunInputRoute.claims(true, true, true));
        assertFalse(GunInputRoute.claims(true, true, false));
        assertFalse(GunInputRoute.claims(true, false, true));
        assertTrue(GunInputRoute.claims(true, false, false));
    }
}
