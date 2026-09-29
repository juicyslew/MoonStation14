package com.juicyslew.moonstation14.ms14.hands;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HandActorAuthorityTest {
    @Test
    void requiresExactlyOneAuthority() {
        assertFalse(HandActorAuthority.acceptsExactlyOneAuthority(false, false));
        assertFalse(HandActorAuthority.acceptsExactlyOneAuthority(true, true));
        assertTrue(HandActorAuthority.acceptsExactlyOneAuthority(true, false));
        assertTrue(HandActorAuthority.acceptsExactlyOneAuthority(false, true));
    }

    @Test
    void changedCapabilityIsNotTheSameResolvedActor() {
        // Entity-backed snapshots require a running Minecraft server, so exercise the pure admission comparison
        // indirectly via a narrow capability comparison helper's corresponding value-level contract below.
        assertFalse(HandActorAuthority.sameCapabilityIds(java.util.List.of("left", "right"),
                java.util.List.of("left")));
        assertFalse(HandActorAuthority.sameCapabilityIds(java.util.List.of("left", "right"),
                java.util.List.of("right", "left")));
        assertTrue(HandActorAuthority.sameCapabilityIds(java.util.List.of("left", "right"),
                java.util.List.of("left", "right")));
    }
}
