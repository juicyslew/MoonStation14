package com.juicyslew.moonstation14.ms14.atmos.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

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

/** SS14-derived finite-cell total-moles equalizer; it is not pressure-driven CFD. */
public final class MonstermosEqualization {
    /** Legacy adapter limit; upstream-shaped discovery uses {@link #UPSTREAM_SOFT_LIMIT}. */
    public static final int MAX_CELLS = 800;
    public static final int UPSTREAM_SOFT_LIMIT = 200;
    public static final int MAX_DISCOVERED_CELLS = 2_000;
    /** Hard bounds on graph-search expansions and face-transfer operations per invocation. */
    public static final int MAX_EDGE_SEARCH_WORK = 8_000;
    public static final int MAX_TRANSFER_OPERATIONS = 8_000;
    public static final double MINIMUM_MOLES_DELTA = AtmosphereUnits.referenceTileQuantity(
            101.325 * AtmosphereUnits.UPSTREAM_TILE_VOLUME_LITERS / (293.15 * 8.314462618) * 0.001);
    private static final Comparator<BlockPos> POSITION_ORDER = Comparator.comparingLong((BlockPos pos) -> pos.getX())
            .thenComparingLong(pos -> pos.getY()).thenComparingLong(pos -> pos.getZ());

    private MonstermosEqualization() { }

    /**
     * Upstream-shaped entry point for callers which have discovered the connected finite frontier.
     * The supplied maps may contain up to the hard discovery limit; only the first 200 BFS cells
     * contribute to the average or receive transfers. Extra cells remain available to the caller
     * to detect a Space neighbor and select the explosive path.
     */
    public static Result equalizeDiscovered(Map<BlockPos, GasMixture> discoveredCells,
                                            Map<BlockPos, Set<BlockPos>> loadedOpenAdjacency,
                                            BlockPos seed) {
        return equalizeDiscovered(discoveredCells, loadedOpenAdjacency, seed, Set.of());
    }

