package com.juicyslew.moonstation14.ms14.power.cable.client;

import com.juicyslew.moonstation14.ms14.power.cable.network.CableVisualPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Consumer;

/** Small client-only holding area for authoritative snapshots received before world readiness. */
public final class CableVisualPendingSnapshots {
    public static final int MAX_CHUNKS = 16;
    public static final int MAX_RECORDS = 65_536;

    private final LinkedHashMap<Key, CableVisualPayload> snapshots = new LinkedHashMap<>();
    private int records;

    /** Keeps the newest revision for each dimension/chunk while enforcing aggregate hard bounds. */
    public void stage(CableVisualPayload payload) {
        Key key = new Key(payload.dimension(), ChunkPos.asLong(payload.chunkX(), payload.chunkZ()));
        CableVisualPayload previous = snapshots.get(key);
        if (previous != null && previous.revision() >= payload.revision()) return;
        int previousSize = previous == null ? 0 : previous.records().size();
        if (payload.records().size() > MAX_RECORDS) return;
        if (previous != null) {
            snapshots.remove(key);
            records -= previousSize;
        }
        snapshots.put(key, payload);
        records += payload.records().size();
        while (snapshots.size() > MAX_CHUNKS || records > MAX_RECORDS) evictOldest();
    }

    /** Applies and removes ready chunks in the active dimension; other dimensions are never applied. */
    public int flush(ResourceLocation dimension, Predicate<CableVisualPayload> isChunkReady,
                     Consumer<CableVisualPayload> apply) {
        Objects.requireNonNull(dimension);
        Objects.requireNonNull(isChunkReady);
        Objects.requireNonNull(apply);
        int applied = 0;
        var iterator = snapshots.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Key, CableVisualPayload> entry = iterator.next();
            CableVisualPayload payload = entry.getValue();
            if (!entry.getKey().dimension.equals(dimension) || !isChunkReady.test(payload)) continue;
            iterator.remove();
            records -= payload.records().size();
            apply.accept(payload);
            applied++;
        }
        return applied;
    }

    /** Drops all staged state, as on a full logout. */
    public void clear() { snapshots.clear(); records = 0; }

    /** Keeps only snapshots staged for the dimension the client is transitioning into. */
    public void retainDimension(ResourceLocation dimension) {
        Objects.requireNonNull(dimension);
        var iterator = snapshots.entrySet().iterator();
        while (iterator.hasNext()) {
            CableVisualPayload payload = iterator.next().getValue();
            if (payload.dimension().equals(dimension)) continue;
            iterator.remove();
            records -= payload.records().size();
        }
    }

    public int chunkCount() { return snapshots.size(); }
    public int recordCount() { return records; }

    private void evictOldest() {
        var iterator = snapshots.entrySet().iterator();
        if (!iterator.hasNext()) return;
        records -= iterator.next().getValue().records().size();
        iterator.remove();
    }

    private record Key(ResourceLocation dimension, long chunk) { }
}
