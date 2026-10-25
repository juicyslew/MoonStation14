package com.juicyslew.moonstation14.ms14.tesla;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TeslaMachineRepairUseTest {
    @Test void durationUsesResolvedRepairSecondsAndRoundsUpToWholeTicks() {
        assertEquals(20, TeslaMachineRepairUse.durationTicks(new TeslaMachineProfile.Repair(1, 5, "Welding")));
        assertEquals(21, TeslaMachineRepairUse.durationTicks(new TeslaMachineProfile.Repair(1.01, 5, "Welding")));
        assertEquals(-1, TeslaMachineRepairUse.durationTicks(null));
        assertEquals(-1, TeslaMachineRepairUse.durationTicks(new TeslaMachineProfile.Repair(0, 5, "Welding")));
    }
}
