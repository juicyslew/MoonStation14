package com.juicyslew.moonstation14.ms14.atmos.core;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Pure, finite-cell LINDA-style neighbor exchange. No world access or vacuum boundary is modeled. */
public final class LindaGasSharing {
    private static final double STANDARD_MOLES = 101_325.0 / (8.31446261815324 * 293.15);
    private static final double COMPARE_MOLES = STANDARD_MOLES * 0.001;
    private static final double GAS_MIN_MOLES = 5e-8;
    private static final double OPEN_HEAT_TRANSFER_COEFFICIENT = 0.4;
    private static final double MINIMUM_TEMPERATURE_DELTA = 0.01;
    private static final double MINIMUM_HEAT_CAPACITY = (double) 0.0003f;
    private static final float MINIMUM_TEMPERATURE = (float) GasMixture.MIN_TEMPERATURE_KELVIN;
    private static final float MAXIMUM_TEMPERATURE = (float) GasMixture.MAX_TEMPERATURE_KELVIN;
    private static final double MINIMUM_TEMPERATURE_TO_COMPARE = 4.0;
    private static final double MINIMUM_MOLES_FOR_TEMPERATURE_COMPARE = 0.04157;

    // SS14 LINDA direction order: down, up, north, south, west, east.
    private static final List<BlockPos> DIRECTIONS = List.of(
            new BlockPos(0, -1, 0), new BlockPos(0, 1, 0),
            new BlockPos(0, 0, -1), new BlockPos(0, 0, 1),
            new BlockPos(-1, 0, 0), new BlockPos(1, 0, 0));

    private LindaGasSharing() { }

    /** An undirected, canonical cell pair. */
    public record Pair(BlockPos first, BlockPos second) {
        public Pair {
            Objects.requireNonNull(first, "first");
            Objects.requireNonNull(second, "second");
            if (compare(first, second) > 0) {
                BlockPos swap = first;
                first = second;
                second = swap;
            }
            first = first.immutable();
            second = second.immutable();
        }

        public static Pair of(BlockPos first, BlockPos second) { return new Pair(first, second); }
    }

    public record Result(Map<BlockPos, GasMixture> mixtures, Set<BlockPos> activatedNeighbors,
                         Map<BlockPos, Double> lastShare, Set<Pair> sharedPairs,
                         Map<Pair, Double> movedMolesByPair,
                         Map<Pair, Double> absoluteMovedMolesByPair,
                         Set<BlockPos> processedCells,
                         int workCount) { }

    /**
     * Applies a sequential live-mole exchange cycle. Compare thresholds use immutable cycle-start
     * snapshots, while each qualifying pair shares the current inventories immediately.
     */
    public static Result process(Map<BlockPos, GasMixture> cells,
                                 Map<BlockPos, Set<BlockPos>> loadedOpenAdjacency,
                                 Set<BlockPos> active, Set<Pair> alreadyGroupedPairs,
                                 int maxProcessedCells) {
        return process(cells, cells, loadedOpenAdjacency, active, alreadyGroupedPairs, maxProcessedCells);
    }

    /** Processes live finite cells against cycle-start archives (which may also contain immutable neighbors). */
    public static Result process(Map<BlockPos, GasMixture> cells, Map<BlockPos, GasMixture> archivedCells,
                                 Map<BlockPos, Set<BlockPos>> loadedOpenAdjacency,
                                 Set<BlockPos> active, Set<Pair> alreadyGroupedPairs,
                                 int maxProcessedCells) {
        return process(cells, archivedCells, loadedOpenAdjacency, active, alreadyGroupedPairs, Set.of(), maxProcessedCells);
    }

    /** Resumes a cycle while preventing a cell processed by an earlier batch from sharing again. */
    public static Result process(Map<BlockPos, GasMixture> cells, Map<BlockPos, GasMixture> archivedCells,
                                 Map<BlockPos, Set<BlockPos>> loadedOpenAdjacency,
                                 Set<BlockPos> active, Set<Pair> alreadyGroupedPairs,
                                 Set<BlockPos> alreadyProcessedThisCycle, int maxProcessedCells) {
        return process(cells, archivedCells, loadedOpenAdjacency, active, alreadyGroupedPairs,
                alreadyProcessedThisCycle, maxProcessedCells, false);
    }

