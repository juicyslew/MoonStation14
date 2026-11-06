package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LifecycleShuttleCargoReconnectGameTestsTest {
    @Test void sceneLeaseIsOptionalBeforeReleaseButMandatoryAfterBoundary() {
        assertTrue(LifecycleShuttleCargoReconnectGameTests.leaseStateValid(false, false),
                "fixture must be allowed to retain its scene lease during launch and ascent");
        assertTrue(LifecycleShuttleCargoReconnectGameTests.leaseStateValid(false, true),
                "pre-release observations do not infer that release has happened");
        assertTrue(LifecycleShuttleCargoReconnectGameTests.leaseStateValid(true, true),
                "closed lease remains valid after the explicit release boundary");
        assertFalse(LifecycleShuttleCargoReconnectGameTests.leaseStateValid(true, false),
                "a reopened/missing close after release must fail the resident observation");
    }
}
