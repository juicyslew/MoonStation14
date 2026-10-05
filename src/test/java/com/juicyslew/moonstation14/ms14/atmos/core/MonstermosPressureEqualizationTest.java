package com.juicyslew.moonstation14.ms14.atmos.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class MonstermosPressureEqualizationTest {
    private static final BlockPos A = new BlockPos(0, 0, 0), B = new BlockPos(1, 0, 0);
    private static GasMixture gas(double amount, double temperature) {
        return new GasMixture(Map.of(GasType.NITROGEN, amount), temperature);
    }
    private static Map<BlockPos, Set<BlockPos>> pair() { return Map.of(A, Set.of(B), B, Set.of(A)); }

    @Test void pressureDrivenTransferExceedsEqualMolesMovementAndConservesInventory() {
        var input = Map.of(A, gas(45, 5000), B, gas(40, 293));
        var result = MonstermosPressureEqualization.equalizeDiscovered(input, pair(), A, Set.of());
        double moved = result.transport().edgeFlows().values().stream().mapToDouble(Double::doubleValue).sum();
        assertEquals(21.3, moved, .1);
        assertEquals(85, result.transport().states().values().stream().mapToDouble(GasMixture::totalMoles).sum(), .001);
        assertSpeciesEnergyConserved(input, result.transport().states(), 0.001);
        assertTrue(result.diagnostics().pressureSpanAfter() < result.diagnostics().pressureSpanBefore());
        assertTrue(result.diagnostics().converged());
        assertTrue(result.diagnostics().pressureSpanAfter() <= Math.max(.1,
                result.diagnostics().pressureSpanBefore() * .001));
    }

    @Test void equalPressureDoesNotMoveGasEvenWhenMoleCountsDiffer() {
        var input = Map.of(A, gas(20, 600), B, gas(40, 300));
        var result = MonstermosPressureEqualization.equalizeDiscovered(input, pair(), A, Set.of());
        assertTrue(result.transport().edgeFlows().isEmpty());
    }

    @Test void chainRoutesAcrossEveryFaceAndRejectsDisconnectedGraph() {
        BlockPos c = new BlockPos(2, 0, 0);
        Map<BlockPos, Set<BlockPos>> graph = Map.of(A, Set.of(B), B, Set.of(A, c), c, Set.of(B));
        var input = Map.of(A, gas(80, 500), B, gas(10, 293), c, gas(10, 293));
        var result = MonstermosPressureEqualization.equalizeDiscovered(input, graph, A, Set.of());
        assertTrue(result.transport().edgeFlows().keySet().stream().anyMatch(e -> e.from().equals(B) || e.to().equals(B)));
        assertEquals(2, result.transport().edgeFlows().keySet().stream()
                .map(e -> Set.of(e.from(), e.to())).distinct().count());
        assertTrue(result.transport().states().get(B).temperatureKelvin() > 293);
        assertSpeciesEnergyConserved(input, result.transport().states(), .01);
        assertThrows(IllegalArgumentException.class, () -> MonstermosPressureEqualization.equalizeDiscovered(
                input, Map.of(A, Set.of()), A, Set.of()));
    }

    @Test void immutableEndpointRemainsUnchangedAndExportsAreReported() {
        var input = Map.of(A, gas(80, 293), B, gas(20, 293));
        var result = MonstermosPressureEqualization.equalizeDiscovered(input, pair(), A, Set.of(B));
        assertSame(input.get(B), result.transport().states().get(B));
        assertTrue(result.transport().includedCells().contains(B));
        assertTrue(result.transport().boundaryExchange().speciesMoles().getOrDefault(GasType.NITROGEN, 0.0) > 0);
        assertEquals(result.transport().states().get(A).totalMoles() - input.get(A).totalMoles(),
                -result.transport().boundaryExchange().speciesMoles().get(GasType.NITROGEN), .001);
        assertEquals(input.get(A).thermalEnergy()-result.transport().states().get(A).thermalEnergy(),
                result.transport().boundaryExchange().thermalEnergyJoules(),.1);
    }

    @Test void immutableAmbientImportsIntoEmptyFiniteCellWithEnergyLedger() {
        var ambient = gas(40, 400);
        var input = Map.of(A, GasMixture.vacuum(), B, ambient);
        var result = MonstermosPressureEqualization.equalizeDiscovered(input, pair(), A, Set.of(B));
        assertTrue(result.transport().states().get(A).totalMoles() > 0);
        assertSame(ambient, result.transport().states().get(B));
        assertTrue(result.transport().boundaryExchange().speciesMoles().get(GasType.NITROGEN) < 0);
        assertEquals(result.transport().states().get(A).thermalEnergy(),
                -result.transport().boundaryExchange().thermalEnergyJoules(), .1);
    }

    @Test void previewBudgetIsCountedAndZeroBudgetPreservesInput() {
        var input = Map.of(A, gas(45, 5000), B, gas(40, 293));
        var tiny = new MonstermosPressureEqualization.Settings(1, 1, 1, .1, .001);
        var result = MonstermosPressureEqualization.equalizeDiscovered(input, pair(), A, Set.of(), tiny);
        assertFalse(result.transport().work().complete());
        assertTrue(result.transport().work().edgeSearchWork() <= 1);
        assertEquals(input, result.transport().states());
    }

    @Test void previewDiagnosticsCountEachPathNodeAndShareBfsBudget() {
        BlockPos c=new BlockPos(2,0,0), d=new BlockPos(3,0,0);
        Map<BlockPos,GasMixture> cells=Map.of(A,gas(100,600),B,gas(20,500),c,gas(20,400),d,gas(10,293));
        Map<BlockPos,Set<BlockPos>> graph=Map.of(A,Set.of(B),B,Set.of(A,c),c,Set.of(B,d),d,Set.of(c));
        var settings=new MonstermosPressureEqualization.Settings(1,60,100,.1,.001);
        var result=MonstermosPressureEqualization.equalizeDiscovered(cells,graph,A,Set.of(),settings);
        assertEquals(14*4,result.diagnostics().scalarPreviewSteps());
        assertEquals(59,result.transport().work().edgeSearchWork());
        assertTrue(result.transport().work().edgeSearchWork()<=settings.searchWork());
    }

    @Test void isothermalPathUsesValidatedSinglePreviewAndConservesEnergy() {
        BlockPos c = new BlockPos(2, 0, 0), d = new BlockPos(3, 0, 0);
        Map<BlockPos, GasMixture> cells = Map.of(A, gas(100, 293), B, gas(20, 293),
                c, gas(20, 293), d, gas(10, 293));
        Map<BlockPos, Set<BlockPos>> graph = Map.of(A, Set.of(B), B, Set.of(A, c),
                c, Set.of(B, d), d, Set.of(c));
        var settings = new MonstermosPressureEqualization.Settings(1, 100, 100, .1, .001);
        var result = MonstermosPressureEqualization.equalizeDiscovered(cells, graph, A, Set.of(), settings);
        assertEquals(4, result.diagnostics().scalarPreviewSteps());
        assertEquals(7, result.transport().work().edgeSearchWork());
        assertTrue(result.diagnostics().pressureSpanAfter() < result.diagnostics().pressureSpanBefore());
        assertSpeciesEnergyConserved(cells, result.transport().states(), .01);
    }

    @Test void unequalVolumesAndDifferentHeatCapacitiesAreSolvedPhysically() {
        var hot = new GasMixture(Map.of(GasType.OXYGEN, 20.0), 1500, 2.0);
        var cold = new GasMixture(Map.of(GasType.FREZON, 20.0), 300, .5);
        var input = Map.of(A, hot, B, cold);
        var result = MonstermosPressureEqualization.equalizeDiscovered(input, pair(), A, Set.of());
        assertTrue(result.diagnostics().pressureSpanAfter() < result.diagnostics().pressureSpanBefore());
        assertSpeciesEnergyConserved(input, result.transport().states(), .05);
    }

    @Test void discoveryIncludesOnlyFirstTwoHundredBfsNodes() {
        Map<BlockPos, GasMixture> cells=new LinkedHashMap<>();
        Map<BlockPos, Set<BlockPos>> adjacency=new LinkedHashMap<>();
        for(int i=0;i<201;i++) {
            BlockPos p=new BlockPos(i,0,0); cells.put(p,gas(i==0?100:10,293));
            Set<BlockPos> neighbors=new LinkedHashSet<>();
            if(i>0) neighbors.add(new BlockPos(i-1,0,0));
            if(i<200) neighbors.add(new BlockPos(i+1,0,0));
            adjacency.put(p,neighbors);
        }
        var result=MonstermosPressureEqualization.equalizeDiscovered(cells,adjacency,A,Set.of());
        assertEquals(MonstermosEqualization.UPSTREAM_SOFT_LIMIT,result.transport().includedCells().size());
        assertFalse(result.transport().includedCells().contains(new BlockPos(200,0,0)));
    }

    @Test void immutableShortcutCannotDisconnectIncludedFiniteCorridor() {
        BlockPos top = new BlockPos(2, 0, 98);
        BlockPos farEnd = new BlockPos(2, 0, 0);
        BlockPos bath = new BlockPos(1, 0, 0);
        Map<BlockPos, GasMixture> cells = new LinkedHashMap<>();
        cells.put(A, gas(45, 5000));
        for (int z = 1; z <= 98; z++) cells.put(new BlockPos(0, 0, z), gas(40, 293));
        cells.put(new BlockPos(1, 0, 98), gas(40, 293));
        cells.put(top, gas(40, 293));
        for (int z = 97; z >= 0; z--) {
            BlockPos position = new BlockPos(2, 0, z);
            cells.put(position, position.equals(farEnd) ? GasMixture.vacuum() : gas(40, 293));
        }
        cells.put(new BlockPos(-1, 0, 0), gas(40, 293));
        cells.put(bath, gas(40, 293));
        assertEquals(201, cells.size());

        Map<BlockPos, Set<BlockPos>> graph = new LinkedHashMap<>();
        for (BlockPos position : cells.keySet()) {
            Set<BlockPos> neighbors = new LinkedHashSet<>();
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = position.relative(direction);
                if (cells.containsKey(neighbor)) neighbors.add(neighbor);
            }
            graph.put(position, neighbors);
        }

        var settings = new MonstermosPressureEqualization.Settings(1, 5000, 200, .1, .001);
        var result = MonstermosPressureEqualization.equalizeDiscovered(
                cells, graph, A, Set.of(bath), settings);
        Set<BlockPos> included = result.transport().includedCells();
        assertEquals(200, included.size(), "the immutable bath consumes an included membership slot");
        assertTrue(included.contains(bath));

        Set<BlockPos> includedFinite = new HashSet<>(included);
        includedFinite.remove(bath);
        Set<BlockPos> reachableFinite = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        reachableFinite.add(A);
        queue.add(A);
        while (!queue.isEmpty()) {
            BlockPos current = queue.remove();
            for (BlockPos neighbor : graph.get(current)) {
                if (includedFinite.contains(neighbor) && reachableFinite.add(neighbor)) queue.add(neighbor);
            }
        }
        assertEquals(includedFinite, reachableFinite,
                "the selected mutable membership prefix must stay connected without crossing the bath");
        assertTrue(result.transport().edgeFlows().values().stream().mapToDouble(Double::doubleValue).sum() > 0,
                "the high-pressure source must find a route within the finite prefix");
        assertTrue(result.transport().edgeFlows().keySet().stream()
                .noneMatch(edge -> edge.from().equals(bath) || edge.to().equals(bath)),
                "the immutable bath is an endpoint, never a finite-corridor shortcut");
        assertEquals(0, result.transport().boundaryExchange().speciesMoles().values().stream()
                .mapToDouble(Math::abs).sum(), 0);

        Map<BlockPos, GasMixture> selectedInput = new LinkedHashMap<>();
        for (BlockPos position : included) selectedInput.put(position, cells.get(position));
        assertSpeciesEnergyConserved(selectedInput, result.transport().states(), .01);
        assertFalse(included.contains(farEnd), "the bounded finite prefix omits the remote corridor endpoint");
    }

    @Test void invalidNonFaceAdjacencyIsRejected() {
        BlockPos diagonal=new BlockPos(1,1,0);
        assertThrows(IllegalArgumentException.class,()->MonstermosPressureEqualization.equalizeDiscovered(
                Map.of(A,gas(2,293),diagonal,gas(1,293)),Map.of(A,Set.of(diagonal),diagonal,Set.of(A)),A,Set.of()));
    }

    @Test void traceInventoryPropagatesAcrossVacuumDespiteLowMeanPressure() {
        Map<BlockPos, GasMixture> state = new LinkedHashMap<>();
        Map<BlockPos, Set<BlockPos>> graph = new LinkedHashMap<>();
        BlockPos seed = new BlockPos(0, 0, 0);
        for (int x = 0; x < 31; x++) {
            for (int y = 0; y < 2; y++) {
                for (int z = 0; z < 2; z++) {
                    BlockPos position = new BlockPos(x, y, z);
                    state.put(position, position.equals(seed)
                            ? new GasMixture(Map.of(GasType.TRITIUM, 2.0), 293)
                            : GasMixture.vacuum());
                }
            }
        }
        for (BlockPos position : state.keySet()) {
            Set<BlockPos> neighbors = new LinkedHashSet<>();
            for (Direction direction : Direction.values()) {
                BlockPos adjacent = position.relative(direction);
                if (state.containsKey(adjacent)) neighbors.add(adjacent);
            }
            graph.put(position, neighbors);
        }

        double initialEnergy = state.values().stream().mapToDouble(GasMixture::thermalEnergy).sum();
        for (int solve = 0; solve < 12; solve++) {
            var result = MonstermosPressureEqualization.equalizeDiscovered(state, graph, seed, Set.of());
            state.putAll(result.transport().states());
            double maximumPressure = result.transport().states().values().stream()
                    .mapToDouble(GasMixture::pressureKpa).max().orElse(0);
            double pressureTolerance = Math.max(1e-6, maximumPressure * .001);
            if (result.diagnostics().pressureSpanAfter() > pressureTolerance) {
                assertFalse(result.diagnostics().converged(), "residual pressure span must not be mislabeled converged");
            }
        }

        BlockPos farEnd = new BlockPos(30, 1, 1);
        assertTrue(state.get(farEnd).moles(GasType.TRITIUM) > 0,
                "trace inventory must reach the far end before any group averaging");
        double totalMoles = state.values().stream().mapToDouble(gas -> gas.moles(GasType.TRITIUM)).sum();
        assertEquals(2.0, totalMoles, state.values().stream()
                .mapToDouble(gas -> Math.ulp((float) gas.moles(GasType.TRITIUM))).sum() * 4);
        double finalEnergy = state.values().stream().mapToDouble(GasMixture::thermalEnergy).sum();
        double energyUlps = state.values().stream().mapToDouble(gas -> Math.ulp((float) gas.thermalEnergy())).sum();
        assertEquals(initialEnergy, finalEnergy, energyUlps * 4);
    }

    @Test void subCleanupTraceDoesNotConsumeUnboundedRouteWork() {
        Map<BlockPos, GasMixture> input = Map.of(
                A, new GasMixture(Map.of(GasType.TRITIUM, 4e-8), 293), B, GasMixture.vacuum());
        var result = MonstermosPressureEqualization.equalizeDiscovered(input, pair(), A, Set.of());
        assertTrue(result.diagnostics().converged());
        assertTrue(result.transport().edgeFlows().isEmpty());
        assertTrue(result.transport().work().edgeSearchWork() <= MonstermosEqualization.MAX_EDGE_SEARCH_WORK);
    }

    private static void assertSpeciesEnergyConserved(Map<BlockPos, GasMixture> before,
                                                       Map<BlockPos, GasMixture> after,double tolerance) {
        for (GasType type : GasType.values()) {
            double a=before.values().stream().mapToDouble(g->g.moles(type)).sum();
            double b=after.values().stream().mapToDouble(g->g.moles(type)).sum();
            assertEquals(a,b,tolerance,"species "+type);
        }
        double initialEnergy=before.values().stream().mapToDouble(GasMixture::thermalEnergy).sum();
        double finalEnergy=after.values().stream().mapToDouble(GasMixture::thermalEnergy).sum();
        double maxEnergy=java.util.stream.Stream.concat(before.values().stream(),after.values().stream())
                .mapToDouble(g->g.thermalEnergy()).max().orElse(0);
        double ulpBudget=Math.ulp((float)maxEnergy)*2*Math.max(1,after.size());
        assertEquals(initialEnergy,finalEnergy,ulpBudget);
    }
}
