package com.juicyslew.moonstation14.ms14.atmos.visual.network;

import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Pure payload cache; deliberately has no client-side Minecraft dependencies. */
public final class AtmosphereVisualClientCache {
    public static final int MAX_CACHED_CHUNKS = 1024;
    public static final int MAX_VISIBLE_CELLS = 65_536;
    public static final int MAX_QUEUED_DELTA_CELLS_PER_CHUNK = 4096;
    private static final int MAX_RESYNC_CANDIDATES = 4096;
    private final LinkedHashMap<ChunkKey, ChunkState> chunks = new LinkedHashMap<>(16, .75f, true);
    private final LinkedHashSet<ChunkKey> evicted = new LinkedHashSet<>();
    private int cellCount;
    private boolean lostEvictionKeys;

    /** Returns true only when a published cache view changed. */
    public boolean apply(AtmosphereVisualPayload payload) {
        Objects.requireNonNull(payload, "payload");
        ChunkKey key = new ChunkKey(payload.dimension(), payload.chunkX(), payload.chunkZ());
        ChunkState state = chunks.get(key);
        if (payload.resetSnapshot()) {
            if (state != null && payload.revision() < Math.max(state.revision, state.stagingRevision)) return false;
            if (state == null) { state = new ChunkState(); chunks.put(key, state); }
            if (state.staging != null) cellCount -= state.staging.size();
            state.staging = new LinkedHashMap<>(); state.stagingRevision = payload.revision();
            state.queuedDeltas.entrySet().removeIf(entry -> entry.getValue().revision <= payload.revision());
            addToStage(state, payload);
            if (payload.finalPacket()) publish(state, key);
            enforceBounds();
            return payload.finalPacket();
        }
        if (state != null && state.staging != null && payload.revision() == state.stagingRevision) {
            addToStage(state, payload);
            if (payload.finalPacket()) publish(state, key);
            enforceBounds();
            return payload.finalPacket();
        }
        if (state == null || payload.revision() < state.revision) return false;
        if (state.staging == null && payload.revision() == state.lastSnapshotRevision) return false;
        if (state.staging != null) {
            if (payload.revision() <= state.stagingRevision) return false;
            for (AtmosphereVisualPayload.VisualCell cell : payload.cells()) {
                CellKey cellKey = new CellKey(cell.localX(), cell.y(), cell.localZ());
                QueuedDelta old = state.queuedDeltas.get(cellKey);
                if (old != null && old.revision > payload.revision()) continue;
                if (old == null && state.queuedDeltas.size() >= MAX_QUEUED_DELTA_CELLS_PER_CHUNK) {
                    evictForResync(key);
                    return false;
                }
                state.queuedDeltas.put(cellKey, new QueuedDelta(payload.revision(), cell));
            }
            return false;
        }
        if (state.published == null) return false;
        applyDelta(state, payload.revision(), payload.cells());
        enforceBounds();
        return true;
    }

    private void applyDelta(ChunkState state, long revision, List<AtmosphereVisualPayload.VisualCell> cells) {
        int previousSize = state.published.size();
        for (AtmosphereVisualPayload.VisualCell cell : cells) {
            CellKey cellKey = new CellKey(cell.localX(), cell.y(), cell.localZ());
            if (isClear(cell)) state.published.remove(cellKey);
            else state.published.put(cellKey, cell);
        }
        cellCount += state.published.size() - previousSize;
        state.revision = Math.max(state.revision, revision);
        refreshView(state);
    }

    private void addToStage(ChunkState state, AtmosphereVisualPayload payload) {
        if (state.staging == null || payload.revision() != state.stagingRevision) return;
        for (AtmosphereVisualPayload.VisualCell cell : payload.cells()) {
            if (state.staging.put(new CellKey(cell.localX(), cell.y(), cell.localZ()), cell) == null) cellCount++;
        }
    }

