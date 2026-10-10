package com.juicyslew.moonstation14.ms14.devicenet.atmos;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AtmosMonitorSubscriptionsTest {
    @Test void registrationIsIdempotentAndDoesNotRequestAStateReply() {
        var subscriptions = new AtmosMonitorSubscriptions();
        assertFalse(subscriptions.receive(new AtmosDevicePayload.Register(), "monitor"));
        assertFalse(subscriptions.receive(new AtmosDevicePayload.Register(), "monitor"));
        assertTrue(subscriptions.isRegistered("monitor"));
        assertTrue(subscriptions.receive(new AtmosDevicePayload.Sync(), "monitor"));
        assertFalse(subscriptions.receive(new AtmosDevicePayload.Deregister(), "monitor"));
        assertFalse(subscriptions.receive(new AtmosDevicePayload.Deregister(), "monitor"));
        assertFalse(subscriptions.isRegistered("monitor"));
    }
}
