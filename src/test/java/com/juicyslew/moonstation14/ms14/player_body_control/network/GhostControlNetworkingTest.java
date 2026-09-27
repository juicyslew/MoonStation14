package com.juicyslew.moonstation14.ms14.player_body_control.network;

import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GhostControlNetworkingTest {
    @Test
    void protocolVersionRequiresMatchingBuildsAfterOfferWireAddition() {
        assertEquals("3", GhostControlNetworking.PROTOCOL_VERSION);
    }

    @Test
    void acceptsBothConfiguredHarnessKinds() {
        assertTrue(GhostControlNetworking.supportedClientHarnessKind(
                new GhostControlPayloads.Begin(1, 2, MobHarnessKind.GHOST)));
        assertTrue(GhostControlNetworking.supportedClientHarnessKind(
                new GhostControlPayloads.Begin(1, 2, MobHarnessKind.CHARACTER)));
        assertTrue(GhostControlNetworking.supportedClientHarnessKind(snapshot(MobHarnessKind.GHOST)));
        assertTrue(GhostControlNetworking.supportedClientHarnessKind(snapshot(MobHarnessKind.CHARACTER)));
    }

    @Test
    void offerEligibilityRejectsStaleAndCrossIdentityOffers() {
        var character = new GhostControlPayloads.Offer(5, 8, MobHarnessKind.CHARACTER);
        assertTrue(GhostControlNetworking.matchesOffer(5, 7, MobHarnessKind.GHOST, character));
        assertFalse(GhostControlNetworking.matchesOffer(4, 7, MobHarnessKind.GHOST, character));
        assertFalse(GhostControlNetworking.matchesOffer(5, 8, MobHarnessKind.GHOST, character));
        assertFalse(GhostControlNetworking.matchesOffer(5, 7, MobHarnessKind.CHARACTER, character));
    }

    @Test
    void allowsTargetlessCommitAndStopPayloads() {
        assertTrue(GhostControlNetworking.supportedClientHarnessKind(new GhostControlPayloads.Commit(1)));
        assertTrue(GhostControlNetworking.supportedClientHarnessKind(new GhostControlPayloads.Stop(1)));
    }

    private static GhostControlPayloads.Snapshot snapshot(MobHarnessKind harnessKind) {
        return new GhostControlPayloads.Snapshot(1, 2, harnessKind, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, false, 1, 1, false);
    }

    @Test
    void acceptsOnlyARealConnectedServerPlayer() {
        assertTrue(GhostControlNetworking.isConnectedServerPlayer(true, false, false,
                false, true, true));
    }

    @Test
    void rejectsMissingFakeRemovedClientSideOrUnlistedPlayers() {
        assertFalse(GhostControlNetworking.isConnectedServerPlayer(false, false, false,
                false, true, true));
        assertFalse(GhostControlNetworking.isConnectedServerPlayer(true, true, false,
                false, true, true));
        assertFalse(GhostControlNetworking.isConnectedServerPlayer(true, false, true,
                false, true, true));
        assertFalse(GhostControlNetworking.isConnectedServerPlayer(true, false, false,
                true, true, true));
        assertFalse(GhostControlNetworking.isConnectedServerPlayer(true, false, false,
                false, false, true));
        assertFalse(GhostControlNetworking.isConnectedServerPlayer(true, false, false,
                false, true, false));
    }
}
