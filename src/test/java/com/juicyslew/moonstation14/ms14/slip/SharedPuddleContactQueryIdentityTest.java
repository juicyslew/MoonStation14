package com.juicyslew.moonstation14.ms14.slip;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Focused source-identity result contract checks; live Sable identity needs native integration coverage. */
class SharedPuddleContactQueryIdentityTest {
    @Test void unavailableResultPreservesKnownFrameIdentity() {
        UUID frameId = UUID.randomUUID();

        var result = SharedPuddleContactQuery.unavailable(frameId);

        assertEquals(SharedPuddleContactQuery.State.UNAVAILABLE, result.state());
        assertEquals(frameId, result.frameId());
        assertTrue(result.contacts().isEmpty());
    }
}
