package com.juicyslew.moonstation14.ms14.atmos.core;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MonstermosRoutingBudgetTest {
    @Test
    void smallBranchedPatchesRouteAcrossSortedFirstHopForBothSourceOrientations() {
        for (boolean reverse : new boolean[]{false, true}) {
            Map<BlockPos, GasMixture> cells = new LinkedHashMap<>();
            Map<BlockPos, Set<BlockPos>> graph = new LinkedHashMap<>();
            // Diamond: the two equal-length routes must resolve by sorted BlockPos neighbors.
            BlockPos source = new BlockPos(reverse ? 1 : 0, reverse ? 1 : 0, 0);
            BlockPos receiver = new BlockPos(reverse ? 0 : 1, reverse ? 0 : 1, 0);
            BlockPos upper = new BlockPos(0, 1, 0), lower = new BlockPos(1, 0, 0);
            BlockPos[] positions = reverse
                    ? new BlockPos[]{receiver, lower, source, upper}
                    : new BlockPos[]{upper, source, receiver, lower};
            for (BlockPos pos : positions) {
                double moles = pos.equals(source) ? 12 : pos.equals(receiver) ? 0 : 6;
                cells.put(pos, new GasMixture(Map.of(GasType.OXYGEN, moles * .6,
                        GasType.NITROGEN, moles * .4), 250 + pos.getY() * 80));
                graph.put(pos, new LinkedHashSet<>());
            }
            connect(graph, source, upper); connect(graph, upper, receiver);
            connect(graph, source, lower); connect(graph, lower, receiver);
            var result = MonstermosEqualization.equalize(cells, graph, 4);
            assertTrue(result.work().complete());
            assertEquals(2, result.work().transferOperations());
            assertFalse(result.edgeFlows().isEmpty(), "the branched patch must route donor surplus");
            for (var edge : result.edgeFlows().keySet())
                assertTrue(graph.get(edge.from()).contains(edge.to()), "route must follow an open face: " + edge);
            for (GasType gas : GasType.values()) {
                double before = cells.values().stream().mapToDouble(mix -> mix.moles(gas)).sum();
                double after = result.states().values().stream().mapToDouble(mix -> mix.moles(gas)).sum();
                double maxCellUlp = result.states().values().stream().mapToDouble(mix -> Math.ulp((float) mix.moles(gas)))
                        .max().orElse(0);
                assertEquals(before, after, 8 * cells.size() * maxCellUlp, gas.name());
            }
        }
    }

    @Test
    void rejectsDisconnectedTopology() {
        BlockPos a = new BlockPos(0, 0, 0), b = new BlockPos(2, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> MonstermosEqualization.equalize(
                Map.of(a, mix(1), b, mix(1)), Map.of(a, Set.of(), b, Set.of()), 2));
    }

    @Test
    void lazyDonorTreeRoutesDeterministicallyAndChargesExpansionsOnce() {
        Map<BlockPos, GasMixture> cells = new LinkedHashMap<>();
        Map<BlockPos, Set<BlockPos>> graph = new LinkedHashMap<>();
        // A long one-cell-wide hallway with a high-pressure source at its end.
        for (int x = 0; x < 800; x++) {
            BlockPos pos = new BlockPos(x, 0, 0);
            cells.put(pos, x == 0 ? mix(1000) : GasMixture.vacuum());
            Set<BlockPos> neighbors = new LinkedHashSet<>();
            if (x > 0) neighbors.add(new BlockPos(x - 1, 0, 0));
            if (x < 799) neighbors.add(new BlockPos(x + 1, 0, 0));
            graph.put(pos, neighbors);
        }
        var first = MonstermosEqualization.equalize(cells, graph, 800);
        var second = MonstermosEqualization.equalize(cells, graph, 800);
        assertFalse(first.work().complete(), "transfer budget should preserve bounded partial progress");
        assertEquals(first.edgeFlows(), second.edgeFlows());
        assertTrue(first.work().edgeSearchWork() <= MonstermosEqualization.MAX_EDGE_SEARCH_WORK);
        assertTrue(first.work().transferOperations() <= MonstermosEqualization.MAX_TRANSFER_OPERATIONS);
        assertTrue(first.states().get(new BlockPos(100, 0, 0)).totalMoles() > 0,
                "far room must receive routed pressure");
        assertEquals(1000, first.states().values().stream().mapToDouble(GasMixture::totalMoles).sum(), 1e-8);
    }

    private static GasMixture mix(double oxygen) {
        return new GasMixture(Map.of(GasType.OXYGEN, oxygen), 293.15);
    }

    private static void connect(Map<BlockPos, Set<BlockPos>> graph, BlockPos a, BlockPos b) {
        graph.get(a).add(b);
        graph.get(b).add(a);
    }

    /** Small reference implementation of the previous fresh BFS and per-hop packet routing. */
    private static Map<BlockPos, GasMixture> legacyRoute(Map<BlockPos, GasMixture> input,
                                                          Map<BlockPos, Set<BlockPos>> graph,
                                                          BlockPos source, BlockPos receiver) {
        Map<BlockPos, GasMixture> states = new LinkedHashMap<>(input);
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        Map<BlockPos, BlockPos> previous = new LinkedHashMap<>();
        Set<BlockPos> seen = new LinkedHashSet<>();
        queue.add(source); seen.add(source);
        while (!queue.isEmpty()) {
            BlockPos current = queue.remove();
            if (current.equals(receiver)) break;
            graph.get(current).stream().sorted(java.util.Comparator.comparingInt((BlockPos p) -> p.getX())
                    .thenComparingInt(p -> p.getY()).thenComparingInt(p -> p.getZ())).forEach(next -> {
                if (seen.add(next)) { previous.put(next, current); queue.add(next); }
            });
        }
        java.util.ArrayList<BlockPos> path = new java.util.ArrayList<>();
        for (BlockPos at = receiver; at != null; at = previous.get(at)) {
            path.add(at);
            if (at.equals(source)) break;
        }
        java.util.Collections.reverse(path);
        double amount = 6;
        for (int i = 1; i < path.size(); i++) {
            BlockPos from = path.get(i - 1), to = path.get(i);
            GasMixture sourceMixture = states.get(from);
            double fraction = amount / sourceMixture.totalMoles();
            GasMixture packet = sourceMixture.withScaledMoles(fraction);
            states.put(from, sourceMixture.withScaledMoles(1 - fraction));
            GasMixture current = states.get(to);
            java.util.Map<GasType, Double> gases = new java.util.EnumMap<>(GasType.class);
            gases.putAll(current.gasMoles());
            packet.gasMoles().forEach((gas, moles) -> gases.merge(gas, moles, Double::sum));
            double capacity = gases.entrySet().stream().mapToDouble(e -> e.getValue() * e.getKey().molarHeatCapacity()).sum();
            double energy = current.thermalEnergy() + packet.thermalEnergy();
            states.put(to, new GasMixture(gases, capacity == 0 ? current.temperatureKelvin() : energy / capacity));
        }
        return states;
    }
}