    /** Pressure-compatible compositional diffusion entry point; the source-compatible kernel remains {@link #process}. */
    public static Result sharePressureAware(Map<BlockPos, GasMixture> cells, Map<BlockPos, GasMixture> archivedCells,
                                            Map<BlockPos, Set<BlockPos>> loadedOpenAdjacency,
                                            Set<BlockPos> active, Set<Pair> alreadyGroupedPairs,
                                            Set<BlockPos> alreadyProcessedThisCycle, int maxProcessedCells) {
        return process(cells, archivedCells, loadedOpenAdjacency, active, alreadyGroupedPairs,
                alreadyProcessedThisCycle, maxProcessedCells, true);
    }

    /** Runtime entry point parallel to the established seven-argument {@link #process} API. */
    public static Result processPressureAware(Map<BlockPos, GasMixture> cells, Map<BlockPos, GasMixture> archivedCells,
                                              Map<BlockPos, Set<BlockPos>> loadedOpenAdjacency,
                                              Set<BlockPos> active, Set<Pair> alreadyGroupedPairs,
                                              Set<BlockPos> alreadyProcessedThisCycle, int maxProcessedCells) {
        return sharePressureAware(cells, archivedCells, loadedOpenAdjacency, active, alreadyGroupedPairs,
                alreadyProcessedThisCycle, maxProcessedCells);
    }

    private static Result process(Map<BlockPos, GasMixture> cells, Map<BlockPos, GasMixture> archivedCells,
                                  Map<BlockPos, Set<BlockPos>> loadedOpenAdjacency, Set<BlockPos> active,
                                  Set<Pair> alreadyGroupedPairs, Set<BlockPos> alreadyProcessedThisCycle,
                                  int maxProcessedCells, boolean pressureAware) {
        Objects.requireNonNull(cells, "cells");
        Objects.requireNonNull(archivedCells, "archivedCells");
        Objects.requireNonNull(loadedOpenAdjacency, "loadedOpenAdjacency");
        Objects.requireNonNull(active, "active");
        Objects.requireNonNull(alreadyGroupedPairs, "alreadyGroupedPairs");
        Objects.requireNonNull(alreadyProcessedThisCycle, "alreadyProcessedThisCycle");
        if (maxProcessedCells < 0 || maxProcessedCells > 800)
            throw new IllegalArgumentException("maxProcessedCells must be between 0 and 800");

        Map<BlockPos, State> live = new HashMap<>();
        cells.forEach((pos, mixture) -> {
            BlockPos key = Objects.requireNonNull(pos, "cell position").immutable();
            GasMixture value = Objects.requireNonNull(mixture, "cell mixture");
            live.put(key, new State(value));
        });
        Map<BlockPos, State> archived = new HashMap<>();
        // Archive only this batch's live cells and adjacent endpoints. The cycle archive can
        // represent an entire station and must not be copied once per bounded source batch.

        Map<BlockPos, Set<BlockPos>> adjacency = new HashMap<>();
        for (Map.Entry<BlockPos, Set<BlockPos>> entry : loadedOpenAdjacency.entrySet()) {
            BlockPos pos = Objects.requireNonNull(entry.getKey(), "adjacency position").immutable();
            Set<BlockPos> neighbors = new HashSet<>();
            for (BlockPos neighbor : Objects.requireNonNull(entry.getValue(), "adjacency neighbors")) {
                BlockPos target = Objects.requireNonNull(neighbor, "adjacency neighbor").immutable();
                if ((cells.containsKey(target) || archivedCells.containsKey(target)) && isFaceNeighbor(pos, target)) {
                    neighbors.add(target);
                }
            }
            adjacency.put(pos, neighbors);
        }

        List<BlockPos> orderedActive = active.stream().map(pos -> Objects.requireNonNull(pos, "active position").immutable())
                .filter(live::containsKey).distinct().sorted(LindaGasSharing::compare).toList();
        for (BlockPos current : orderedActive) {
            archiveState(current, live, archivedCells, archived);
            Set<BlockPos> connected = adjacency.getOrDefault(current, Set.of());
            for (BlockPos direction : DIRECTIONS) {
                BlockPos neighbor = current.offset(direction).immutable();
                if (connected.contains(neighbor)) archiveState(neighbor, live, archivedCells, archived);
            }
        }
        Set<BlockPos> activeCells = Set.copyOf(orderedActive);
        Set<BlockPos> processedNow = new LinkedHashSet<>();
        Set<BlockPos> activated = new LinkedHashSet<>();
        Set<Pair> shared = new LinkedHashSet<>();
        Map<Pair, Double> movedByPair = new LinkedHashMap<>();
        Map<Pair, Double> absoluteMovedByPair = new LinkedHashMap<>();
        Map<BlockPos, Double> lastShare = new LinkedHashMap<>();
        int work = 0;

        for (BlockPos current : orderedActive) {
            if (work >= maxProcessedCells) break;
            if (alreadyProcessedThisCycle.contains(current) || !processedNow.add(current)) continue;
            work++;
            if (!archived.containsKey(current)) continue;
            for (BlockPos direction : DIRECTIONS) {
                BlockPos neighbor = current.offset(direction).immutable();
                Set<BlockPos> connected = adjacency.getOrDefault(current, Set.of());
                if (!connected.contains(neighbor) || !archived.containsKey(neighbor)
                        || alreadyProcessedThisCycle.contains(neighbor) || processedNow.contains(neighbor)) continue;
                Pair pair = Pair.of(current, neighbor);
                boolean qualifies = alreadyGroupedPairs.contains(pair)
                        || (pressureAware ? comparePressureAware(archived.get(current).mixture(), archived.get(neighbor).mixture())
                        : compareExchange(archived.get(current).mixture(), archived.get(neighbor).mixture()));
                if (!qualifies) continue;
                State neighborState = live.getOrDefault(neighbor, archived.get(neighbor));
                if (!activeCells.contains(neighbor) && live.containsKey(neighbor)) activated.add(neighbor);
                double moved = pressureAware
                        ? sharePressureAware(live.get(current), neighborState, archived.get(current), archived.get(neighbor), connected.size() + 1)
                        : share(live.get(current), neighborState, archived.get(current), archived.get(neighbor), connected.size() + 1);
                lastShare.put(current, live.get(current).lastMoved);
                shared.add(pair);
                movedByPair.put(pair, moved);
                absoluteMovedByPair.put(pair, live.get(current).lastMoved);
            }
        }

        Map<BlockPos, GasMixture> output = new LinkedHashMap<>();
        List<BlockPos> cellOrder = new ArrayList<>(live.keySet());
        cellOrder.sort(LindaGasSharing::compare);
        for (BlockPos pos : cellOrder) output.put(pos, live.get(pos).mixture());
        return new Result(Collections.unmodifiableMap(output),
                Collections.unmodifiableSet(activated), Collections.unmodifiableMap(lastShare),
                Collections.unmodifiableSet(shared), Collections.unmodifiableMap(movedByPair),
                Collections.unmodifiableMap(absoluteMovedByPair), Collections.unmodifiableSet(processedNow), work);
    }

