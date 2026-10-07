package com.juicyslew.moonstation14.ms14.atmos.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

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

    /** Pure SS14-derived Monstermos space progression over finite cells and explicit Space faces. */
public final class MonstermosSpaceFlow {
    public static final int MAX_CANDIDATE_CELLS = 2_000;
    /** Space progression applies through the full upstream hard discovery bound. */
    public static final int MAX_APPLIED_CELLS = 2_000;
    public static final int MAX_EXTERIOR_POSITIONS = 8_000;
    public static final double SPACING_ESCAPE_RATIO = 0.15;
    /** Upstream SpacingMinGas is a numeric target pressure (kPa), despite its gas-oriented name. */
    public static final double SPACING_MINIMUM_TARGET_KPA = 2.0;
    public static final double SPACING_MAX_WIND_KPA = 500.0;
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
     * Discovery/progression is bounded at the upstream 2,000-cell hard limit; no 800-cell local
     * segmentation limit or independent 8,000-cell transfer cap is applied.
     */
    public static Result run(Map<BlockPos, GasMixture> finiteInputs,
                             Map<BlockPos, Set<BlockPos>> finiteAdjacency,
                             Set<BlockPos> exteriorPositions, int maxCandidateCells) {
        Objects.requireNonNull(exteriorPositions, "exteriorPositions");
        Set<BlockPos> exterior = new HashSet<>(exteriorPositions);
        Map<DirectedEdge, GasMixture> faces = new LinkedHashMap<>();
        for (BlockPos cell : finiteInputs.keySet().stream().sorted(POSITION_ORDER).toList()) for (Direction direction : Direction.values()) {
            BlockPos outside = cell.relative(direction);
            if (exterior.contains(outside)) faces.put(new DirectedEdge(cell, outside), GasMixture.vacuum());
        }
        BlockPos seed = finiteInputs.keySet().stream().min(POSITION_ORDER).orElse(null);
        return runUpstreamSpace(finiteInputs, finiteAdjacency, faces, seed, maxCandidateCells);
    }

    /**
     * Upstream-shaped overload accepting the immutable gas profile for each finite-to-Space face.
     * Keys are directed from the finite boundary cell toward its adjacent Space cell.
     */
    public static Result run(Map<BlockPos, GasMixture> finiteInputs,
                             Map<BlockPos, Set<BlockPos>> finiteAdjacency,
                             Map<DirectedEdge, GasMixture> immutableSpaceFaces, int maxCandidateCells) {
        BlockPos seed = finiteInputs.keySet().stream().min(POSITION_ORDER).orElse(null);
        return runUpstreamSpace(finiteInputs, finiteAdjacency, immutableSpaceFaces, seed, maxCandidateCells);
    }

    /** Starts discovery at the triggering finite cell; Space is represented by one immutable layer. */
    public static Result run(Map<BlockPos, GasMixture> finiteInputs,
                             Map<BlockPos, Set<BlockPos>> finiteAdjacency,
                             Map<DirectedEdge, GasMixture> immutableSpaceFaces, BlockPos seed,
                             int maxCandidateCells) {
        return runUpstreamSpace(finiteInputs, finiteAdjacency, immutableSpaceFaces, seed, maxCandidateCells);
    }