    /**
     * SS14 graph entry points may include immutable non-Space exterior air in the solve. Include
     * those endpoint mixtures and their finite face adjacency in discovery; publish only states
     * whose positions are not in {@code immutableCells}. {@link Result#boundaryExchange()} reports
     * signed packet species/energy (positive finite-to-immutable, negative immutable-to-finite).
     */
    public static Result equalizeDiscovered(Map<BlockPos, GasMixture> discoveredCells,
                                            Map<BlockPos, Set<BlockPos>> loadedOpenAdjacency,
                                            BlockPos seed, Set<BlockPos> immutableCells) {
        Objects.requireNonNull(discoveredCells, "discoveredCells");
        Objects.requireNonNull(loadedOpenAdjacency, "loadedOpenAdjacency");
        Objects.requireNonNull(seed, "seed");
        Objects.requireNonNull(immutableCells, "immutableCells");
        if (discoveredCells.size() > MAX_DISCOVERED_CELLS)
            throw new IllegalArgumentException("Discovery exceeds 2000 cells");
        if (!discoveredCells.containsKey(seed)) throw new IllegalArgumentException("Seed must be discovered");
        if (!discoveredCells.keySet().containsAll(immutableCells))
            throw new IllegalArgumentException("Immutable endpoints must be discovered cells");
        for (var entry : loadedOpenAdjacency.entrySet()) {
            if (!discoveredCells.containsKey(entry.getKey()))
                throw new IllegalArgumentException("Adjacency keys must be discovered finite cells");
            for (BlockPos neighbor : entry.getValue()) {
                if (neighbor == null || !discoveredCells.containsKey(neighbor)
                        || !loadedOpenAdjacency.getOrDefault(neighbor, Set.of()).contains(entry.getKey()))
                    throw new IllegalArgumentException("Adjacency must be symmetric between discovered finite cells");
                if (manhattan(entry.getKey(), neighbor) != 1)
                    throw new IllegalArgumentException("Adjacency must be six-face connected");
            }
        }
        List<BlockPos> bfs = new ArrayList<>();
        Set<BlockPos> seen = new LinkedHashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(seed.immutable()); seen.add(seed.immutable());
        while (!queue.isEmpty() && bfs.size() < MAX_DISCOVERED_CELLS) {
            BlockPos current = queue.remove(); bfs.add(current);
            List<BlockPos> neighbors = faceOrderedNeighbors(current, loadedOpenAdjacency.getOrDefault(current, Set.of()));
            for (BlockPos neighbor : neighbors)
                if (discoveredCells.containsKey(neighbor) && seen.add(neighbor.immutable())) queue.add(neighbor.immutable());
        }
        boolean activated = false;
        for (BlockPos pos : loadedOpenAdjacency.getOrDefault(seed, Set.of())) {
            GasMixture neighbor = discoveredCells.get(pos);
            if (neighbor != null && Math.abs(neighbor.totalMoles() - discoveredCells.get(seed).totalMoles())
                    > MINIMUM_MOLES_DELTA) {
                activated = true;
                break;
            }
        }
        if (!activated) return new Result(Map.of(seed.immutable(), discoveredCells.get(seed)), Map.of(),
                new Work(bfs.size(), 0, 0, true, "neighbor-gradient-below-upstream-activation"),
                BoundaryExchange.empty(), Set.of(seed.immutable()));
        List<BlockPos> included = bfs.subList(0, Math.min(UPSTREAM_SOFT_LIMIT, bfs.size()));
        Map<BlockPos, GasMixture> cells = new LinkedHashMap<>();
        Map<BlockPos, Set<BlockPos>> graph = new LinkedHashMap<>();
        Set<BlockPos> selected = new LinkedHashSet<>(included);
        for (BlockPos pos : included) {
            cells.put(pos, discoveredCells.get(pos));
            Set<BlockPos> adjacent = new LinkedHashSet<>();
            for (BlockPos next : faceOrderedNeighbors(pos, loadedOpenAdjacency.getOrDefault(pos, Set.of())))
                if (selected.contains(next)) adjacent.add(next);
            graph.put(pos, adjacent);
        }
        if (cells.isEmpty()) throw new IllegalArgumentException("No finite cells discovered");
        // Upstream's average is computed only over the first soft-limit BFS entries. Its fast pass
        // is a sorted, one-way sweep: a node gives only to neighbors not yet visited by the sweep.
        if (cells.isEmpty()) throw new IllegalArgumentException("No finite cells discovered");
        double average = cells.values().stream().mapToDouble(GasMixture::totalMoles).sum() / cells.size();
        Map<BlockPos, Double> delta = new LinkedHashMap<>();
        List<BlockPos> givers = new ArrayList<>(), takers = new ArrayList<>();
        for (BlockPos pos : cells.keySet()) {
            double d = cells.get(pos).totalMoles() - average;
            delta.put(pos, d);
            (d > 0 ? givers : takers).add(pos);
        }
        Map<DirectedEdge, Double> requested = new LinkedHashMap<>();
        if (givers.size() > Math.log(cells.size()) / Math.log(2)
                && takers.size() > Math.log(cells.size()) / Math.log(2)) {
            List<BlockPos> sorted = new ArrayList<>(cells.keySet());
            sorted.sort(Comparator.comparingDouble(delta::get));
            Set<BlockPos> done = new LinkedHashSet<>();
            for (BlockPos pos : sorted) {
                done.add(pos);
                double d = delta.get(pos);
                if (d <= 0) continue;
                List<BlockPos> eligible = graph.get(pos).stream().filter(n -> !done.contains(n)).toList();
                if (eligible.isEmpty()) continue;
                double each = d / eligible.size();
                for (BlockPos n : eligible) {
                    requested.merge(new DirectedEdge(pos, n), each, Double::sum);
                    delta.put(pos, delta.get(pos) - each);
                    delta.put(n, delta.get(n) + each);
                }
            }
            givers.clear(); takers.clear();
            for (BlockPos pos : cells.keySet()) (delta.get(pos) > 0 ? givers : takers).add(pos);
        }
        // The slower upstream pass starts a BFS at every member of the smaller side. Routes are
        // recorded first; gas mixtures are not mutated until FinalizeEq's ordered packet phase.
        boolean giverRoots = givers.size() < takers.size();
        List<BlockPos> roots = giverRoots ? givers : takers;
        for (BlockPos root : roots) {
            double need = delta.get(root);
            if (giverRoots ? need <= 0 : need >= 0) continue;
            Map<BlockPos, BlockPos> previous = new HashMap<>();
            Set<BlockPos> rooted = new LinkedHashSet<>();
            queue.clear(); queue.add(root); rooted.add(root);
            while (!queue.isEmpty() && (giverRoots ? delta.get(root) > 0 : delta.get(root) < 0)) {
                BlockPos at = queue.remove();
                for (BlockPos next : graph.get(at)) {
                    if (giverRoots ? delta.get(root) <= 0 : delta.get(root) >= 0) break;
                    if (!rooted.add(next)) continue;
                    previous.put(next, at); queue.add(next);
                    double opposite = delta.get(next);
                    if (giverRoots ? opposite < 0 : opposite > 0) {
                        BlockPos giver = giverRoots ? root : next;
                        BlockPos taker = giverRoots ? next : root;
                        double amount = Math.min(delta.get(giver), -delta.get(taker));
                        if (!(amount > 0)) continue;
                        List<BlockPos> path = new ArrayList<>();
                        BlockPos endpoint = giverRoots ? taker : giver;
                        for (BlockPos p = endpoint; p != null; p = previous.get(p)) { path.add(p); if (p.equals(root)) break; }
                        java.util.Collections.reverse(path);
                        if (!giverRoots) java.util.Collections.reverse(path);
                        for (int i = 1; i < path.size(); i++) requested.merge(new DirectedEdge(path.get(i - 1), path.get(i)), amount, Double::sum);
                        delta.put(giver, delta.get(giver) - amount);
                        delta.put(taker, delta.get(taker) + amount);
                    }
                }
            }
        }
        Map<BlockPos, GasMixture> states = new LinkedHashMap<>(cells);
        // AdjustEqMovement stores equal/opposite directional amounts on the two adjacent tiles;
        // FinalizeEq therefore applies only the positive net direction on each face.
        for (var entry : new ArrayList<>(requested.entrySet())) {
            DirectedEdge edge = entry.getKey();
            DirectedEdge reverse = new DirectedEdge(edge.to(), edge.from());
            if (POSITION_ORDER.compare(edge.from(), edge.to()) > 0) continue;
            double net = entry.getValue() - requested.getOrDefault(reverse, 0.0);
            requested.remove(edge); requested.remove(reverse);
            if (net > 0) requested.put(edge, net);
            else if (net < 0) requested.put(reverse, -net);
        }
        // Finalize in discovery order, applying source removals and reciprocal destination merges.
        Map<DirectedEdge, Double> pending = new LinkedHashMap<>(requested);
        Map<DirectedEdge, Double> actual = new LinkedHashMap<>();
        BoundaryLedger boundaryLedger = new BoundaryLedger();
        Set<BlockPos> finalized = new LinkedHashSet<>(), finalizing = new LinkedHashSet<>();
        for (BlockPos pos : cells.keySet()) finalizeTransfers(pos, graph, states, pending, actual,
                finalized, finalizing, immutableCells, boundaryLedger);
        return new Result(states, actual, new Work(cells.size(), 0, actual.size(), true, "upstream-soft-limit-complete"),
                boundaryLedger.snapshot(), selected);
    }

