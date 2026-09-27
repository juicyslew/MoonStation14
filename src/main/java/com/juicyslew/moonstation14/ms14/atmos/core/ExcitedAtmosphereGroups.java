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

/**
 * Transient, server-thread-owned excited-group membership and cycle processing. Unlike SS14's
 * unconditional fifth-pass averaging, Minecraft group breakdown waits until LINDA has made every
 * species and temperature gradient small enough that final averaging is visually imperceptible.
 */
public final class ExcitedAtmosphereGroups {
    private static final double STANDARD_MOLES = 101_325.0 / (8.31446261815324 * 293.15);
    public static final double MINIMUM_AIR_TO_SUSPEND = STANDARD_MOLES * 0.1;
    public static final double MINIMUM_MOLES_DELTA_TO_MOVE = STANDARD_MOLES * 0.001;
    private static final double MAX_BREAKDOWN_GAS_DEVIATION = STANDARD_MOLES * 0.05;
    private static final double MAX_BREAKDOWN_TEMPERATURE_DEVIATION = 4.0;
    private static final int BREAKDOWN_CYCLES = 4;
    private static final int DISMANTLE_CYCLES = 16;
    private static final int MAX_TRACKED_CELLS = 800;

    private final Map<Long, Group> groups = new LinkedHashMap<>();
    private final Map<BlockPos, Long> membership = new HashMap<>();
    private long nextId;
    private PendingBreakdown pending;

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
            if (membership.size() + 2 > MAX_TRACKED_CELLS) return;
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
            if (membership.size() >= MAX_TRACKED_CELLS) return;
            BlockPos added = firstId == null ? first : second;
            group.cells.add(added);
            membership.put(added, group.id);
        } else {
            Group left = groups.get(firstId);
            Group right = groups.get(secondId);
            if ((long) membership.size() > MAX_TRACKED_CELLS) return;
            group = preferred(left, right);
            Group loser = group == left ? right : left;
            if (group.cells.size() + loser.cells.size() > MAX_TRACKED_CELLS) return;
            group.cells.addAll(loser.cells);
            for (BlockPos cell : loser.cells) membership.put(cell, group.id);
            groups.remove(loser.id);
            group.breakdownCooldown = 0;
            group.dismantleCooldown = 0;
            if (pending != null && pending.groupId == loser.id) pending = null;
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

    /** Advances each group exactly once for an atmosphere cycle; partial breakdowns are atomic. */
    public CycleResult advanceFullCycle(Map<BlockPos, GasMixture> currentFiniteCells, int workBudget) {
        Objects.requireNonNull(currentFiniteCells, "currentFiniteCells");
        if (workBudget < 0 || workBudget > MAX_TRACKED_CELLS)
            throw new IllegalArgumentException("workBudget must be between 0 and 800");
        Map<BlockPos, GasMixture> current = immutableMixtures(currentFiniteCells);
        Map<BlockPos, GasMixture> replacements = new LinkedHashMap<>();
        Set<BlockPos> deactivated = new LinkedHashSet<>();
        int work = 0;
        Long resumedGroupId = pending == null ? null : pending.groupId;

        if (pending != null) {
            Group group = groups.get(pending.groupId);
            if (group == null || !allLoaded(group, current)) {
                if (group != null) group.breakdownCooldown = 0;
                pending = null;
            } else if (pending.snapshots.size() != group.cells.size()
                    || !pending.snapshots.keySet().containsAll(group.cells)
                    || !snapshotsMatch(pending.snapshots, current)) {
                // Gas changed while this bounded job was suspended; discard its stale average.
                group.breakdownCooldown = 0;
                pending = null;
            } else {
                while (pending.progress < pending.cells.size() && work < workBudget) {
                    pending.progress++;
                    work++;
                }
                if (pending.progress == pending.cells.size()) {
                    for (BlockPos cell : pending.cells) replacements.put(cell, pending.average);
                    group.breakdownCooldown = 0;
                    pending = null;
                }
            }
        }

        List<Group> ordered = new ArrayList<>(groups.values());
        ordered.sort(Comparator.comparing(group -> group.cells.stream().min(ExcitedAtmosphereGroups::compare).orElseThrow(),
                ExcitedAtmosphereGroups::compare));
        for (Group group : ordered) {
            if (resumedGroupId != null && group.id == resumedGroupId) continue;
            if (group.id == (pending == null ? -1 : pending.groupId)) continue;
            if (!allLoaded(group, current)) continue;
            group.breakdownCooldown++;
            group.dismantleCooldown++;
            if (group.dismantleCooldown > DISMANTLE_CYCLES) {
                for (BlockPos cell : group.cells) {
                    membership.remove(cell);
                    deactivated.add(cell);
                }
                groups.remove(group.id);
                continue;
            }
            if (group.breakdownCooldown > BREAKDOWN_CYCLES) {
                GasMixture average = average(group.cells, current);
                if (!isNearUniform(group.cells, current, average)) {
                    // LINDA continues doing the visible diffusion; never turn a large gradient into
                    // an instantaneous full-room equalization merely because a cycle elapsed.
                    group.breakdownCooldown = 0;
                    continue;
                }
                pending = new PendingBreakdown(group.id, sorted(group.cells), snapshot(group.cells, current),
                        average);
                // Do not apply even a partial average. Future cycle calls resume this atomic job.
                while (pending.progress < pending.cells.size() && work < workBudget) {
                    pending.progress++;
                    work++;
                }
                if (pending.progress == pending.cells.size()) {
                    for (BlockPos cell : pending.cells) replacements.put(cell, pending.average);
                    group.breakdownCooldown = 0;
                    pending = null;
                }
            }
        }
        return new CycleResult(Collections.unmodifiableMap(replacements),
                Collections.unmodifiableSet(deactivated), work);
    }

    private static Group preferred(Group a, Group b) {
        if (a.cells.size() != b.cells.size()) return a.cells.size() > b.cells.size() ? a : b;
        BlockPos aMin = a.cells.stream().min(ExcitedAtmosphereGroups::compare).orElseThrow();
        BlockPos bMin = b.cells.stream().min(ExcitedAtmosphereGroups::compare).orElseThrow();
        return compare(aMin, bMin) <= 0 ? a : b;
    }

    private static boolean allLoaded(Group group, Map<BlockPos, GasMixture> current) {
        return group.cells.stream().allMatch(current::containsKey);
    }

    private static Map<BlockPos, GasMixture> snapshot(Set<BlockPos> cells, Map<BlockPos, GasMixture> current) {
        Map<BlockPos, GasMixture> result = new HashMap<>();
        for (BlockPos cell : cells) result.put(cell, current.get(cell));
        return Map.copyOf(result);
    }

    private static boolean snapshotsMatch(Map<BlockPos, GasMixture> snapshots, Map<BlockPos, GasMixture> current) {
        for (Map.Entry<BlockPos, GasMixture> entry : snapshots.entrySet()) {
            GasMixture candidate = current.get(entry.getKey());
            if (candidate == null || !sameMixture(entry.getValue(), candidate)) return false;
        }
        return true;
    }

    private static boolean sameMixture(GasMixture first, GasMixture second) {
        return first.temperatureKelvin() == second.temperatureKelvin()
                && first.gasMoles().equals(second.gasMoles());
    }

    private static GasMixture average(Set<BlockPos> cells, Map<BlockPos, GasMixture> current) {
        EnumMap<GasType, Double> totals = new EnumMap<>(GasType.class);
        double energy = 0.0;
        for (BlockPos cell : cells) {
            GasMixture mixture = current.get(cell);
            for (GasType gas : GasType.values()) totals.merge(gas, mixture.moles(gas), Double::sum);
            energy += mixture.thermalEnergy();
        }
        double count = cells.size();
        totals.replaceAll((gas, amount) -> amount / count);
        double capacity = totals.entrySet().stream()
                .mapToDouble(entry -> entry.getValue() * entry.getKey().molarHeatCapacity()).sum();
        double temperature = capacity == 0.0 ? current.get(cells.iterator().next()).temperatureKelvin()
                : energy / count / capacity;
        return new GasMixture(totals, temperature);
    }

    private static boolean isNearUniform(Set<BlockPos> cells, Map<BlockPos, GasMixture> current,
                                         GasMixture average) {
        for (BlockPos cell : cells) {
            GasMixture mixture = current.get(cell);
            for (GasType gas : GasType.values()) {
                if (Math.abs(mixture.moles(gas) - average.moles(gas)) > MAX_BREAKDOWN_GAS_DEVIATION) return false;
            }
            if (Math.abs(mixture.temperatureKelvin() - average.temperatureKelvin())
                    > MAX_BREAKDOWN_TEMPERATURE_DEVIATION) return false;
        }
        return true;
    }

    private static Map<BlockPos, GasMixture> immutableMixtures(Map<BlockPos, GasMixture> input) {
        Map<BlockPos, GasMixture> result = new HashMap<>();
        input.forEach((pos, mixture) -> result.put(Objects.requireNonNull(pos, "cell position").immutable(),
                Objects.requireNonNull(mixture, "cell mixture")));
        return result;
    }

    private static List<BlockPos> sorted(Set<BlockPos> cells) {
        return cells.stream().sorted(ExcitedAtmosphereGroups::compare).toList();
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

    public record CycleResult(Map<BlockPos, GasMixture> replacements, Set<BlockPos> deactivatedCells, int workCount) { }

    private static final class Group {
        private final long id;
        private final Set<BlockPos> cells = new LinkedHashSet<>();
        private int breakdownCooldown;
        private int dismantleCooldown;

        private Group(long id) { this.id = id; }
    }

    private static final class PendingBreakdown {
        private final long groupId;
        private final List<BlockPos> cells;
        private final Map<BlockPos, GasMixture> snapshots;
        private final GasMixture average;
        private int progress;

        private PendingBreakdown(long groupId, List<BlockPos> cells, Map<BlockPos, GasMixture> snapshots,
                                 GasMixture average) {
            this.groupId = groupId;
            this.cells = cells;
            this.snapshots = snapshots;
            this.average = average;
        }
    }
}
