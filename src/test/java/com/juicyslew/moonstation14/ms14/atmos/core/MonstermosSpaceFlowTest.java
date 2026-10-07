package com.juicyslew.moonstation14.ms14.atmos.core;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MonstermosSpaceFlowTest {
    private static final double T = 293.15;

    @Test
    void releaseUsesReferenceInventoryAsPressureTargetThenCoolsSource() {
        BlockPos cell = new BlockPos(0, 0, 0);
        GasMixture initial = new GasMixture(Map.of(GasType.OXYGEN, 1_000.0), T);
        var result = MonstermosSpaceFlow.run(Map.of(cell, initial), Map.of(cell, Set.of()), Set.of(cell.west()), 2_000);

        double targetPressure = 1_000 * 0.15 / AtmosphereUnits.REFERENCE_INVENTORY_SCALE;
        double releasedByFirstRelease = targetPressure * 1_000 / (T * 8.31446261815324);
        assertTrue(result.work().complete());
        assertEquals(releasedByFirstRelease, result.exported().speciesMoles().get(GasType.OXYGEN), 2e-4);
        assertEquals(1_000 - releasedByFirstRelease, result.states().get(cell).totalMoles(), 2e-4);
        double residualFraction = (result.states().get(cell).totalMoles()
                - targetPressure * AtmosphereUnits.REFERENCE_INVENTORY_SCALE)
                / result.states().get(cell).totalMoles();
        assertEquals(T * (0.9 + 0.1 * residualFraction), result.states().get(cell).temperatureKelvin(), 1e-4);
    }

    @Test
    void hotMixedSpeciesReleaseMatchesReleaseGasToTargetPressureAndCoolingFormula() {
        BlockPos cell = new BlockPos(0, 0, 0);
        GasMixture source = new GasMixture(Map.of(GasType.OXYGEN, 80.0, GasType.PLASMA, 20.0), 800.0);
        var result = MonstermosSpaceFlow.run(Map.of(cell, source), Map.of(cell, Set.of()), Set.of(cell.west()), 2_000);
        double target = AtmosphereUnits.localCellQuantityToReferenceTile(source.totalMoles() * 0.15);
        double amount = target * 1_000 / (800.0 * 8.31446261815324);
        double fraction = amount / source.totalMoles();
        double remaining = source.totalMoles() - amount;
        double expectedTemperature = 800.0 * (0.9 + 0.1 *
                (remaining - AtmosphereUnits.referenceTileQuantity(target)) / remaining);

        assertEquals(source.moles(GasType.OXYGEN) * fraction,
                result.exported().speciesMoles().get(GasType.OXYGEN), 2e-5);
        assertEquals(source.moles(GasType.PLASMA) * fraction,
                result.exported().speciesMoles().get(GasType.PLASMA), 2e-5);
        assertEquals(expectedTemperature, result.states().get(cell).temperatureKelvin(), 2e-4);
    }

    @Test
    void releaseGasToEnforcesTenKpaFrictionGuardAndUpstreamClearingBranch() {
        BlockPos cell = new BlockPos(0, 0, 0);
        GasMixture lowPressure = new GasMixture(Map.of(GasType.OXYGEN, 0.8), T);
        var result = MonstermosSpaceFlow.run(Map.of(cell, lowPressure), Map.of(cell, Set.of()), Set.of(cell.west()), 2_000);

        assertEquals(0.0, result.states().get(cell).totalMoles());
        assertEquals(0.8, result.exported().speciesMoles().get(GasType.OXYGEN), 1e-7);
        assertEquals(GasMixture.MIN_TEMPERATURE_KELVIN, result.states().get(cell).temperatureKelvin(), 1e-7);
    }

    @Test
    void connectedProgressionRoutesFromInteriorTowardSpaceAndAccountsSpeciesEnergy() {
        BlockPos inside = new BlockPos(0, 0, 0), boundary = inside.east();
        Map<BlockPos, GasMixture> input = Map.of(
                inside, new GasMixture(Map.of(GasType.TRITIUM, 500.0), T),
                boundary, GasMixture.vacuum());
        Map<BlockPos, Set<BlockPos>> graph = Map.of(inside, Set.of(boundary), boundary, Set.of(inside));
        var result = MonstermosSpaceFlow.run(input, graph, Set.of(boundary.east()), 2_000);

        assertTrue(result.work().complete());
        assertTrue(result.edgeTransfersMoles().containsKey(new MonstermosSpaceFlow.DirectedEdge(inside, boundary)));
        for (GasType gas : GasType.values())
            assertEquals(input.get(inside).moles(gas) + input.get(boundary).moles(gas),
                    result.states().values().stream().mapToDouble(m -> m.moles(gas)).sum()
                            + result.exported().speciesMoles().getOrDefault(gas, 0.0), 2e-4);
        assertEquals(input.values().stream().mapToDouble(GasMixture::thermalEnergy).sum(),
                result.states().values().stream().mapToDouble(GasMixture::thermalEnergy).sum()
                        + result.exported().thermalEnergyJoules(), 1e-6);
    }

    @Test
    void multiedgeTransitIsStableWhenInputAndGraphOrdersAreReversed() {
        BlockPos a = new BlockPos(0, 0, 0), b = a.east(), c = b.east(), d = c.east();
        Map<BlockPos, GasMixture> input = new java.util.LinkedHashMap<>();
        input.put(a, new GasMixture(Map.of(GasType.OXYGEN, 1.0), T)); input.put(b, GasMixture.vacuum());
        input.put(c, GasMixture.vacuum()); input.put(d, new GasMixture(Map.of(GasType.TRITIUM, 500.0), T));
        Map<BlockPos, Set<BlockPos>> graph = Map.of(a, Set.of(b), b, Set.of(a, c), c, Set.of(b, d), d, Set.of(c));
        var first = MonstermosSpaceFlow.run(input, graph, Set.of(a.west()), 2_000);

        Map<BlockPos, GasMixture> reversedInput = new java.util.LinkedHashMap<>();
        input.entrySet().stream().toList().reversed().forEach(e -> reversedInput.put(e.getKey(), e.getValue()));
        Map<BlockPos, Set<BlockPos>> reversedGraph = new java.util.LinkedHashMap<>();
        graph.entrySet().stream().toList().reversed().forEach(e ->
                reversedGraph.put(e.getKey(), new java.util.LinkedHashSet<>(e.getValue().stream().toList().reversed())));
        var reordered = MonstermosSpaceFlow.run(reversedInput, reversedGraph, Set.of(a.west()), 2_000);

        for (BlockPos pos : input.keySet()) {
            assertEquals(first.states().get(pos).gasMoles(), reordered.states().get(pos).gasMoles());
            assertEquals(first.states().get(pos).temperatureKelvin(), reordered.states().get(pos).temperatureKelvin(), 0.0);
        }
        assertEquals(first.exported(), reordered.exported());
        assertEquals(first.edgeTransfersMoles(), reordered.edgeTransfersMoles());
        assertTrue(first.edgeTransfersMoles().containsKey(new MonstermosSpaceFlow.DirectedEdge(d, c)));
        assertTrue(first.edgeTransfersMoles().containsKey(new MonstermosSpaceFlow.DirectedEdge(c, b)));
        assertTrue(first.edgeTransfersMoles().containsKey(new MonstermosSpaceFlow.DirectedEdge(b, a)));
        for (GasType gas : GasType.values()) {
            double before = input.values().stream().mapToDouble(m -> m.moles(gas)).sum();
            double after = first.states().values().stream().mapToDouble(m -> m.moles(gas)).sum()
                    + first.exported().speciesMoles().getOrDefault(gas, 0.0);
            assertEquals(before, after, 2e-4, "species accounting for " + gas);
        }
    }

    @Test
    void immutableFiniteSpaceProfileCanRejectReleaseWhileSourceStillCools() {
        BlockPos cell = new BlockPos(0, 0, 0), outside = cell.west();
        GasMixture source = new GasMixture(Map.of(GasType.OXYGEN, 100.0), 400.0);
        GasMixture immutableAmbient = GasMixture.breathableAir();
        var face = new MonstermosSpaceFlow.DirectedEdge(cell, outside);
        var result = MonstermosSpaceFlow.run(Map.of(cell, source), Map.of(cell, Set.of()),
                Map.of(face, immutableAmbient), 2_000);

        assertEquals(100.0, result.states().get(cell).totalMoles(), 1e-4,
                "101 kPa exterior exceeds the mole-derived 37.5 kPa target, so ReleaseGasTo refuses transfer");
        assertEquals(0.0, result.exported().speciesMoles().getOrDefault(GasType.OXYGEN, 0.0), 0.0);
        assertTrue(result.states().get(cell).temperatureKelvin() < source.temperatureKelvin(),
                "upstream artificial cooling runs after a rejected release call");
    }

    @Test
    void hardDiscoveryBoundProcessesFirstTwoThousandAndLeavesUnvisitedFrontierCellUntouched() {
        Map<BlockPos, GasMixture> cells = new java.util.LinkedHashMap<>();
        Map<BlockPos, Set<BlockPos>> graph = new java.util.LinkedHashMap<>();
        for (int x = 0; x <= 2_000; x++) {
            BlockPos pos = new BlockPos(x, 0, 0);
            cells.put(pos, x == 0 ? new GasMixture(Map.of(GasType.OXYGEN, 1_000.0), T) : GasMixture.vacuum());
            Set<BlockPos> adjacent = new java.util.LinkedHashSet<>();
            if (x > 0) adjacent.add(new BlockPos(x - 1, 0, 0));
            if (x < 2_000) adjacent.add(new BlockPos(x + 1, 0, 0));
            graph.put(pos, adjacent);
        }
        BlockPos frontier = new BlockPos(2_000, 0, 0);
        var result = MonstermosSpaceFlow.run(cells, graph, Set.of(new BlockPos(-1, 0, 0)), 2_000);

        assertTrue(result.work().complete());
        assertEquals(2_000, result.work().discoveredCells());
        assertEquals(cells.size(), result.states().size());
        assertTrue(result.states().get(new BlockPos(0, 0, 0)).totalMoles() < 1_000.0);
        assertEquals(cells.get(frontier).totalMoles(), result.states().get(frontier).totalMoles(), 0.0);
        assertEquals("hard-discovery-frontier-bounded", result.work().status());
    }

    @Test
    void mixedDiscoveryBudgetCountsInitialSpaceVertexAgainstFiniteCells() {
        Map<BlockPos, GasMixture> cells = new java.util.LinkedHashMap<>();
        Map<BlockPos, Set<BlockPos>> graph = new java.util.LinkedHashMap<>();
        for (int x = 0; x < 2_000; x++) {
            BlockPos pos = new BlockPos(x, 0, 0);
            cells.put(pos, x == 1_999 ? new GasMixture(Map.of(GasType.OXYGEN, 1_000.0), T) : GasMixture.vacuum());
            Set<BlockPos> adjacent = new java.util.LinkedHashSet<>();
            if (x > 0) adjacent.add(new BlockPos(x - 1, 0, 0));
            if (x < 1_999) adjacent.add(new BlockPos(x + 1, 0, 0));
            graph.put(pos, adjacent);
        }
        BlockPos seed = new BlockPos(0, 0, 0);
        var face = new MonstermosSpaceFlow.DirectedEdge(seed, seed.west());
        var result = MonstermosSpaceFlow.run(cells, graph, Map.of(face, GasMixture.vacuum()), seed, 2_000);

        assertEquals(2_000, result.work().discoveredCells());
        assertEquals(1_000.0, result.states().get(new BlockPos(1_999, 0, 0)).totalMoles(), 0.0,
                "the 2,000th finite cell is beyond the total discovery bound");
        assertEquals("hard-discovery-frontier-bounded", result.work().status());
    }

    @Test
    void multiBreachExportsAreDeterministicAcrossInputPermutations() {
        BlockPos center = new BlockPos(0, 0, 0);
        BlockPos west = center.west(), east = center.east(), north = center.north();
        Map<BlockPos, GasMixture> input = new java.util.LinkedHashMap<>();
        input.put(center, new GasMixture(Map.of(GasType.OXYGEN, 80.0), T));
        input.put(west, new GasMixture(Map.of(GasType.PLASMA, 120.0), T));
        input.put(east, new GasMixture(Map.of(GasType.TRITIUM, 160.0), T));
        input.put(north, GasMixture.vacuum());
        Map<BlockPos, Set<BlockPos>> graph = Map.of(
                center, Set.of(west, east, north), west, Set.of(center), east, Set.of(center), north, Set.of(center));
        Map<MonstermosSpaceFlow.DirectedEdge, GasMixture> faces = Map.of(
                new MonstermosSpaceFlow.DirectedEdge(west, west.west()), GasMixture.vacuum(),
                new MonstermosSpaceFlow.DirectedEdge(east, east.east()), GasMixture.vacuum());

        var seededWest = MonstermosSpaceFlow.run(input, graph, faces, west, 2_000);
        Map<BlockPos, GasMixture> reversedInput = new java.util.LinkedHashMap<>();
        input.entrySet().stream().toList().reversed().forEach(entry -> reversedInput.put(entry.getKey(), entry.getValue()));
        Map<BlockPos, Set<BlockPos>> reversedGraph = new java.util.LinkedHashMap<>();
        graph.entrySet().stream().toList().reversed().forEach(entry ->
                reversedGraph.put(entry.getKey(), new java.util.LinkedHashSet<>(entry.getValue().stream().toList().reversed())));
        var reordered = MonstermosSpaceFlow.run(reversedInput, reversedGraph, faces, west, 2_000);
        var seededEast = MonstermosSpaceFlow.run(input, graph, faces, east, 2_000);

        assertEquals(seededWest.states().keySet(), reordered.states().keySet());
        for (BlockPos pos : seededWest.states().keySet()) {
            GasMixture expected = seededWest.states().get(pos);
            GasMixture actual = reordered.states().get(pos);
            for (GasType gas : GasType.values())
                assertEquals(expected.moles(gas), actual.moles(gas), 0.0, gas + " at " + pos);
            assertEquals(expected.temperatureKelvin(), actual.temperatureKelvin(), 0.0, "temperature at " + pos);
            assertEquals(expected.volumeCubicMeters(), actual.volumeCubicMeters(), 0.0, "volume at " + pos);
        }
        assertEquals(seededWest.exported(), reordered.exported());
        assertEquals(seededWest.edgeTransfersMoles(), reordered.edgeTransfersMoles());
        assertTrue(seededWest.edgeTransfersMoles().containsKey(
                new MonstermosSpaceFlow.DirectedEdge(west, west.west())), "west exterior face receives export");
        assertTrue(seededWest.edgeTransfersMoles().containsKey(
                new MonstermosSpaceFlow.DirectedEdge(east, east.east())), "east exterior face receives export");
    }
}
