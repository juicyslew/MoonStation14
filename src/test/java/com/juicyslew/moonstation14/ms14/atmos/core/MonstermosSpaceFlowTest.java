package com.juicyslew.moonstation14.ms14.atmos.core;

import com.juicyslew.moonstation14.ms14.atmos.visual.GasVisibility;
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
    void adjacentSameDepthOpeningsExchangeAndExportAcrossRepeatedCycles() {
        BlockPos left = new BlockPos(0, 0, 0), right = left.east();
        GasMixture ambient = GasMixture.breathableAir();
        GasMixture high = new GasMixture(Map.of(GasType.OXYGEN, ambient.moles(GasType.OXYGEN) * 10,
                GasType.NITROGEN, ambient.moles(GasType.NITROGEN) * 10, GasType.TRITIUM, 2.0), T);
        Map<BlockPos, GasMixture> state = new LinkedHashMap<>();
        state.put(left, high);
        state.put(right, ambient);
        var leftFace = new MonstermosSpaceFlow.DirectedEdge(left, left.west());
        var rightFace = new MonstermosSpaceFlow.DirectedEdge(right, right.east());
        Map<MonstermosSpaceFlow.DirectedEdge, GasMixture> faces = new LinkedHashMap<>();
        faces.put(rightFace, ambient);
        faces.put(leftFace, ambient);
        var edges = graph(state.keySet());
        EnumMap<GasType, Double> cumulative = new EnumMap<>(GasType.class);
        double cumulativeEnergy = 0;
        Map<BlockPos, GasMixture> initial = Map.copyOf(state);
        for (int cycle = 0; cycle < 8; cycle++) {
            var result = MonstermosSpaceFlow.runAmbient(state, edges, faces, 800);
            Map<BlockPos, GasMixture> reversed = new LinkedHashMap<>();
            reversed.put(right, state.get(right));
            reversed.put(left, state.get(left));
            var reordered = MonstermosSpaceFlow.runAmbient(reversed, edges,
                    Map.of(leftFace, ambient, rightFace, ambient), 800);
            for (BlockPos pos : state.keySet()) {
                assertEquals(result.states().get(pos).gasMoles(), reordered.states().get(pos).gasMoles());
                assertEquals(result.states().get(pos).thermalEnergy(),
                        reordered.states().get(pos).thermalEnergy(), 1e-9);
            }
            assertEquals(result.edgeTransfersMoles(), reordered.edgeTransfersMoles());
            assertEquals(result.exported(), reordered.exported());
            assertTrue(result.work().complete());
            assertTrue(result.work().cellSteps() <= 2 + 2 * 2 + 2 * 2 + 2 + 3);
            if (cycle == 0) {
                assertTrue(result.edgeTransfersMoles().getOrDefault(
                        new MonstermosSpaceFlow.DirectedEdge(left, right), 0.0) > 0,
                        "equal-depth finite edge must transfer gas");
                assertTrue(result.edgeTransfersMoles().containsKey(leftFace));
                assertTrue(result.edgeTransfersMoles().containsKey(rightFace));
                assertTrue(result.states().get(right).moles(GasType.TRITIUM) > 0,
                        "species must travel with the donor packet");
                assertTrue(result.states().get(left).pressureKpa(1) >= result.states().get(right).pressureKpa(1),
                        "finite relaxation must not reverse the pressure gradient");
            }
            for (GasType gas : GasType.values()) {
                double signed = result.exported().speciesMoles().getOrDefault(gas, 0.0);
                assertEquals(total(state, gas), total(result.states(), gas) + signed, 1e-8);
                cumulative.merge(gas, signed, Double::sum);
            }
            assertEquals(energy(state), energy(result.states()) + result.exported().thermalEnergyJoules(), 1e-7);
            cumulativeEnergy += result.exported().thermalEnergyJoules();
            state = result.states();
        }
        for (GasType gas : GasType.values())
            assertEquals(total(initial, gas), total(state, gas) + cumulative.getOrDefault(gas, 0.0), 1e-8);
        assertEquals(energy(initial), energy(state) + cumulativeEnergy, 1e-6);
    }

    @Test
    void multipleFacesOnOneBoundaryAllReceiveGasInPositionOrder() {
        BlockPos cell = new BlockPos(0, 0, 0);
        GasMixture ambient = GasMixture.breathableAir();
        GasMixture high = new GasMixture(Map.of(GasType.OXYGEN, ambient.moles(GasType.OXYGEN) * 10,
                GasType.NITROGEN, ambient.moles(GasType.NITROGEN) * 10), T);
        var west = new MonstermosSpaceFlow.DirectedEdge(cell, cell.west());
        var east = new MonstermosSpaceFlow.DirectedEdge(cell, cell.east());
        var result = MonstermosSpaceFlow.runAmbient(Map.of(cell, high), Map.of(cell, Set.of()),
                Map.of(east, ambient, west, ambient), 800);
        Map<MonstermosSpaceFlow.DirectedEdge, GasMixture> reversed = new LinkedHashMap<>();
        reversed.put(west, ambient);
        reversed.put(east, ambient);
        assertEquals(result.edgeTransfersMoles(), MonstermosSpaceFlow.runAmbient(Map.of(cell, high),
                Map.of(cell, Set.of()), reversed, 800).edgeTransfersMoles());
        for (int cycle = 0; cycle < 3; cycle++) {
            assertTrue(result.edgeTransfersMoles().getOrDefault(west, 0.0) > 0);
            assertTrue(result.edgeTransfersMoles().getOrDefault(east, 0.0) > 0);
            result = MonstermosSpaceFlow.runAmbient(result.states(), Map.of(cell, Set.of()), reversed, 800);
        }
        assertTrue(result.states().get(cell).pressureKpa(1) >= ambient.pressureKpa(1));
    }

    @Test
    void ambientGraphExportsOverpressureAndInhalesIntoVacuumAcrossRealEdges() {
        BlockPos inside = new BlockPos(0, 0, 0), door = inside.east(), outside = door.east();
        GasMixture ambient = GasMixture.breathableAir();
        Map<BlockPos, Set<BlockPos>> edges = Map.of(inside, Set.of(door), door, Set.of(inside));
        var faces = Map.of(new MonstermosSpaceFlow.DirectedEdge(door, outside), ambient);
        GasMixture hot = new GasMixture(Map.of(GasType.OXYGEN, ambient.moles(GasType.OXYGEN) * 10,
                GasType.NITROGEN, ambient.moles(GasType.NITROGEN) * 10), ambient.temperatureKelvin());
        Map<BlockPos, GasMixture> before = Map.of(inside, hot, door, hot);
        var export = MonstermosSpaceFlow.runAmbient(before, edges, faces, 800);
        assertTrue(export.exported().speciesMoles().get(GasType.OXYGEN) > 0);
        assertTrue(export.states().get(door).pressureKpa(1) >= ambient.pressureKpa(1));
        assertEquals(total(before, GasType.OXYGEN), total(export.states(), GasType.OXYGEN)
                + export.exported().speciesMoles().get(GasType.OXYGEN), 1e-8);
        assertEquals(energy(before), energy(export.states()) + export.exported().thermalEnergyJoules(), 1e-7);
        Map<BlockPos, GasMixture> empty = Map.of(inside, GasMixture.vacuum(), door, GasMixture.vacuum());
        var inhale = MonstermosSpaceFlow.runAmbient(empty, edges, faces, 800);
        assertTrue(inhale.states().get(inside).moles(GasType.OXYGEN) > 0);
        assertTrue(inhale.states().get(inside).moles(GasType.NITROGEN) > 0);
        assertTrue(inhale.exported().speciesMoles().get(GasType.OXYGEN) < 0);
        assertEquals(0, total(inhale.states(), GasType.OXYGEN)
                + inhale.exported().speciesMoles().get(GasType.OXYGEN), 1e-8);
        assertEquals(0, energy(inhale.states()) + inhale.exported().thermalEnergyJoules(), 1e-7);
        assertEquals(ambient, faces.get(new MonstermosSpaceFlow.DirectedEdge(door, outside)));
        var equilibrium = MonstermosSpaceFlow.runAmbient(Map.of(inside, ambient, door, ambient), edges, faces, 800);
        assertTrue(equilibrium.edgeTransfersMoles().isEmpty());
        assertEquals(0, equilibrium.exported().thermalEnergyJoules());
    }

    @Test
    void ambientFacesAreFairDeterministicAndNeverPublishIncompleteWork() {
        BlockPos left = new BlockPos(0, 0, 0), right = left.east();
        GasMixture ambient = GasMixture.breathableAir();
        Map<BlockPos, GasMixture> cells = new LinkedHashMap<>();
        GasMixture hot = new GasMixture(Map.of(GasType.OXYGEN, ambient.moles(GasType.OXYGEN) * 10,
                GasType.NITROGEN, ambient.moles(GasType.NITROGEN) * 10), ambient.temperatureKelvin());
        cells.put(left, hot);
        cells.put(right, hot);
        var faces = Map.of(new MonstermosSpaceFlow.DirectedEdge(left, left.west()), ambient,
                new MonstermosSpaceFlow.DirectedEdge(right, right.east()), ambient);
        var graph = graph(cells.keySet());
        var first = MonstermosSpaceFlow.runAmbient(cells, graph, faces, 800);
        assertTrue(first.edgeTransfersMoles().containsKey(new MonstermosSpaceFlow.DirectedEdge(left, left.west())));
        assertTrue(first.edgeTransfersMoles().containsKey(new MonstermosSpaceFlow.DirectedEdge(right, right.east())));
        Map<BlockPos, GasMixture> reordered = new LinkedHashMap<>();
        reordered.put(right, cells.get(right));
        reordered.put(left, cells.get(left));
        var repeated = MonstermosSpaceFlow.runAmbient(reordered, graph, faces, 800);
        for (BlockPos pos : cells.keySet()) {
            assertEquals(first.states().get(pos).gasMoles(), repeated.states().get(pos).gasMoles());
            assertEquals(first.states().get(pos).thermalEnergy(), repeated.states().get(pos).thermalEnergy(), 1e-9);
        }
        assertEquals(first.exported(), MonstermosSpaceFlow.runAmbient(reordered, graph, faces, 800).exported());
        var incomplete = MonstermosSpaceFlow.runAmbient(cells, graph, faces, 1);
        assertFalse(incomplete.work().complete());
        assertTrue(incomplete.states().isEmpty());
        assertEquals(cells.keySet(), MonstermosSpaceFlow.runAmbient(cells, graph, Map.of(), 800).states().keySet());
    }

    @Test
    void hotMixedSpeciesPacketsStayPressureDirectedAndConserveEnergy() {
        BlockPos finite = new BlockPos(0, 0, 0), exterior = finite.east();
        GasMixture ambient = GasMixture.breathableAir();
        GasMixture contaminated = new GasMixture(Map.of(GasType.PLASMA, 80.0,
                GasType.OXYGEN, 20.0), 500.0);
        Map<BlockPos, GasMixture> before = Map.of(finite, contaminated);
        var result = MonstermosSpaceFlow.runAmbient(before, Map.of(finite, Set.of()),
                Map.of(new MonstermosSpaceFlow.DirectedEdge(finite, exterior), ambient), 800);
        assertTrue(result.states().get(finite).pressureKpa(1) >= ambient.pressureKpa(1));
        for (GasType gas : GasType.values())
            assertEquals(contaminated.moles(gas), result.states().get(finite).moles(gas)
                    + result.exported().speciesMoles().getOrDefault(gas, 0.0), 1e-8);
        assertEquals(contaminated.thermalEnergy(), result.states().get(finite).thermalEnergy()
                + result.exported().thermalEnergyJoules(), 1e-7);

        GasMixture warmAmbient = new GasMixture(Map.of(GasType.OXYGEN, 10.0,
                GasType.NITROGEN, 30.0), 400);
        GasMixture coldRoom = new GasMixture(Map.of(GasType.TRITIUM, 2.0), 200);
        var incoming = MonstermosSpaceFlow.runAmbient(Map.of(finite, coldRoom), Map.of(finite, Set.of()),
                Map.of(new MonstermosSpaceFlow.DirectedEdge(finite, exterior), warmAmbient), 800);
        assertTrue(incoming.states().get(finite).pressureKpa(1) <= warmAmbient.pressureKpa(1));
        assertTrue(incoming.states().get(finite).moles(GasType.TRITIUM) > 0);
        assertTrue(incoming.exported().speciesMoles().get(GasType.OXYGEN) < 0);
    }

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
        assertEquals(18_000, result.work().cellSteps() - result.work().discoveredCells()
                - result.work().simulationCalls() - 2 * (800 - 1));
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

    @Test
    void emptyLongSpacePathRetainsVisibleGasAndContinuesExportingUnderEmission() {
        Map<BlockPos, GasMixture> state = new LinkedHashMap<>();
        for (int x = 0; x <= 10; x++) state.put(new BlockPos(x, 0, 0), mixture(0));
        Map<BlockPos, Set<BlockPos>> adjacency = graph(state.keySet());
        BlockPos producer = new BlockPos(0, 0, 0);
        Set<BlockPos> opening = Set.of(new BlockPos(11, 0, 0));

        // The first packet makes a visible foothold rather than passing through every empty cell.
        Map<BlockPos, GasMixture> firstInput = new LinkedHashMap<>(state);
        firstInput.put(producer, firstInput.get(producer).withGasDelta(GasType.PLASMA, 2.0));
        MonstermosSpaceFlow.Result first = MonstermosSpaceFlow.run(firstInput, adjacency, opening, 800);
        assertTrue(first.states().get(new BlockPos(1, 0, 0)).moles(GasType.PLASMA) >= 0.1);
        assertConserved(2.0, total(first.states(), GasType.PLASMA),
                first.exported().speciesMoles().getOrDefault(GasType.PLASMA, 0.0));

        state = first.states();
        double cumulativeExport = exportedMoles(first);
        double cumulativePlasma = first.exported().speciesMoles().getOrDefault(GasType.PLASMA, 0.0);
        double cumulativeEnergy = first.exported().thermalEnergyJoules();
        double injectedEnergy = firstInput.get(producer).thermalEnergy();
        for (int step = 1; step < 20; step++) {
            Map<BlockPos, GasMixture> withEmission = new LinkedHashMap<>(state);
            withEmission.put(producer, withEmission.get(producer).withGasDelta(GasType.PLASMA, 2.0));
            double beforePlasma = total(state, GasType.PLASMA);
            double beforeEnergy = energy(state);
            double emissionEnergy = withEmission.get(producer).thermalEnergy() - state.get(producer).thermalEnergy();
            MonstermosSpaceFlow.Result result = MonstermosSpaceFlow.run(withEmission, adjacency, opening, 800);
            assertTrue(result.work().complete());
            assertEquals(beforePlasma + 2.0, total(result.states(), GasType.PLASMA)
                    + result.exported().speciesMoles().getOrDefault(GasType.PLASMA, 0.0), 1e-9);
            assertEquals(beforeEnergy + emissionEnergy,
                    energy(result.states()) + result.exported().thermalEnergyJoules(), 1e-7);
            injectedEnergy += emissionEnergy;
            state = result.states();
            cumulativeExport += exportedMoles(result);
            cumulativePlasma += result.exported().speciesMoles().getOrDefault(GasType.PLASMA, 0.0);
            cumulativeEnergy += result.exported().thermalEnergyJoules();
        }

        for (int x = 1; x < 10; x++) {
            double plasma = state.get(new BlockPos(x, 0, 0)).moles(GasType.PLASMA);
            int alpha = GasVisibility.alphaByte(GasType.PLASMA, plasma, 1.0);
            assertTrue(alpha >= 40, "path cell " + x + " should have visible plasma opacity >= 40: " + alpha);
        }
        assertTrue(cumulativeExport > 0.0);
        assertTrue(cumulativePlasma > 0.0, "the explicit exterior opening should export injected plasma");
        assertTrue(state.get(producer).totalMoles() < 30.0, "ongoing emission must not accumulate without bound");
        assertEquals(40.0, total(state, GasType.PLASMA) + cumulativePlasma, 1e-8);
        assertEquals(injectedEnergy, energy(state) + cumulativeEnergy, 1e-6);

        // A missing exterior opening is not inferred from the end of a finite chain.
        MonstermosSpaceFlow.Result sealed = MonstermosSpaceFlow.run(state, adjacency, Set.of(), 800);
        assertEquals(0.0, exportedMoles(sealed));
        assertEquals(state, sealed.states());

        Map<BlockPos, GasMixture> residual = Map.of(new BlockPos(0, 0, 0), mixture(5.0e-7),
                new BlockPos(1, 0, 0), mixture(0.0));
        Map<BlockPos, Set<BlockPos>> residualGraph = graph(residual.keySet());
        MonstermosSpaceFlow.Result residualFirst = MonstermosSpaceFlow.run(residual, residualGraph,
                Set.of(new BlockPos(2, 0, 0)), 800);
        MonstermosSpaceFlow.Result residualLast = MonstermosSpaceFlow.run(residualFirst.states(), residualGraph,
                Set.of(new BlockPos(2, 0, 0)), 800);
        assertEquals(0.0, total(residualLast.states()), 0.0, "sub-epsilon interior residue should drain after source-off steps");
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
