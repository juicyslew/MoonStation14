package com.juicyslew.moonstation14.ms14.hands;

import org.junit.jupiter.api.Test;
import java.util.List;

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

    @Test
    void persistedLayoutMismatchFailsClosedWithoutAlteringHeldTokens() {
        List<String> ids = List.of("left", "right");
        HandComponent stored = HandComponent.from(HandState.create(ids)
                .place("left", new ItemToken("held-token")).state());
        HandAttachment matching = stored.toAttachment();
        HandAttachment reordered = HandComponent.from(HandState.create(List.of("right", "left"))
                .place("left", new ItemToken("held-token")).state()).toAttachment();
        assertTrue(HandActorAuthority.compatiblePersisted(false, null, ids));
        assertFalse(HandActorAuthority.compatiblePersisted(true, null, ids));
        assertTrue(HandActorAuthority.compatiblePersisted(true, matching, ids));
        assertFalse(HandActorAuthority.compatiblePersisted(true, reordered, ids));
        assertFalse(HandActorAuthority.compatiblePersisted(true, matching, List.of("left")));
        assertEquals(stored, matching.toComponent());
        assertEquals("held-token", reordered.toComponent().hands().get(1).token().orElseThrow());
    }
}
