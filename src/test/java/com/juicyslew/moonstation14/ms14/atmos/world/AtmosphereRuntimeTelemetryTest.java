package com.juicyslew.moonstation14.ms14.atmos.world;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtmosphereRuntimeTelemetryTest {
    @Test void admissionsResumptionsAndCompletionsAreIndependent() {
        AtmosphereRuntimeTelemetry t = new AtmosphereRuntimeTelemetry(100, 1_000_000_000L);
        t.callback(); t.admitted(AtmosphereCycle.Stage.REVALIDATE);
        t.callback(); t.admitted(AtmosphereCycle.Stage.REVALIDATE);
        t.callback(); t.admitted(AtmosphereCycle.Stage.MONSTERMOS);
        var s = t.snapshot(120, 3_000_000_000L, 7, "MONSTERMOS", 5, 0, 0, 0, 0, 0, 0, 0);
        assertEquals(3, s.admissions()); assertEquals(1, s.resumptions());
        assertEquals(7, s.completedCycles()); assertEquals(3, s.callbacks());
        assertEquals(1.5, s.admissionsPerWallSecond());
    }

    @Test void snapshotsAreReadOnlyAndRatesRemainFiniteForResetOrZeroWindows() {
        AtmosphereRuntimeTelemetry t = new AtmosphereRuntimeTelemetry(20, 2_000_000_000L);
        t.callback(); t.admitted(AtmosphereCycle.Stage.LINDA);
        var before = t.snapshot(10, 1_000_000_000L, 2, "LINDA", 0, 0, 0, 0, 0, 0, 0, 0);
        var again = t.snapshot(10, 1_000_000_000L, 2, "LINDA", 0, 0, 0, 0, 0, 0, 0, 0);
        assertEquals(before, again);
        assertEquals(0, before.elapsedGameTicks()); assertEquals(0, before.elapsedWallMs());
        assertTrue(Double.isFinite(before.callbacksPerWallSecond()));
        assertTrue(Double.isFinite(before.admissionsPerGameSecond()));
        assertEquals(0, before.callbacksPerWallSecond());
    }

    @Test void workCountersAreCumulativeAndClampNegativeInputs() {
        AtmosphereRuntimeTelemetry t = new AtmosphereRuntimeTelemetry(0, 0);
        t.lindaSources(4); t.lindaSources(-2); t.equalizationCommitted(3, 2); t.equalizationCommitted(-1, -1);
        t.ownershipClaimed(2); t.ownershipClaimed(-1);
        var s = t.snapshot(20, 1_000_000_000L, 0, "REVALIDATE", 0, 0, 0, 0, 0, 0, 0, 0);
        assertEquals(4, s.lindaSources()); assertEquals(2, s.equalizationPatches());
        assertEquals(3, s.equalizationCells()); assertEquals(2, s.equalizationIncludedCells());
        assertEquals(2, s.ownershipClaimedCells());
    }

    @Test void committedTransportCountersAccumulateZeroAndPositiveWork() {
        AtmosphereRuntimeTelemetry t = new AtmosphereRuntimeTelemetry(0, 0);
        t.pressureFaceTransfers(0); t.pressureFaceTransfers(7); t.pressureFaceTransfers(-2);
        t.lindaSharedPairs(3); t.lindaSharedPairs(0); t.lindaSharedPairs(-1);
        t.groupAveragedMembers(12); t.groupAveragedMembers(0); t.groupAveragedMembers(-4);
        var s = t.snapshot(1, 1_000_000L, 0, "MONSTERMOS", 0, 0, 0, 0, 0, 0, 0, 0);
        assertEquals(7, s.pressureFaceTransfers());
        assertEquals(3, s.lindaSharedPairs());
        assertEquals(12, s.groupAveragedMembers());
    }

    @Test void groupCommittedMemberCountExcludesImmutableUnchangedAndUncapturedCells() {
        var a = new net.minecraft.core.BlockPos(0, 1, 0);
        var b = a.east();
        var missing = b.east();
        var before = GasMixture.breathableAir();
        var changed = GasMixture.vacuum();
        assertEquals(1, AtmosphereService.committedGroupFiniteMembers(
                java.util.Map.of(a, before, b, before), java.util.Map.of(a, changed, b, changed, missing, changed),
                java.util.Set.of(b)));
        assertEquals(0, AtmosphereService.committedGroupFiniteMembers(
                java.util.Map.of(a, before), java.util.Map.of(a, before), java.util.Set.of()));
    }

    @Test void excitedGroupPhaseCountersExposeRealSnapshotsAndSeparateElapsedPhases() {
        AtmosphereRuntimeTelemetry t = new AtmosphereRuntimeTelemetry(0, 0);
        t.groupWork(1, 5000, 1, 0, 2_000_000, 3_000_000);
        t.groupPublication(4_000_000);
        var s = t.snapshot(20, 1_000_000_000L, 0, "GROUPS", 0, 0, 0, 0, 0, 0, 0, 0);
        assertEquals(1, s.groupOperations()); assertEquals(5000, s.groupMembersCaptured());
        assertEquals(1, s.groupAverages()); assertEquals(2.0, s.groupCaptureMs());
        assertEquals(3.0, s.groupAverageMs()); assertEquals(4.0, s.groupPublicationMs());
    }

    @Test void budgetDrainRepeatsProgressStopsOnStallAndAlwaysAllowsOneSlice() {
        int[] runs = {0};
        int completed = BoundedBudgetDrain.drain(() -> runs[0] >= 3, () -> ++runs[0] < 10);
        assertEquals(3, completed);
        assertEquals(3, runs[0]);
        int[] stalled = {0};
        assertEquals(1, BoundedBudgetDrain.drain(() -> false, () -> { stalled[0]++; return false; }));
        assertEquals(1, stalled[0]);
        int[] expired = {0};
        assertEquals(1, BoundedBudgetDrain.drain(() -> true, () -> { expired[0]++; return true; }));
    }
}
