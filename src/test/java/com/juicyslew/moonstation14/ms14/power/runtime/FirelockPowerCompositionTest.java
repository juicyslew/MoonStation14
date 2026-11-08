package com.juicyslew.moonstation14.ms14.power.runtime;

import com.juicyslew.moonstation14.ms14.doors.FirelockController;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FirelockPowerCompositionTest {
    @Test void baseAirlockAndOverlayHaveDistinctControllerLoadIdentityAndDemand() {
        var base = new PowerLiveLoop.ApcConsumer("airlock-base", 3, true, 20);
        var overlayController = new FirelockController();
        var overlay = new PowerLiveLoop.ApcConsumer("firelock-" + overlayController.identity(), 3, true, 5);
        assertNotEquals(base.id(), overlay.id());
        assertEquals(20, base.demandWatts());
        assertEquals(5, overlay.demandWatts());
    }

    @Test void distinctControllerIdentityCannotReuseAnotherTargetSampleIdentity() {
        var first = new FirelockController();
        var second = new FirelockController();
        assertNotEquals(first.identity(), second.identity());
        assertNotEquals("firelock-" + first.identity(), "firelock-" + second.identity());
        var firstTarget = new PowerRuntime.FirelockTarget(null, first, null, null);
        var secondTarget = new PowerRuntime.FirelockTarget(null, second, null, null);
        assertTrue(firstTarget.sameController(firstTarget));
        assertFalse(firstTarget.sameController(secondTarget));
    }
}
