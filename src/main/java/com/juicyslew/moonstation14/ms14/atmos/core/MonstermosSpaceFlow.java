package com.juicyslew.moonstation14.ms14.atmos.core;

import net.minecraft.core.BlockPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Pure, bounded Monstermos-inspired routing of finite-cell gas to explicit space openings. */
public final class MonstermosSpaceFlow {
    public static final int MAX_CANDIDATE_CELLS = 8_000;
    public static final int MAX_APPLIED_CELLS = 800;
    public static final int MAX_EXTERIOR_POSITIONS = 8_000;
    public static final double SPACING_ESCAPE_RATIO = 0.15;
    public static final double SPACING_MINIMUM_MOLES = 2.0;
    public static final double SPACING_MAX_WIND_KPA = 500.0;
    public static final double MINIMUM_RELEASE_PRESSURE_KPA = 10.0;
    /** Below this local inventory, finish clearing numerical residue rather than leave a gas speck. */
    public static final double SPACE_FLOW_VACUUM_EPSILON_MOLES = 1.0e-6;
    /** Small visible inventory retained by an otherwise empty finite transit cell. */
    public static final double SPACE_FLOW_INTERIOR_RETENTION_MOLES = 0.6;
    private static final double GAS_CONSTANT = 8.31446261815324;
    private static final Comparator<BlockPos> POSITION_ORDER = Comparator.comparingLong((BlockPos pos) -> pos.getX())
            .thenComparingLong(pos -> pos.getY()).thenComparingLong(pos -> pos.getZ());

    private MonstermosSpaceFlow() { }

    public record DirectedEdge(BlockPos from, BlockPos to) {
        public DirectedEdge {
            from = Objects.requireNonNull(from, "from").immutable();
            to = Objects.requireNonNull(to, "to").immutable();
        }
    }

    /** Signed export is positive out of the finite patch; energy is joules. */
    public record ExportLedger(Map<GasType, Double> speciesMoles, double thermalEnergyJoules) {
        public ExportLedger { speciesMoles = Map.copyOf(speciesMoles); }
    }

    public record Work(int discoveredCells, int cellSteps, int simulationCalls,
                       boolean complete, String status) { }

    public record Result(Map<BlockPos, GasMixture> states, ExportLedger exported,
                         Map<DirectedEdge, Double> edgeTransfersMoles,
                         Map<DirectedEdge, List<BlockPos>> paths, Work work) {
        public Result {
            states = Map.copyOf(states);
            edgeTransfersMoles = Map.copyOf(edgeTransfersMoles);
            Map<DirectedEdge, List<BlockPos>> copy = new LinkedHashMap<>();
            paths.forEach((edge, path) -> copy.put(edge, List.copyOf(path)));
            paths = Map.copyOf(copy);
        }
    }