    public record DirectedEdge(BlockPos from, BlockPos to) {
        public DirectedEdge {
            from = Objects.requireNonNull(from, "from").immutable();
            to = Objects.requireNonNull(to, "to").immutable();
        }
    }

    public record Work(int discoveredCells, int edgeSearchWork, int transferOperations,
                       boolean complete, String status) { }

    /** Positive values leave finite mixtures for immutable endpoints; negative values enter finite mixtures. */
    public record BoundaryExchange(Map<GasType, Double> speciesMoles, double thermalEnergyJoules) {
        public BoundaryExchange { speciesMoles = Map.copyOf(speciesMoles); }
        public static BoundaryExchange empty() { return new BoundaryExchange(Map.of(), 0); }
    }

    /** states contains every included solve node, including unchanged immutable endpoint snapshots.
     * includedCells identifies exact LastCycle membership: only selected solve nodes are stamped. */
    public record Result(Map<BlockPos, GasMixture> states, Map<DirectedEdge, Double> edgeFlows, Work work,
                         BoundaryExchange boundaryExchange, Set<BlockPos> includedCells) {
        public Result {
            includedCells = Set.copyOf(includedCells);
        }
        public Result(Map<BlockPos, GasMixture> states, Map<DirectedEdge, Double> edgeFlows, Work work) {
            this(states, edgeFlows, work, BoundaryExchange.empty(), states.keySet());
        }
        public Result(Map<BlockPos, GasMixture> states, Map<DirectedEdge, Double> edgeFlows, Work work,
                      BoundaryExchange boundaryExchange) {
            this(states, edgeFlows, work, boundaryExchange, states.keySet());
        }
    }

