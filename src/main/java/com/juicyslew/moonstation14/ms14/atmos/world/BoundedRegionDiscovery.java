package com.juicyslew.moonstation14.ms14.atmos.world;

import com.juicyslew.moonstation14.ms14.atmos.core.BoundedGasEqualizer;
import net.minecraft.core.BlockPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Resumable, deterministic, bounded BFS over mutable finite cells. It performs no world reads. */
public final class BoundedRegionDiscovery {
    public static final int MAX_PATCH_CELLS = BoundedGasEqualizer.MAX_EQUALIZE_CELLS;
    public static final int MAX_CANDIDATES = BoundedGasEqualizer.MAX_DISCOVERY_CELLS;

    private static final int[][] DIRECTIONS = {
            {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}
    };

    public enum ProbeResult { FINITE_LOADED, EXTERIOR_IMMUTABLE, BLOCKED, UNKNOWN_UNLOADED }
    public enum Status { IN_PROGRESS, PATCH_READY, COMPLETE, UNKNOWN_CHUNK, HARD_LIMIT, CANCELLED }

    @FunctionalInterface
    public interface NeighborProbe {
        ProbeResult inspect(BlockPos position);
    }

    /** Each snapshot's cells form the next connected BFS-ordered equalization patch. */
    public record Snapshot(Status status, List<BlockPos> cells, boolean boundaryContact,
                           List<BlockPos> exteriorSeeds, int distinctCandidates,
                           List<BlockPos> continuationSeeds) { }

    private final ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
    private final List<BlockPos> discovered = new ArrayList<>();
    private final Set<BlockPos> candidates = new HashSet<>();
    private final Set<BlockPos> exteriorSeeds = new HashSet<>();
    private BlockPos current;
    private int directionIndex;
    private int patchOffset;
    private BlockPos retryPosition;
    private boolean boundaryContact;
    private Status terminal;

    public BoundedRegionDiscovery(BlockPos seed) {
        if (seed == null) throw new IllegalArgumentException("Seed cannot be null");
        retryPosition = seed.immutable();
    }

    /**
     * Performs at most {@code maxInspections} probe calls. UNKNOWN pauses at that exact candidate;
     * call again when it may be loaded. PATCH_READY retains all remaining frontier state; consume
     * its patch before stepping again. HARD_LIMIT means the job stopped without classifying space.
     */
    public Snapshot step(NeighborProbe probe, int maxInspections) {
        if (probe == null) throw new IllegalArgumentException("Probe cannot be null");
        if (maxInspections < 0) throw new IllegalArgumentException("Inspection budget cannot be negative");
        if (terminal != null) return snapshot(terminal);
        if (unconsumed() >= MAX_PATCH_CELLS) return snapshot(Status.PATCH_READY);

        int inspections = 0;
        while (inspections < maxInspections) {
            if (retryPosition == null) {
                if (current == null) {
                    current = frontier.pollFirst();
                    directionIndex = 0;
                    if (current == null) return snapshot(Status.COMPLETE);
                }
                if (directionIndex >= DIRECTIONS.length) {
                    current = null;
                    continue;
                }
                int[] direction = DIRECTIONS[directionIndex++];
                BlockPos neighbor = current.offset(direction[0], direction[1], direction[2]).immutable();
                if (candidates.contains(neighbor)) continue;
                retryPosition = neighbor;
            }

            if (!candidates.contains(retryPosition) && candidates.size() >= MAX_CANDIDATES) {
                terminal = Status.HARD_LIMIT;
                return snapshot(terminal);
            }
            BlockPos inspected = retryPosition;
            candidates.add(inspected);
            inspections++;
            ProbeResult result = probe.inspect(inspected);
            retryPosition = null;
            if (result == null) throw new IllegalStateException("Probe result cannot be null");
            switch (result) {
                case FINITE_LOADED -> {
                    discovered.add(inspected);
                    frontier.addLast(inspected);
                    if (unconsumed() >= MAX_PATCH_CELLS) return snapshot(Status.PATCH_READY);
                }
                case EXTERIOR_IMMUTABLE -> {
                    boundaryContact = true;
                    exteriorSeeds.add(inspected);
                }
                case BLOCKED -> { }
                case UNKNOWN_UNLOADED -> {
                    retryPosition = inspected;
                    candidates.remove(inspected);
                    return snapshot(Status.UNKNOWN_CHUNK);
                }
            }
        }
        return snapshot(Status.IN_PROGRESS);
    }

    /**
     * Advances the emitted patch cursor; the BFS queue/frontier is not discarded. A partial patch
     * may only be consumed once discovery is COMPLETE; otherwise the full 800-cell patch is
     * required so a budget-limited progress snapshot cannot accidentally become an equalization.
     */
    public Snapshot consumePatch() {
        boolean complete = frontier.isEmpty() && current == null && retryPosition == null;
        if (unconsumed() < MAX_PATCH_CELLS && !complete && terminal != Status.HARD_LIMIT) {
            throw new IllegalStateException("A partial patch is consumable only after discovery completes");
        }
        int count = Math.min(MAX_PATCH_CELLS, unconsumed());
        patchOffset += count;
        return snapshot(terminal == Status.HARD_LIMIT ? Status.HARD_LIMIT
                : complete ? Status.COMPLETE : Status.PATCH_READY);
    }

    /**
     * Deterministic unclassified neighbors of the retained BFS frontier. Returned positions are
     * seeds, not claims about their topology; callers must probe them normally. The finite list is
     * bounded by six neighbors per tracked candidate and lets a capped job continue without
     * restarting from its original seed.
     */
    public List<BlockPos> continuationSeeds() {
        if (terminal != Status.HARD_LIMIT) return List.of();
        Set<BlockPos> seeds = new java.util.LinkedHashSet<>();
        addIfUnclassified(seeds, retryPosition);
        if (current != null) addRemainingNeighbors(seeds, current, directionIndex);
        for (BlockPos position : frontier) addRemainingNeighbors(seeds, position, 0);
        return List.copyOf(seeds);
    }

    /** Candidate paused at the unloaded boundary, if any. */
    public BlockPos retryCandidate() { return retryPosition; }

    public Snapshot cancel() {
        terminal = Status.CANCELLED;
        frontier.clear();
        current = null;
        retryPosition = null;
        return snapshot(terminal);
    }

    private int unconsumed() { return discovered.size() - patchOffset; }

    private Snapshot snapshot(Status status) {
        int end = Math.min(discovered.size(), patchOffset + MAX_PATCH_CELLS);
        return new Snapshot(status, List.copyOf(discovered.subList(patchOffset, end)), boundaryContact,
                exteriorSeeds.stream().sorted(BoundedRegionDiscovery::comparePositions).toList(), candidates.size(),
                status == Status.HARD_LIMIT ? continuationSeeds() : List.of());
    }

    private void addRemainingNeighbors(Set<BlockPos> seeds, BlockPos position, int startDirection) {
        for (int index = startDirection; index < DIRECTIONS.length; index++) {
            int[] direction = DIRECTIONS[index];
            addIfUnclassified(seeds, position.offset(direction[0], direction[1], direction[2]).immutable());
        }
    }

    private void addIfUnclassified(Set<BlockPos> seeds, BlockPos position) {
        if (position != null && !candidates.contains(position)) seeds.add(position.immutable());
    }

    private static int comparePositions(BlockPos left, BlockPos right) {
        int comparison = Integer.compare(left.getX(), right.getX());
        if (comparison == 0) comparison = Integer.compare(left.getY(), right.getY());
        if (comparison == 0) comparison = Integer.compare(left.getZ(), right.getZ());
        return comparison;
    }
}
