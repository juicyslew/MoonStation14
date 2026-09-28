package com.juicyslew.moonstation14.ms14.player_body_control.client;

import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind;
import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GhostControlClientPolicyTest {
    @Test
    void customRegisteredCharacterDoesNotDependOnLegacyHostMapping() {
        assertTrue(GhostControlClient.characterBodyRecognized(true, false));
    }

    @Test
    void legacyConfiguredMobStillRequiresItsMappedPolicy() {
        assertTrue(GhostControlClient.characterBodyRecognized(false, true));
        assertFalse(GhostControlClient.characterBodyRecognized(false, false));
    }

    @Test
    void deferredBeginSurvivesTransientSessionCleanupAndWaitsForOwner() {
        GhostControlPayloads.Begin begin = begin(4);
        GhostControlPayloads.Begin queued = GhostControlClient.ClientBeginPolicy.defer(null, begin, 3);

        assertSame(begin, GhostControlClient.ClientBeginPolicy.afterSessionClear(queued));
        assertFalse(GhostControlClient.ClientBeginPolicy.canAccept(queued, 3, false));
        assertTrue(GhostControlClient.ClientBeginPolicy.canAccept(queued, 3, true));
    }

    @Test
    void deferredBeginRequiresNewEpochAndLogoutDiscardsIt() {
        GhostControlPayloads.Begin begin = begin(4);
        assertNull(GhostControlClient.ClientBeginPolicy.defer(null, begin, 4));
        assertFalse(GhostControlClient.ClientBeginPolicy.canAccept(begin, 4, true));
        assertNull(GhostControlClient.ClientBeginPolicy.afterLogout());
    }

    @Test
    void beginIsAcceptedOnlyFromMatchingPayloadContext() {
        GhostControlPayloads.Begin begin = begin(4);
        assertTrue(GhostControlClient.ClientBeginPolicy.canAcceptPayload(begin, 3, true));
        assertFalse(GhostControlClient.ClientBeginPolicy.canAcceptPayload(begin, 3, false));
    }

    private static GhostControlPayloads.Begin begin(long epoch) {
        return new GhostControlPayloads.Begin(epoch, 7, MobHarnessKind.GHOST);
    }
}