    private static final class BoundaryLedger {
        private final java.util.EnumMap<GasType, Double> species = new java.util.EnumMap<>(GasType.class);
        private double energy;

        private void record(GasMixture packet, double sign) {
            packet.gasMoles().forEach((gas, amount) -> species.merge(gas, amount * sign, Double::sum));
            energy += packet.thermalEnergy() * sign;
        }

        private BoundaryExchange snapshot() {
            species.entrySet().removeIf(entry -> entry.getValue() == 0.0);
            return new BoundaryExchange(species, energy);
        }
    }

    private static void finalizeTransfers(BlockPos pos, Map<BlockPos, Set<BlockPos>> graph,
                                          Map<BlockPos, GasMixture> states,
                                          Map<DirectedEdge, Double> pending,
                                          Map<DirectedEdge, Double> actual,
                                          Set<BlockPos> finalized, Set<BlockPos> finalizing,
                                          Set<BlockPos> immutableCells, BoundaryLedger boundaryLedger) {
        if (finalized.contains(pos) || !finalizing.add(pos)) return;
        List<BlockPos> neighbors = graph.get(pos).stream()
                .filter(next -> pending.containsKey(new DirectedEdge(pos, next))).toList();
        for (BlockPos next : neighbors) {
            DirectedEdge edge = new DirectedEdge(pos, next);
            Double planned = pending.remove(edge);
            if (planned == null || planned <= 0) continue;
            GasMixture source = states.get(pos);
            if (source.totalMoles() + tolerance(source.totalMoles()) < planned) {
                // FinalizeEq recursively finalizes neighbors carrying negative reciprocal
                // directions before attempting the source's outgoing packet.
                for (BlockPos incoming : graph.get(pos)) {
                    if (pending.containsKey(new DirectedEdge(incoming, pos)))
                        finalizeTransfers(incoming, graph, states, pending, actual, finalized, finalizing,
                                immutableCells, boundaryLedger);
                }
                source = states.get(pos);
            }
            double fraction = source.totalMoles() == 0 ? 0 : Math.min(1, planned / source.totalMoles());
            GasMixture.Removal split = source.splitRatio(fraction);
            boolean immutableSource = immutableCells.contains(pos);
            boolean immutableReceiver = immutableCells.contains(next);
            if (!immutableSource) states.put(pos, split.remaining());
            if (!immutableReceiver) states.put(next, add(states.get(next), split.removed()));
            if (immutableSource != immutableReceiver)
                boundaryLedger.record(split.removed(), immutableSource ? -1.0 : 1.0);
            double moved = split.removed().totalMoles();
            if (moved > 0) actual.merge(edge, moved, Double::sum);
        }
        finalizing.remove(pos);
        finalized.add(pos);
    }

    private static List<BlockPos> faceOrderedNeighbors(BlockPos pos, Set<BlockPos> neighbors) {
        List<BlockPos> ordered = new ArrayList<>(neighbors.size());
        for (Direction direction : Direction.values()) {
            BlockPos adjacent = pos.relative(direction);
            if (neighbors.contains(adjacent)) ordered.add(adjacent.immutable());
        }
        if (ordered.size() != neighbors.size())
            throw new IllegalArgumentException("Adjacency must consist only of unique six-face neighbors");
        return ordered;
    }