    private static boolean compareExchange(GasMixture first, GasMixture second) {
        for (GasType gas : GasType.values()) {
            double sample = first.moles(gas);
            double difference = Math.abs(sample - second.moles(gas));
            if (difference > COMPARE_MOLES && difference > sample * 0.001) return true;
        }
        return first.totalMoles() > MINIMUM_MOLES_FOR_TEMPERATURE_COMPARE
                && Math.abs(first.temperatureKelvin() - second.temperatureKelvin()) > MINIMUM_TEMPERATURE_TO_COMPARE;
    }

    private static double share(State receiver, State sharer, State receiverArchive, State sharerArchive, int divider) {
        receiver.lastMoved = 0.0;
        double temperatureDelta = receiverArchive.temperature() - sharerArchive.temperature();
        double oldReceiverCp = receiver.normalizedHeatCapacity();
        double oldSharerCp = sharer.normalizedHeatCapacity();
        double heatCapacityToSharer = 0.0;
        double heatCapacityToReceiver = 0.0;
        double moved = 0.0;
        for (GasType gas : GasType.values()) {
            double delta = (receiver.moles.getOrDefault(gas, 0.0) - sharer.moles.getOrDefault(gas, 0.0)) / divider;
            if (Math.abs(delta) < GAS_MIN_MOLES) continue;
            if (Math.abs(temperatureDelta) > MINIMUM_TEMPERATURE_DELTA) {
                if (delta > 0) heatCapacityToSharer += delta * gas.molarHeatCapacity()
                        / AtmosphereUnits.UPSTREAM_HEAT_SCALE_DEFAULT;
                else heatCapacityToReceiver -= delta * gas.molarHeatCapacity()
                        / AtmosphereUnits.UPSTREAM_HEAT_SCALE_DEFAULT;
            }
            if (!receiver.immutable) receiver.moles.merge(gas, -delta, Double::sum);
            if (!sharer.immutable) sharer.moles.merge(gas, delta, Double::sum);
            moved += delta;
            receiver.lastMoved += Math.abs(delta);
        }
        double newReceiverCp = oldReceiverCp + heatCapacityToReceiver - heatCapacityToSharer;
        double newSharerCp = oldSharerCp + heatCapacityToSharer - heatCapacityToReceiver;
        if (receiver.mutable && newReceiverCp > MINIMUM_HEAT_CAPACITY)
            receiver.temperature = clampTemperature((oldReceiverCp * receiver.temperature
                    - heatCapacityToSharer * receiverArchive.temperature()
                    + heatCapacityToReceiver * sharerArchive.temperature()) / newReceiverCp);
        if (sharer.mutable && newSharerCp > MINIMUM_HEAT_CAPACITY)
            sharer.temperature = clampTemperature((oldSharerCp * sharer.temperature
                    - heatCapacityToReceiver * sharerArchive.temperature()
                    + heatCapacityToSharer * receiverArchive.temperature()) / newSharerCp);
        if (Math.abs(temperatureDelta) > MINIMUM_TEMPERATURE_DELTA
                && Math.abs(oldSharerCp) > MINIMUM_HEAT_CAPACITY
                && Math.abs(newSharerCp / oldSharerCp - 1.0) < 0.1) {
            temperatureShare(receiver, sharer, receiverArchive, sharerArchive, OPEN_HEAT_TRANSFER_COEFFICIENT);
        }
        return moved;
    }

