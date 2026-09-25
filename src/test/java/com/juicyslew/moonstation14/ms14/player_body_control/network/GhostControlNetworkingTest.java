package com.juicyslew.moonstation14.ms14.player_body_control.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GhostControlNetworkingTest {
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
