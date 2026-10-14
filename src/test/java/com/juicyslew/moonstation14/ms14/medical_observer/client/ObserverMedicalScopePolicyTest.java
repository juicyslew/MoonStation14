package com.juicyslew.moonstation14.ms14.medical_observer.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ObserverMedicalScopePolicyTest {
    @Test void canonicalGhostSuppressesRetainedMedicalSnapshot() {
        assertFalse(ObserverMedicalScopePolicy.expose(true, false, true));
        assertFalse(ObserverMedicalScopePolicy.expose(true, false, false));
    }

    @Test void characterControlRequiresSnapshotForExactOwnedBody() {
        assertTrue(ObserverMedicalScopePolicy.expose(false, true, true));
        assertFalse(ObserverMedicalScopePolicy.expose(false, true, false));
    }

    @Test void unownedLegacyMedicalObservationRemainsAvailable() {
        assertTrue(ObserverMedicalScopePolicy.expose(false, false, true));
        assertTrue(ObserverMedicalScopePolicy.expose(false, false, false));
    }
}
