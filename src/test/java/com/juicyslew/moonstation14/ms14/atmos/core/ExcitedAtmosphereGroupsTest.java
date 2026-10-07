package com.juicyslew.moonstation14.ms14.atmos.core;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ExcitedAtmosphereGroupsTest {
    private static final BlockPos A = new BlockPos(0, 0, 0);
    private static final BlockPos B = new BlockPos(1, 0, 0);
    private static final BlockPos C = new BlockPos(2, 0, 0);
    private static final BlockPos D = new BlockPos(3, 0, 0);

    @Test
    void cycleReadsOnlyTrackedCellsFromLoadedMixtureMap() {
        ExcitedAtmosphereGroups groups = new ExcitedAtmosphereGroups();
        Map<BlockPos, GasMixture> cells = cells(A, B);
        groups.onShare(LindaGasSharing.Pair.of(A, B), 0, cells);
        Map<BlockPos, GasMixture> nonEnumerable = new AbstractMap<>() {
            @Override public GasMixture get(Object key) { return cells.get(key); }
            @Override public boolean containsKey(Object key) { return cells.containsKey(key); }
            @Override public Set<Entry<BlockPos, GasMixture>> entrySet() {
                throw new AssertionError("cycle must not enumerate the world-sized map");
            }
        };

        for (long cycle = 1; cycle <= 4; cycle++)
            assertTrue(groups.advanceCycle(cycle, nonEnumerable, Set.of(), Set.of(), false, 1).replacements().isEmpty());
        assertEquals(2, groups.advanceCycle(5, nonEnumerable, Set.of(), Set.of(), false, 1).replacements().size());
    }

    @Test
    void providerCapturesOnlyCurrentGroupAndDeadlineResumesBetweenWholeOperations() {
        ExcitedAtmosphereGroups groups = new ExcitedAtmosphereGroups();
        Map<BlockPos, GasMixture> cells = cells(A, B, C, D);
        groups.onShare(LindaGasSharing.Pair.of(A, B), 0, cells);
        groups.onShare(LindaGasSharing.Pair.of(C, D), 0, cells);
        List<Set<BlockPos>> captured = new java.util.ArrayList<>();
        var first = groups.advanceCycle(1, members -> {
            captured.add(members);
            Map<BlockPos, GasMixture> local = new HashMap<>();
            members.forEach(pos -> local.put(pos, cells.get(pos)));
            return new ExcitedAtmosphereGroups.LoadedGroupSnapshot(local, Set.of(), Set.of());
        }, false, 10, () -> true);
        assertEquals(1, first.groupOperations());
        assertTrue(captured.isEmpty(), "cooldown ticks must not snapshot gas members");
        assertEquals(1, groups.advanceCycle(1, members -> {
            captured.add(members);
            Map<BlockPos, GasMixture> local = new HashMap<>();
            members.forEach(pos -> local.put(pos, cells.get(pos)));
            return new ExcitedAtmosphereGroups.LoadedGroupSnapshot(local, Set.of(), Set.of());
        }, false, 10, () -> false).groupOperations());
        assertTrue(captured.isEmpty(), "deadline-resumed cooldown ticks still do not snapshot gas");
        for (long cycle = 2; cycle <= 4; cycle++) groups.advanceCycle(cycle, cells, 10);
        assertEquals(1, groups.advanceCycle(5, members -> {
            captured.add(members);
            Map<BlockPos, GasMixture> local = new HashMap<>();
            members.forEach(pos -> local.put(pos, cells.get(pos)));
            return new ExcitedAtmosphereGroups.LoadedGroupSnapshot(local, Set.of(), Set.of());
        }, false, 1, () -> false).groupOperations());
        assertEquals(1, captured.size(), "only the breakdown-eligible component captures its members");
        assertEquals(Set.of(2), captured.stream().map(Set::size).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void groupQueueIsStableForTheCycleAndDefersGroupsAdmittedAfterItsSnapshot() {
        ExcitedAtmosphereGroups groups = new ExcitedAtmosphereGroups();
        Map<BlockPos, GasMixture> cells = cells(A, B, C, D);
        groups.onShare(LindaGasSharing.Pair.of(A, B), 0, cells);
        assertEquals(1, groups.advanceCycle(1, cells, 1).groupOperations()); // fixes this cycle's queue
        groups.onShare(LindaGasSharing.Pair.of(C, D), 0, cells);
        assertEquals(0, groups.advanceCycle(1, cells, 1).groupOperations(),
                "same-cycle admissions do not rebuild or extend the stable group queue");
        assertFalse(groups.hasUnprocessedCycleWork());
        for (long cycle = 2; cycle <= 4; cycle++) groups.advanceCycle(cycle, cells, 1);
        var fifth = groups.advanceCycle(5, cells, 1);
        assertEquals(Set.of(A, B), fifth.replacements().keySet(),
                "the original group completes its fifth cooldown; the newly formed group starts next cycle");
        assertEquals(1, groups.advanceCycle(6, cells, 1).groupOperations());
        assertEquals(1, groups.advanceCycle(6, cells, 1).groupOperations(),
                "resume advances the retained queue cursor rather than re-sorting all groups");
    }

    @Test
    void zeroBudgetDoesNotInitializeQueueReadGasOrTickCooldown() {
        ExcitedAtmosphereGroups groups = new ExcitedAtmosphereGroups();
        Map<BlockPos, GasMixture> cells = cells(A, B, C, D);
        groups.onShare(LindaGasSharing.Pair.of(A, B), 0, cells);
        int[] captures = {0};
        java.util.function.Function<Set<BlockPos>, ExcitedAtmosphereGroups.LoadedGroupSnapshot> provider = members -> {
            captures[0]++;
            Map<BlockPos, GasMixture> local = new HashMap<>();
            members.forEach(pos -> local.put(pos, cells.get(pos)));
            return new ExcitedAtmosphereGroups.LoadedGroupSnapshot(local, Set.of(), Set.of());
        };
        var zero = groups.advanceCycle(1, provider, false, 0, () -> false);
        assertEquals(0, zero.groupOperations());
        assertTrue(zero.replacements().isEmpty());
        assertEquals(0, captures[0]);

        // Queue creation was skipped: this same-cycle group must be included by the first positive call.
        groups.onShare(LindaGasSharing.Pair.of(C, D), 0, cells);
        assertEquals(1, groups.advanceCycle(1, provider, false, 1, () -> false).groupOperations());
        assertEquals(1, groups.advanceCycle(1, provider, false, 1, () -> false).groupOperations());
        for (long cycle = 2; cycle <= 4; cycle++)
            assertEquals(2, groups.advanceCycle(cycle, provider, false, 2, () -> false).groupOperations());
        assertEquals(0, captures[0], "the zero-budget admission did not count toward cooldowns");
        var fifth = groups.advanceCycle(5, provider, false, 2, () -> false);
        assertEquals(2, fifth.averages());
        assertEquals(4, fifth.replacements().size());
        assertEquals(2, captures[0]);
    }

    @Test
    void completeGroupOperationAcceptsEquivalentReplacementMixtureObjects() {
        ExcitedAtmosphereGroups groups = new ExcitedAtmosphereGroups();
        Map<BlockPos, GasMixture> cells = new HashMap<>();
        for (int x = 0; x < 10; x++) cells.put(new BlockPos(x, 0, 0), mix(Map.of(GasType.OXYGEN, 1.0), 293.15));
        for (int x = 0; x < 9; x++)
            groups.onShare(LindaGasSharing.Pair.of(new BlockPos(x, 0, 0), new BlockPos(x + 1, 0, 0)), 0, cells);
        Map<BlockPos, GasMixture> equivalent = new HashMap<>();
        cells.forEach((position, mixture) -> equivalent.put(position,
                new GasMixture(mixture.gasMoles(), mixture.temperatureKelvin())));
        for (long cycle = 1; cycle <= 4; cycle++) groups.advanceCycle(cycle, cells, 1);
        var resumed = groups.advanceCycle(5, equivalent, 1);
        assertEquals(1, resumed.groupOperations());
        assertEquals(10, resumed.replacements().size());
    }

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
        assertEquals((double) (float) expected, result.temperatureKelvin(), 1e-10);
        assertEquals(cells.values().stream().mapToDouble(GasMixture::thermalEnergy).sum(),
                result.thermalEnergy() * 2, 1e-4);
    }

    @Test
    void resettingCellResetsExistingGroupsBothCooldownsWithoutCreatingMembership() {
        ExcitedAtmosphereGroups groups = new ExcitedAtmosphereGroups();
        Map<BlockPos, GasMixture> cells = cells(A, B, C);
        assertFalse(groups.resetCooldownsForCell(A));
        groups.onShare(LindaGasSharing.Pair.of(A, B), 0, cells);
        for (int i = 0; i < 4; i++) groups.advanceFullCycle(cells, 800);
        assertTrue(groups.resetCooldownsForCell(B));
        assertFalse(groups.resetCooldownsForCell(C));
        for (int i = 0; i < 4; i++)
            assertTrue(groups.advanceFullCycle(cells, 800).replacements().isEmpty());
        assertEquals(2, groups.advanceFullCycle(cells, 800).replacements().size());

        ExcitedAtmosphereGroups dismantle = new ExcitedAtmosphereGroups();
        dismantle.onShare(LindaGasSharing.Pair.of(A, B), 0, cells);
        for (int i = 0; i < 16; i++) dismantle.advanceFullCycle(cells, 800);
        assertTrue(dismantle.resetCooldownsForCell(A));
        for (int i = 0; i < 16; i++)
            assertTrue(dismantle.advanceFullCycle(cells, 800).deactivatedCells().isEmpty());
        assertEquals(Set.of(A, B), dismantle.advanceFullCycle(cells, 800).deactivatedCells());
    }

    @Test
    void immutableVacuumBreakdownReportsFiniteSpeciesAndEnergyExportOnlyWhenCommitted() {
        ExcitedAtmosphereGroups groups = new ExcitedAtmosphereGroups();
        GasMixture twoMoles = new GasMixture(Map.of(GasType.OXYGEN, 2.0), 293.15);
        GasMixture oneMole = new GasMixture(Map.of(GasType.OXYGEN, 1.0), 293.15);
        GasMixture vacuum = GasMixture.vacuum();
        Map<BlockPos, GasMixture> all = Map.of(A, twoMoles, B, oneMole, C, vacuum);
        Set<BlockPos> immutable = Set.of(C);
        groups.onShare(LindaGasSharing.Pair.of(A, B), 1.0, all);
        groups.onShare(LindaGasSharing.Pair.of(B, C), 1.0, all);

        for (long cycle = 1; cycle <= 4; cycle++)
            groups.advanceCycle(cycle, all, immutable, Set.of(), false, 1);
        var completed = groups.advanceCycle(5, all, immutable, Set.of(), false, 1);

        assertEquals(2, completed.replacements().size());
        assertEquals(1.0, completed.boundarySpeciesMoles().get(GasType.OXYGEN), 1e-5,
                "the finite cells' 3 mol before minus 2 mol after is an export to immutable vacuum");
        double finiteBeforeEnergy = twoMoles.thermalEnergy() + oneMole.thermalEnergy();
        double finiteAfterEnergy = completed.replacements().values().stream()
                .mapToDouble(GasMixture::thermalEnergy).sum();
        assertEquals(finiteBeforeEnergy - finiteAfterEnergy, completed.boundaryEnergyJoules(), 1e-6);
        var repeated = groups.advanceCycle(7, all, immutable, Set.of(), false, 1);
        assertTrue(repeated.boundarySpeciesMoles().isEmpty(), "the completed ledger is emitted once");
        assertEquals(0.0, vacuum.moles(GasType.OXYGEN), 0.0, "the immutable vacuum remains unchanged");
    }

    @Test
    void immutableGroupCanImportAndFiniteOnlyGroupsReportNoBoundaryExchange() {
        ExcitedAtmosphereGroups importing = new ExcitedAtmosphereGroups();
        GasMixture empty = GasMixture.vacuum();
        GasMixture reservoir = new GasMixture(Map.of(GasType.OXYGEN, 2.0), 293.15);
        Map<BlockPos, GasMixture> importedCells = Map.of(A, empty, B, empty, C, reservoir);
        importing.onShare(LindaGasSharing.Pair.of(A, B), 1.0, importedCells);
        importing.onShare(LindaGasSharing.Pair.of(B, C), 1.0, importedCells);
        ExcitedAtmosphereGroups.CycleResult imported = null;
        for (long cycle = 1; cycle <= 5; cycle++)
            imported = importing.advanceCycle(cycle, importedCells, Set.of(C), Set.of(), false, 800);
        assertTrue(imported.boundarySpeciesMoles().get(GasType.OXYGEN) < 0.0,
                "negative finite-before-minus-after denotes import from the reservoir");

        ExcitedAtmosphereGroups finiteOnly = new ExcitedAtmosphereGroups();
        Map<BlockPos, GasMixture> finiteCells = Map.of(A, reservoir, B, empty);
        finiteOnly.onShare(LindaGasSharing.Pair.of(A, B), 1.0, finiteCells);
        ExcitedAtmosphereGroups.CycleResult finiteResult = null;
        for (long cycle = 1; cycle <= 5; cycle++)
            finiteResult = finiteOnly.advanceCycle(cycle, finiteCells, Set.of(), Set.of(), false, 800);
        assertTrue(finiteResult.boundarySpeciesMoles().isEmpty());
        assertEquals(0.0, finiteResult.boundaryEnergyJoules(), 0.0);
    }

    @Test
    void repeatedAdvanceForSameCycleDoesNotTickCooldownTwice() {
        ExcitedAtmosphereGroups groups = new ExcitedAtmosphereGroups();
        Map<BlockPos, GasMixture> cells = cells(A, B);
        groups.onShare(LindaGasSharing.Pair.of(A, B), 0, cells);
        assertTrue(groups.advanceCycle(1, cells, 800).replacements().isEmpty());
        assertTrue(groups.advanceCycle(1, cells, 800).replacements().isEmpty());
        for (long cycle = 2; cycle <= 4; cycle++)
            assertTrue(groups.advanceCycle(cycle, cells, 800).replacements().isEmpty());
        assertEquals(2, groups.advanceCycle(5, cells, 800).replacements().size());
    }

    @Test
    void breakdownUsesUpstreamMergeTemperatureGateForMicroCapacity() {
        ExcitedAtmosphereGroups groups = new ExcitedAtmosphereGroups();
        Map<BlockPos, GasMixture> cells = Map.of(A, mix(Map.of(GasType.OXYGEN, 0.00001), 300),
                B, mix(Map.of(GasType.OXYGEN, 0.00001), 310));
        groups.onShare(LindaGasSharing.Pair.of(A, B), 0, cells);
        for (int i = 0; i < 4; i++) groups.advanceFullCycle(cells, 800);
        GasMixture result = groups.advanceFullCycle(cells, 800).replacements().get(A);
        assertEquals(0.00001, result.moles(GasType.OXYGEN), 1e-10);
        // Starting from vacuum (2.7 K), upstream's combined-capacity gate rejects these tiny inputs.
        assertEquals((double) (float) GasMixture.MIN_TEMPERATURE_KELVIN, result.temperatureKelvin(), 0.0);
    }

    @Test
    void immutableMembersContributeToAverageWithoutBeingReplaced() {
        ExcitedAtmosphereGroups groups = new ExcitedAtmosphereGroups();
        Map<BlockPos, GasMixture> cells = Map.of(A, mix(Map.of(GasType.OXYGEN, 10.0), 293),
                B, mix(Map.of(GasType.OXYGEN, 30.0), 293));
        groups.onShare(LindaGasSharing.Pair.of(A, B), 0, cells);
        for (int i = 1; i <= 4; i++) groups.advanceCycle(i, cells, Set.of(B), Set.of(), false, 800);
        var result = groups.advanceCycle(5, cells, Set.of(B), Set.of(), false, 800);
        assertEquals(Set.of(A), result.replacements().keySet());
        assertEquals(20.0, result.replacements().get(A).moles(GasType.OXYGEN), 1e-5);
        assertEquals(30.0, cells.get(B).moles(GasType.OXYGEN), 0.0);
    }

    @Test
    void bothEndpointsCanBeQueriedAsGroupedEvenBeforeTheirGroupsMerge() {
        ExcitedAtmosphereGroups groups = new ExcitedAtmosphereGroups();
        Map<BlockPos, GasMixture> cells = cells(A, B, C, D);
        groups.onShare(LindaGasSharing.Pair.of(A, B), 0, cells);
        groups.onShare(LindaGasSharing.Pair.of(C, D), 0, cells);
        assertTrue(groups.bothGrouped(LindaGasSharing.Pair.of(B, C)));
        assertNotEquals(groups.groupMembership().get(B), groups.groupMembership().get(C));
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
        assertEquals(300.0, breakdown.replacements().get(A).temperatureKelvin(), 1e-6);
        assertEquals(300.0, breakdown.replacements().get(B).temperatureKelvin(), 1e-6);
        assertTrue(breakdown.replacements().get(A).temperatureKelvin() > cells.get(A).temperatureKelvin());
        assertTrue(breakdown.replacements().get(B).temperatureKelvin() < cells.get(B).temperatureKelvin());
        assertEquals(initialEnergy,
                breakdown.replacements().values().stream().mapToDouble(GasMixture::thermalEnergy).sum(), 1e-4);

        for (int cycle = 5; cycle < 16; cycle++)
            assertTrue(groups.advanceFullCycle(cells, 800).deactivatedCells().isEmpty());
        assertEquals(java.util.Set.of(A, B), groups.advanceFullCycle(cells, 800).deactivatedCells());
        assertFalse(groups.sameGroupPair(LindaGasSharing.Pair.of(A, B)));
    }

    @Test
    void opposingEqualPressureRoomsAreAveragedOnFifthCycleRegardlessOfGradient() {
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

        for (int i = 0; i < 4; i++) assertTrue(groups.advanceFullCycle(current, 800).replacements().isEmpty());
        var breakdown = groups.advanceFullCycle(current, 800);
        assertEquals(8, breakdown.replacements().size());
        GasMixture uniform = breakdown.replacements().get(new BlockPos(0, 0, 0));
        assertEquals((double) (float) (standardMoles / 2), uniform.moles(GasType.OXYGEN), 1e-6);
        assertEquals((double) (float) (standardMoles / 2), uniform.moles(GasType.NITROGEN), 1e-6);
        assertEquals(initialOxygen, breakdown.replacements().values().stream()
                .mapToDouble(mix -> mix.moles(GasType.OXYGEN)).sum(), 1e-5);
        assertEquals(initialNitrogen, breakdown.replacements().values().stream()
                .mapToDouble(mix -> mix.moles(GasType.NITROGEN)).sum(), 1e-5);
    }

    @Test
    void dismantlesAfterSeventeenthQuietCycleAndUnavailableCellsNeverGetWiped() {
        ExcitedAtmosphereGroups groups = new ExcitedAtmosphereGroups();
        Map<BlockPos, GasMixture> cells = cells(A, B);
        groups.onShare(LindaGasSharing.Pair.of(A, B), 1, cells);
        Map<BlockPos, GasMixture> beforeRemoval = new HashMap<>(cells);
        for (int i = 0; i < 16; i++) assertTrue(groups.advanceFullCycle(cells, 800).deactivatedCells().isEmpty());
        var removed = groups.advanceFullCycle(cells, 800);
        assertEquals(java.util.Set.of(A, B), removed.deactivatedCells());
        assertFalse(groups.sameGroupPair(LindaGasSharing.Pair.of(A, B)));
        assertEquals(beforeRemoval, cells, "dismantling must not modify or discard cell gas");

        ExcitedAtmosphereGroups unavailable = new ExcitedAtmosphereGroups();
        unavailable.onShare(LindaGasSharing.Pair.of(A, B), 1, cells);
        for (int i = 0; i < 4; i++) unavailable.advanceFullCycle(cells, 800);
        assertEquals(2, unavailable.advanceFullCycle(cells, 1).replacements().size());
        for (int i = 0; i < 4; i++) unavailable.advanceFullCycle(cells, 800);
        var missing = unavailable.advanceFullCycle(Map.of(A, cells.get(A)), 800);
        assertTrue(missing.replacements().isEmpty());
        assertEquals(Set.of(A, B), missing.deactivatedCells());
        assertFalse(unavailable.sameGroupPair(LindaGasSharing.Pair.of(A, B)));
    }

    @Test
    void fiveThousandCellGroupCompletesAtomicallyWithOneGroupOperationBudget() {
        ExcitedAtmosphereGroups groups = new ExcitedAtmosphereGroups();
        Map<BlockPos, GasMixture> cells = largeGroup(groups, 5000);
        long[] captureNanos = {0}, reads = {0}, captures = {0};
        java.util.function.Function<Set<BlockPos>, ExcitedAtmosphereGroups.LoadedGroupSnapshot> provider = members -> {
            long captureStarted = System.nanoTime();
            Map<BlockPos, GasMixture> captured = new LinkedHashMap<>();
            captures[0]++;
            for (BlockPos pos : members) { reads[0]++; captured.put(pos, cells.get(pos)); }
            captureNanos[0] += System.nanoTime() - captureStarted;
            return new ExcitedAtmosphereGroups.LoadedGroupSnapshot(captured, Set.of(), Set.of(), System.nanoTime() - captureStarted);
        };
        for (int cycle = 1; cycle <= 4; cycle++)
            groups.advanceCycle(cycle, provider, false, 1, () -> false);
        assertEquals(0, captures[0], "cooldown-only admissions do not read group gas");
        long operationStarted = System.nanoTime();
        ExcitedAtmosphereGroups.CycleResult resumed = groups.advanceCycle(5, provider, false, 1, () -> false);
        long totalOperationNanos = System.nanoTime() - operationStarted;
        assertEquals(1, resumed.groupOperations());
        assertEquals(1, captures[0], "eligible breakdown captures the group once");
        assertEquals(5000, reads[0], "the atomic operation captures each member once; no pending resume validation replay");
        assertEquals(5000, resumed.replacements().size());
        assertEquals(3.4995,
                resumed.replacements().get(new BlockPos(4999, 0, 0)).moles(GasType.OXYGEN), 5e-6);
        double originalMoles = cells.values().stream().mapToDouble(GasMixture::totalMoles).sum();
        double outputMoles = resumed.replacements().values().stream().mapToDouble(GasMixture::totalMoles).sum();
        assertEquals(originalMoles, outputMoles, 0.02);
        assertEquals(cells.values().stream().mapToDouble(GasMixture::thermalEnergy).sum(),
                resumed.replacements().values().stream().mapToDouble(GasMixture::thermalEnergy).sum(), 1000.0);
        long publicationStarted = System.nanoTime();
        Map<BlockPos, GasMixture> published = new HashMap<>();
        resumed.replacements().forEach(published::put);
        long publicationNanos = System.nanoTime() - publicationStarted;
        assertEquals(5000, published.size(), "headless map-store transaction publishes the complete group");
        System.out.printf(java.util.Locale.ROOT,
                "headless excited-group N=5000 operations=1 captures=5000 operation-wall-ms=%.3f average-ms=%.3f capture-ms=%.3f map-publication-ms=%.3f writes=5000%n",
                totalOperationNanos / 1_000_000.0, resumed.averageNanos() / 1_000_000.0, captureNanos[0] / 1_000_000.0,
                publicationNanos / 1_000_000.0);
    }

    @Test
    void eachCycleUsesCurrentWholeGroupDataAndProducesCompleteAverage() {
        ExcitedAtmosphereGroups groups = new ExcitedAtmosphereGroups();
        Map<BlockPos, GasMixture> cells = largeGroup(groups);
        Map<BlockPos, GasMixture> changed = new HashMap<>(cells);
        BlockPos changedCell = new BlockPos(0, 0, 0);
        for (int admission = 1; admission <= 4; admission++) {
            // Model an external producer writing after each group-stage admission. The next
            // admission must capture the latest state instead of replaying an old detached map.
            changed.put(changedCell, changed.get(changedCell).withGasDelta(GasType.TRITIUM, 0.75));
            groups.advanceCycle(admission, changed, 1);
        }
        double expectedOxygen = changed.values().stream().mapToDouble(mix -> mix.moles(GasType.OXYGEN)).sum();
        double expectedTritium = changed.values().stream().mapToDouble(mix -> mix.moles(GasType.TRITIUM)).sum();
        double expectedEnergy = changed.values().stream().mapToDouble(GasMixture::thermalEnergy).sum();

        var result = groups.advanceCycle(5, changed, 1);
        assertEquals(800, result.replacements().size());
        assertEquals(expectedOxygen, result.replacements().values().stream().mapToDouble(mix -> mix.moles(GasType.OXYGEN)).sum(), 0.01);
        assertEquals(expectedTritium, result.replacements().values().stream().mapToDouble(mix -> mix.moles(GasType.TRITIUM)).sum(), 0.01);
        assertEquals(expectedEnergy, result.replacements().values().stream().mapToDouble(GasMixture::thermalEnergy).sum(), 10.0);
        assertEquals(result.replacements().get(changedCell), result.replacements().get(new BlockPos(1, 0, 0)));
        assertTrue(groups.sameGroupPair(LindaGasSharing.Pair.of(new BlockPos(0, 0, 0), new BlockPos(1, 0, 0))));
    }

    @Test
    void groupLargerThanGroupOperationBudgetBreaksDownAtomically() {
        ExcitedAtmosphereGroups groups = new ExcitedAtmosphereGroups();
        Map<BlockPos, GasMixture> cells = largeGroup(groups);
        BlockPos overflow = new BlockPos(800, 0, 0);
        cells.put(overflow, mix(Map.of(GasType.OXYGEN, 1000.0), 293.15));
        groups.onShare(LindaGasSharing.Pair.of(new BlockPos(799, 0, 0), overflow), 1.0, cells);
        for (long cycle = 1; cycle <= 4; cycle++) groups.advanceCycle(cycle, cells, 1);

        var tracked = groups.trackedCells();
        assertEquals(801, tracked.size());
        assertTrue(tracked.contains(overflow));
        assertThrows(UnsupportedOperationException.class, () -> tracked.add(overflow));
        Map<BlockPos, GasMixture> authoritativeCurrent = new HashMap<>();
        tracked.forEach(pos -> authoritativeCurrent.put(pos, cells.get(pos)));

        var resumed = groups.advanceCycle(5, authoritativeCurrent, 1);
        assertEquals(1, resumed.groupOperations());
        assertEquals(801, resumed.replacements().size());
        assertTrue(resumed.replacements().containsKey(overflow));
        authoritativeCurrent.putAll(resumed.replacements());

        for (long cycle = 6; cycle <= 16; cycle++) {
            ExcitedAtmosphereGroups.CycleResult result = groups.advanceCycle(cycle, authoritativeCurrent, 800);
            Map<BlockPos, GasMixture> replacements = result.replacements();
            assertTrue(result.deactivatedCells().isEmpty());
            authoritativeCurrent.putAll(replacements);
        }
        assertEquals(801, groups.advanceCycle(17, authoritativeCurrent, 800).deactivatedCells().size());
        assertTrue(groups.trackedCells().isEmpty());
    }

    private static Map<BlockPos, GasMixture> largeGroup(ExcitedAtmosphereGroups groups) { return largeGroup(groups, 800); }
    private static Map<BlockPos, GasMixture> largeGroup(ExcitedAtmosphereGroups groups, int size) {
        Map<BlockPos, GasMixture> cells = new HashMap<>();
        for (int x = 0; x < size; x++) cells.put(new BlockPos(x, 0, 0), mix(Map.of(GasType.OXYGEN, 1.0 + x * 0.001), 293.15));
        groups.onShare(LindaGasSharing.Pair.of(new BlockPos(0, 0, 0), new BlockPos(1, 0, 0)), 1, cells);
        // Extend membership through qualifying neighboring shares.
        for (int x = 1; x < size - 1; x++)
            groups.onShare(LindaGasSharing.Pair.of(new BlockPos(x, 0, 0), new BlockPos(x + 1, 0, 0)), 1, cells);
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