    private void publish(ChunkState state, ChunkKey key) {
        if (state.staging == null) return;
        cellCount -= state.published == null ? 0 : state.published.size();
        // Staging cells are already included in the running count; publication only replaces old cells.
        state.published = state.staging;
        state.revision = Math.max(state.revision, state.stagingRevision);
        state.lastSnapshotRevision = state.stagingRevision;
        state.staging = null;
        evicted.remove(key);
        refreshView(state);
        List<QueuedDelta> replay = state.queuedDeltas.values().stream()
                .filter(delta -> delta.revision > state.revision)
                .sorted(java.util.Comparator.comparingLong(QueuedDelta::revision)).toList();
        state.queuedDeltas.clear();
        for (QueuedDelta delta : replay) applyDelta(state, delta.revision, List.of(delta.cell));
    }

    private static void refreshView(ChunkState state) {
        state.view = state.published == null || state.published.isEmpty() ? Map.of() : Map.copyOf(state.published);
    }

    public Map<CellKey, AtmosphereVisualPayload.VisualCell> cells(ResourceLocation dimension, int chunkX, int chunkZ) {
        ChunkState state = chunks.get(new ChunkKey(dimension, chunkX, chunkZ));
        return state == null || state.view == null ? Map.of() : state.view;
    }

    /** Immutable per-chunk views; unchanged snapshots are reused across render frames. */
    public List<ChunkView> visibleChunks(ResourceLocation dimension) {
        List<ChunkView> result = new ArrayList<>();
        for (var entry : chunks.entrySet()) {
            ChunkKey key = entry.getKey();
            ChunkState state = entry.getValue();
            if (key.dimension().equals(dimension) && state.view != null && !state.view.isEmpty())
                result.add(new ChunkView(key.x(), key.z(), state.view));
        }
        return List.copyOf(result);
    }

    /** Picks nearest cells first, with tritium favored only at equal distance, and a hard frame bound. */
    public List<VisibleCell> visibleCells(ResourceLocation dimension, double cameraX, double cameraY,
                                          double cameraZ, double maxDistance, int limit) {
        if (limit <= 0 || maxDistance < 0) return List.of();
        double maxDistanceSquared = maxDistance * maxDistance;
        List<VisibleCell> candidates = new ArrayList<>();
        int minChunkX = ((int) Math.floor(cameraX - maxDistance)) >> 4;
        int maxChunkX = ((int) Math.floor(cameraX + maxDistance)) >> 4;
        int minChunkZ = ((int) Math.floor(cameraZ - maxDistance)) >> 4;
        int maxChunkZ = ((int) Math.floor(cameraZ + maxDistance)) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                ChunkState state = chunks.get(new ChunkKey(dimension, chunkX, chunkZ));
                if (state == null || state.view == null) continue;
                int baseX = chunkX << 4;
                int baseZ = chunkZ << 4;
                for (var entry : state.view.entrySet()) {
                    var cell = entry.getValue();
                    double x = baseX + cell.localX() + .5;
                    double y = cell.y() + .5;
                    double z = baseZ + cell.localZ() + .5;
                    double distanceSquared = (x - cameraX) * (x - cameraX) + (y - cameraY) * (y - cameraY)
                            + (z - cameraZ) * (z - cameraZ);
                    if (distanceSquared <= maxDistanceSquared)
                        candidates.add(new VisibleCell(x, y, z, distanceSquared, cell));
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(VisibleCell::distanceSquared)
                .thenComparing(Comparator.comparingInt(
                        (VisibleCell candidate) -> candidate.cell().tritiumAlpha()).reversed())
                .thenComparingDouble(VisibleCell::x)
                .thenComparingDouble(VisibleCell::y)
                .thenComparingDouble(VisibleCell::z));
        if (candidates.size() > limit) candidates.subList(limit, candidates.size()).clear();
        return List.copyOf(candidates);
    }

