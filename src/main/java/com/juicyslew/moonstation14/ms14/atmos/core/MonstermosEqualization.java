package com.juicyslew.moonstation14.ms14.atmos.core;

import net.minecraft.core.BlockPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Pure, bounded finite-cell approximation of SS14 normal Monstermos. */
public final class MonstermosEqualization {
    public static final int MAX_CELLS = 800;
    /** Hard bounds on graph-search expansions and face-transfer operations per invocation. */
    public static final int MAX_EDGE_SEARCH_WORK = 8_000;
    public static final int MAX_TRANSFER_OPERATIONS = 8_000;
    public static final double MINIMUM_MOLES_DELTA = 0.0416;
    private static final Comparator<BlockPos> POSITION_ORDER = Comparator.comparingLong((BlockPos pos) -> pos.getX())
            .thenComparingLong(pos -> pos.getY()).thenComparingLong(pos -> pos.getZ());

    private MonstermosEqualization() { }

    public record DirectedEdge(BlockPos from, BlockPos to) {
        public DirectedEdge {
            from = Objects.requireNonNull(from, "from").immutable();
            to = Objects.requireNonNull(to, "to").immutable();
        }
    }

    public record Work(int discoveredCells, int edgeSearchWork, int transferOperations,
                       boolean complete, String status) { }

    public record Result(Map<BlockPos, GasMixture> states, Map<DirectedEdge, Double> edgeFlows, Work work) { }

    /**
     * Moves gas toward the total-mole patch average over a complete connected finite patch. A donor
     * may route its full surplus in this bounded invocation, as in upstream normal Monstermos. The caller owns world discovery:
     * missing adjacency entries are treated as closed boundaries and are never traversed.
     * Disconnected supplied patches are rejected rather than silently leaving unknown boundaries.
     */
    public static Result equalize(Map<BlockPos, GasMixture> finiteCells,
                                  Map<BlockPos, Set<BlockPos>> loadedOpenAdjacency, int maxCellBudget) {
        if (finiteCells == null || loadedOpenAdjacency == null) throw new IllegalArgumentException("Inputs cannot be null");
        if (maxCellBudget <= 0 || maxCellBudget > MAX_CELLS) throw new IllegalArgumentException("maxCellBudget must be in [1, 800]");
        if (finiteCells.isEmpty() || finiteCells.size() > maxCellBudget) throw new IllegalArgumentException("Finite patch exceeds cell budget");
        Map<BlockPos, GasMixture> input = new LinkedHashMap<>();
        List<Map.Entry<BlockPos, GasMixture>> orderedCells = new ArrayList<>(finiteCells.entrySet());
        for (Map.Entry<BlockPos, GasMixture> entry : orderedCells) {
            if (entry.getKey() == null || entry.getValue() == null) throw new IllegalArgumentException("Cells cannot contain null");
        }
        orderedCells.sort(Map.Entry.comparingByKey(POSITION_ORDER));
        for (Map.Entry<BlockPos, GasMixture> entry : orderedCells) {
            input.put(entry.getKey().immutable(), entry.getValue());
        }
        for (BlockPos pos : loadedOpenAdjacency.keySet()) {
            if (pos == null || !input.containsKey(pos)) throw new IllegalArgumentException("Adjacency keys must belong to supplied finite cells");
        }
        Map<BlockPos, List<BlockPos>> graph = new LinkedHashMap<>();
        for (BlockPos pos : input.keySet()) {
            Set<BlockPos> adjacent = loadedOpenAdjacency.get(pos);
            if (adjacent == null) adjacent = Set.of();
            List<BlockPos> ordered = new ArrayList<>();
            for (BlockPos neighbor : adjacent) {
                if (neighbor == null || !input.containsKey(neighbor)) throw new IllegalArgumentException("Adjacency must reference supplied finite cells only");
                if (manhattan(pos, neighbor) != 1) throw new IllegalArgumentException("Open adjacency must be face-connected");
                Set<BlockPos> reverse = loadedOpenAdjacency.get(neighbor);
                if (reverse == null || !reverse.contains(pos)) throw new IllegalArgumentException("Open adjacency must be symmetric");
                ordered.add(neighbor.immutable());
            }
            ordered.sort(POSITION_ORDER);
            graph.put(pos, List.copyOf(ordered));
        }
        ensureConnected(input.keySet(), graph);

        Map<BlockPos, GasMixture> states = new LinkedHashMap<>(input);
        Map<DirectedEdge, Double> flows = new LinkedHashMap<>();
        double sum = 0;
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        for (GasMixture mixture : input.values()) {
            sum += mixture.totalMoles();
            min = Math.min(min, mixture.totalMoles());
            max = Math.max(max, mixture.totalMoles());
        }
        if (!Double.isFinite(sum)) throw new IllegalArgumentException("Patch total moles must be finite");
        int searchWork = 0, operations = 0;
        boolean complete = true;
        String status = "complete";
        if (max - min > MINIMUM_MOLES_DELTA) {
            double target = sum / input.size();
            List<BlockPos> donors = new ArrayList<>(), receivers = new ArrayList<>();
            for (BlockPos pos : input.keySet()) {
                if (states.get(pos).totalMoles() > target) donors.add(pos);
                else if (states.get(pos).totalMoles() < target) receivers.add(pos);
            }
            int ri = 0;
            double receiverNeed = receivers.isEmpty() ? 0 : target - states.get(receivers.get(0)).totalMoles();
            outer: for (BlockPos donor : donors) {
                double donorInitialSurplus = states.get(donor).totalMoles() - target;
                double donorAvailable = donorInitialSurplus;
                while (donorAvailable > 0.0 && ri < receivers.size()) {
                    BlockPos receiver = receivers.get(ri);
                    receiverNeed = Math.max(0.0, target - states.get(receiver).totalMoles());
                    if (receiverNeed == 0.0) {
                        ri++;
                        continue;
                    }
                    Search search = shortestPath(donor, receiver, graph, MAX_EDGE_SEARCH_WORK - searchWork);
                    if (search == null) {
                        complete = false; status = "edge-search-budget-exhausted"; break outer;
                    }
                    List<BlockPos> path = search.path;
                    searchWork += search.expanded;
                    if (path.size() - 1 > MAX_TRANSFER_OPERATIONS - operations) {
                        complete = false; status = "transfer-budget-exhausted"; break outer;
                    }
                    double actualSurplus = Math.max(0.0, states.get(donor).totalMoles() - target);
                    double amount = Math.min(Math.min(Math.min(donorAvailable, actualSurplus), receiverNeed), states.get(donor).totalMoles());
                    if (amount <= 0) break;
                    double delivered = amount;
                    for (int i = 1; i < path.size(); i++) {
                        BlockPos from = path.get(i - 1), to = path.get(i);
                        GasMixture source = states.get(from);
                        double fraction = delivered / source.totalMoles();
                        GasMixture packet = source.withScaledMoles(fraction);
                        states.put(from, source.withScaledMoles(1.0 - fraction));
                        GasMixture current = states.get(to);
                        states.put(to, add(current, packet));
                        flows.merge(new DirectedEdge(from, to), delivered, Double::sum);
                        operations++;
                    }
                    donorAvailable -= amount;
                    receiverNeed -= delivered;
                    if (receiverNeed <= tolerance(target)) {
                        ri++;
                        if (ri < receivers.size()) receiverNeed = target - states.get(receivers.get(ri)).totalMoles();
                    }
                }
                if (ri >= receivers.size()) break;
            }
        }
        return new Result(Map.copyOf(states), Map.copyOf(flows), new Work(input.size(), searchWork, operations, complete, status));
    }