    /**
     * Routes gas inward-to-outward through the supplied finite graph and exports it only through
     * listed exterior positions. Missing adjacency is closed/unknown, never treated as vacuum.
     * Inputs above 800 candidate cells return an incomplete, unapplied result so callers can
     * segment work. The absolute traversal ceiling is 8,000 candidates.
     */
    public static Result run(Map<BlockPos, GasMixture> finiteInputs,
                             Map<BlockPos, Set<BlockPos>> finiteAdjacency,
                             Set<BlockPos> exteriorPositions, int maxCandidateCells) {
        Objects.requireNonNull(finiteInputs, "finiteInputs");
        Objects.requireNonNull(finiteAdjacency, "finiteAdjacency");
        Objects.requireNonNull(exteriorPositions, "exteriorPositions");
        if (maxCandidateCells <= 0 || maxCandidateCells > MAX_CANDIDATE_CELLS) {
            throw new IllegalArgumentException("maxCandidateCells must be in [1, 8000]");
        }
        Map<BlockPos, GasMixture> input = new LinkedHashMap<>();
        finiteInputs.entrySet().stream().sorted(Map.Entry.comparingByKey(POSITION_ORDER)).forEach(entry -> {
            if (entry.getKey() == null || entry.getValue() == null) throw new IllegalArgumentException("Finite cells cannot be null");
            input.put(entry.getKey().immutable(), entry.getValue());
        });
        if (input.size() > MAX_CANDIDATE_CELLS) throw new IllegalArgumentException("Input exceeds 8000-cell traversal ceiling");
        if (input.size() > MAX_APPLIED_CELLS || input.size() > maxCandidateCells) {
            return incomplete(input.size(), input.size() > MAX_APPLIED_CELLS ? "applied-cell-budget-exceeded" : "candidate-budget-exceeded");
        }
        if (exteriorPositions.size() > MAX_EXTERIOR_POSITIONS) {
            throw new IllegalArgumentException("Exterior positions exceed 8000-position scan ceiling");
        }
        Set<BlockPos> exteriorSnapshot = new HashSet<>();
        for (BlockPos exterior : exteriorPositions) {
            if (exterior == null) throw new IllegalArgumentException("Exterior positions cannot contain null");
            BlockPos snapshot = exterior.immutable();
            if (input.containsKey(snapshot)) throw new IllegalArgumentException("Exterior positions cannot overlap finite cells");
            if (!exteriorSnapshot.add(snapshot)) {
                throw new IllegalArgumentException("Exterior positions must be unique after immutable snapshotting");
            }
        }
        List<BlockPos> orderedExterior = exteriorSnapshot.stream().sorted(POSITION_ORDER).toList();
        Map<BlockPos, List<BlockPos>> graph = new LinkedHashMap<>();
        for (BlockPos pos : input.keySet()) {
            Set<BlockPos> adjacent = finiteAdjacency.getOrDefault(pos, Set.of());
            List<BlockPos> neighbors = new ArrayList<>();
            for (BlockPos neighbor : adjacent) {
                if (!input.containsKey(neighbor) || manhattan(pos, neighbor) != 1) throw new IllegalArgumentException("Adjacency must reference face-adjacent finite cells");
                if (!finiteAdjacency.getOrDefault(neighbor, Set.of()).contains(pos)) throw new IllegalArgumentException("Finite adjacency must be symmetric");
                neighbors.add(neighbor.immutable());
            }
            neighbors.sort(POSITION_ORDER);
            graph.put(pos, List.copyOf(neighbors));
        }
        Map<BlockPos, List<BlockPos>> openings = new LinkedHashMap<>();
        int exteriorNeighborChecks = 0;
        List<BlockPos> faceOffsets = List.of(new BlockPos(1, 0, 0), new BlockPos(-1, 0, 0),
                new BlockPos(0, 1, 0), new BlockPos(0, -1, 0), new BlockPos(0, 0, 1), new BlockPos(0, 0, -1));
        for (BlockPos exterior : orderedExterior) {
            for (BlockPos offset : faceOffsets) {
                exteriorNeighborChecks++;
                BlockPos boundary = exterior.offset(offset).immutable();
                if (input.containsKey(boundary)) {
                    openings.computeIfAbsent(boundary, ignored -> new ArrayList<>()).add(exterior);
                }
            }
        }
        openings.replaceAll((pos, faces) -> List.copyOf(faces));
        if (openings.isEmpty() || input.isEmpty()) {
            return new Result(input, new ExportLedger(Map.of(), 0), Map.of(), Map.of(),
                    new Work(input.size(), input.size() + exteriorNeighborChecks, 0, true, "no-space-opening"));
        }

        DistanceSearch search = distances(openings.keySet(), graph);
        Map<BlockPos, Integer> distance = search.distances();
        if (distance.size() > maxCandidateCells) return incomplete(distance.size(), "candidate-budget-exceeded");
        Map<BlockPos, GasMixture> states = new LinkedHashMap<>(input);
        Map<DirectedEdge, Double> transfers = new LinkedHashMap<>();
        Map<DirectedEdge, List<BlockPos>> paths = new LinkedHashMap<>();
        Map<BlockPos, Double> inboundMoles = new HashMap<>();
        int calls = 0;
        Map<BlockPos, BlockPos> predecessor = new HashMap<>();
        for (BlockPos pos : distance.keySet()) {
            int depth = distance.get(pos);
            if (depth == 0) continue;
            graph.get(pos).stream()
                    .filter(neighbor -> distance.getOrDefault(neighbor, Integer.MAX_VALUE) == depth - 1)
                    .min(POSITION_ORDER)
                    .ifPresent(parent -> predecessor.put(pos, parent));
        }
        List<BlockPos> inward = new ArrayList<>(distance.keySet());
        inward.sort(Comparator.<BlockPos>comparingInt(distance::get).reversed().thenComparing(POSITION_ORDER));
        for (BlockPos sourcePos : inward) {
            BlockPos parent = predecessor.get(sourcePos);
            if (parent == null) continue;
            GasMixture source = states.get(sourcePos);
            double initialMoles = input.get(sourcePos).totalMoles();
            double amount = initialMoles * SPACING_ESCAPE_RATIO + inboundMoles.getOrDefault(sourcePos, 0.0);
            if (source.totalMoles() <= SPACE_FLOW_VACUUM_EPSILON_MOLES) amount = source.totalMoles();
            // Keep only the deficit to a small finite-cell inventory from this invocation's inbound
            // packet. Once filled, inbound gas passes through without per-hop attenuation.
            double inbound = inboundMoles.getOrDefault(sourcePos, 0.0);
            double retained = Math.min(inbound, Math.max(0.0,
                    SPACE_FLOW_INTERIOR_RETENTION_MOLES - initialMoles));
            amount -= retained;
            if (amount <= 0.0) continue;
            amount = Math.min(amount, source.totalMoles());
            GasMixture packet = source.withScaledMoles(amount / source.totalMoles());
            states.put(sourcePos, source.withScaledMoles(1.0 - amount / source.totalMoles()));
            states.put(parent, add(states.get(parent), packet));
            inboundMoles.merge(parent, amount, Double::sum);
            DirectedEdge edge = new DirectedEdge(sourcePos, parent);
            transfers.merge(edge, amount, Double::sum);
            paths.put(edge, List.of(sourcePos, parent));
            calls++;
        }

        EnumMap<GasType, Double> exported = new EnumMap<>(GasType.class);
        double energyExported = 0.0;
        for (BlockPos boundary : openings.keySet().stream().sorted(POSITION_ORDER).toList()) {
            GasMixture source = states.get(boundary);
            double pressure = source.pressureKpa(1.0);
            double pressureBudget = Math.min(SPACING_MAX_WIND_KPA, Math.max(0.0, pressure * 0.5));
            if (pressureBudget < MINIMUM_RELEASE_PRESSURE_KPA && source.totalMoles() * GAS_CONSTANT * source.temperatureKelvin() / 1000.0 > 0) {
                // Spacing decay is intentionally allowed below the pressure-release threshold.
                pressureBudget = pressure;
            }
            double capMoles = pressureBudget * 1000.0 / (GAS_CONSTANT * Math.max(source.temperatureKelvin(), 1e-12));
            // The SS14 minimum is a pressure-release reference, not a per-tick vacuuming floor.
            // Child packets are already requested escape gas and must not be attenuated per hop.
            double initialMoles = input.get(boundary).totalMoles();
            double requested = initialMoles * SPACING_ESCAPE_RATIO + inboundMoles.getOrDefault(boundary, 0.0);
            if (source.totalMoles() <= SPACE_FLOW_VACUUM_EPSILON_MOLES) requested = source.totalMoles();
            double amount = Math.min(requested, capMoles);
            if (amount <= 0.0) continue;
            double fraction = amount / source.totalMoles();
            GasMixture packet = source.withScaledMoles(fraction);
            states.put(boundary, source.withScaledMoles(1.0 - fraction));
            packet.gasMoles().forEach((gas, moles) -> exported.merge(gas, moles, Double::sum));
            energyExported += packet.thermalEnergy();
            BlockPos exterior = openings.get(boundary).get(0);
            DirectedEdge edge = new DirectedEdge(boundary, exterior);
            transfers.merge(edge, amount, Double::sum);
            paths.put(edge, List.of(boundary, exterior));
            calls++;
        }
        return new Result(states, new ExportLedger(exported, energyExported), transfers, paths,
                new Work(distance.size(), exteriorNeighborChecks + distance.size() + search.traversedEdges() + calls, calls, true, "complete"));
    }

