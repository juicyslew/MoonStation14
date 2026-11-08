package com.juicyslew.moonstation14.ms14.atmos.core;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * Transient, server-thread-owned excited-group membership and cycle processing.
 */
public final class ExcitedAtmosphereGroups {
    private static final double STANDARD_MOLES = 101_325.0 / (8.31446261815324 * 293.15);
    public static final double MINIMUM_AIR_TO_SUSPEND = STANDARD_MOLES * 0.1;
    public static final double MINIMUM_MOLES_DELTA_TO_MOVE = STANDARD_MOLES * 0.001;
    private static final int BREAKDOWN_CYCLES = 4;
    private static final int DISMANTLE_CYCLES = 16;

    private final Map<Long, Group> groups = new LinkedHashMap<>();
    private final Map<BlockPos, Long> membership = new HashMap<>();
    private long nextId;
    private long lastCycleId = Long.MIN_VALUE;
    private List<Long> groupQueue = List.of();
    private int groupQueueCursor;
    private long legacyCycleId;

    /**
     * Register a LINDA-qualified shared pair, including temperature-only comparisons that move no
     * gas. The caller must only pass pairs accepted by LINDA's comparison or same-group gate.
     * Snapshots must contain both loaded finite endpoints.
     */
    public void onShare(LindaGasSharing.Pair pair, double movedMoles, Map<BlockPos, GasMixture> snapshots) {
        Objects.requireNonNull(pair, "pair");
        Objects.requireNonNull(snapshots, "snapshots");
        if (!Double.isFinite(movedMoles) || movedMoles < 0.0)
            throw new IllegalArgumentException("movedMoles must be finite and nonnegative");
        BlockPos first = pair.first().immutable();
        BlockPos second = pair.second().immutable();
        GasMixture firstMixture = snapshots.get(first);
        GasMixture secondMixture = snapshots.get(second);
        if (firstMixture == null || secondMixture == null || !faceNeighbor(first, second)) return;
        Long firstId = membership.get(first);
        Long secondId = membership.get(second);
        Group group;
        if (firstId == null && secondId == null) {
            group = new Group(nextId++);
            group.cells.add(first);
            group.cells.add(second);
            groups.put(group.id, group);
            membership.put(first, group.id);
            membership.put(second, group.id);
        } else if (firstId != null && secondId != null && firstId.equals(secondId)) {
            group = groups.get(firstId);
        } else if (firstId == null || secondId == null) {
            group = groups.get(firstId != null ? firstId : secondId);
            BlockPos added = firstId == null ? first : second;
            group.cells.add(added);
            membership.put(added, group.id);
        } else {
            Group left = groups.get(firstId);
            Group right = groups.get(secondId);
            group = preferred(left, right);
            Group loser = group == left ? right : left;
            group.cells.addAll(loser.cells);
            for (BlockPos cell : loser.cells) membership.put(cell, group.id);
            groups.remove(loser.id);
            group.breakdownCooldown = 0;
            group.dismantleCooldown = 0;
        }
        if (group == null) return;
        if (movedMoles > MINIMUM_AIR_TO_SUSPEND) {
            group.breakdownCooldown = 0;
            group.dismantleCooldown = 0;
        } else if (movedMoles > MINIMUM_MOLES_DELTA_TO_MOVE) {
            group.dismantleCooldown = 0;
        }
    }

    /** True when LINDA may bypass its normal exchange-comparison gate for this pair. */
    public boolean sameGroupPair(LindaGasSharing.Pair pair) {
        Objects.requireNonNull(pair, "pair");
        Long first = membership.get(pair.first());
        return first != null && first.equals(membership.get(pair.second()));
    }

