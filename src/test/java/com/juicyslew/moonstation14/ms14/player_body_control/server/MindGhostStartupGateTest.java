package com.juicyslew.moonstation14.ms14.player_body_control.server;

import com.juicyslew.moonstation14.Config;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MindGhostStartupGateTest {
    @Test
    void commonConfigDefaultsOff() {
        assertFalse(Config.EXPERIMENTAL_MIND_GHOST_CONTROL.get());
    }

    @Test
    void gateSamplesOnlyAtStartupAndResetsWhenServerStops() {
        MindGhostStartupGate.onServerStopped();
        assertFalse(MindGhostStartupGate.enabledForServer());

        MindGhostStartupGate.onServerStarting(true);
        assertTrue(MindGhostStartupGate.enabledForServer());

        // A changed config value has no effect until the next startup sample.
        assertTrue(MindGhostStartupGate.enabledForServer());
        MindGhostStartupGate.onServerStopped();
        assertFalse(MindGhostStartupGate.enabledForServer());

        // Even if configuration is true, the gate remains closed before startup.
        assertFalse(MindGhostStartupGate.enabledForServer());
        MindGhostStartupGate.onServerStarting(false);
        assertFalse(MindGhostStartupGate.enabledForServer());
    }
}
