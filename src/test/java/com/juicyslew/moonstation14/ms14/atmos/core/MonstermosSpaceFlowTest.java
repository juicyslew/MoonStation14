package com.juicyslew.moonstation14.ms14.atmos.core;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MonstermosSpaceFlowTest {
    private static final double T = 293.15;

    @Test
    void routesInteriorGasAndExportsWithConservationOfEverySpeciesAndHeat() {
        Map<BlockPos, GasMixture> room = room(4, 4, 3, 1000.0 / 48.0);
        BlockPos holeCell = new BlockPos(0, 3, 0);
        Map<BlockPos, Set<BlockPos>> graph = graph(room.keySet());
        double initialEnergy = energy(room);
        double initialOxygen = total(room, GasType.OXYGEN);
        double initialNitrogen = total(room, GasType.NITROGEN);
        double initialTritium = total(room, GasType.TRITIUM);

        MonstermosSpaceFlow.Result result = MonstermosSpaceFlow.run(room, graph, Set.of(holeCell.above()), 800);

        assertTrue(result.work().complete());
        assertTrue(result.exported().speciesMoles().get(GasType.OXYGEN) > 0);
        assertTrue(result.exported().speciesMoles().get(GasType.NITROGEN) > 0);
        assertTrue(result.exported().speciesMoles().get(GasType.TRITIUM) > 0);
        assertTrue(result.exported().speciesMoles().values().stream().mapToDouble(Double::doubleValue).sum() > 4.0);
        assertTrue(result.states().get(holeCell).totalMoles() > 0.0);
        assertConserved(initialOxygen, total(result.states(), GasType.OXYGEN), result.exported().speciesMoles().get(GasType.OXYGEN));
        assertConserved(initialNitrogen, total(result.states(), GasType.NITROGEN), result.exported().speciesMoles().get(GasType.NITROGEN));
        assertConserved(initialTritium, total(result.states(), GasType.TRITIUM), result.exported().speciesMoles().get(GasType.TRITIUM));
        assertEquals(initialEnergy, energy(result.states()) + result.exported().thermalEnergyJoules(), 1e-7);
        assertFalse(result.edgeTransfersMoles().isEmpty());
        assertEquals(result.edgeTransfersMoles().keySet(), result.paths().keySet());
    }

    @Test
    void moreOpeningsExportMoreGasAndEqualPressureWithNoSpaceDoesNothing() {
        Map<BlockPos, GasMixture> room = room(4, 4, 3, 1000.0 / 48.0);
        Set<BlockPos> twoHoles = Set.of(new BlockPos(0, 4, 0), new BlockPos(1, 4, 0));
        MonstermosSpaceFlow.Result one = MonstermosSpaceFlow.run(room, graph(room.keySet()), Set.of(new BlockPos(0, 4, 0)), 800);
        MonstermosSpaceFlow.Result multiple = MonstermosSpaceFlow.run(room, graph(room.keySet()), twoHoles, 800);
        assertTrue(exportedMoles(multiple) > exportedMoles(one));
        MonstermosSpaceFlow.Result sealed = MonstermosSpaceFlow.run(room, graph(room.keySet()), Set.of(), 800);
        assertEquals(0, exportedMoles(sealed));
        assertEquals(room, sealed.states());
    }

    @Test
    void isolatedRoomIsUnchangedAndLowInventoryHasBoundedCleanup() {
        Map<BlockPos, GasMixture> combined = new LinkedHashMap<>();
        combined.put(new BlockPos(0, 0, 0), mixture(20.0));
        combined.put(new BlockPos(10, 0, 0), mixture(2.0));
        Map<BlockPos, Set<BlockPos>> adjacency = graph(combined.keySet());
        MonstermosSpaceFlow.Result result = MonstermosSpaceFlow.run(combined, adjacency, Set.of(new BlockPos(11, 0, 0)), 800);
        assertEquals(20.0, result.states().get(new BlockPos(0, 0, 0)).totalMoles(), 0);
        assertEquals(0.3, exportedMoles(result), 1e-10);
        assertEquals(1.7, result.states().get(new BlockPos(10, 0, 0)).totalMoles(), 1e-10);
    }

    @Test
    void rejectsIncompleteBudgetWithoutPublishingPartialOutputAndDoesNotTraverseUnknownCells() {
        Map<BlockPos, GasMixture> tooLarge = room(9, 9, 10, 1.0);
        MonstermosSpaceFlow.Result oversized = MonstermosSpaceFlow.run(tooLarge, graph(tooLarge.keySet()), Set.of(new BlockPos(0, 9, 0)), 8000);
        assertFalse(oversized.work().complete());
        assertTrue(oversized.states().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> MonstermosSpaceFlow.run(Map.of(), Map.of(), Set.of(), 8001));

        BlockPos finite = new BlockPos(0, 0, 0);
        MonstermosSpaceFlow.Result unknown = MonstermosSpaceFlow.run(Map.of(finite, mixture(10)), Map.of(finite, Set.of()), Set.of(), 800);
        assertEquals(0, exportedMoles(unknown));
        assertEquals(10, unknown.states().get(finite).totalMoles(), 0);
    }

    @Test
    void exteriorScanIsBoundedByExteriorSeedsAndDeterministicForLargeReorderedInputs() {
        Map<BlockPos, GasMixture> cells = new LinkedHashMap<>();
        for (int x = 0; x < 800; x++) cells.put(new BlockPos(x, 0, 0), mixture(1.0));
        Map<BlockPos, Set<BlockPos>> adjacency = graph(cells.keySet());
        Set<BlockPos> exterior = new LinkedHashSet<>();
        exterior.add(new BlockPos(-1, 0, 0));
        for (int i = 0; i < 2_999; i++) exterior.add(new BlockPos(10_000 + i, 0, 0));

        MonstermosSpaceFlow.Result result = MonstermosSpaceFlow.run(cells, adjacency, exterior, 800);
        Map<BlockPos, GasMixture> reversedCells = new LinkedHashMap<>();
        List<BlockPos> cellOrder = new ArrayList<>(cells.keySet());
        java.util.Collections.reverse(cellOrder);
        for (BlockPos pos : cellOrder) reversedCells.put(pos, cells.get(pos));
        List<BlockPos> exteriorOrder = new ArrayList<>(exterior);
        java.util.Collections.reverse(exteriorOrder);
        MonstermosSpaceFlow.Result reordered = MonstermosSpaceFlow.run(reversedCells, adjacency,
                new LinkedHashSet<>(exteriorOrder), 800);

        assertTrue(result.work().complete());
        assertEquals(18_000, result.work().cellSteps() - result.work().discoveredCells() - result.work().simulationCalls());
        for (BlockPos pos : cells.keySet()) {
            assertEquals(result.states().get(pos).gasMoles(), reordered.states().get(pos).gasMoles());
            assertEquals(result.states().get(pos).thermalEnergy(), reordered.states().get(pos).thermalEnergy(), 1e-10);
        }
        assertEquals(result.exported(), reordered.exported());
        assertEquals(result.edgeTransfersMoles(), reordered.edgeTransfersMoles());

        MonstermosSpaceFlow.Result golden = MonstermosSpaceFlow.run(Map.of(new BlockPos(0, 0, 0), mixture(1.0)),
                Map.of(new BlockPos(0, 0, 0), Set.of()), Set.of(new BlockPos(-1, 0, 0)), 800);
        assertEquals(0.15, exportedMoles(golden), 1e-12);
    }

    @Test
    void repeatedEscapeKeepsVisibleBoundaryCellsNonzeroAndPreservesCumulativeLedger() {
        Map<BlockPos, GasMixture> state = room(4, 4, 3, 1000.0 / 48.0);
        Map<BlockPos, Set<BlockPos>> adjacency = graph(state.keySet());
        BlockPos boundary = new BlockPos(0, 3, 0);
        Set<BlockPos> oneHole = Set.of(boundary.above());
        double initialOxygen = total(state, GasType.OXYGEN);
        double initialNitrogen = total(state, GasType.NITROGEN);
        double initialTritium = total(state, GasType.TRITIUM);
        double initialEnergy = energy(state);
        EnumMap<GasType, Double> exported = new EnumMap<>(GasType.class);
        double exportedEnergy = 0;
        for (int step = 0; step < 10; step++) {
            MonstermosSpaceFlow.Result result = MonstermosSpaceFlow.run(state, adjacency, oneHole, 800);
            if (step == 0) assertTrue(exportedMoles(result) > 4.0);
            state = result.states();
            exported.putAll(sumSpecies(exported, result.exported().speciesMoles()));
            exportedEnergy += result.exported().thermalEnergyJoules();
            if (total(state) > 100.0) assertTrue(state.get(boundary).totalMoles() > 0.1);
        }
        assertTrue(total(state) < 900.0, "ten cycles should release a meaningful amount");
        assertTrue(state.get(boundary).totalMoles() > 0.1);
        assertConserved(initialOxygen, total(state, GasType.OXYGEN), exported.get(GasType.OXYGEN));
        assertConserved(initialNitrogen, total(state, GasType.NITROGEN), exported.get(GasType.NITROGEN));
        assertConserved(initialTritium, total(state, GasType.TRITIUM), exported.get(GasType.TRITIUM));
        assertEquals(initialEnergy, energy(state) + exportedEnergy, 1e-7);
    }

    @Test
    void twelveOpeningsExportFasterAndTwoMolBoundaryWithDonorDoesNotEmpty() {
        Map<BlockPos, GasMixture> room = room(4, 4, 3, 1000.0 / 48.0);
        Map<BlockPos, Set<BlockPos>> adjacency = graph(room.keySet());
        Set<BlockPos> twelveHoles = new LinkedHashSet<>();
        for (int x = 0; x < 4; x++) for (int z = 0; z < 3; z++) twelveHoles.add(new BlockPos(x, 4, z));
        MonstermosSpaceFlow.Result one = MonstermosSpaceFlow.run(room, adjacency, Set.of(new BlockPos(0, 4, 0)), 800);
        MonstermosSpaceFlow.Result twelve = MonstermosSpaceFlow.run(room, adjacency, twelveHoles, 800);
        assertTrue(exportedMoles(twelve) > exportedMoles(one));

        BlockPos ventCell = new BlockPos(0, 0, 0);
        BlockPos donor = new BlockPos(1, 0, 0);
        Map<BlockPos, GasMixture> lowBoundary = Map.of(ventCell, mixture(2.0), donor, mixture(10.0));
        MonstermosSpaceFlow.Result replenished = MonstermosSpaceFlow.run(lowBoundary,
                graph(lowBoundary.keySet()), Set.of(new BlockPos(0, -1, 0)), 800);
        assertTrue(replenished.states().get(ventCell).totalMoles() > 0.0);
    }

    @Test
    void twoMolPerStepEmitterStillLosesPressureThroughLargeOpening() {
        Map<BlockPos, GasMixture> state = room(4, 4, 3, 1000.0 / 48.0);
        Map<BlockPos, Set<BlockPos>> adjacency = graph(state.keySet());
        Set<BlockPos> twelveHoles = new LinkedHashSet<>();
        for (int x = 0; x < 4; x++) for (int z = 0; z < 3; z++) twelveHoles.add(new BlockPos(x, 4, z));
        BlockPos emitter = new BlockPos(0, 3, 0);
        double startingMoles = total(state);
        double cumulativeExport = 0;

        for (int step = 0; step < 10; step++) {
            Map<BlockPos, GasMixture> withEmission = new LinkedHashMap<>(state);
            withEmission.put(emitter, withEmission.get(emitter).withGasDelta(GasType.OXYGEN, 2.0));
            MonstermosSpaceFlow.Result result = MonstermosSpaceFlow.run(withEmission, adjacency, twelveHoles, 800);
            state = result.states();
            cumulativeExport += exportedMoles(result);
        }
        assertTrue(total(state) < startingMoles, "the large opening should outpace a 2 mol/cycle emitter");
        assertEquals(startingMoles + 20.0, total(state) + cumulativeExport, 1e-8);
    }

    @Test
    void finalSubEpsilonInventoryIsExportedInsteadOfLeavingAGasSpeck() {
        BlockPos cell = new BlockPos(0, 0, 0);
        Map<BlockPos, GasMixture> trace = Map.of(cell, mixture(1.1e-6));
        MonstermosSpaceFlow.Result first = MonstermosSpaceFlow.run(trace, Map.of(cell, Set.of()),
                Set.of(new BlockPos(-1, 0, 0)), 800);
        assertTrue(first.states().get(cell).totalMoles() > 0.0);
        assertTrue(first.states().get(cell).totalMoles() < MonstermosSpaceFlow.SPACE_FLOW_VACUUM_EPSILON_MOLES);
        MonstermosSpaceFlow.Result last = MonstermosSpaceFlow.run(first.states(), Map.of(cell, Set.of()),
                Set.of(new BlockPos(-1, 0, 0)), 800);
        assertEquals(0.0, last.states().get(cell).totalMoles(), 0);
        assertEquals(first.states().get(cell).totalMoles(), exportedMoles(last), 1e-15);
        assertEquals(1.1e-6, total(last.states()) + exportedMoles(first) + exportedMoles(last), 1e-15);
    }

    private static Map<BlockPos, GasMixture> room(int x, int y, int z, double perCell) {
        Map<BlockPos, GasMixture> result = new LinkedHashMap<>();
        GasMixture gas = mixture(perCell);
        for (int ix = 0; ix < x; ix++) for (int iy = 0; iy < y; iy++) for (int iz = 0; iz < z; iz++) {
            result.put(new BlockPos(ix, iy, iz), gas);
        }
        return result;
    }

    private static GasMixture mixture(double total) {
        return new GasMixture(Map.of(GasType.OXYGEN, total * 0.21, GasType.NITROGEN, total * 0.78,
                GasType.TRITIUM, total * 0.01), T);
    }

    private static Map<BlockPos, Set<BlockPos>> graph(Set<BlockPos> cells) {
        Map<BlockPos, Set<BlockPos>> result = new HashMap<>();
        for (BlockPos pos : cells) {
            Set<BlockPos> adjacent = new HashSet<>();
            for (BlockPos offset : List.of(new BlockPos(1, 0, 0), new BlockPos(-1, 0, 0),
                    new BlockPos(0, 1, 0), new BlockPos(0, -1, 0), new BlockPos(0, 0, 1), new BlockPos(0, 0, -1))) {
                BlockPos neighbor = pos.offset(offset);
                if (cells.contains(neighbor)) adjacent.add(neighbor);
            }
            result.put(pos, adjacent);
        }
        return result;
    }

    private static double energy(Map<BlockPos, GasMixture> cells) {
        return cells.values().stream().mapToDouble(GasMixture::thermalEnergy).sum();
    }

    private static double total(Map<BlockPos, GasMixture> cells, GasType gas) {
        return cells.values().stream().mapToDouble(cell -> cell.moles(gas)).sum();
    }

    private static double total(Map<BlockPos, GasMixture> cells) {
        return cells.values().stream().mapToDouble(GasMixture::totalMoles).sum();
    }

    private static Map<GasType, Double> sumSpecies(Map<GasType, Double> first, Map<GasType, Double> second) {
        EnumMap<GasType, Double> result = new EnumMap<>(GasType.class);
        result.putAll(first);
        second.forEach((gas, amount) -> result.merge(gas, amount, Double::sum));
        return result;
    }

    private static double exportedMoles(MonstermosSpaceFlow.Result result) {
        return result.exported().speciesMoles().values().stream().mapToDouble(Double::doubleValue).sum();
    }

    private static void assertConserved(double before, double after, double exported) {
        assertEquals(before, after + exported, 1e-9);
    }
}
