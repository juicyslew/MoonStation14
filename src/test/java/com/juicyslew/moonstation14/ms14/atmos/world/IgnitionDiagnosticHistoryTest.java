package com.juicyslew.moonstation14.ms14.atmos.world;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IgnitionDiagnosticHistoryTest {
    @Test
    void cycleRootSurvivesCellRingChurnAndBothHistoriesRemainBounded() {
        IgnitionDiagnosticHistory history = new IgnitionDiagnosticHistory();
        var root = event(7, 1, "WHOLE_AIR_REACTION");
        history.append(root);
        for (int i = 0; i < 70; i++) history.append(event(7, i + 2, "NEIGHBOR_SPREAD"));

        assertEquals(64, history.recent(null).size());
        assertEquals(1, history.firstByCycle().size());
        assertEquals(root, history.firstByCycle().getFirst());
        assertFalse(history.recent(null).contains(root), "cell ring may evict the original event");

        for (int cycle = 8; cycle < 71; cycle++) history.append(event(cycle, cycle, "DIRECT_EXPOSURE"));
        assertEquals(64, history.recent(null).size());
        assertEquals(64, history.firstByCycle().size());
        assertEquals(7, history.firstByCycle().getFirst().cycleIdentity(), "oldest cycle root is evicted first");
        history.append(event(7, 999, "NEIGHBOR_SPREAD"));
        assertEquals(root, history.firstByCycle().getFirst(), "later events never overwrite a cycle's first ignition");
    }

    private static AtmosphereService.IgnitionDiagnostic event(long cycle, long time, String origin) {
        return new AtmosphereService.IgnitionDiagnostic(new BlockPos((int) time, 0, 0), cycle, time, origin,
                400, 500, 2, 10, 500, 6250, 0.5, "test:fire");
    }
}
