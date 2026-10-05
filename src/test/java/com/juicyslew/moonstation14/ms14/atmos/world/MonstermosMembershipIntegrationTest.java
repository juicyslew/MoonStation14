package com.juicyslew.moonstation14.ms14.atmos.world;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class MonstermosMembershipIntegrationTest {
    @Test void onlySolverIncludedFiniteNodesAreMarkedProcessed() {
        BlockPos included = new BlockPos(0, 0, 0), omitted = new BlockPos(1, 0, 0);
        BlockPos immutableEndpoint = new BlockPos(2, 0, 0);
        Set<BlockPos> finite = Set.of(included, omitted);
        Set<BlockPos> processed = AtmosphereService.monstermosProcessedFiniteCells(
                Set.of(included, immutableEndpoint), finite);
        assertEquals(Set.of(included), processed);
        assertFalse(processed.contains(omitted));
        assertFalse(processed.contains(immutableEndpoint));
    }

    @Test void noIncludedMembershipMarksNoFiniteSource() {
        assertTrue(AtmosphereService.monstermosProcessedFiniteCells(Set.of(), Set.of(new BlockPos(0, 0, 0))).isEmpty());
    }

    @Test void newSourceDiscoveryCanTraversePreviouslyProcessedSeamCells() {
        Set<BlockPos> previousSourceMembership = new java.util.HashSet<>();
        ArrayList<BlockPos> connected = new ArrayList<>();
        for (int x = 0; x < 420; x++) {
            BlockPos pos = new BlockPos(x, 0, 0); connected.add(pos);
            if (x < 200) previousSourceMembership.add(pos);
        }
        BoundedRegionDiscovery discovery = new BoundedRegionDiscovery(new BlockPos(200, 0, 0));
        BoundedRegionDiscovery.Snapshot result;
        do {
            result = discovery.step(pos -> pos.getY() == 0 && pos.getZ() == 0 && pos.getX() >= 0 && pos.getX() < connected.size()
                    ? BoundedRegionDiscovery.ProbeResult.FINITE_LOADED
                    : BoundedRegionDiscovery.ProbeResult.BLOCKED, 384);
            if (result.status() == BoundedRegionDiscovery.Status.PATCH_READY) break;
        } while (result.status() == BoundedRegionDiscovery.Status.IN_PROGRESS);
        assertEquals(BoundedRegionDiscovery.Status.COMPLETE, result.status());
        assertTrue(result.cells().stream().anyMatch(previousSourceMembership::contains),
                "new BFS must cross already-included cells to overlap the prior patch");
    }

    @Test void expiredDiscoveryAdmissionRetainsCursorAndMatchesUninterruptedSnapshot() {
        var loaded = new java.util.HashSet<BlockPos>();
        for (int x = 0; x < 30; x++) for (int y = 0; y < 20; y++) loaded.add(new BlockPos(x, y, 0));
        var resumed = new BoundedRegionDiscovery(new BlockPos(10, 10, 0));
        BoundedRegionDiscovery.Snapshot[] partial = {null};
        int[] slices = {0};
        BoundedBudgetDrain.drain(() -> slices[0] >= 2, () -> {
            slices[0]++;
            partial[0] = resumed.step(p -> loaded.contains(p) ? BoundedRegionDiscovery.ProbeResult.FINITE_LOADED
                    : BoundedRegionDiscovery.ProbeResult.BLOCKED, AtmosphereService.MAX_DISCOVERY_PROBES_PER_SLICE);
            return partial[0].status() == BoundedRegionDiscovery.Status.IN_PROGRESS;
        });
        assertEquals(2, slices[0]);
        int cursorCandidates = partial[0].distinctCandidates();
        assertTrue(cursorCandidates > AtmosphereService.MAX_DISCOVERY_PROBES_PER_SLICE);

        while (partial[0].status() == BoundedRegionDiscovery.Status.IN_PROGRESS) {
            BoundedBudgetDrain.drain(() -> false, () -> {
                partial[0] = resumed.step(p -> loaded.contains(p) ? BoundedRegionDiscovery.ProbeResult.FINITE_LOADED
                        : BoundedRegionDiscovery.ProbeResult.BLOCKED, AtmosphereService.MAX_DISCOVERY_PROBES_PER_SLICE);
                return partial[0].status() == BoundedRegionDiscovery.Status.IN_PROGRESS;
            });
        }
        var uninterrupted = new BoundedRegionDiscovery(new BlockPos(10, 10, 0));
        BoundedRegionDiscovery.Snapshot expected;
        do {
            expected = uninterrupted.step(p -> loaded.contains(p) ? BoundedRegionDiscovery.ProbeResult.FINITE_LOADED
                    : BoundedRegionDiscovery.ProbeResult.BLOCKED, 100_000);
        } while (expected.status() == BoundedRegionDiscovery.Status.IN_PROGRESS);
        assertEquals(expected.status(), partial[0].status());
        assertEquals(expected.cells(), partial[0].cells());
    }

    @Test void rejectedExteriorWitnessRequeuesAndYieldsCurrentAdmission() {
        var work = new AtmosphereService.OwnershipWork();
        BlockPos seed = new BlockPos(4, 5, 6);
        int slices = BoundedBudgetDrain.drain(() -> false,
                () -> !AtmosphereService.rejectExteriorWitnessForAdmission(work, seed));
        assertEquals(1, slices);
        assertEquals(1, work.queuedSeedCount());
        assertNull(work.active);
    }
}
