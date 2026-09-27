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
                         int workCount) { }

    /**
     * Applies a sequential live-mole exchange cycle. Compare thresholds use immutable cycle-start
     * snapshots, while each qualifying pair shares the current inventories immediately.
     */
    public static Result process(Map<BlockPos, GasMixture> cells,
                                 Map<BlockPos, Set<BlockPos>> loadedOpenAdjacency,
                                 Set<BlockPos> active, Set<Pair> alreadyGroupedPairs,
                                 int maxProcessedCells) {
        Objects.requireNonNull(cells, "cells");
        Objects.requireNonNull(loadedOpenAdjacency, "loadedOpenAdjacency");
        Objects.requireNonNull(active, "active");
        Objects.requireNonNull(alreadyGroupedPairs, "alreadyGroupedPairs");
        if (maxProcessedCells < 0 || maxProcessedCells > 800)
            throw new IllegalArgumentException("maxProcessedCells must be between 0 and 800");

        Map<BlockPos, GasMixture> archived = new HashMap<>();
        Map<BlockPos, State> live = new HashMap<>();
        cells.forEach((pos, mixture) -> {
            BlockPos key = Objects.requireNonNull(pos, "cell position").immutable();
            GasMixture value = Objects.requireNonNull(mixture, "cell mixture");
            archived.put(key, value);
            live.put(key, new State(value));
        });

        Map<BlockPos, Set<BlockPos>> adjacency = new HashMap<>();
        for (Map.Entry<BlockPos, Set<BlockPos>> entry : loadedOpenAdjacency.entrySet()) {
            BlockPos pos = Objects.requireNonNull(entry.getKey(), "adjacency position").immutable();
            Set<BlockPos> neighbors = new HashSet<>();
            for (BlockPos neighbor : Objects.requireNonNull(entry.getValue(), "adjacency neighbors")) {
                BlockPos target = Objects.requireNonNull(neighbor, "adjacency neighbor").immutable();
                if (cells.containsKey(target) && isFaceNeighbor(pos, target)) neighbors.add(target);
            }
            adjacency.put(pos, neighbors);
        }

        List<BlockPos> orderedActive = active.stream().map(pos -> Objects.requireNonNull(pos, "active position").immutable())
                .filter(live::containsKey).distinct().sorted(LindaGasSharing::compare).toList();
        Set<BlockPos> activeCells = Set.copyOf(orderedActive);
        Set<BlockPos> processed = new HashSet<>();
        Set<BlockPos> activated = new LinkedHashSet<>();
        Set<Pair> shared = new LinkedHashSet<>();
        Map<Pair, Double> movedByPair = new LinkedHashMap<>();
        Map<BlockPos, Double> lastShare = new LinkedHashMap<>();
        int work = 0;

        for (BlockPos current : orderedActive) {
            if (work >= maxProcessedCells) break;
            if (!processed.add(current)) continue;
            work++;
            for (BlockPos direction : DIRECTIONS) {
                BlockPos neighbor = current.offset(direction).immutable();
                Set<BlockPos> connected = adjacency.getOrDefault(current, Set.of());
                if (!connected.contains(neighbor) || !live.containsKey(neighbor) || processed.contains(neighbor)) continue;
                Pair pair = Pair.of(current, neighbor);
                boolean qualifies = alreadyGroupedPairs.contains(pair)
                        || compareExchange(archived.get(current), archived.get(neighbor));
                if (!qualifies) continue;
                if (!activeCells.contains(neighbor)) activated.add(neighbor);
                double moved = share(live.get(current), live.get(neighbor), connected.size() + 1);
                lastShare.put(current, moved);
                shared.add(pair);
                movedByPair.put(pair, moved);
            }
        }

        Map<BlockPos, GasMixture> output = new LinkedHashMap<>();
        List<BlockPos> cellOrder = new ArrayList<>(live.keySet());
        cellOrder.sort(LindaGasSharing::compare);
        for (BlockPos pos : cellOrder) output.put(pos, live.get(pos).mixture());
        return new Result(Collections.unmodifiableMap(output),
                Collections.unmodifiableSet(activated), Collections.unmodifiableMap(lastShare),
                Collections.unmodifiableSet(shared), Collections.unmodifiableMap(movedByPair), work);
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

    private static double share(State first, State second, int divider) {
        double firstStartTemperature = first.temperature();
        double secondStartTemperature = second.temperature();
        double moved = 0.0;
        for (GasType gas : GasType.values()) {
            double delta = (first.moles.getOrDefault(gas, 0.0) - second.moles.getOrDefault(gas, 0.0)) / divider;
            if (Math.abs(delta) < GAS_MIN_MOLES) continue;
            State donor = delta > 0 ? first : second;
            State receiver = delta > 0 ? second : first;
            double amount = Math.min(Math.abs(delta), donor.moles.getOrDefault(gas, 0.0));
            donor.moles.put(gas, Math.max(0.0, donor.moles.getOrDefault(gas, 0.0) - amount));
            receiver.moles.merge(gas, amount, Double::sum);
            double enthalpy = amount * gas.molarHeatCapacity() * (delta > 0 ? firstStartTemperature : secondStartTemperature);
            donor.energy -= enthalpy;
            receiver.energy += enthalpy;
            moved += amount;
        }
        first.normalizeEnergy();
        second.normalizeEnergy();

        double temperatureDelta = first.temperature() - second.temperature();
        if (Math.abs(temperatureDelta) > MINIMUM_TEMPERATURE_DELTA) {
            double capFirst = first.heatCapacity();
            double capSecond = second.heatCapacity();
            if (capFirst > 0.0 && capSecond > 0.0) {
                double heat = OPEN_HEAT_TRANSFER_COEFFICIENT * temperatureDelta * capFirst * capSecond / (capFirst + capSecond);
                first.energy -= heat;
                second.energy += heat;
                first.normalizeEnergy();
                second.normalizeEnergy();
            }
        }
        return moved;
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
        private double energy;
        private double emptyTemperature;

        State(GasMixture mixture) {
            moles.putAll(mixture.gasMoles());
            energy = mixture.thermalEnergy();
            emptyTemperature = mixture.temperatureKelvin();
        }

        double heatCapacity() {
            return moles.entrySet().stream().mapToDouble(entry -> entry.getValue() * entry.getKey().molarHeatCapacity()).sum();
        }

        double temperature() { return heatCapacity() == 0.0 ? emptyTemperature : energy / heatCapacity(); }

        void normalizeEnergy() {
            moles.values().removeIf(value -> value == 0.0);
            double capacity = heatCapacity();
            if (capacity == 0.0) {
                energy = 0.0;
                return;
            }
            if (!Double.isFinite(energy) || energy < 0.0) throw new IllegalStateException("Exchange produced invalid energy");
            emptyTemperature = energy / capacity;
        }

        GasMixture mixture() { return new GasMixture(moles, temperature()); }
    }
}
