package com.juicyslew.moonstation14.ms14.atmos.world;

import com.juicyslew.moonstation14.ms14.atmos.reaction.HotspotKernel;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

/** Transient per-level, round-robin chunk work. Overflow remains in its owning chunk. */
final class ReactionWorkQueue {
    /** Publication failed after the raw gas transaction committed; the due-step stamp must survive. */
    static final class CommittedFailure extends RuntimeException {
        CommittedFailure(RuntimeException cause) { super(cause); }
    }
    static final int ACTIVE_CAPACITY = 256;
    static final int MAX_REACTED_CELLS_PER_TICK = 8192;
    private final Map<ChunkPos, Bucket> buckets = new HashMap<>();
    private final ArrayDeque<ChunkPos> ready = new ArrayDeque<>();
    private final ArrayDeque<ChunkPos> overflow = new ArrayDeque<>();
    private final ArrayDeque<ChunkPos> loading = new ArrayDeque<>();
    private int active;
    private long reactedAt = Long.MIN_VALUE;
    private final Set<BlockPos> reacted = new java.util.HashSet<>();
    private final Set<BlockPos> deferredReceivers = new java.util.HashSet<>();
    private final Map<BlockPos, HotspotKernel.HotspotState> hotspots = new HashMap<>();
    private final Map<ChunkPos, java.util.NavigableMap<AtmosphereChunkData.CellPosition, Integer>> burning = new HashMap<>();

    private static final java.util.Comparator<AtmosphereChunkData.CellPosition> CELL_ORDER = java.util.Comparator
            .comparingInt(AtmosphereChunkData.CellPosition::x).thenComparingInt(AtmosphereChunkData.CellPosition::z)
            .thenComparingInt(AtmosphereChunkData.CellPosition::y);
    int fireIntensity(BlockPos pos) {
        var map = burning.get(new ChunkPos(pos));
        return map == null ? 0 : map.getOrDefault(new AtmosphereChunkData.CellPosition(pos.getX() & 15, pos.getY(), pos.getZ() & 15), 0);
    }
    java.util.Optional<AtmosphereChunkData.CellPosition> nextBurningAfter(ChunkPos chunk, AtmosphereChunkData.CellPosition cursor) {
        var map = burning.get(chunk);
        if (map == null || map.isEmpty()) return java.util.Optional.empty();
        return java.util.Optional.ofNullable(cursor == null ? map.firstKey() : map.higherKey(cursor));
    }
    void setBurning(BlockPos pos, int intensity) {
        ChunkPos chunk = new ChunkPos(pos);
        var cell = new AtmosphereChunkData.CellPosition(pos.getX() & 15, pos.getY(), pos.getZ() & 15);
        if (intensity <= 0) {
            var map = burning.get(chunk);
            if (map != null) { map.remove(cell); if (map.isEmpty()) burning.remove(chunk); }
        } else burning.computeIfAbsent(chunk, ignored -> new java.util.TreeMap<>(CELL_ORDER))
                .put(cell, Math.min(255, intensity));
    }

    HotspotKernel.HotspotState hotspot(BlockPos pos) { return hotspots.get(pos); }

    void setHotspot(BlockPos pos, Optional<HotspotKernel.HotspotState> state) {
        if (state.isPresent()) hotspots.put(pos.immutable(), state.get());
        else hotspots.remove(pos);
    }

    /** Only a verified, currently loaded finite cell may quench transient state. */
    boolean quenchIfPresent(BlockPos pos) {
        boolean changed = hotspots.remove(pos) != null || fireIntensity(pos) != 0;
        setBurning(pos, 0);
        return changed;
    }

    private static final class Bucket {
        final Set<BlockPos> active = new LinkedHashSet<>();
        final Set<BlockPos> backlog = new LinkedHashSet<>();
        AtmosphereChunkData.CellPosition cursor;
        boolean loading;
        boolean ready;
        boolean overflow;
    }

    void offer(BlockPos pos) {
        ChunkPos chunk = new ChunkPos(pos);
        Bucket bucket = buckets.computeIfAbsent(chunk, ignored -> new Bucket());
        BlockPos key = pos.immutable();
        if (bucket.active.contains(key) || bucket.backlog.contains(key)) return;
        if (active < ACTIVE_CAPACITY) {
            bucket.active.add(key);
            active++;
            ready(bucket, chunk);
        } else {
            bucket.backlog.add(key);
            if (!bucket.overflow) { bucket.overflow = true; overflow.addLast(chunk); }
        }
    }

