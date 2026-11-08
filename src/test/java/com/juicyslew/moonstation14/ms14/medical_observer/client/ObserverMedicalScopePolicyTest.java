package com.juicyslew.moonstation14.ms14.medical_observer.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ObserverMedicalScopePolicyTest {
    private static com.juicyslew.moonstation14.ms14.medical_observer.ObserverPayloads.Snapshot snapshot(
            com.juicyslew.moonstation14.ms14.medical_observer.ObserverPayloads.Mode mode, long epoch, boolean available) {
        return new com.juicyslew.moonstation14.ms14.medical_observer.ObserverPayloads.Snapshot(
                mode, epoch, java.util.UUID.randomUUID(), 1, java.util.UUID.randomUUID(), 2, available);
    }

    @Test void incomingSnapshotsComposeThroughProductionReturnRequestBuilder() {
        var ghost = java.util.UUID.randomUUID();
        ObserverMedicalScopePolicy.ReturnCredentialState state = null;
        var initialUnavailable = new com.juicyslew.moonstation14.ms14.medical_observer.ObserverPayloads.Snapshot(
                com.juicyslew.moonstation14.ms14.medical_observer.ObserverPayloads.Mode.GHOST,
                1, java.util.UUID.randomUUID(), 1, ghost, 2, false);
        state = ObserverMedicalScopePolicy.reconcileReturnCredential(true, state, initialUnavailable, ghost);
        assertNull(ObserverMedicalScopePolicy.returnRequest(true, state, ghost));

        var available = new com.juicyslew.moonstation14.ms14.medical_observer.ObserverPayloads.Snapshot(
                initialUnavailable.mode(), 2, initialUnavailable.body(), 1, ghost, 2, true);
        state = ObserverMedicalScopePolicy.reconcileReturnCredential(true, state, available, ghost);
        var request = ObserverMedicalScopePolicy.returnRequest(true, state, ghost);
        assertNotNull(request);
        assertEquals(com.juicyslew.moonstation14.ms14.medical_observer.ObserverPayloads.Request.Kind.RETURN, request.kind());
        assertEquals(2, request.epoch());

        var freshUnavailable = new com.juicyslew.moonstation14.ms14.medical_observer.ObserverPayloads.Snapshot(
                available.mode(), 3, available.body(), 1, ghost, 2, false);
        state = ObserverMedicalScopePolicy.reconcileReturnCredential(true, state, freshUnavailable, ghost);
        assertNull(ObserverMedicalScopePolicy.returnRequest(true, state, ghost));
        state = ObserverMedicalScopePolicy.reconcileReturnCredential(true, state, available, ghost);
        assertNull(ObserverMedicalScopePolicy.returnRequest(true, state, ghost));
        var mismatchedGhost = new com.juicyslew.moonstation14.ms14.medical_observer.ObserverPayloads.Snapshot(
                available.mode(), 4, available.body(), 1, java.util.UUID.randomUUID(), 2, true);
        state = ObserverMedicalScopePolicy.reconcileReturnCredential(true, state, mismatchedGhost, ghost);
        assertNull(ObserverMedicalScopePolicy.returnRequest(true, state, ghost));

        var nextAvailable = new com.juicyslew.moonstation14.ms14.medical_observer.ObserverPayloads.Snapshot(
                available.mode(), 5, available.body(), 1, ghost, 2, true);
        state = ObserverMedicalScopePolicy.reconcileReturnCredential(true, state, nextAvailable, ghost);
        var matchingEnd = new com.juicyslew.moonstation14.ms14.medical_observer.ObserverPayloads.Snapshot(
                com.juicyslew.moonstation14.ms14.medical_observer.ObserverPayloads.Mode.END,
                5, nextAvailable.body(), 1, null, -1, false);
        state = ObserverMedicalScopePolicy.reconcileReturnCredential(true, state, matchingEnd, ghost);
        assertNull(ObserverMedicalScopePolicy.returnRequest(true, state, ghost));
        assertNull(ObserverMedicalScopePolicy.returnRequest(true, state,
                java.util.UUID.randomUUID()));
        assertNull(ObserverMedicalScopePolicy.returnRequest(false, state, ghost));

        var newSession = new com.juicyslew.moonstation14.ms14.medical_observer.ObserverPayloads.Snapshot(
                available.mode(), 6, available.body(), 1, ghost, 2, true);
        state = ObserverMedicalScopePolicy.reconcileReturnCredential(true, state, newSession, ghost);
        request = ObserverMedicalScopePolicy.returnRequest(true, state, ghost);
        assertNotNull(request);
        assertEquals(com.juicyslew.moonstation14.ms14.medical_observer.ObserverPayloads.Request.Kind.RETURN, request.kind());
        assertEquals(6, request.epoch());
    }

    @Test void canonicalGhostCredentialTransitionRevokesOnFreshUnavailableSnapshot() {
        var available = snapshot(com.juicyslew.moonstation14.ms14.medical_observer.ObserverPayloads.Mode.GHOST, 8, true);
        var ghostId = available.ghost();
        var state = new ObserverMedicalScopePolicy.ReturnCredentialState(available, 8, ghostId);
        var unavailable = new com.juicyslew.moonstation14.ms14.medical_observer.ObserverPayloads.Snapshot(
                com.juicyslew.moonstation14.ms14.medical_observer.ObserverPayloads.Mode.GHOST,
                9, available.body(), available.bodyEntity(), ghostId, available.ghostEntity(), false);
        state = ObserverMedicalScopePolicy.reconcileReturnCredential(true, state, unavailable, ghostId);
        assertNull(state.credential());
        assertEquals(9, state.epoch());
        var oldAvailable = new com.juicyslew.moonstation14.ms14.medical_observer.ObserverPayloads.Snapshot(
                available.mode(), 8, available.body(), available.bodyEntity(), ghostId,
                available.ghostEntity(), true);
        state = ObserverMedicalScopePolicy.reconcileReturnCredential(true, state, oldAvailable, ghostId);
        assertNull(state.credential());
        var freshAvailable = new com.juicyslew.moonstation14.ms14.medical_observer.ObserverPayloads.Snapshot(
                available.mode(), 12, available.body(), available.bodyEntity(), ghostId,
                available.ghostEntity(), true);
        state = ObserverMedicalScopePolicy.reconcileReturnCredential(true, state, freshAvailable, ghostId);
        assertSame(freshAvailable, state.credential());
        var staleUnavailable = new com.juicyslew.moonstation14.ms14.medical_observer.ObserverPayloads.Snapshot(
                unavailable.mode(), 10, unavailable.body(), unavailable.bodyEntity(), ghostId,
                unavailable.ghostEntity(), false);
        assertSame(state, ObserverMedicalScopePolicy.reconcileReturnCredential(true, state,
                staleUnavailable, ghostId));
        var otherGhost = new com.juicyslew.moonstation14.ms14.medical_observer.ObserverPayloads.Snapshot(
                unavailable.mode(), 13, unavailable.body(), unavailable.bodyEntity(),
                java.util.UUID.randomUUID(), unavailable.ghostEntity(), false);
        assertSame(state, ObserverMedicalScopePolicy.reconcileReturnCredential(true, state, otherGhost, ghostId));
        assertSame(state, ObserverMedicalScopePolicy.reconcileReturnCredential(false, state, unavailable, ghostId));
        assertSame(state, ObserverMedicalScopePolicy.reconcileReturnCredential(true, state,
                snapshot(com.juicyslew.moonstation14.ms14.medical_observer.ObserverPayloads.Mode.CORPSE, 13, false), ghostId));
        assertTrue(ObserverMedicalScopePolicy.consumeReturn(true, state.credential()));
        assertFalse(ObserverMedicalScopePolicy.consumeReturn(false, state.credential()));
        assertFalse(ObserverMedicalScopePolicy.consumeReturn(true,
                snapshot(com.juicyslew.moonstation14.ms14.medical_observer.ObserverPayloads.Mode.CORPSE, 8, true)));
        var staleEnd = snapshot(com.juicyslew.moonstation14.ms14.medical_observer.ObserverPayloads.Mode.END, 7, false);
        var matchingEnd = new com.juicyslew.moonstation14.ms14.medical_observer.ObserverPayloads.Snapshot(
                com.juicyslew.moonstation14.ms14.medical_observer.ObserverPayloads.Mode.END,
                12, freshAvailable.body(), freshAvailable.bodyEntity(), null, -1, false);
        var otherEnd = new com.juicyslew.moonstation14.ms14.medical_observer.ObserverPayloads.Snapshot(
                matchingEnd.mode(), 12, java.util.UUID.randomUUID(), 1, null, -1, false);
        assertSame(state, ObserverMedicalScopePolicy.reconcileReturnCredential(true, state, staleEnd, ghostId));
        assertSame(state, ObserverMedicalScopePolicy.reconcileReturnCredential(true, state, otherEnd, ghostId));
        state = ObserverMedicalScopePolicy.reconcileReturnCredential(true, state, matchingEnd, ghostId);
        assertNull(state.credential());
        assertEquals(12, state.epoch());
        assertNull(ObserverMedicalScopePolicy.reconcileReturnCredential(true, state, oldAvailable, ghostId).credential());
    }

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
