package com.juicyslew.moonstation14.ms14.atmos.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Resumable sky-connectivity classifier. It only observes topology supplied by its caller. */
public final class AtmosphereOwnershipSearch {
    public static final int DEFAULT_MAX_CANDIDATES = 131_072;

    private static final int[][] DIRECTIONS = {
            {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}
    };

    public enum ProbeResult { BLOCKED, OPEN_SKY_SEED, OPEN_COVERED, FINITE_CLAIMED, UNKNOWN_UNLOADED }
    public enum Status { IN_PROGRESS, EXTERIOR, FINITE, UNKNOWN, SATURATED, CANCELLED }

    @FunctionalInterface
    public interface NeighborProbe {
        ProbeResult inspect(BlockPos position);
    }

    /**
     * visitedOpenCells are unclaimed passable cells and are safe candidates for ownership claims.
     * Membership is exposed only for EXTERIOR/FINITE results; intermediate and unresolved snapshots
     * deliberately carry an empty list to keep incremental steps bounded. Terminal order is BFS order.
     */
    public record Snapshot(Status status, List<BlockPos> visitedOpenCells, int trackedCandidates) { }

    private final int maxCandidates;
    private final ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
    private final Set<BlockPos> candidates = new HashSet<>();
    private final Set<BlockPos> visited = new LinkedHashSet<>();
    private final Set<BlockPos> unresolved = new LinkedHashSet<>();
    private final Set<ChunkPos> touchedChunks = new HashSet<>();
    private final Set<ChunkPos> unresolvedChunks = new HashSet<>();
    private BlockPos current;
    private int directionIndex;
    private Status terminal;
    private Snapshot terminalSnapshot;
    private boolean seedPending = true;

    public AtmosphereOwnershipSearch(BlockPos seed) {
        this(seed, DEFAULT_MAX_CANDIDATES);
    }

    /** The candidate cap is configurable for bounded-memory callers and focused tests. */
    public AtmosphereOwnershipSearch(BlockPos seed, int maxCandidates) {
        if (seed == null) throw new IllegalArgumentException("Seed cannot be null");
        if (maxCandidates < 1) throw new IllegalArgumentException("Candidate cap must be positive");
        this.maxCandidates = maxCandidates;
        candidates.add(seed.immutable());
        frontier.addLast(seed.immutable());
    }

