package com.juicyslew.moonstation14.ms14.atmos.visual.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Bounded FIFO for atmosphere fragments received before their world or chunk is ready. */
public final class AtmosphereVisualPendingSnapshots {
    public static final int MAX_CHUNKS = 64;
    public static final int MAX_PACKETS_PER_CHUNK = 128;

    private final LinkedHashMap<Key, ArrayDeque<AtmosphereVisualPayload>> pending = new LinkedHashMap<>();
    private final LinkedHashMap<Key, ResyncCandidate> resync = new LinkedHashMap<>();

    public void stage(AtmosphereVisualPayload payload) {
        Key key = new Key(payload.dimension(), ChunkPos.asLong(payload.chunkX(), payload.chunkZ()));
        ArrayDeque<AtmosphereVisualPayload> queue = pending.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        if (queue.size() == MAX_PACKETS_PER_CHUNK) {
            AtmosphereVisualPayload dropped = queue.removeFirst();
            if (dropped.resetSnapshot()) {
                ResyncCandidate candidate = resync.get(key);
                if (candidate != null && candidate.resetRevision == dropped.revision()) candidate.resetRevision = 0;
            }
            markForResync(key);
        }
        queue.addLast(payload);
        noteReset(payload);
        while (pending.size() > MAX_CHUNKS) {
            Key evicted = pending.keySet().iterator().next();
            pending.remove(evicted);
            markForResync(evicted);
        }
    }

    public int flush(ResourceLocation dimension, Predicate<AtmosphereVisualPayload> ready,
                     Consumer<AtmosphereVisualPayload> apply) {
        Objects.requireNonNull(dimension);
        Objects.requireNonNull(ready);
        Objects.requireNonNull(apply);
        int count = 0;
        Iterator<Map.Entry<Key, ArrayDeque<AtmosphereVisualPayload>>> iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (!entry.getKey().dimension.equals(dimension)) continue;
            ArrayDeque<AtmosphereVisualPayload> queue = entry.getValue();
            while (!queue.isEmpty() && ready.test(queue.peekFirst())) {
                apply.accept(queue.removeFirst());
                count++;
            }
            if (queue.isEmpty()) iterator.remove();
        }
        return count;
    }

    public void unload(ResourceLocation dimension, int chunkX, int chunkZ) {
        Key key = new Key(dimension, ChunkPos.asLong(chunkX, chunkZ));
        pending.remove(key);
        resync.remove(key);
    }

    public void retainDimension(ResourceLocation dimension) {
        pending.keySet().removeIf(key -> !key.dimension.equals(dimension));
        resync.keySet().removeIf(key -> !key.dimension.equals(dimension));
    }

    public int resyncCandidates(ResourceLocation dimension, Predicate<Long> loadedChunk, Consumer<Long> request, int limit) {
        int count = 0;
        Iterator<Key> iterator = resync.keySet().iterator();
        while (iterator.hasNext() && count < limit) {
            Key key = iterator.next();
            if (!key.dimension.equals(dimension)) continue;
            if (!loadedChunk.test(key.chunk)) continue;
            request.accept(key.chunk);
            count++;
        }
        return count;
    }

    /** Records a reset observed after loss, but does not acknowledge it until cache publication succeeds. */
    public void noteReset(AtmosphereVisualPayload payload) {
        if (!payload.resetSnapshot()) return;
        ResyncCandidate candidate = resync.get(key(payload));
        if (candidate != null) candidate.resetRevision = payload.revision();
    }

    /** Acknowledges only the final fragment for the fresh reset that was observed after loss. */
    public void acknowledgeApplied(AtmosphereVisualPayload payload) {
        if (!payload.finalPacket()) return;
        Key key = key(payload);
        ResyncCandidate candidate = resync.get(key);
        if (candidate != null && candidate.resetRevision == payload.revision()) resync.remove(key);
    }

    private static Key key(AtmosphereVisualPayload payload) {
        return new Key(payload.dimension(), ChunkPos.asLong(payload.chunkX(), payload.chunkZ()));
    }

    private void markForResync(Key key) {
        resync.computeIfAbsent(key, ignored -> new ResyncCandidate());
        while (resync.size() > MAX_CHUNKS) resync.remove(resync.keySet().iterator().next());
    }

    public void clear() { pending.clear(); resync.clear(); }
    public int chunkCount() { return pending.size(); }
    public int packetCount() { return pending.values().stream().mapToInt(ArrayDeque::size).sum(); }

    private record Key(ResourceLocation dimension, long chunk) { }
    private static final class ResyncCandidate { long resetRevision; }
}
