package com.juicyslew.moonstation14.ms14.atmos.world;

import com.juicyslew.moonstation14.ms14.atmos.core.BoundedGasEqualizer;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class BoundedRegionDiscoveryTest {
    @Test
    void discoversThreeDimensionalRoomToCompletion() {
        Set<BlockPos> room = new HashSet<>();
        for (int x = 0; x < 4; x++) for (int y = 0; y < 4; y++) for (int z = 0; z < 3; z++) {
            room.add(new BlockPos(x, y, z));
        }
        BoundedRegionDiscovery discovery = new BoundedRegionDiscovery(BlockPos.ZERO);
        BoundedRegionDiscovery.Snapshot result = runToCompletion(discovery,
                pos -> room.contains(pos) ? BoundedRegionDiscovery.ProbeResult.FINITE_LOADED
                        : BoundedRegionDiscovery.ProbeResult.BLOCKED);
        assertEquals(BoundedRegionDiscovery.Status.COMPLETE, result.status());
        assertEquals(48, result.cells().size());
        assertEquals(room, new HashSet<>(result.cells()));
    }

    @Test
    void connectedHallCanBeConsumedInPatchesWithoutLosingFrontier() {
        BoundedRegionDiscovery discovery = new BoundedRegionDiscovery(BlockPos.ZERO);
        var probe = (BoundedRegionDiscovery.NeighborProbe) pos -> pos.getX() >= 0 && pos.getX() < 900
                && pos.getY() == 0 && pos.getZ() == 0
                ? BoundedRegionDiscovery.ProbeResult.FINITE_LOADED : BoundedRegionDiscovery.ProbeResult.BLOCKED;
        BoundedRegionDiscovery.Snapshot first = runUntil(discovery, probe, BoundedRegionDiscovery.Status.PATCH_READY);
        assertEquals(800, first.cells().size());
        assertEquals(800, new HashSet<>(first.cells()).size());
        discovery.consumePatch();
        BoundedRegionDiscovery.Snapshot second = runToCompletion(discovery, probe);
        assertEquals(100, second.cells().size());
        assertEquals(900, second.cells().size() + first.cells().size());
        Set<BlockPos> all = new HashSet<>(first.cells());
        all.addAll(second.cells());
        assertEquals(900, all.size());
        assertTrue(all.contains(new BlockPos(899, 0, 0)));
    }

    @Test
    void hardCandidateLimitIsNotReportedAsExteriorOrCompletion() {
        BoundedRegionDiscovery discovery = new BoundedRegionDiscovery(BlockPos.ZERO);
        var result = runUntil(discovery, pos -> pos.getX() >= 0 && pos.getX() < 9000
                        && pos.getY() == 0 && pos.getZ() == 0
                        ? BoundedRegionDiscovery.ProbeResult.FINITE_LOADED : BoundedRegionDiscovery.ProbeResult.BLOCKED,
                BoundedRegionDiscovery.Status.HARD_LIMIT);
        assertEquals(BoundedRegionDiscovery.MAX_CANDIDATES, result.distinctCandidates());
        assertNotEquals(BoundedRegionDiscovery.Status.COMPLETE, result.status());
        assertFalse(result.boundaryContact());
        assertTrue(result.cells().size() <= BoundedRegionDiscovery.MAX_PATCH_CELLS);
        assertFalse(result.continuationSeeds().isEmpty());
        assertTrue(result.continuationSeeds().stream().noneMatch(result.cells()::contains));
    }

    @Test
    void hardLimitContinuationMovesBeyondTheOriginalPrefixWithoutResettingAtOrigin() {
        var probe = (BoundedRegionDiscovery.NeighborProbe) pos -> pos.getX() >= 0 && pos.getX() < 9001
                && pos.getY() == 0 && pos.getZ() == 0
                ? BoundedRegionDiscovery.ProbeResult.FINITE_LOADED : BoundedRegionDiscovery.ProbeResult.BLOCKED;
        BoundedRegionDiscovery first = new BoundedRegionDiscovery(BlockPos.ZERO);
        BoundedRegionDiscovery.Snapshot limited = runToHardLimit(first, probe);
        BlockPos continuation = limited.continuationSeeds().stream()
                .max(java.util.Comparator.comparingInt(BlockPos::getX)).orElseThrow();
        assertTrue(continuation.getX() > 800);
        assertNotEquals(BlockPos.ZERO, continuation);

        BoundedRegionDiscovery next = new BoundedRegionDiscovery(continuation);
        BoundedRegionDiscovery.Snapshot nextLimit = runToHardLimit(next, probe);
        assertTrue(nextLimit.cells().stream().anyMatch(pos -> pos.getX() > 800));
        assertTrue(nextLimit.cells().stream().mapToInt(BlockPos::getX).max().orElseThrow() > continuation.getX());
        assertNotEquals(BoundedRegionDiscovery.Status.COMPLETE, nextLimit.status());
        assertFalse(nextLimit.boundaryContact());
    }

    @Test
    void partialHardLimitPatchConservesSpeciesAndThermalEnergy() {
        var discovery = new BoundedRegionDiscovery(BlockPos.ZERO);
        BoundedRegionDiscovery.Snapshot hardLimit = runToHardLimit(discovery,
                pos -> pos.getX() >= 0 && pos.getX() < 9000 && pos.getY() == 0 && pos.getZ() == 0
                        ? BoundedRegionDiscovery.ProbeResult.FINITE_LOADED
                        : BoundedRegionDiscovery.ProbeResult.BLOCKED);
        assertEquals(BoundedRegionDiscovery.Status.HARD_LIMIT, hardLimit.status());
        assertTrue(hardLimit.cells().size() <= BoundedRegionDiscovery.MAX_PATCH_CELLS);
        // A patch can be empty at the precise candidate-count boundary; the kernel conservation
        // check below uses the kind of bounded finite patch emitted by this job.
        List<GasMixture> input = List.of(
                new GasMixture(java.util.Map.of(GasType.OXYGEN, 90.0), 360.0),
                new GasMixture(java.util.Map.of(GasType.NITROGEN, 20.0), 180.0),
                GasMixture.vacuum());
        List<GasMixture> output = AtmosphereService.equalizeCurrentPatch(input);
        for (GasType type : GasType.values()) {
            assertEquals(input.stream().mapToDouble(cell -> cell.moles(type)).sum(),
                    output.stream().mapToDouble(cell -> cell.moles(type)).sum(), 1.0e-8);
        }
        assertEquals(input.stream().mapToDouble(GasMixture::thermalEnergy).sum(),
                output.stream().mapToDouble(GasMixture::thermalEnergy).sum(), 1.0e-7);
        var remainder = discovery.consumePatch();
        assertEquals(BoundedRegionDiscovery.Status.HARD_LIMIT, remainder.status());
        assertTrue(remainder.cells().size() <= BoundedRegionDiscovery.MAX_PATCH_CELLS);
    }

    @Test
    void unknownCandidatePausesAndCanBeRetriedAndExteriorIsOnlyBoundary() {
        BoundedRegionDiscovery discovery = new BoundedRegionDiscovery(BlockPos.ZERO);
        AtomicInteger calls = new AtomicInteger();
        var unknown = discovery.step(pos -> {
            calls.incrementAndGet();
            return BoundedRegionDiscovery.ProbeResult.UNKNOWN_UNLOADED;
        }, 10);
        assertEquals(BoundedRegionDiscovery.Status.UNKNOWN_CHUNK, unknown.status());
        assertEquals(1, calls.get());
        var complete = runToCompletion(discovery, pos -> pos.equals(BlockPos.ZERO)
                ? BoundedRegionDiscovery.ProbeResult.FINITE_LOADED
                : BoundedRegionDiscovery.ProbeResult.EXTERIOR_IMMUTABLE);
        assertEquals(BoundedRegionDiscovery.Status.COMPLETE, complete.status());
        assertEquals(1, complete.cells().size());
        assertTrue(complete.boundaryContact());
        assertFalse(complete.cells().contains(new BlockPos(0, -1, 0)));
        assertEquals(6, complete.exteriorSeeds().size());
    }

    @Test
    void stepNeverExceedsProbeBudgetAndBfsIsReproducible() {
        Set<BlockPos> room = new HashSet<>();
        for (int x = 0; x < 5; x++) for (int y = 0; y < 5; y++) room.add(new BlockPos(x, y, 0));
        var first = new BoundedRegionDiscovery(new BlockPos(2, 2, 0));
        var second = new BoundedRegionDiscovery(new BlockPos(2, 2, 0));
        var probe = (BoundedRegionDiscovery.NeighborProbe) pos -> room.contains(pos)
                ? BoundedRegionDiscovery.ProbeResult.FINITE_LOADED : BoundedRegionDiscovery.ProbeResult.BLOCKED;
        for (int i = 0; i < 1000; i++) {
            AtomicInteger count = new AtomicInteger();
            var a = first.step(pos -> { count.incrementAndGet(); return probe.inspect(pos); }, 3);
            assertTrue(count.get() <= 3);
            var b = second.step(probe, 3);
            assertEquals(a.cells(), b.cells());
            assertEquals(a.status(), b.status());
            if (a.status() == BoundedRegionDiscovery.Status.COMPLETE) return;
        }
        fail("Discovery did not finish");
    }

    @Test
    void budgetExhaustionIsProgressNotAConsumablePartialPatch() {
        BoundedRegionDiscovery discovery = new BoundedRegionDiscovery(BlockPos.ZERO);
        var progress = discovery.step(pos -> BoundedRegionDiscovery.ProbeResult.FINITE_LOADED, 1);
        assertEquals(BoundedRegionDiscovery.Status.IN_PROGRESS, progress.status());
        assertTrue(progress.cells().size() < BoundedRegionDiscovery.MAX_PATCH_CELLS);
        assertThrows(IllegalStateException.class, discovery::consumePatch);
    }

    @Test
    void completedRoomPatchCarriesGasAcrossTheFullFourCellDistance() {
        Set<BlockPos> room = new HashSet<>();
        for (int x = 0; x < 4; x++) room.add(new BlockPos(x, 0, 0));
        var discovery = new BoundedRegionDiscovery(BlockPos.ZERO);
        var patch = runToCompletion(discovery, pos -> room.contains(pos)
                ? BoundedRegionDiscovery.ProbeResult.FINITE_LOADED
                : BoundedRegionDiscovery.ProbeResult.BLOCKED);
        List<GasMixture> before = new java.util.ArrayList<>();
        for (BlockPos pos : patch.cells()) before.add(pos.getX() == 0
                ? new GasMixture(java.util.Map.of(GasType.OXYGEN, 40.0), 300.0) : GasMixture.vacuum());
        List<GasMixture> after = BoundedGasEqualizer.equalize(before);
        assertTrue(after.get(patch.cells().indexOf(new BlockPos(3, 0, 0))).totalMoles() > 0.0);
    }

    @Test
    void gasChangesDuringDiscoveryUseCurrentMixturesAndOnlyTopologyCancelsJob() {
        AtmosphereService.EqualizationJob job = new AtmosphereService.EqualizationJob(BlockPos.ZERO);
        job.discovery.step(pos -> BoundedRegionDiscovery.ProbeResult.FINITE_LOADED, 1);
        // These are the current states at commit, not the values which may have existed when
        // discovery first encountered the seed; an unrelated gas write must not cancel the plan.
        GasMixture changedCell = new GasMixture(java.util.Map.of(GasType.OXYGEN, 30.0), 315.0);
        List<GasMixture> equalized = AtmosphereService.equalizeCurrentPatch(
                List.of(changedCell, GasMixture.vacuum()));
        assertEquals(30.0, equalized.stream().mapToDouble(GasMixture::totalMoles).sum(), 1.0e-9);
        assertEquals(equalized.get(0).totalMoles(), equalized.get(1).totalMoles(), 1.0e-9);

        // Ordinary gas mutations do not invalidate a discovery cursor; graph edits do.
        assertTrue(job.isActive());
        job.cancel();
        assertFalse(job.isActive());
    }

    private static BoundedRegionDiscovery.Snapshot runToCompletion(BoundedRegionDiscovery discovery,
                                                                    BoundedRegionDiscovery.NeighborProbe probe) {
        return runUntil(discovery, probe, BoundedRegionDiscovery.Status.COMPLETE);
    }

    private static BoundedRegionDiscovery.Snapshot runToHardLimit(BoundedRegionDiscovery discovery,
                                                                   BoundedRegionDiscovery.NeighborProbe probe) {
        BoundedRegionDiscovery.Snapshot result = null;
        for (int i = 0; i < 100000; i++) {
            result = discovery.step(probe, 512);
            if (result.status() == BoundedRegionDiscovery.Status.PATCH_READY
                    && result.cells().size() == BoundedRegionDiscovery.MAX_PATCH_CELLS) {
                discovery.consumePatch();
            } else if (result.status() == BoundedRegionDiscovery.Status.HARD_LIMIT) {
                return result;
            } else if (result.status() == BoundedRegionDiscovery.Status.COMPLETE
                    || result.status() == BoundedRegionDiscovery.Status.UNKNOWN_CHUNK) {
                fail("Unexpected discovery status before hard limit: " + result.status());
            }
        }
        fail("Discovery did not reach hard limit");
        return result;
    }

    private static BoundedRegionDiscovery.Snapshot runUntil(BoundedRegionDiscovery discovery,
                                                             BoundedRegionDiscovery.NeighborProbe probe,
                                                             BoundedRegionDiscovery.Status target) {
        BoundedRegionDiscovery.Snapshot result = null;
        for (int i = 0; i < 100000; i++) {
            result = discovery.step(probe, 128);
            if (result.status() == BoundedRegionDiscovery.Status.PATCH_READY
                    && result.cells().size() == BoundedRegionDiscovery.MAX_PATCH_CELLS) {
                if (target == BoundedRegionDiscovery.Status.PATCH_READY) return result;
                discovery.consumePatch();
            } else if (result.status() == target
                    && result.status() != BoundedRegionDiscovery.Status.PATCH_READY) return result;
            else if (result.status() == BoundedRegionDiscovery.Status.UNKNOWN_CHUNK
                    || result.status() == BoundedRegionDiscovery.Status.HARD_LIMIT
                    || result.status() == BoundedRegionDiscovery.Status.CANCELLED) {
                if (result.status() == target) return result;
                fail("Unexpected discovery status: " + result.status());
            }
        }
        fail("Discovery did not reach " + target);
        return result;
    }
}