    /** SS14 explosive space progression adapted to ordered six Minecraft faces and 1,000 L cells. */
    private static Result runUpstreamSpace(Map<BlockPos, GasMixture> finiteInputs,
                                           Map<BlockPos, Set<BlockPos>> finiteAdjacency,
                                           Map<DirectedEdge, GasMixture> immutableSpaceFaces, BlockPos seed,
                                           int maxCandidateCells) {
        Objects.requireNonNull(finiteInputs, "finiteInputs");
        Objects.requireNonNull(finiteAdjacency, "finiteAdjacency");
        Objects.requireNonNull(immutableSpaceFaces, "immutableSpaceFaces");
        if (maxCandidateCells <= 0 || maxCandidateCells > 8_000) {
            throw new IllegalArgumentException("maxCandidateCells must be in [1, 8000]");
        }
        Map<BlockPos, GasMixture> input = new LinkedHashMap<>();
        finiteInputs.entrySet().stream().sorted(Map.Entry.comparingByKey(POSITION_ORDER)).forEach(entry -> {
            if (entry.getKey() == null || entry.getValue() == null) throw new IllegalArgumentException("Finite cells cannot be null");
            input.put(entry.getKey().immutable(), entry.getValue());
        });
        if (input.size() > MAX_EXTERIOR_POSITIONS)
            throw new IllegalArgumentException("Supplied finite frontier exceeds the 8000-cell adapter safety ceiling");
        if (immutableSpaceFaces.size() > MAX_EXTERIOR_POSITIONS) {
            throw new IllegalArgumentException("Exterior positions exceed 8000-position scan ceiling");
        }
        Map<BlockPos, List<BlockPos>> graph = new LinkedHashMap<>();
        for (BlockPos pos : input.keySet()) {
            Set<BlockPos> adjacent = finiteAdjacency.getOrDefault(pos, Set.of());
            for (BlockPos neighbor : adjacent) {
                if (!input.containsKey(neighbor) || manhattan(pos, neighbor) != 1) throw new IllegalArgumentException("Adjacency must reference face-adjacent finite cells");
                if (!finiteAdjacency.getOrDefault(neighbor, Set.of()).contains(pos)) throw new IllegalArgumentException("Finite adjacency must be symmetric");
            }
            graph.put(pos, List.copyOf(faceOrderedNeighbors(pos, adjacent)));
        }
        Map<BlockPos, List<BlockPos>> openings = new LinkedHashMap<>();
        int exteriorNeighborChecks = 0;
        Map<DirectedEdge, GasMixture> spaceFaces = new LinkedHashMap<>();
        List<Map.Entry<DirectedEdge, GasMixture>> orderedFaces = immutableSpaceFaces.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(DirectedEdge::from, POSITION_ORDER)
                        .thenComparingInt(edge -> faceDirectionIndex(edge.from(), edge.to())))).toList();
        for (var face : orderedFaces) {
            DirectedEdge edge = face.getKey();
            if (edge == null || face.getValue() == null || !input.containsKey(edge.from())
                    || input.containsKey(edge.to()) || manhattan(edge.from(), edge.to()) != 1)
                throw new IllegalArgumentException("Space faces must be verified finite-to-space face neighbors");
            spaceFaces.put(edge, face.getValue());
            openings.computeIfAbsent(edge.from(), ignored -> new ArrayList<>()).add(edge.to());
            exteriorNeighborChecks++;
        }
        openings.replaceAll((pos, faces) -> List.copyOf(faces));
        if (openings.isEmpty() || input.isEmpty()) {
            return new Result(input, new ExportLedger(Map.of(), 0), Map.of(), Map.of(),
                    new Work(input.size(), input.size() + exteriorNeighborChecks, 0, true, "no-space-opening"));
        }

        // Seed each boundary cell as a Space neighbor would seed a progression root. BFS records
        // one deterministic parent per finite tile; processing is then reversed toward the opening.
        if (seed == null || !input.containsKey(seed))
            throw new IllegalArgumentException("Discovery seed must identify a supplied finite cell");
        // Initial discovery is a stable face-ordered BFS over finite cells and a single immutable
        // layer of Space vertices. Space vertices count against the same upstream 2,000 budget,
        // but are terminal: this adapter does not traverse the unbounded exterior world.
        int discoveryLimit = Math.min(MAX_CANDIDATE_CELLS, maxCandidateCells);
        Map<BlockPos, List<DirectedEdge>> spaceByOutside = new HashMap<>();
        spaceFaces.keySet().forEach(edge -> spaceByOutside.computeIfAbsent(edge.to(), ignored -> new ArrayList<>()).add(edge));
        Map<BlockPos, Integer> discoveredFinite = new LinkedHashMap<>();
        Set<BlockPos> discoveredSpace = new HashSet<>();
        List<BlockPos> spaceOrder = new ArrayList<>();
        ArrayDeque<BlockPos> discovery = new ArrayDeque<>();
        discoveredFinite.put(seed, 0);
        discovery.add(seed);
        boolean hardFrontier = false;
        int traversed = 0;
        while (!discovery.isEmpty()) {
            BlockPos current = discovery.remove();
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction).immutable();
                if (input.containsKey(next) && graph.get(current).contains(next)) {
                    traversed++;
                    if (!discoveredFinite.containsKey(next)) {
                        if (discoveredFinite.size() + discoveredSpace.size() >= discoveryLimit) { hardFrontier = true; continue; }
                        discoveredFinite.put(next, discoveredFinite.get(current) + 1);
                        discovery.add(next);
                    }
                }
                List<DirectedEdge> facesAtPosition = spaceByOutside.get(next);
                if (facesAtPosition != null && facesAtPosition.stream().anyMatch(edge -> edge.from().equals(current))
                        && discoveredSpace.add(next)) {
                    if (discoveredFinite.size() + discoveredSpace.size() > discoveryLimit) {
                        discoveredSpace.remove(next); hardFrontier = true; continue;
                    }
                    spaceOrder.add(next);
                }
            }
        }
        // Space vertices are terminal but are discovered when the finite boundary face is scanned.
        // Rebuild their stable order against budget after finite nodes, as each BFS step scans faces.
        List<BlockPos> roots = new ArrayList<>();
        for (BlockPos outside : spaceOrder) {
            for (Direction direction : Direction.values()) {
                BlockPos boundary = outside.relative(direction);
                if (openings.getOrDefault(boundary, List.of()).contains(outside)
                        && discoveredFinite.containsKey(boundary)) roots.add(boundary);
            }
        }
        Map<BlockPos, Integer> distance = new LinkedHashMap<>();
        Map<BlockPos, BlockPos> predecessor = new HashMap<>();
        ArrayDeque<BlockPos> bfs = new ArrayDeque<>();
        for (BlockPos pos : roots) {
            if (distance.size() >= discoveredFinite.size()) break;
            if (!discoveredFinite.containsKey(pos) || distance.containsKey(pos)) continue;
            distance.put(pos, 0);
            bfs.add(pos);
        }
        while (!bfs.isEmpty()) {
            BlockPos current = bfs.remove();
            for (BlockPos next : graph.get(current)) {
                traversed++;
                if (!discoveredFinite.containsKey(next) || distance.containsKey(next)) continue;
                if (distance.putIfAbsent(next, distance.get(current) + 1) == null) {
                    predecessor.put(next, current); bfs.add(next);
                }
            }
        }
        Map<BlockPos, GasMixture> states = new LinkedHashMap<>(input);
        Map<DirectedEdge, Double> transfers = new LinkedHashMap<>();
        Map<DirectedEdge, List<BlockPos>> paths = new LinkedHashMap<>();
        Map<BlockPos, Double> progressionMoles = new HashMap<>();
        int calls = 0;
        Map<BlockPos, Integer> progressionIndex = new HashMap<>();
        int progressionOrdinal = 0;
        for (BlockPos pos : distance.keySet()) progressionIndex.put(pos, progressionOrdinal++);
        List<BlockPos> inward = new ArrayList<>(distance.keySet());
        inward.sort(Comparator.<BlockPos>comparingInt(distance::get).reversed()
                .thenComparing(Comparator.comparingInt((BlockPos pos) -> progressionIndex.get(pos)).reversed()));
        for (BlockPos sourcePos : inward) {
            BlockPos parent = predecessor.get(sourcePos);
            GasMixture source = states.get(sourcePos);
            double sourceReferenceMoles = AtmosphereUnits.localCellQuantityToReferenceTile(source.totalMoles());
            double requestedReference = sourceReferenceMoles * SPACING_ESCAPE_RATIO;
            if (SPACING_ESCAPE_RATIO < 1.0 && requestedReference < SPACING_MINIMUM_TARGET_KPA)
                requestedReference = Math.min(SPACING_MINIMUM_TARGET_KPA, sourceReferenceMoles);
            double inbound = progressionMoles.getOrDefault(sourcePos, 0.0);
            if (requestedReference + inbound > SPACING_MAX_WIND_KPA)
                requestedReference = Math.max(SPACING_MINIMUM_TARGET_KPA, SPACING_MAX_WIND_KPA - inbound);
            requestedReference = Math.min(requestedReference, SPACING_MAX_WIND_KPA);
            if (parent != null) progressionMoles.merge(parent, requestedReference + inbound, Double::sum);
            if (source.pressureKpa(source.volumeCubicMeters()) - requestedReference <= 0.2) {
                states.put(sourcePos, new GasMixture(Map.of(), GasMixture.MIN_TEMPERATURE_KELVIN,
                        source.volumeCubicMeters()));
                continue;
            }
            BlockPos exterior = parent == null ? openings.get(sourcePos).get(0) : null;
            GasMixture target = parent == null
                    ? spaceFaces.get(new DirectedEdge(sourcePos, exterior)) : states.get(parent);
            Release moved = releaseTo(source, target, requestedReference, false, parent == null);
            if (moved.amount > 0) {
                states.put(sourcePos, moved.source);
                if (parent != null) states.put(parent, moved.output);
                BlockPos targetPos = parent == null ? exterior : parent;
                record(transfers, paths, new DirectedEdge(sourcePos, targetPos), moved.amount, pathToRoot(sourcePos, predecessor));
                calls++;
            }
            GasMixture spaceMixture = parent == null ? moved.output : states.get(parent);
            Release vent = releaseTo(spaceMixture, null, requestedReference * 0.3, parent == null, false);
            if (parent != null) states.put(parent, vent.source);
            GasMixture cooled = states.get(sourcePos);
            if (cooled.temperatureKelvin() > 280.0 && cooled.totalMoles() > 0) {
                double realLoss = (cooled.totalMoles() - AtmosphereUnits.referenceTileQuantity(requestedReference))
                        / cooled.totalMoles();
                cooled = new GasMixture(cooled.gasMoles(),
                        cooled.temperatureKelvin() * (0.9 + 0.1 * realLoss), cooled.volumeCubicMeters());
                states.put(sourcePos, cooled);
            }
            if (vent.amount > 0) {
                BlockPos ventSource = parent == null ? sourcePos : parent;
                BlockPos ventTarget = predecessor.containsKey(ventSource)
                        ? predecessor.get(ventSource) : openings.get(ventSource).get(0);
                DirectedEdge edge = new DirectedEdge(ventSource, ventTarget);
                transfers.merge(edge, vent.amount, Double::sum);
                List<BlockPos> ventPath = new ArrayList<>(pathToRoot(ventSource, predecessor));
                ventPath.add(ventTarget);
                paths.put(edge, List.copyOf(ventPath));
                calls++;
            }
        }

        EnumMap<GasType, Double> exported = new EnumMap<>(GasType.class);
        double energyExported = 0.0;
        for (BlockPos pos : input.keySet()) for (GasType gas : GasType.values())
            exported.merge(gas, input.get(pos).moles(gas) - states.get(pos).moles(gas), Double::sum);
        energyExported = input.values().stream().mapToDouble(GasMixture::thermalEnergy).sum()
                - states.values().stream().mapToDouble(GasMixture::thermalEnergy).sum();
        exported.entrySet().removeIf(e -> e.getValue() == 0.0);
        return new Result(states, new ExportLedger(exported, energyExported), transfers, paths,
                new Work(discoveredFinite.size() + discoveredSpace.size(), exteriorNeighborChecks + distance.size() + traversed + calls, calls, true,
                        hardFrontier ? "hard-discovery-frontier-bounded" : "upstream-space-progression"));
    }

    private record Release(GasMixture source, GasMixture output, double amount) { }

    /** ReleaseGasTo: target pressure is deliberately the upstream mole-derived numeric target. */
    private static Release releaseTo(GasMixture source, GasMixture output, double targetPressureKpa,
                                     boolean immutableSource, boolean immutableOutput) {
        double inputPressure = source.pressureKpa(source.volumeCubicMeters());
        double outputPressure = output == null ? 0 : output.pressureKpa(output.volumeCubicMeters());
        if (immutableSource || outputPressure >= Math.min(targetPressureKpa, inputPressure - 10.0)
                || source.totalMoles() <= 0 || source.temperatureKelvin() <= 0)
            return new Release(source, output, 0);
        double pressureDelta = Math.min(targetPressureKpa - outputPressure, (inputPressure - outputPressure) / 2.0);
        double volumeLiters = output == null ? source.volumeCubicMeters() * 1000.0 : output.volumeCubicMeters() * 1000.0;
        double amount = Math.min(source.totalMoles(), Math.max(0, pressureDelta) * volumeLiters
                / (source.temperatureKelvin() * GAS_CONSTANT));
        if (!(amount > 0)) return new Release(source, output, 0);
        double fraction = amount / source.totalMoles();
        GasMixture.Removal split = source.splitRatio(fraction);
        GasMixture removed = split.removed();
        return new Release(split.remaining(), output == null || immutableOutput ? output : output.mergedWith(removed), removed.totalMoles());
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

    private static int faceDirectionIndex(BlockPos from, BlockPos to) {
        for (int i = 0; i < Direction.values().length; i++)
            if (from.relative(Direction.values()[i]).equals(to)) return i;
        return Integer.MAX_VALUE;
    }

    private static List<BlockPos> pathToRoot(BlockPos start, Map<BlockPos, BlockPos> previous) {
        List<BlockPos> path = new ArrayList<>();
        for (BlockPos at = start; at != null; at = previous.get(at)) path.add(at);
        return List.copyOf(path);
    }

    private static void record(Map<DirectedEdge, Double> transfers, Map<DirectedEdge, List<BlockPos>> paths,
                                DirectedEdge edge, double amount, List<BlockPos> path) {
        transfers.merge(edge, amount, Double::sum);
        paths.put(edge, path);
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

    /**
     * @deprecated Historical local ambient bisection adapter. Non-space immutable ambient is now
     * handled by LINDA; production callers must not use this pressure-directed approximation.
     * Pressure-directed finite graph exchange against immutable, preflighted exterior faces.
     * Each invocation moves packets across actual adjacent edges only. A second invocation can
     * carry the pressure wave further; the exterior is never stored in the returned states.
     */
    @Deprecated
    public static Result runAmbient(Map<BlockPos, GasMixture> finiteInputs,
                                    Map<BlockPos, Set<BlockPos>> finiteAdjacency,
                                    Map<DirectedEdge, GasMixture> exteriorFaces, int maxCandidateCells) {
        Objects.requireNonNull(finiteInputs);
        Objects.requireNonNull(finiteAdjacency);
        Objects.requireNonNull(exteriorFaces);
        if (maxCandidateCells <= 0 || maxCandidateCells > MAX_CANDIDATE_CELLS)
            throw new IllegalArgumentException("Invalid candidate budget");
        if (finiteInputs.size() > MAX_CANDIDATE_CELLS || exteriorFaces.size() > MAX_EXTERIOR_POSITIONS)
            throw new IllegalArgumentException("Traversal ceiling exceeded");
        if (finiteInputs.size() > MAX_APPLIED_CELLS || finiteInputs.size() > maxCandidateCells)
            return incomplete(finiteInputs.size(), "applied-cell-budget-exceeded");
        Map<BlockPos, GasMixture> states = new LinkedHashMap<>();
        finiteInputs.entrySet().stream().sorted(Map.Entry.comparingByKey(POSITION_ORDER)).forEach(e -> {
            if (e.getKey() == null || e.getValue() == null) throw new IllegalArgumentException("Null finite cell");
            states.put(e.getKey().immutable(), e.getValue());
        });
        Map<BlockPos, List<BlockPos>> graph = new LinkedHashMap<>();
        for (BlockPos pos : states.keySet()) {
            List<BlockPos> neighbors = new ArrayList<>();
            for (BlockPos neighbor : finiteAdjacency.getOrDefault(pos, Set.of())) {
                if (!states.containsKey(neighbor) || manhattan(pos, neighbor) != 1
                        || !finiteAdjacency.getOrDefault(neighbor, Set.of()).contains(pos))
                    throw new IllegalArgumentException("Invalid finite adjacency");
                neighbors.add(neighbor.immutable());
            }
            neighbors.sort(POSITION_ORDER);
            graph.put(pos, neighbors);
        }
        List<DirectedEdge> faces = exteriorFaces.keySet().stream()
                .sorted(Comparator.comparing(DirectedEdge::from, POSITION_ORDER)
                        .thenComparing(DirectedEdge::to, POSITION_ORDER)).toList();
        for (DirectedEdge face : faces) {
            if (!states.containsKey(face.from()) || states.containsKey(face.to())
                    || manhattan(face.from(), face.to()) != 1 || exteriorFaces.get(face) == null)
                throw new IllegalArgumentException("Unverified exterior face");
        }
        if (faces.isEmpty()) return unchanged(states, "no-exterior-opening");
        Set<BlockPos> seeds = new HashSet<>();
        faces.forEach(face -> seeds.add(face.from()));
        DistanceSearch search = distances(seeds, graph);
        Map<BlockPos, Integer> depth = search.distances();
        List<BlockPos> inward = new ArrayList<>(depth.keySet());
        inward.sort(Comparator.<BlockPos>comparingInt(depth::get).reversed().thenComparing(POSITION_ORDER));
        Map<DirectedEdge, Double> transfers = new LinkedHashMap<>();
        Map<DirectedEdge, List<BlockPos>> paths = new LinkedHashMap<>();
        Set<DirectedEdge> movedFiniteEdges = new HashSet<>();
        int calls = 0;
        int finiteEdgeChecks = 0;
        // Outward pressure relaxation, deepest first. An unmoved edge can later
        // carry newly imported gas inward, but never transfers twice in one call.
        for (BlockPos pos : inward) for (BlockPos neighbor : graph.get(pos)) {
            if (depth.getOrDefault(neighbor, Integer.MAX_VALUE) >= depth.get(pos)) continue;
            finiteEdgeChecks++;
            if (move(states, pos, neighbor, null, transfers, paths)) {
                movedFiniteEdges.add(finiteEdge(pos, neighbor));
                calls++;
            }
        }
        // Equal-depth neighbors (including adjacent doorway cells) have no BFS parent.
        // Relax each such face once before exterior exchange so either opening can export
        // the packet in this invocation. Position order is independent of input order.
        for (BlockPos pos : states.keySet()) for (BlockPos neighbor : graph.get(pos)) {
            if (POSITION_ORDER.compare(pos, neighbor) >= 0 || !depth.containsKey(pos)
                    || !depth.get(pos).equals(depth.get(neighbor))) continue;
            finiteEdgeChecks++;
            if (move(states, pos, neighbor, null, transfers, paths)) {
                movedFiniteEdges.add(finiteEdge(pos, neighbor));
                calls++;
            }
        }
        // Faces are sorted; each sees the updated boundary state from preceding exchanges.
        for (DirectedEdge face : faces)
            if (move(states, face.from(), face.to(), exteriorFaces.get(face), transfers, paths)) calls++;
        // Propagate incoming ambient down actual edges, nearest first. Outflow from remote
        // cells is likewise limited by local pressure rather than routing a remote packet.
        inward.sort(Comparator.<BlockPos>comparingInt(depth::get).thenComparing(POSITION_ORDER));
        for (BlockPos pos : inward) for (BlockPos neighbor : graph.get(pos)) {
            if (depth.getOrDefault(neighbor, Integer.MAX_VALUE) <= depth.get(pos)) continue;
            finiteEdgeChecks++;
            DirectedEdge edge = finiteEdge(pos, neighbor);
            if (!movedFiniteEdges.contains(edge) && move(states, pos, neighbor, null, transfers, paths)) {
                movedFiniteEdges.add(edge);
                calls++;
            }
        }
        EnumMap<GasType, Double> exported = new EnumMap<>(GasType.class);
        for (GasType gas : GasType.values()) {
            double delta = 0;
            for (BlockPos pos : states.keySet()) delta += finiteInputs.get(pos).moles(gas) - states.get(pos).moles(gas);
            if (delta != 0) exported.put(gas, delta);
        }
        double energy = 0;
        for (BlockPos pos : states.keySet()) energy += finiteInputs.get(pos).thermalEnergy() - states.get(pos).thermalEnergy();
        return new Result(states, new ExportLedger(exported, energy), transfers, paths,
                new Work(depth.size(), states.size() + search.traversedEdges() + finiteEdgeChecks + faces.size() + calls,
                        calls, true, "complete"));
    }

    private static DirectedEdge finiteEdge(BlockPos a, BlockPos b) {
        return POSITION_ORDER.compare(a, b) < 0 ? new DirectedEdge(a, b) : new DirectedEdge(b, a);
    }

    private static boolean move(Map<BlockPos, GasMixture> states, BlockPos a, BlockPos b,
                                GasMixture exterior, Map<DirectedEdge, Double> transfers,
                                Map<DirectedEdge, List<BlockPos>> paths) {
        GasMixture left = states.get(a), right = exterior == null ? states.get(b) : exterior;
        double difference = left.pressureKpa(1.0) - right.pressureKpa(1.0);
        if (difference == 0) return false;
        boolean outward = difference > 0;
        GasMixture donor = outward ? left : right;
        GasMixture receiver = outward ? right : left;
        if (donor.totalMoles() <= 0) return false;
        double cap = Math.min(donor.totalMoles(), Math.min(SPACING_MAX_WIND_KPA, Math.abs(difference))
                * 1000.0 / (GAS_CONSTANT * Math.max(donor.temperatureKelvin(), 1e-12)));
        // Bisection handles unequal donor/receiver temperatures and heat capacities: a warm
        // packet can raise receiver pressure faster than a simple mole-based estimate predicts.
        double donorMoles = donor.totalMoles();
        double donorCapacity = donor.heatCapacity();
        double donorEnergy = donor.thermalEnergy();
        double receiverMoles = receiver.totalMoles();
        double receiverCapacity = receiver.heatCapacity();
        double receiverEnergy = receiver.thermalEnergy();
        double donorPressure = donor.pressureKpa(1.0);
        double low = 0, high = cap;
        for (int i = 0; i < 48; i++) {
            double mid = (low + high) * 0.5;
            double ratio = mid / donorMoles;
            double trialReceiverPressure;
            if (exterior != null && outward) {
                trialReceiverPressure = receiver.pressureKpa(1.0);
            } else {
                double capacity = receiverCapacity + donorCapacity * ratio;
                double energy = receiverEnergy + donorEnergy * ratio;
                double temperature = capacity == 0.0 ? receiver.temperatureKelvin() : energy / capacity;
                trialReceiverPressure = (receiverMoles + mid) * GAS_CONSTANT * temperature / 1000.0;
            }
            double trialDonorPressure = exterior != null && !outward
                    ? donorPressure : donorPressure * (1.0 - ratio);
            if (trialReceiverPressure <= trialDonorPressure) low = mid;
            else high = mid;
        }
        double amount = low * 0.5; // bounded relaxation, never an equilibrium overshoot
        if (!(amount > 0)) return false;
        GasMixture packet = donor.withScaledMoles(amount / donor.totalMoles());
        BlockPos source = outward ? a : b, target = outward ? b : a;
        if (exterior == null || outward)
            states.put(source, donor.withScaledMoles(1.0 - amount / donor.totalMoles()));
        if (exterior == null || !outward) states.put(target, add(receiver, packet));
        DirectedEdge edge = new DirectedEdge(source, target);
        transfers.merge(edge, amount, Double::sum);
        paths.put(edge, List.of(source, target));
        return true;
    }

    private static int manhattan(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY()) + Math.abs(a.getZ() - b.getZ());
    }
}