    private static int manhattan(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY()) + Math.abs(a.getZ() - b.getZ());
    }

    private static void ensureConnected(Set<BlockPos> cells, Map<BlockPos, List<BlockPos>> graph) {
        Set<BlockPos> seen = new LinkedHashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        BlockPos first = cells.iterator().next(); queue.add(first); seen.add(first);
        while (!queue.isEmpty()) for (BlockPos neighbor : graph.get(queue.remove())) if (seen.add(neighbor)) queue.add(neighbor);
        if (seen.size() != cells.size()) throw new IllegalArgumentException("Finite patch must be connected");
    }

    private record Search(List<BlockPos> path, int expanded) { }

    private static Search shortestPath(BlockPos start, BlockPos goal, Map<BlockPos, List<BlockPos>> graph, int remainingWork) {
        if (remainingWork <= 0) return null;
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Map<BlockPos, BlockPos> previous = new HashMap<>();
        Set<BlockPos> seen = new LinkedHashSet<>();
        queue.add(start); seen.add(start);
        int expanded = 0;
        while (!queue.isEmpty()) {
            BlockPos current = queue.remove();
            if (++expanded > remainingWork) return null;
            if (current.equals(goal)) break;
            for (BlockPos neighbor : graph.get(current)) if (seen.add(neighbor)) { previous.put(neighbor, current); queue.add(neighbor); }
        }
        if (!seen.contains(goal)) return null;
        List<BlockPos> path = new ArrayList<>();
        for (BlockPos at = goal; at != null; at = previous.get(at)) { path.add(at); if (at.equals(start)) break; }
        java.util.Collections.reverse(path);
        return new Search(path, expanded);
    }

    private static GasMixture add(GasMixture mixture, GasMixture packet) {
        Map<GasType, Double> gases = new java.util.EnumMap<>(GasType.class);
        gases.putAll(mixture.gasMoles());
        packet.gasMoles().forEach((gas, amount) -> gases.merge(gas, amount, Double::sum));
        double capacity = gases.entrySet().stream().mapToDouble(e -> e.getValue() * e.getKey().molarHeatCapacity()).sum();
        double energy = mixture.thermalEnergy() + packet.thermalEnergy();
        return new GasMixture(gases, capacity == 0 ? mixture.temperatureKelvin() : energy / capacity);
    }

    private static double tolerance(double value) { return Math.ulp(Math.max(1.0, value)) * 8; }
}
