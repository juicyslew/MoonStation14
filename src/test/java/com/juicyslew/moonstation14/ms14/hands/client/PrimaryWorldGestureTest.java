package com.juicyslew.moonstation14.ms14.hands.client;

import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PrimaryWorldGestureTest {
    @Test void routesOnlyOwnedWorldPrimaryGestures() {
        assertEquals(PrimaryWorldGesture.Route.VANILLA, PrimaryWorldGesture.route(false, true, true, true));
        assertEquals(PrimaryWorldGesture.Route.BLOCK_USE, PrimaryWorldGesture.route(true, true, true, true));
        assertEquals(PrimaryWorldGesture.Route.PICKUP, PrimaryWorldGesture.route(true, true, false, true));
        assertEquals(BodyHandActionRequest.Action.PICKUP,
                PrimaryWorldGesture.action(PrimaryWorldGesture.route(true, true, false, true)));
        assertEquals(PrimaryWorldGesture.Route.BLOCK_USE, PrimaryWorldGesture.route(true, true, false, false));
        assertEquals(PrimaryWorldGesture.Route.HELD_USE, PrimaryWorldGesture.route(true, false, true, false));
        assertEquals(BodyHandActionRequest.Action.USE_HELD,
                PrimaryWorldGesture.action(PrimaryWorldGesture.route(true, false, true, false)));
        assertEquals(PrimaryWorldGesture.Route.PICKUP, PrimaryWorldGesture.route(true, false, false, true));
        assertEquals(BodyHandActionRequest.Action.PICKUP,
                PrimaryWorldGesture.action(PrimaryWorldGesture.route(true, false, false, true)));
        assertEquals(PrimaryWorldGesture.Route.VANILLA, PrimaryWorldGesture.route(true, false, false, false));
        assertNull(PrimaryWorldGesture.action(PrimaryWorldGesture.Route.VANILLA));
    }
}
