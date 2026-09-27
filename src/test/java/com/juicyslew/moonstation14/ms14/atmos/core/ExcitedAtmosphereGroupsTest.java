package com.juicyslew.moonstation14.ms14.atmos.core;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ExcitedAtmosphereGroupsTest {
    private static final BlockPos A = new BlockPos(0, 0, 0);
    private static final BlockPos B = new BlockPos(1, 0, 0);
    private static final BlockPos C = new BlockPos(2, 0, 0);
    private static final BlockPos D = new BlockPos(3, 0, 0);

    @Test
    void formsAndMergesGroupsAndOnlyGroupsBypassLindaGate() {
        ExcitedAtmosphereGroups groups = new ExcitedAtmosphereGroups();
        Map<BlockPos, GasMixture> cells = cells(A, B, C, D);
        groups.onShare(LindaGasSharing.Pair.of(A, B), 1.0, cells);
        groups.onShare(LindaGasSharing.Pair.of(C, D), 1.0, cells);
        assertTrue(groups.sameGroupPair(LindaGasSharing.Pair.of(A, B)));
        assertFalse(groups.sameGroupPair(LindaGasSharing.Pair.of(B, C)));
        groups.onShare(LindaGasSharing.Pair.of(B, C), 1.0, cells);
        assertTrue(groups.sameGroupPair(LindaGasSharing.Pair.of(A, D)),
                "all cells in the merged group share a membership id");
        assertTrue(groups.sameGroupPair(LindaGasSharing.Pair.of(B, C)));
        assertTrue(groups.sameGroupPair(LindaGasSharing.Pair.of(C, D)));
    }

    @Test
    void usesStrictMovementThresholdsAndNoMovementDoesNotResetCooldown() {
        ExcitedAtmosphereGroups groups = new ExcitedAtmosphereGroups();
        Map<BlockPos, GasMixture> cells = cells(A, B);
        groups.onShare(LindaGasSharing.Pair.of(A, B), ExcitedAtmosphereGroups.MINIMUM_MOLES_DELTA_TO_MOVE, cells);
        assertTrue(groups.sameGroupPair(LindaGasSharing.Pair.of(A, B)),
                "a passed qualifying LINDA pair forms a group even at the exact movement threshold");
        groups.onShare(LindaGasSharing.Pair.of(A, B), ExcitedAtmosphereGroups.MINIMUM_MOLES_DELTA_TO_MOVE + 1e-6, cells);
        for (int i = 0; i < 3; i++) groups.advanceFullCycle(cells, 800);
        groups.onShare(LindaGasSharing.Pair.of(A, B), ExcitedAtmosphereGroups.MINIMUM_AIR_TO_SUSPEND, cells);
        assertTrue(groups.advanceFullCycle(cells, 800).replacements().isEmpty());
        assertEquals(2, groups.advanceFullCycle(cells, 800).replacements().size(),
                "the exact suspension threshold does not reset breakdown");

        ExcitedAtmosphereGroups high = new ExcitedAtmosphereGroups();
        high.onShare(LindaGasSharing.Pair.of(A, B), ExcitedAtmosphereGroups.MINIMUM_MOLES_DELTA_TO_MOVE + 1e-6, cells);
        for (int i = 0; i < 4; i++) high.advanceFullCycle(cells, 800);
        high.onShare(LindaGasSharing.Pair.of(A, B), ExcitedAtmosphereGroups.MINIMUM_AIR_TO_SUSPEND + 1e-6, cells);
        assertTrue(high.advanceFullCycle(cells, 800).replacements().isEmpty());
    }

    @Test
    void breaksDownOnFifthCycleAndConservesSpeciesAndEnergyWithEqualVolumeAverage() {
        ExcitedAtmosphereGroups groups = new ExcitedAtmosphereGroups();
        Map<BlockPos, GasMixture> cells = new HashMap<>();
        cells.put(A, mix(Map.of(GasType.OXYGEN, 6.0, GasType.NITROGEN, 5.0), 293.0));
        cells.put(B, mix(Map.of(GasType.OXYGEN, 5.0, GasType.NITROGEN, 6.0), 294.0));
        groups.onShare(LindaGasSharing.Pair.of(A, B), 1, cells);
        for (int i = 0; i < 4; i++) assertTrue(groups.advanceFullCycle(cells, 800).replacements().isEmpty());
        var cycle = groups.advanceFullCycle(cells, 800);
        assertEquals(2, cycle.replacements().size());
        GasMixture average = cycle.replacements().get(A);
        assertEquals(5.5, average.moles(GasType.OXYGEN), 1e-12);
        assertEquals(5.5, average.moles(GasType.NITROGEN), 1e-12);
        assertEquals(average.moles(GasType.OXYGEN), cycle.replacements().get(B).moles(GasType.OXYGEN), 0.0);
        assertEquals(average.temperatureKelvin(), cycle.replacements().get(B).temperatureKelvin(), 0.0);
        assertEquals(11, average.totalMoles(), 1e-12, "each cell remains a one cubic meter mixture");
        assertEquals(22, cycle.replacements().values().stream().mapToDouble(GasMixture::totalMoles).sum(), 1e-12);
        assertEquals(cells.values().stream().mapToDouble(GasMixture::thermalEnergy).sum(),
                cycle.replacements().values().stream().mapToDouble(GasMixture::thermalEnergy).sum(), 1e-9);
    }

    @Test
    void energyAverageUsesUnequalHeatCapacities() {
        ExcitedAtmosphereGroups groups = new ExcitedAtmosphereGroups();
        Map<BlockPos, GasMixture> cells = Map.of(A, mix(Map.of(GasType.OXYGEN, 1.0), 299),
                B, mix(Map.of(GasType.FREZON, 1.0), 301));
        groups.onShare(LindaGasSharing.Pair.of(A, B), 1, cells);
        for (int i = 0; i < 4; i++) groups.advanceFullCycle(cells, 800);
        GasMixture result = groups.advanceFullCycle(cells, 800).replacements().get(A);
        double expected = (20.0 * 299 + 600.0 * 301) / (20.0 + 600.0);
        assertEquals(expected, result.temperatureKelvin(), 1e-10);
        assertEquals(cells.values().stream().mapToDouble(GasMixture::thermalEnergy).sum(),
                result.thermalEnergy() * 2, 1e-9);
    }

    @Test
    void zeroMoleTemperatureQualifiedShareFormsGroupAveragesHeatAndEventuallyDismantles() {
        ExcitedAtmosphereGroups groups = new ExcitedAtmosphereGroups();
        Map<BlockPos, GasMixture> cells = Map.of(A, mix(Map.of(GasType.OXYGEN, 5.0), 299),
                B, mix(Map.of(GasType.OXYGEN, 5.0), 301));
        double initialEnergy = cells.values().stream().mapToDouble(GasMixture::thermalEnergy).sum();

        // LINDA's >4 K temperature comparison qualifies this pair despite zero gas movement.
        groups.onShare(LindaGasSharing.Pair.of(A, B), 0.0, cells);
        assertTrue(groups.sameGroupPair(LindaGasSharing.Pair.of(A, B)));
        for (int cycle = 0; cycle < 4; cycle++)
            assertTrue(groups.advanceFullCycle(cells, 800).replacements().isEmpty());
        var breakdown = groups.advanceFullCycle(cells, 800);
        assertEquals(300.0, breakdown.replacements().get(A).temperatureKelvin(), 1e-12);
        assertEquals(300.0, breakdown.replacements().get(B).temperatureKelvin(), 1e-12);
        assertTrue(breakdown.replacements().get(A).temperatureKelvin() > cells.get(A).temperatureKelvin());
        assertTrue(breakdown.replacements().get(B).temperatureKelvin() < cells.get(B).temperatureKelvin());
        assertEquals(initialEnergy,
                breakdown.replacements().values().stream().mapToDouble(GasMixture::thermalEnergy).sum(), 1e-9);

        for (int cycle = 5; cycle < 16; cycle++)
            assertTrue(groups.advanceFullCycle(cells, 800).deactivatedCells().isEmpty());
        assertEquals(java.util.Set.of(A, B), groups.advanceFullCycle(cells, 800).deactivatedCells());
        assertFalse(groups.sameGroupPair(LindaGasSharing.Pair.of(A, B)));
    }

    @Test
    void opposingEqualPressureRoomsDoNotInstantlyAverageAndLindaGraduallySettlesBeforeFinalization() {
        ExcitedAtmosphereGroups groups = new ExcitedAtmosphereGroups();
        double standardMoles = 101_325.0 / (8.31446261815324 * 293.15);
        Map<BlockPos, GasMixture> current = new HashMap<>();
        Map<BlockPos, Set<BlockPos>> adjacency = new HashMap<>();
        Set<BlockPos> active = new LinkedHashSet<>();
        Set<LindaGasSharing.Pair> pairs = new LinkedHashSet<>();
        for (int x = 0; x < 8; x++) {
            BlockPos pos = new BlockPos(x, 0, 0);
            current.put(pos, mix(Map.of(x < 4 ? GasType.OXYGEN : GasType.NITROGEN, standardMoles), 293.15));
            adjacency.put(pos, new LinkedHashSet<>());
            active.add(pos);
            if (x > 0) {
                BlockPos previous = new BlockPos(x - 1, 0, 0);
                adjacency.get(pos).add(previous);
                adjacency.get(previous).add(pos);
                LindaGasSharing.Pair pair = LindaGasSharing.Pair.of(previous, pos);
                pairs.add(pair);
                groups.onShare(pair, 1.0, current);
            }
        }
        double initialOxygen = current.values().stream().mapToDouble(mix -> mix.moles(GasType.OXYGEN)).sum();
        double initialNitrogen = current.values().stream().mapToDouble(mix -> mix.moles(GasType.NITROGEN)).sum();

        for (int i = 0; i < 5; i++) {
            var attempted = groups.advanceFullCycle(current, 800);
            assertTrue(attempted.replacements().isEmpty(), "large room gradients must not be fully averaged");
        }
        assertEquals(standardMoles, current.get(new BlockPos(0, 0, 0)).moles(GasType.OXYGEN), 0.0);
        assertEquals(standardMoles, current.get(new BlockPos(7, 0, 0)).moles(GasType.NITROGEN), 0.0);

        boolean finalized = false;
        for (int cycle = 0; cycle < 500 && !finalized; cycle++) {
            Set<LindaGasSharing.Pair> groupedPairs = new LinkedHashSet<>();
            for (LindaGasSharing.Pair pair : pairs) if (groups.sameGroupPair(pair)) groupedPairs.add(pair);
            LindaGasSharing.Result linda = LindaGasSharing.process(current, adjacency, active, groupedPairs, 800);
            current = new HashMap<>(linda.mixtures());
            for (LindaGasSharing.Pair pair : linda.sharedPairs())
                groups.onShare(pair, linda.movedMolesByPair().getOrDefault(pair, 0.0), current);
            var result = groups.advanceFullCycle(current, 800);
            if (!result.replacements().isEmpty()) {
                finalized = true;
                assertEquals(8, result.replacements().size());
                GasMixture uniform = result.replacements().get(new BlockPos(0, 0, 0));
                for (GasMixture mixture : result.replacements().values()) {
                    assertEquals(uniform.moles(GasType.OXYGEN), mixture.moles(GasType.OXYGEN), 0.0);
                    assertEquals(uniform.moles(GasType.NITROGEN), mixture.moles(GasType.NITROGEN), 0.0);
                }
                assertEquals(initialOxygen, result.replacements().values().stream()
                        .mapToDouble(mix -> mix.moles(GasType.OXYGEN)).sum(), 1e-8);
                assertEquals(initialNitrogen, result.replacements().values().stream()
                        .mapToDouble(mix -> mix.moles(GasType.NITROGEN)).sum(), 1e-8);
            }
        }
        assertTrue(finalized, "LINDA diffusion should eventually reach the imperceptible-breakdown tolerance");
    }

    @Test
    void dismantlesAfterSeventeenthQuietCycleAndUnavailableCellsNeverGetWiped() {
        ExcitedAtmosphereGroups groups = new ExcitedAtmosphereGroups();
        Map<BlockPos, GasMixture> cells = cells(A, B);
        groups.onShare(LindaGasSharing.Pair.of(A, B), 1, cells);
        for (int i = 0; i < 16; i++) assertTrue(groups.advanceFullCycle(cells, 800).deactivatedCells().isEmpty());
        var removed = groups.advanceFullCycle(cells, 800);
        assertEquals(java.util.Set.of(A, B), removed.deactivatedCells());
        assertFalse(groups.sameGroupPair(LindaGasSharing.Pair.of(A, B)));

        ExcitedAtmosphereGroups unavailable = new ExcitedAtmosphereGroups();
        unavailable.onShare(LindaGasSharing.Pair.of(A, B), 1, cells);
        for (int i = 0; i < 4; i++) unavailable.advanceFullCycle(cells, 800);
        assertTrue(unavailable.advanceFullCycle(cells, 1).replacements().isEmpty());
        var missing = unavailable.advanceFullCycle(Map.of(A, cells.get(A)), 800);
        assertTrue(missing.replacements().isEmpty());
        assertTrue(missing.deactivatedCells().isEmpty());
        assertTrue(unavailable.sameGroupPair(LindaGasSharing.Pair.of(A, B)));
    }

    @Test
    void budgetedBreakdownRemainsAtomicAndResumableUpToEightHundredCells() {
        ExcitedAtmosphereGroups groups = new ExcitedAtmosphereGroups();
        Map<BlockPos, GasMixture> cells = largeGroup(groups);
        var first = groups.advanceFullCycle(cells, 100);
        assertEquals(100, first.workCount());
        assertTrue(first.replacements().isEmpty());
        ExcitedAtmosphereGroups.CycleResult resumed = null;
        for (int cycle = 0; cycle < 7; cycle++) {
            resumed = groups.advanceFullCycle(cells, 100);
            assertEquals(100, resumed.workCount());
            if (cycle < 6) assertTrue(resumed.replacements().isEmpty());
        }
        assertNotNull(resumed);
        assertEquals(800, resumed.replacements().size());
        assertEquals(1.3995, resumed.replacements().get(new BlockPos(799, 0, 0)).moles(GasType.OXYGEN), 1e-10);
        double originalMoles = cells.values().stream().mapToDouble(GasMixture::totalMoles).sum();
        double outputMoles = resumed.replacements().values().stream().mapToDouble(GasMixture::totalMoles).sum();
        assertEquals(originalMoles, outputMoles, 1e-9);
        assertEquals(cells.values().stream().mapToDouble(GasMixture::thermalEnergy).sum(),
                resumed.replacements().values().stream().mapToDouble(GasMixture::thermalEnergy).sum(), 1e-5);
    }

    @Test
    void discardsPendingAverageIfAnyCellChangesAndNeverPublishesStaleLedger() {
        ExcitedAtmosphereGroups groups = new ExcitedAtmosphereGroups();
        Map<BlockPos, GasMixture> cells = largeGroup(groups);
        assertTrue(groups.advanceFullCycle(cells, 100).replacements().isEmpty());

        Map<BlockPos, GasMixture> changed = new HashMap<>(cells);
        BlockPos changedCell = new BlockPos(0, 0, 0);
        changed.put(changedCell, cells.get(changedCell).withGasDelta(GasType.TRITIUM, 3.0));
        double expectedOxygen = changed.values().stream().mapToDouble(mix -> mix.moles(GasType.OXYGEN)).sum();
        double expectedTritium = changed.values().stream().mapToDouble(mix -> mix.moles(GasType.TRITIUM)).sum();
        double expectedEnergy = changed.values().stream().mapToDouble(GasMixture::thermalEnergy).sum();

        for (int cycle = 0; cycle < 7; cycle++)
            assertTrue(groups.advanceFullCycle(changed, 100).replacements().isEmpty());
        assertEquals(expectedOxygen, changed.values().stream().mapToDouble(mix -> mix.moles(GasType.OXYGEN)).sum(), 0.0);
        assertEquals(expectedTritium, changed.values().stream().mapToDouble(mix -> mix.moles(GasType.TRITIUM)).sum(), 0.0);
        assertEquals(expectedEnergy, changed.values().stream().mapToDouble(GasMixture::thermalEnergy).sum(), 0.0);
        assertEquals(expectedTritium, changed.get(changedCell).moles(GasType.TRITIUM), 0.0);
        assertTrue(groups.sameGroupPair(LindaGasSharing.Pair.of(new BlockPos(0, 0, 0), new BlockPos(1, 0, 0))));
    }

    @Test
    void rejectedOverflowPairCannotPolluteAuthoritativeMembershipOrLoseGroupWork() {
        ExcitedAtmosphereGroups groups = new ExcitedAtmosphereGroups();
        Map<BlockPos, GasMixture> cells = largeGroup(groups);
        BlockPos overflow = new BlockPos(800, 0, 0);
        cells.put(overflow, mix(Map.of(GasType.OXYGEN, 1000.0), 293.15));
        groups.onShare(LindaGasSharing.Pair.of(new BlockPos(799, 0, 0), overflow), 1.0, cells);

        var tracked = groups.trackedCells();
        assertEquals(800, tracked.size());
        assertFalse(tracked.contains(overflow));
        assertThrows(UnsupportedOperationException.class, () -> tracked.add(overflow));
        Map<BlockPos, GasMixture> authoritativeCurrent = new HashMap<>();
        tracked.forEach(pos -> authoritativeCurrent.put(pos, cells.get(pos)));

        var breakdown = groups.advanceFullCycle(authoritativeCurrent, 800);
        assertEquals(800, breakdown.replacements().size());
        assertFalse(breakdown.replacements().containsKey(overflow));
        assertEquals(1.3995, breakdown.replacements().get(new BlockPos(0, 0, 0)).moles(GasType.OXYGEN), 1e-10);
        assertEquals(1000.0, cells.get(overflow).moles(GasType.OXYGEN), 0.0);
        for (int cycle = 0; cycle < 11; cycle++)
            assertTrue(groups.advanceFullCycle(authoritativeCurrent, 800).deactivatedCells().isEmpty());
        assertEquals(800, groups.advanceFullCycle(authoritativeCurrent, 800).deactivatedCells().size());
        assertTrue(groups.trackedCells().isEmpty());
    }

    private static Map<BlockPos, GasMixture> largeGroup(ExcitedAtmosphereGroups groups) {
        Map<BlockPos, GasMixture> cells = new HashMap<>();
        for (int x = 0; x < 800; x++) cells.put(new BlockPos(x, 0, 0), mix(Map.of(GasType.OXYGEN, 1.0 + x * 0.001), 293.15));
        groups.onShare(LindaGasSharing.Pair.of(new BlockPos(0, 0, 0), new BlockPos(1, 0, 0)), 1, cells);
        // Extend membership through qualifying neighboring shares; cap is exactly 800 cells.
        for (int x = 1; x < 799; x++)
            groups.onShare(LindaGasSharing.Pair.of(new BlockPos(x, 0, 0), new BlockPos(x + 1, 0, 0)), 1, cells);
        for (int i = 0; i < 4; i++) groups.advanceFullCycle(cells, 800);
        return cells;
    }

    private static Map<BlockPos, GasMixture> cells(BlockPos... positions) {
        Map<BlockPos, GasMixture> result = new HashMap<>();
        for (BlockPos pos : positions) result.put(pos, mix(Map.of(GasType.OXYGEN, 1.0), 293.15));
        return result;
    }

    private static GasMixture mix(Map<GasType, Double> moles, double temperature) {
        return new GasMixture(moles, temperature);
    }
}
