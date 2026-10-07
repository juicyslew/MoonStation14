package com.juicyslew.moonstation14.ms14.medical_observer;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ObserverCarrierVisibilityTest {
    private final UUID owner = UUID.randomUUID();
    private final UUID viewer = UUID.randomUUID();
    private final UUID distinctBody = UUID.randomUUID();

    @Test
    void corpseCarrierIsHiddenFromOtherViewersButNotOwner() {
        var corpse = new ObserverSession(owner, distinctBody, null, 1, ObserverSession.Mode.CORPSE, false);
        assertTrue(ObserverSystem.mustHideObserverCarrier(viewer, owner, corpse));
        assertFalse(ObserverSystem.mustHideObserverCarrier(owner, owner, corpse));
    }

    @Test
    void ghostCarrierIsHiddenFromOtherViewersButNotOwner() {
        var ghost = new ObserverSession(owner, distinctBody, UUID.randomUUID(), 1, ObserverSession.Mode.GHOST, true);
        assertTrue(ObserverSystem.mustHideObserverCarrier(viewer, owner, ghost));
        assertFalse(ObserverSystem.mustHideObserverCarrier(owner, owner, ghost));
    }

    @Test
    void nativeCorpseAndUnrelatedSpectatorRemainVisibleAndEndRestoresCarrier() {
        var nativeCorpse = new ObserverSession(owner, owner, null, 1, ObserverSession.Mode.CORPSE, false);
        assertFalse(ObserverSystem.mustHideObserverCarrier(viewer, owner, nativeCorpse));
        assertFalse(ObserverSystem.mustHideObserverCarrier(viewer, UUID.randomUUID(), null));
        assertFalse(ObserverSystem.mustHideObserverCarrier(viewer, owner, null));
    }
}