    BlockPos poll() {
        promote();
        ChunkPos chunk = ready.pollFirst();
        if (chunk == null) return null;
        Bucket bucket = buckets.get(chunk);
        bucket.ready = false;
        BlockPos pos = bucket.active.iterator().next();
        bucket.active.remove(pos);
        active--;
        if (!bucket.active.isEmpty()) ready(bucket, chunk);
        promote();
        return pos;
    }

    private void ready(Bucket bucket, ChunkPos chunk) {
        if (!bucket.ready) { bucket.ready = true; ready.addLast(chunk); }
    }

    // Rotate overflow chunks even if a hot chunk continually refills its own active set.
    private void promote() {
        if (active >= ACTIVE_CAPACITY) return;
        while (active < ACTIVE_CAPACITY && !overflow.isEmpty()) {
            ChunkPos chunk = overflow.removeFirst();
            Bucket bucket = buckets.get(chunk);
            bucket.overflow = false;
            BlockPos pos = bucket.backlog.iterator().next();
            bucket.backlog.remove(pos);
            bucket.active.add(pos);
            active++;
            ready(bucket, chunk);
            if (!bucket.backlog.isEmpty()) { bucket.overflow = true; overflow.addLast(chunk); }
        }
    }

    void startLoad(ChunkPos chunk) {
        Bucket bucket = buckets.computeIfAbsent(chunk, ignored -> new Bucket());
        bucket.cursor = null;
        if (!bucket.loading) { bucket.loading = true; loading.addLast(chunk); }
    }

    ChunkPos pollLoad() {
        ChunkPos chunk = loading.pollFirst();
        if (chunk != null) buckets.get(chunk).loading = false;
        return chunk;
    }

    AtmosphereChunkData.CellPosition cursor(ChunkPos chunk) { return buckets.get(chunk).cursor; }

    /** Every cursor attempt consumes budget, including missing and exhausted chunks. */
    int drainLoads(int maxAttempts, int maxCells, Predicate<ChunkPos> visit) {
        int attempts = 0;
        int cells = 0;
        while (attempts < maxAttempts && cells < maxCells) {
            ChunkPos chunk = pollLoad();
            if (chunk == null) break;
            attempts++;
            if (visit.test(chunk)) cells++;
        }
        return attempts;
    }

    boolean reacted(long dueStep, BlockPos pos) {
        advanceReactionTime(dueStep);
        return reacted.contains(pos) || deferredReceivers.contains(pos);
    }

    /** A heated receiver may first burn on the following due step, not later in this pass. */
    void deferReceiver(long dueStep, BlockPos pos) {
        advanceReactionTime(dueStep);
        deferredReceivers.add(pos.immutable());
        offer(pos);
    }

    boolean reactionCapacityReached(long dueStep) {
        advanceReactionTime(dueStep);
        return reacted.size() >= MAX_REACTED_CELLS_PER_TICK;
    }

    /** Only a committed change is stamped; capacity rejects new writes rather than evicting a live stamp. */
    <T> Optional<T> commitOnce(long dueStep, BlockPos pos, Supplier<Optional<T>> commit) {
        advanceReactionTime(dueStep);
        if (reacted.contains(pos) || deferredReceivers.contains(pos)
                || reacted.size() >= MAX_REACTED_CELLS_PER_TICK) return Optional.empty();
        Optional<T> result;
        try {
            result = commit.get();
        } catch (CommittedFailure failure) {
            reacted.add(pos.immutable());
            throw (RuntimeException) failure.getCause();
        }
        if (result.isPresent()) reacted.add(pos.immutable());
        return result;
    }

    private void advanceReactionTime(long dueStep) {
        if (reactedAt == dueStep) return;
        reactedAt = dueStep;
        reacted.clear();
        deferredReceivers.clear();
    }
    void continueLoad(ChunkPos chunk, AtmosphereChunkData.CellPosition cursor) {
        Bucket bucket = buckets.get(chunk);
        bucket.cursor = cursor;
        if (!bucket.loading) { bucket.loading = true; loading.addLast(chunk); }
    }

    void removeChunk(ChunkPos chunk) {
        // Successful stamps outlive unload/reload within this due step; the next step clears them.
        hotspots.keySet().removeIf(pos -> (pos.getX() >> 4) == chunk.x && (pos.getZ() >> 4) == chunk.z);
        burning.remove(chunk);
        Bucket bucket = buckets.remove(chunk);
        if (bucket == null) return;
        active -= bucket.active.size();
        ready.remove(chunk);
        overflow.remove(chunk);
        loading.remove(chunk);
    }

    int activeSize() { return active; }
    int backlogSize() { return buckets.values().stream().mapToInt(b -> b.backlog.size()).sum(); }
}