    /**
     * Moves gas toward the total-mole patch average over a complete connected finite patch. A donor
     * may route its full surplus in this bounded invocation, as in upstream normal Monstermos. The caller owns world discovery:
     * missing adjacency entries are treated as closed boundaries and are never traversed.
     * Disconnected supplied patches are rejected rather than silently leaving unknown boundaries.
     */
    /** @deprecated Compatibility adapter for historical focused fixtures; production uses {@link #equalizeDiscovered}. */
    @Deprecated
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
            for (BlockPos neighbor : adjacent) {
                if (neighbor == null || !input.containsKey(neighbor)) throw new IllegalArgumentException("Adjacency must reference supplied finite cells only");
                if (manhattan(pos, neighbor) != 1) throw new IllegalArgumentException("Open adjacency must be face-connected");
                Set<BlockPos> reverse = loadedOpenAdjacency.get(neighbor);
                if (reverse == null || !reverse.contains(pos)) throw new IllegalArgumentException("Open adjacency must be symmetric");
            }
            graph.put(pos, List.copyOf(faceOrderedNeighbors(pos, adjacent)));
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
                RoutingSearch routing = new RoutingSearch(donor, graph);
                while (donorAvailable > 0.0 && ri < receivers.size()) {
                    BlockPos receiver = receivers.get(ri);
                    receiverNeed = Math.max(0.0, target - states.get(receiver).totalMoles());
                    if (receiverNeed == 0.0) {
                        ri++;
                        continue;
                    }
                    List<BlockPos> path = routing.pathTo(receiver, MAX_EDGE_SEARCH_WORK - searchWork);
                    searchWork += routing.takeNewExpansions();
                    if (path == null) {
                        complete = false; status = "edge-search-budget-exhausted"; break outer;
                    }
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
                        GasMixture.Removal split = source.splitRatio(fraction);
                        GasMixture packet = split.removed();
                        states.put(from, split.remaining());
                        GasMixture current = states.get(to);
                        states.put(to, add(current, packet));
                        flows.merge(new DirectedEdge(from, to), packet.totalMoles(), Double::sum);
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
        return new Result(Map.copyOf(states), Map.copyOf(flows), new Work(input.size(), searchWork, operations, complete, status),
                BoundaryExchange.empty(), input.keySet());
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

    private static final class RoutingSearch {
        private final BlockPos root;
        private final Map<BlockPos, List<BlockPos>> graph;
        private final ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        private final Map<BlockPos, BlockPos> previous = new HashMap<>();
        private final Set<BlockPos> seen = new LinkedHashSet<>();
        private final Set<BlockPos> expanded = new LinkedHashSet<>();
        private int unreportedExpansions;

        private RoutingSearch(BlockPos root, Map<BlockPos, List<BlockPos>> graph) {
            this.root = root;
            this.graph = graph;
            queue.add(root);
            seen.add(root);
        }

        private List<BlockPos> pathTo(BlockPos goal, int remainingWork) {
            while (!expanded.contains(goal)) {
                if (queue.isEmpty() || unreportedExpansions >= remainingWork) return null;
                BlockPos current = queue.remove();
                expanded.add(current);
                unreportedExpansions++;
                for (BlockPos neighbor : graph.get(current)) {
                    if (seen.add(neighbor)) { previous.put(neighbor, current); queue.add(neighbor); }
                }
            }
        List<BlockPos> path = new ArrayList<>();
            for (BlockPos at = goal; at != null; at = previous.get(at)) { path.add(at); if (at.equals(root)) break; }
        java.util.Collections.reverse(path);
            return path;
        }

        private int takeNewExpansions() {
            int count = unreportedExpansions;
            unreportedExpansions = 0;
            return count;
        }
    }

    private static GasMixture add(GasMixture mixture, GasMixture packet) {
        return mixture.mergedWith(packet);
    }

    private static double tolerance(double value) { return Math.ulp(Math.max(1.0, value)) * 8; }
}

