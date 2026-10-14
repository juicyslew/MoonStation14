package com.juicyslew.moonstation14.ms14.player_body_control.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BodyHudSourcePolicyTest {
    @Test void canonicalMouseBodyWinsOverStaleHumanObservation() {
        Object mouse = new Object(), staleHuman = new Object();
        assertSame(mouse, BodyHudSourcePolicy.select(mouse, staleHuman, true, false));
    }

    @Test void pendingCharacterAndGhostDoNotFallBackToObserverBody() {
        Object staleHuman = new Object();
        assertNull(BodyHudSourcePolicy.select(null, staleHuman, true, false));
        assertNull(BodyHudSourcePolicy.select(null, staleHuman, false, true));
    }

    @Test void legacyObserverHintAndOrdinaryPlayerSourceRemainAvailable() {
        Object observer = new Object();
        assertSame(observer, BodyHudSourcePolicy.select(null, observer, false, false));
        assertNull(BodyHudSourcePolicy.select(null, null, false, false));
    }
}