    private static boolean comparePressureAware(GasMixture a, GasMixture b) {
        double ta = total(a.gasMoles()), tb = total(b.gasMoles());
        double compositionDelta = 0;
        if (ta > 0 && tb > 0) for (GasType gas : GasType.values())
            compositionDelta += Math.abs(a.moles(gas) / ta - b.moles(gas) / tb);
        return compositionDelta > 1e-9 || Math.abs(a.temperatureKelvin() - b.temperatureKelvin()) > MINIMUM_TEMPERATURE_TO_COMPARE;
    }

    private static double total(Map<GasType, Double> amounts) {
        double sum = 0;
        for (GasType gas : GasType.values()) sum += amounts.getOrDefault(gas, 0.0);
        return sum;
    }

    private static double rawCp(State state) {
        double cp = 0;
        for (GasType gas : GasType.values()) cp += state.moles.getOrDefault(gas, 0.0) * gas.molarHeatCapacity();
        if (state.immutable && cp == 0) return GasMixture.spaceNormalizedHeatCapacity() * AtmosphereUnits.UPSTREAM_HEAT_SCALE_DEFAULT;
        return cp;
    }

    private static double sharePressureAware(State a, State b, State archiveA, State archiveB, int divider) {
        a.lastMoved = 0;
        double netMoved = 0;
        double na = total(a.moles), nb = total(b.moles);
        if (na > 0 && nb > 0 && (a.mutable || b.mutable)) {
            double q = Math.min(na, nb) / divider;
            double archivedA = total(archiveA.moles), archivedB = total(archiveB.moles);
            if (archivedA == 0) archivedA = na;
            if (archivedB == 0) archivedB = nb;
            EnumMap<GasType, Double> delta = new EnumMap<>(GasType.class);
            double factor = 1;
            for (GasType gas : GasType.values()) {
                double fractionA = archiveA.moles.getOrDefault(gas, 0.0) / archivedA;
                double fractionB = archiveB.moles.getOrDefault(gas, 0.0) / archivedB;
                if (total(archiveA.moles) == 0) fractionA = a.moles.getOrDefault(gas, 0.0) / na;
                if (total(archiveB.moles) == 0) fractionB = b.moles.getOrDefault(gas, 0.0) / nb;
                double d = q * (fractionB - fractionA);
                delta.put(gas, d);
                if (d < 0 && a.mutable && -d > a.moles.getOrDefault(gas, 0.0)) factor = Math.min(factor, a.moles.getOrDefault(gas, 0.0) / -d);
                if (d > 0 && b.mutable && d > b.moles.getOrDefault(gas, 0.0)) factor = Math.min(factor, b.moles.getOrDefault(gas, 0.0) / d);
            }
            double ea = a.energy, eb = b.energy;
            for (GasType gas : GasType.values()) {
                double d = delta.get(gas) * factor;
                if (d == 0) continue;
                if (a.mutable) a.moles.merge(gas, d, Double::sum);
                if (b.mutable) b.moles.merge(gas, -d, Double::sum);
                double movedEnergy = d * gas.molarHeatCapacity() * (d > 0 ? b.temperature : a.temperature);
                if (a.mutable) a.energy += movedEnergy;
                if (b.mutable) b.energy -= movedEnergy;
                a.lastMoved += Math.abs(d);
                netMoved += d;
            }
            if (a.mutable) a.temperature = rawCp(a) > 0 ? a.energy / rawCp(a) : a.temperature;
            if (b.mutable) b.temperature = rawCp(b) > 0 ? b.energy / rawCp(b) : b.temperature;
        }
        double ca = rawCp(a), cb = rawCp(b), dt = a.temperature - b.temperature;
        if (ca > 0 && cb > 0 && dt != 0) {
            double heat = OPEN_HEAT_TRANSFER_COEFFICIENT * dt * ca * cb / (ca + cb);
            if (a.mutable) { a.energy -= heat; a.temperature = a.energy / ca; }
            if (b.mutable) { b.energy += heat; b.temperature = b.energy / cb; }
        }
        return netMoved;
    }