    private static Result unchanged(Map<BlockPos, GasMixture> input, String status) {
        return new Result(input, new ExportLedger(Map.of(), 0), Map.of(), Map.of(), new Work(input.size(), input.size(), 0, true, status));
    }

    private static Result incomplete(int discovered, String status) {
        return new Result(Map.of(), new ExportLedger(Map.of(), 0), Map.of(), Map.of(), new Work(discovered, 0, 0, false, status));
    }

    private record DistanceSearch(Map<BlockPos, Integer> distances, int traversedEdges) { }

    private static DistanceSearch distances(Set<BlockPos> seeds, Map<BlockPos, List<BlockPos>> graph) {
        Map<BlockPos, Integer> result = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seeds.stream().sorted(POSITION_ORDER).forEach(pos -> { result.put(pos, 0); queue.add(pos); });
        int traversedEdges = 0;
        while (!queue.isEmpty()) {
            BlockPos current = queue.remove();
            for (BlockPos next : graph.get(current)) {
                traversedEdges++;
                if (!result.containsKey(next)) {
                    result.put(next, result.get(current) + 1);
                    queue.add(next);
                }
            }
        }
        return new DistanceSearch(result, traversedEdges);
    }

    private static GasMixture add(GasMixture first, GasMixture second) {
        EnumMap<GasType, Double> gases = new EnumMap<>(GasType.class);
        gases.putAll(first.gasMoles());
        second.gasMoles().forEach((gas, amount) -> gases.merge(gas, amount, Double::sum));
        double energy = first.thermalEnergy() + second.thermalEnergy();
        double capacity = gases.entrySet().stream().mapToDouble(entry -> entry.getValue() * entry.getKey().molarHeatCapacity()).sum();
        return new GasMixture(gases, capacity == 0 ? first.temperatureKelvin() : energy / capacity);
    }

    private static int manhattan(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY()) + Math.abs(a.getZ() - b.getZ());
    }
}
