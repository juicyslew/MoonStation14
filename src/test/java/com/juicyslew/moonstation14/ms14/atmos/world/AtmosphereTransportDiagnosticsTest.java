package com.juicyslew.moonstation14.ms14.atmos.world;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtmosphereTransportDiagnosticsTest {
    @Test void patchSnapshotIsImmutableValueAndDistinguishesNoAttemptFromCommit() {
        var attempt = new AtmosphereService.PatchDiagnostic("AMBIENT_FLOW", "none", "solver incomplete", 12, 8, 3, 0, 0);
        assertEquals("none", attempt.committed());
        assertEquals("solver incomplete", attempt.status());
        var committed = new AtmosphereService.PatchDiagnostic("AMBIENT_FLOW", "AMBIENT_FLOW", "committed", 12, 8, 3, 1.25, 44);
        assertEquals(1.25, committed.exportMoles());
        assertNotSame(attempt, committed);
    }
}