    private static void archiveState(BlockPos key, Map<BlockPos, State> live,
                                     Map<BlockPos, GasMixture> archivedCells, Map<BlockPos, State> archived) {
        if (archived.containsKey(key) || !archivedCells.containsKey(key)) return;
        GasMixture mixture = Objects.requireNonNull(archivedCells.get(key), "archived mixture");
        archived.put(key, new State(mixture, !live.containsKey(key), false));
    }

    private static void temperatureShare(State receiver, State sharer, State receiverArchive,
                                         State sharerArchive, double coefficient) {
        double delta = receiverArchive.temperature() - sharerArchive.temperature();
        if (Math.abs(delta) <= MINIMUM_TEMPERATURE_DELTA) return;
        double cpReceiver = receiverArchive.normalizedHeatCapacity();
        double cpSharer = sharerArchive.normalizedHeatCapacity();
        if (cpReceiver <= MINIMUM_HEAT_CAPACITY || cpSharer <= MINIMUM_HEAT_CAPACITY) return;
        double heat = coefficient * delta * cpReceiver * cpSharer / (cpReceiver + cpSharer);
        if (receiver.mutable) receiver.temperature = clampTemperature(receiver.temperature - heat / cpReceiver);
        if (sharer.mutable) sharer.temperature = clampTemperature(sharer.temperature + heat / cpSharer);
    }

    private static double clampTemperature(double temperature) {
        return Math.max(MINIMUM_TEMPERATURE, Math.min(MAXIMUM_TEMPERATURE, (float) temperature));
    }

    private static boolean isFaceNeighbor(BlockPos first, BlockPos second) {
        long distance = Math.abs((long) first.getX() - second.getX())
                + Math.abs((long) first.getY() - second.getY()) + Math.abs((long) first.getZ() - second.getZ());
        return distance == 1;
    }

    private static int compare(BlockPos first, BlockPos second) {
        int order = Integer.compare(first.getX(), second.getX());
        if (order == 0) order = Integer.compare(first.getY(), second.getY());
        if (order == 0) order = Integer.compare(first.getZ(), second.getZ());
        return order;
    }

    private static final class State {
        private final EnumMap<GasType, Double> moles = new EnumMap<>(GasType.class);
        private final boolean immutable;
        private final boolean mutable;
        private double temperature;
        private double energy;
        private double lastMoved;
        private final double volume;
        private final GasMixture sourceMixture;

        State(GasMixture mixture) { this(mixture, false, true); }

        State(GasMixture mixture, boolean immutable, boolean mutable) {
            sourceMixture = mixture;
            moles.putAll(mixture.gasMoles());
            this.immutable = immutable;
            this.mutable = mutable;
            temperature = mixture.temperatureKelvin();
            energy = mixture.thermalEnergy();
            volume = mixture.volumeCubicMeters();
        }

        double normalizedHeatCapacity() {
            if (immutable) {
                float totalMoles = 0;
                for (double amount : moles.values()) totalMoles += (float) amount;
                if (Math.abs(totalMoles) <= 1e-7f) return GasMixture.spaceNormalizedHeatCapacity();
                return sourceMixture.normalizedHeatCapacity();
            }
            return new GasMixture(moles, temperature, volume).normalizedHeatCapacity();
        }

        double temperature() { return temperature; }

        GasMixture mixture() {
            if (immutable) return sourceMixture;
            moles.values().removeIf(value -> value <= 0.0);
            return new GasMixture(moles, temperature, volume);
        }
    }
}
