package com.juicyslew.moonstation14.ms14.power.ui;

import org.junit.jupiter.api.Test;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceRules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApcInteractionActorContextTest {
    @Test
    void bodyUsesBodyReachAndDoesNotInheritCarrierBuildPermission() {
        var selected = ApcMenuService.selectInteractionActor(true, true, true, false, 9, false, 400);
        assertTrue(selected.allowed());
        assertEquals(9, selected.distanceSquared());
    }

    @Test
    void bodyRejectsFarBodyAndInvalidOrIncapableContextsRegardlessOfCarrier() {
        var tooFar = ApcMenuService.selectInteractionActor(true, true, true, false, 100, true, 1);
        assertTrue(tooFar.allowed()); // Actor policy grants the validated body; APC core owns range enforcement.
        assertEquals(100, tooFar.distanceSquared());
        assertFalse(PowerDeviceRules.canToggle(true, tooFar.allowed(), tooFar.distanceSquared()));
        assertFalse(ApcMenuService.selectInteractionActor(true, false, true, false, 1, true, 1).allowed());
        assertFalse(ApcMenuService.selectInteractionActor(true, true, false, false, 1, true, 1).allowed());
        assertFalse(ApcMenuService.selectInteractionActor(true, true, true, true, 1, true, 1).allowed());
    }

    @Test
    void vanillaMenuRetainsCarrierBuildPermissionAndReach() {
        var denied = ApcMenuService.selectInteractionActor(false, false, false, true, 1, false, 1);
        assertFalse(denied.allowed());
        var allowed = ApcMenuService.selectInteractionActor(false, false, false, true, 400, true, 4);
        assertTrue(allowed.allowed());
        assertEquals(4, allowed.distanceSquared());
    }
}