    /** Returns one detached immutable snapshot of the bounded authoritative membership. */
    public Set<BlockPos> trackedCells() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(membership.keySet()));
    }

    /** Detached position-to-group snapshot for LINDA's component-aware share gate. */
    public Map<BlockPos, Long> groupMembership() { return Map.copyOf(membership); }

    /** True when both endpoints belong to some excited group, including different groups. */
    public boolean bothGrouped(LindaGasSharing.Pair pair) {
        return membership.containsKey(pair.first()) && membership.containsKey(pair.second());
    }

    /** Reset both pacing timers for the existing group containing this cell; never creates membership. */
    public boolean resetCooldownsForCell(BlockPos pos) {
        Objects.requireNonNull(pos, "pos");
        Long id = membership.get(pos);
        Group group = id == null ? null : groups.get(id);
        if (group == null) return false;
        group.breakdownCooldown = 0;
        group.dismantleCooldown = 0;
        return true;
    }

    /** Advances each group exactly once for an atmosphere cycle; partial breakdowns are atomic. */
    public CycleResult advanceFullCycle(Map<BlockPos, GasMixture> currentFiniteCells, int workBudget) {
        return advanceCycle(++legacyCycleId, currentFiniteCells, workBudget);
    }

    /** Advances/resumes work for a stable caller-supplied cycle id without ticking groups twice. */
    public CycleResult advanceCycle(long cycleId, Map<BlockPos, GasMixture> currentFiniteCells, int workBudget) {
        return advanceCycle(cycleId, currentFiniteCells, Set.of(), Set.of(), false, workBudget);
    }

    /** Advances all loaded members; immutable members participate in averaging but are never replaced. */
    public CycleResult advanceCycle(long cycleId, Map<BlockPos, GasMixture> allLoadedMixtures,
                                     Set<BlockPos> immutablePositions, Set<BlockPos> spacePositions,
                                     boolean spaceAllConsuming, int workBudget) {
        return advanceCycle(cycleId, group -> snapshotGroup(group, allLoadedMixtures, immutablePositions, spacePositions),
                spaceAllConsuming, workBudget, () -> false);
    }

    /** Captures only the group currently being processed. Null means a member is unavailable. */
    public CycleResult advanceCycle(long cycleId, Function<Set<BlockPos>, LoadedGroupSnapshot> provider,
                                     boolean spaceAllConsuming, int groupOperationBudget,
                                     BooleanSupplier deadlineReached) {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(deadlineReached, "deadlineReached");
        if (groupOperationBudget < 0) throw new IllegalArgumentException("groupOperationBudget must be nonnegative");
        if (groupOperationBudget == 0) return new CycleResult(Map.of(), Set.of(), 0);
        if (cycleId != lastCycleId) {
            lastCycleId = cycleId;
            List<GroupOrder> ordered = new ArrayList<>(groups.size());
            for (Group group : groups.values()) ordered.add(new GroupOrder(group,
                    group.cells.stream().min(ExcitedAtmosphereGroups::compare).orElseThrow()));
            ordered.sort(Comparator.comparing(GroupOrder::firstCell, ExcitedAtmosphereGroups::compare));
            groupQueue = ordered.stream().map(entry -> entry.group().id).toList();
            groupQueueCursor = 0;
        }
        Map<BlockPos, GasMixture> replacements = new LinkedHashMap<>();
        Set<BlockPos> deactivated = new LinkedHashSet<>();
        EnumMap<GasType, Double> boundarySpeciesMoles = new EnumMap<>(GasType.class);
        double boundaryEnergyJoules = 0.0;
        int work = 0;
        int averages = 0;
        long captureNanos = 0, averageNanos = 0;
        int skippedQueueEntries = 0;
        while (groupQueueCursor < groupQueue.size()) {
            if (work >= groupOperationBudget) break;
            if ((work > 0 || skippedQueueEntries > 0) && deadlineReached.getAsBoolean()) break;
            long id = groupQueue.get(groupQueueCursor++);
            Group group = groups.get(id);
            if (group == null) { skippedQueueEntries++; continue; }
            work++;
            group.dismantleCooldown++;
            group.breakdownCooldown++;
            if (group.breakdownCooldown > BREAKDOWN_CYCLES) {
                LoadedGroupSnapshot captured = provider.apply(Set.copyOf(group.cells));
                if (captured == null || !captured.mixtures().keySet().containsAll(group.cells)) {
                    removeGroup(group, deactivated);
                    continue;
                }
                Map<BlockPos, GasMixture> current = captured.mixtures();
                captureNanos += captured.captureNanos();
                averages++;
                long averageStarted = System.nanoTime();
                GasMixture average = average(group.cells, current, captured.immutablePositions(), captured.spacePositions(), spaceAllConsuming);
                BoundaryExchange exchange = boundaryExchange(group.cells, current, captured.immutablePositions(), average);
                for (BlockPos cell : group.cells) if (!captured.immutablePositions().contains(cell)) replacements.put(cell, average);
                exchange.speciesMoles().forEach((gas, amount) -> boundarySpeciesMoles.merge(gas, amount, Double::sum));
                boundaryEnergyJoules += exchange.energyJoules();
                group.breakdownCooldown = 0;
                averageNanos += System.nanoTime() - averageStarted;
            } else if (group.dismantleCooldown > DISMANTLE_CYCLES) removeGroup(group, deactivated);
        }
        return new CycleResult(Collections.unmodifiableMap(replacements), Collections.unmodifiableSet(deactivated), work,
                Collections.unmodifiableMap(boundarySpeciesMoles), boundaryEnergyJoules, averages, captureNanos, averageNanos);
    }

    private static LoadedGroupSnapshot snapshotGroup(Set<BlockPos> group, Map<BlockPos, GasMixture> all,
                                                       Set<BlockPos> immutable, Set<BlockPos> space) {
        Map<BlockPos, GasMixture> mixtures = new LinkedHashMap<>();
        for (BlockPos pos : group) { GasMixture gas = all.get(pos); if (gas == null) return null; mixtures.put(pos, gas); }
        Set<BlockPos> imm = new LinkedHashSet<>(immutable); imm.retainAll(group);
        Set<BlockPos> sp = new LinkedHashSet<>(space); sp.retainAll(group);
        return new LoadedGroupSnapshot(mixtures, imm, sp);
    }

    private void removeGroup(Group group, Set<BlockPos> deactivated) {
        for (BlockPos cell : group.cells) { membership.remove(cell); deactivated.add(cell); }
        groups.remove(group.id);
    }

    public record LoadedGroupSnapshot(Map<BlockPos, GasMixture> mixtures, Set<BlockPos> immutablePositions,
                                      Set<BlockPos> spacePositions, long captureNanos) {
        public LoadedGroupSnapshot(Map<BlockPos, GasMixture> mixtures, Set<BlockPos> immutablePositions,
                                   Set<BlockPos> spacePositions) { this(mixtures, immutablePositions, spacePositions, 0); }
        public LoadedGroupSnapshot { mixtures = Map.copyOf(mixtures); immutablePositions = Set.copyOf(immutablePositions); spacePositions = Set.copyOf(spacePositions); }
    }

    private static BoundaryExchange boundaryExchange(Set<BlockPos> cells, Map<BlockPos, GasMixture> current,
                                                       Set<BlockPos> immutablePositions, GasMixture average) {
        if (cells.stream().noneMatch(immutablePositions::contains)) return new BoundaryExchange(Map.of(), 0.0);
        int finiteCount = 0;
        EnumMap<GasType, Double> species = new EnumMap<>(GasType.class);
        double finiteBeforeEnergy = 0.0;
        for (BlockPos cell : cells) {
            if (immutablePositions.contains(cell)) continue;
            GasMixture mixture = current.get(cell);
            if (mixture == null) continue;
            finiteCount++;
            finiteBeforeEnergy += mixture.thermalEnergy();
            for (GasType gas : GasType.values()) species.merge(gas, mixture.moles(gas), Double::sum);
        }
        if (finiteCount == 0) return new BoundaryExchange(Map.of(), 0.0);
        for (GasType gas : GasType.values()) {
            double moved = species.getOrDefault(gas, 0.0) - average.moles(gas) * finiteCount;
            if (moved == 0.0) species.remove(gas);
            else species.put(gas, moved);
        }
        return new BoundaryExchange(Collections.unmodifiableMap(species),
                finiteBeforeEnergy - average.thermalEnergy() * finiteCount);
    }

    /** Whether a caller still owes this cycle a group counter/tick or pending breakdown work. */
    public boolean hasUnprocessedCycleWork() {
        return groupQueueCursor < groupQueue.size();
    }

    private static Group preferred(Group a, Group b) {
        if (a.cells.size() != b.cells.size()) return a.cells.size() > b.cells.size() ? a : b;
        return b;
    }

    private static GasMixture average(Set<BlockPos> cells, Map<BlockPos, GasMixture> current,
                                      Set<BlockPos> immutablePositions, Set<BlockPos> spacePositions,
                                      boolean spaceAllConsuming) {
        GasMixture merged = GasMixture.vacuum();
        for (BlockPos cell : cells) {
            GasMixture giver = current.get(cell);
            if (spaceAllConsuming && spacePositions.contains(cell))
                merged = new GasMixture(Map.of(), merged.temperatureKelvin(), merged.volumeCubicMeters());
            float receiverCapacity = (float) merged.heatCapacity() / 8f;
            float giverCapacity = immutablePositions.contains(cell) ? 2800f
                    : (float) giver.heatCapacity() / 8f;
            float combined = receiverCapacity + giverCapacity;
            double temperature = merged.temperatureKelvin();
            if (Math.abs(temperature - giver.temperatureKelvin()) > 0.01 && combined > 0.0003f)
                temperature = (float) (((float) temperature * receiverCapacity
                        + (float) giver.temperatureKelvin() * giverCapacity) / combined);
            EnumMap<GasType, Double> totals = new EnumMap<>(GasType.class);
            for (GasType gas : GasType.values()) {
                double sum = merged.moles(gas) + giver.moles(gas);
                if (sum > 0) totals.put(gas, sum);
            }
            merged = new GasMixture(totals, temperature, merged.volumeCubicMeters());
        }
        return merged.withScaledMoles(1.0 / cells.size());
    }

    private static boolean faceNeighbor(BlockPos first, BlockPos second) {
        return Math.abs((long) first.getX() - second.getX()) + Math.abs((long) first.getY() - second.getY())
                + Math.abs((long) first.getZ() - second.getZ()) == 1;
    }

    private static int compare(BlockPos first, BlockPos second) {
        int result = Integer.compare(first.getX(), second.getX());
        if (result == 0) result = Integer.compare(first.getY(), second.getY());
        if (result == 0) result = Integer.compare(first.getZ(), second.getZ());
        return result;
    }

    public record CycleResult(Map<BlockPos, GasMixture> replacements, Set<BlockPos> deactivatedCells, int groupOperations,
                              Map<GasType, Double> boundarySpeciesMoles, double boundaryEnergyJoules, int averages,
                              long captureNanos, long averageNanos) {
        public CycleResult(Map<BlockPos, GasMixture> replacements, Set<BlockPos> deactivatedCells, int workCount) {
            this(replacements, deactivatedCells, workCount, Map.of(), 0.0, 0, 0, 0);
        }
        public CycleResult { boundarySpeciesMoles = Map.copyOf(boundarySpeciesMoles); }
    }

    private record BoundaryExchange(Map<GasType, Double> speciesMoles, double energyJoules) { }

    private record GroupOrder(Group group, BlockPos firstCell) { }

    private static final class Group {
        private final long id;
        private final Set<BlockPos> cells = new LinkedHashSet<>();
        private int breakdownCooldown;
        private int dismantleCooldown;

        private Group(long id) { this.id = id; }
    }

}

