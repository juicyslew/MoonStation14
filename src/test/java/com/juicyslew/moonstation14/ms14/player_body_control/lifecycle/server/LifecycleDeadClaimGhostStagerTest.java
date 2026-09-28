package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.ms14.player_body_control.server.MindGhostStartupGate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LifecycleDeadClaimGhostStagerTest {
    @AfterEach void resetGate() { MindGhostStartupGate.onServerStopped(); }

    @Test void defaultOffRejectsBeforePlayerServerOrWorldAccess() {
        MindGhostStartupGate.onServerStopped();
        var result = LifecycleDeadClaimGhostStager.stage(null, null);
        assertEquals(LifecycleDeadClaimGhostStager.Outcome.FAILED, result.outcome());
        assertNull(result.prepared());
        assertTrue(result.diagnostic().contains("gate is off"));
    }
}