    /**
     * Advances by at most {@code maxInspections} neighbor examinations (including already-known
     * neighbors), so both probing and queue bookkeeping have a strict per-call bound. An unresolved
     * unloaded boundary is retried on later calls; it never proves a finite component.
     */
    public Snapshot step(NeighborProbe probe, int maxInspections) {
        if (probe == null) throw new IllegalArgumentException("Probe cannot be null");
        if (maxInspections < 0) throw new IllegalArgumentException("Inspection budget cannot be negative");
        if (terminal != null) return snapshot(terminal);
        int work = 0;

        if (seedPending && maxInspections > 0) {
            seedPending = false;
            BlockPos seed = frontier.removeFirst();
            touchedChunks.add(new ChunkPos(seed));
            ProbeResult result = inspect(probe, seed);
            work++;
            if (result == ProbeResult.FINITE_CLAIMED) {
                terminal = Status.FINITE;
                return snapshot(terminal);
            }
            if (result == ProbeResult.OPEN_SKY_SEED) {
                visited.add(seed);
                terminal = Status.EXTERIOR;
                return snapshot(terminal);
            }
            if (result == ProbeResult.OPEN_COVERED) {
                visited.add(seed);
                frontier.addLast(seed);
            } else if (result == ProbeResult.UNKNOWN_UNLOADED) {
                unresolved.add(seed);
                unresolvedChunks.add(new ChunkPos(seed));
                frontier.clear();
            } else {
                frontier.clear();
            }
        }

        while (work < maxInspections) {
            if (current == null) {
                current = frontier.pollFirst();
                directionIndex = 0;
                if (current == null) break;
            }
            if (directionIndex >= DIRECTIONS.length) {
                current = null;
                continue;
            }
            int[] direction = DIRECTIONS[directionIndex++];
            work++;
            BlockPos neighbor = current.offset(direction[0], direction[1], direction[2]).immutable();
            if (candidates.contains(neighbor)) continue;
            if (candidates.size() >= maxCandidates) {
                terminal = Status.SATURATED;
                return snapshot(terminal);
            }
            candidates.add(neighbor);
            ProbeResult result = checked(probe.inspect(neighbor));
            // The candidate is counted before probing, so the distinct dependency set is bounded
            // by the same cap as the search itself.
            touchedChunks.add(new ChunkPos(neighbor));
            if (result == ProbeResult.OPEN_SKY_SEED) {
                visited.add(neighbor);
                terminal = Status.EXTERIOR;
                return snapshot(terminal);
            }
            if (result == ProbeResult.OPEN_COVERED) {
                visited.add(neighbor);
                frontier.addLast(neighbor);
            } else if (result == ProbeResult.UNKNOWN_UNLOADED) {
                unresolved.add(neighbor);
                unresolvedChunks.add(new ChunkPos(neighbor));
            }
            // BLOCKED and FINITE_CLAIMED are barriers, not members of this component.
        }

        if (current != null || !frontier.isEmpty()) return snapshot(Status.IN_PROGRESS);
        if (unresolved.isEmpty()) {
            terminal = Status.FINITE;
            return snapshot(terminal);
        }
        if (work < maxInspections) {
            BlockPos retry = unresolved.iterator().next();
            unresolved.remove(retry);
            ProbeResult result = checked(probe.inspect(retry));
            touchedChunks.add(new ChunkPos(retry));
            work++;
            if (result != ProbeResult.UNKNOWN_UNLOADED) unresolvedChunks.remove(new ChunkPos(retry));
            if (result == ProbeResult.OPEN_SKY_SEED) {
                visited.add(retry);
                terminal = Status.EXTERIOR;
                return snapshot(terminal);
            }
            if (result == ProbeResult.OPEN_COVERED) {
                visited.add(retry);
                frontier.addLast(retry);
                return snapshot(Status.IN_PROGRESS);
            }
            if (result == ProbeResult.UNKNOWN_UNLOADED) {
                unresolved.add(retry);
                unresolvedChunks.add(new ChunkPos(retry));
            } else if (result == ProbeResult.FINITE_CLAIMED) {
                // The cell became a claim while unloaded; it remains a barrier.
            }
            return snapshot(unresolved.isEmpty() ? (terminal = Status.FINITE) : Status.UNKNOWN);
        }
        return snapshot(unresolved.isEmpty() ? (terminal = Status.FINITE) : Status.UNKNOWN);
    }

    /** Cancels retained topology work, for example after a block/claim topology change. */
    public Snapshot cancel() {
        terminal = Status.CANCELLED;
        frontier.clear();
        unresolved.clear();
        current = null;
        return snapshot(terminal);
    }

    /** Alias for explicit invalidation when the observed topology changes. */
    public Snapshot topologyChanged() { return cancel(); }

    /** Chunks already observed by this search; future frontier chunks are deliberately absent. */
    boolean hasTouchedChunk(ChunkPos chunk) { return touchedChunks.contains(chunk); }

    /** Unloaded chunks whose cells were actually probed, for targeted parked-search wakeups. */
    Set<ChunkPos> unresolvedChunks() { return Set.copyOf(unresolvedChunks); }


    private ProbeResult inspect(NeighborProbe probe, BlockPos position) {
        return checked(probe.inspect(position));
    }

    private static ProbeResult checked(ProbeResult result) {
        if (result == null) throw new IllegalStateException("Probe result cannot be null");
        return result;
    }

    private Snapshot snapshot(Status status) {
        if (status == Status.EXTERIOR || status == Status.FINITE) {
            if (terminalSnapshot == null)
                terminalSnapshot = new Snapshot(status, List.copyOf(visited), candidates.size());
            return terminalSnapshot;
        }
        return new Snapshot(status, List.of(), candidates.size());
    }
}
