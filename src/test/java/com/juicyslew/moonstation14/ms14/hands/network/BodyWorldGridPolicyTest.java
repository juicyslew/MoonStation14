package com.juicyslew.moonstation14.ms14.hands.network;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class BodyWorldGridPolicyTest {
    @Test void postDropGrantRequiresExactBindingsAndUnexpiredLease() {
        assertTrue(grant(true, true, true, 99, 100));
        assertFalse(grant(false, true, true, 99, 100)); // actor/body epoch/target/revision binding changed
        assertFalse(BodyWorldGridService.grantBindingMatches(true, true, true, true, true, true, false, true, true, 99, 100));
        assertFalse(BodyWorldGridService.grantBindingMatches(true, true, true, true, true, true, true, false, true, 99, 100));
        assertFalse(BodyWorldGridService.grantBindingMatches(true, true, true, true, true, true, true, true, false, 99, 100));
        assertFalse(grant(true, true, true, 100, 100));
        assertFalse(grant(true, true, true, 99, 99));
    }

    private static boolean grant(boolean binding, boolean bodyEpochAndTarget, boolean revision, long now, long expiry) {
        return BodyWorldGridService.grantBindingMatches(binding, bodyEpochAndTarget, true, true, true,
                revision, true, true, true, now, expiry);
    }

    @Test void refreshRenewsLeaseButMissedRefreshStillHardExpires() {
        long opened = 100;
        long expiry = BodyWorldGridService.renewedExpiry(opened);
        assertEquals(200, expiry);
        assertFalse(BodyWorldGridService.expired(199, expiry));

        long refreshed = 140;
        expiry = BodyWorldGridService.renewedExpiry(refreshed);
        assertEquals(240, expiry);
        assertFalse(BodyWorldGridService.expired(239, expiry));
        assertTrue(BodyWorldGridService.expired(240, expiry));
        assertTrue(BodyWorldGridService.expired(200, BodyWorldGridService.renewedExpiry(100)));
    }

    @Test void queryEvidenceClassificationSeparatesUnstableFromLostReach() {
        assertNull(BodyWorldGridService.queryEvidenceDenial(true, true));
        assertEquals(BodyWorldGridSnapshot.Reason.NOT_READY,
                BodyWorldGridService.queryEvidenceDenial(false, false));
        assertEquals(BodyWorldGridSnapshot.Reason.UNAVAILABLE,
                BodyWorldGridService.queryEvidenceDenial(true, false));
    }

    @Test void sequenceAndEpochHistoryAreBoundedWithoutResettingOnRateRejection() {
        var gate = new BodyWorldGridService.Gate();
        assertTrue(gate.admit(1, 10, 5));
        assertTrue(gate.admit(1, 11, 5));
        assertTrue(gate.admit(1, 12, 5));
        assertTrue(gate.admit(1, 13, 5));
        assertFalse(gate.admit(1, 14, 5));
        assertFalse(gate.admit(1, 13, 6));
        assertTrue(gate.admit(1, 14, 6));
        for (long epoch = 2; epoch <= 9; epoch++) assertTrue(gate.admit(epoch, 1, epoch + 10));
        assertTrue(gate.admit(1, 15, 30)); // Oldest epoch is evicted after eight distinct epochs.
    }

    @Test void perTickLimitIsFour() {
        var gate = new BodyWorldGridService.Gate();
        for (int i = 1; i <= 4; i++) assertTrue(gate.admit(1, i, 100));
        assertFalse(gate.admit(1, 5, 100));
        assertTrue(gate.admit(1, 5, 101));
    }

    @Test void reasonDistinguishesRateFromReplay() {
        var gate = new BodyWorldGridService.Gate();
        assertNull(gate.reject(42, 1, 100));
        assertEquals(BodyWorldGridSnapshot.Reason.REPLAY, gate.reject(42, 1, 100));
        assertNull(gate.reject(42, 2, 100));
        assertNull(gate.reject(42, 3, 100));
        assertEquals(BodyWorldGridSnapshot.Reason.RATE_LIMITED, gate.reject(42, 4, 100));
        assertNull(gate.reject(42, 4, 101));
    }

    @Test void threeDistinctHostsCloseNeedsExactNonceAndTargetAndCannotReuse() {
        var slots = new BodyWorldGridService.SlotLedger();
        UUID[] hosts = { UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID() };
        UUID[] nonces = { UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID() };
        for (int i = 0; i < 3; i++) assertTrue(slots.add(nonces[i], hosts[i]));
        assertFalse(slots.available(hosts[3]));
        assertFalse(slots.add(nonces[3], hosts[3]));
        assertFalse(slots.add(UUID.randomUUID(), hosts[0]));
        assertFalse(slots.close(UUID.randomUUID(), hosts[0]));
        assertFalse(slots.close(nonces[0], hosts[1]));
        assertFalse(slots.available(hosts[3]));
        assertTrue(slots.close(nonces[0], hosts[0]));
        assertFalse(slots.close(nonces[0], hosts[0]));
        assertTrue(slots.add(nonces[3], hosts[3]));
        assertFalse(slots.close(nonces[0], hosts[3]));
        slots.clear();
        assertFalse(slots.close(nonces[3], hosts[3]));
    }

    @Test void observationOutlivesActionLeaseAndOnlyCloseFreesItsQuota() {
        var slots = new BodyWorldGridService.SlotLedger();
        UUID[] hosts = { UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID() };
        UUID[] nonces = { UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID() };
        for (int i = 0; i < 3; i++) assertTrue(slots.add(nonces[i], hosts[i]));
        long expiry = BodyWorldGridService.renewedExpiry(100);
        assertTrue(BodyWorldGridService.expired(200, expiry));
        assertFalse(BodyWorldGridService.actionLeaseValid(200, expiry));
        // A fresh observation validates material before renewal without dropping the nonce.
        assertEquals(300, BodyWorldGridService.renewedExpiry(200));
        assertFalse(slots.available(hosts[0]));
        assertFalse(slots.add(UUID.randomUUID(), hosts[3]));
        assertTrue(slots.close(nonces[0], hosts[0]));
        assertTrue(slots.add(UUID.randomUUID(), hosts[3]));
    }

    @Test void requestAndCloseRejectInvalidCapabilities() {
        var target = UUID.randomUUID();
        assertNull(new BodyWorldGridQuery(1, 1, target, 0).session());
        assertEquals(target, new BodyWorldGridQuery(1, 2, target, 0, UUID.randomUUID()).target());
        assertThrows(IllegalArgumentException.class, () -> new BodyWorldGridQuery(0, 1, target, 0));
        assertThrows(IllegalArgumentException.class, () -> new BodyWorldGridClose(1, target, null));
        assertThrows(IllegalArgumentException.class, () -> new BodyWorldGridClose(0, target, UUID.randomUUID()));
    }
}