    public long revision(ResourceLocation dimension, int chunkX, int chunkZ) {
        ChunkState state = chunks.get(new ChunkKey(dimension, chunkX, chunkZ));
        return state == null ? 0 : state.revision;
    }

    public void unload(ResourceLocation dimension, int chunkX, int chunkZ) {
        ChunkKey key = new ChunkKey(dimension, chunkX, chunkZ);
        ChunkState removed = chunks.remove(key);
        if (removed != null) cellCount -= chunkCellCount(removed);
        evicted.remove(key);
    }

    public void clear() { chunks.clear(); evicted.clear(); cellCount = 0; lostEvictionKeys = false; }
    /** True if a hard cache bound forced omission; callers should use this as an incomplete-view indicator. */
    public boolean isIncomplete() { return lostEvictionKeys || !evicted.isEmpty(); }

    /** Bounded retry candidates; retained until a replacement snapshot publishes or the chunk unloads. */
    public List<ChunkKey> resyncCandidates(int max) {
        if (max <= 0 || evicted.isEmpty()) return List.of();
        List<ChunkKey> result = new ArrayList<>(Math.min(max, evicted.size()));
        for (ChunkKey key : evicted) {
            result.add(key);
            if (result.size() == max) break;
        }
        return List.copyOf(result);
    }

    /** Selects candidates to queue without clearing them; only a published snapshot acknowledges recovery. */
    public List<ChunkKey> drainResyncCandidates(int max) {
        return resyncCandidates(max);
    }

    private void enforceBounds() {
        while (chunks.size() > MAX_CACHED_CHUNKS || cellCount > MAX_VISIBLE_CELLS) {
            var iterator = chunks.entrySet().iterator();
            if (!iterator.hasNext()) break;
            var removedEntry = iterator.next();
            iterator.remove();
            cellCount -= chunkCellCount(removedEntry.getValue());
            if (evicted.size() == MAX_RESYNC_CANDIDATES) {
                evicted.remove(evicted.iterator().next());
                lostEvictionKeys = true;
            }
            evicted.add(removedEntry.getKey());
        }
    }
    private void evictForResync(ChunkKey key) {
        ChunkState removed = chunks.remove(key);
        if (removed != null) cellCount -= chunkCellCount(removed);
        if (evicted.size() == MAX_RESYNC_CANDIDATES) {
            evicted.remove(evicted.iterator().next());
            lostEvictionKeys = true;
        }
        evicted.add(key);
    }
    private static int chunkCellCount(ChunkState state) {
        return (state.published == null ? 0 : state.published.size()) + (state.staging == null ? 0 : state.staging.size());
    }
    private static boolean isClear(AtmosphereVisualPayload.VisualCell cell) {
        return cell.plasmaAlpha() == 0 && cell.tritiumAlpha() == 0 && cell.waterVaporAlpha() == 0
                && cell.ammoniaAlpha() == 0 && cell.frezonAlpha() == 0;
    }

    public record ChunkKey(ResourceLocation dimension, int x, int z) { }
    public record CellKey(int x, int y, int z) { }
    public record ChunkView(int x, int z, Map<CellKey, AtmosphereVisualPayload.VisualCell> cells) { }
    public record VisibleCell(double x, double y, double z, double distanceSquared,
                              AtmosphereVisualPayload.VisualCell cell) { }
    private static final class ChunkState {
        long revision, stagingRevision, lastSnapshotRevision;
        LinkedHashMap<CellKey, AtmosphereVisualPayload.VisualCell> published;
        LinkedHashMap<CellKey, AtmosphereVisualPayload.VisualCell> staging;
        final LinkedHashMap<CellKey, QueuedDelta> queuedDeltas = new LinkedHashMap<>();
        Map<CellKey, AtmosphereVisualPayload.VisualCell> view;
    }
    private record QueuedDelta(long revision, AtmosphereVisualPayload.VisualCell cell) { }
}
