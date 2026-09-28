package com.juicyslew.moonstation14.ms14.power.cable.client;

import com.juicyslew.moonstation14.ms14.power.cable.CableChunkData;
import com.juicyslew.moonstation14.ms14.power.cable.network.CableVisualPayload;
import com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode;
import com.juicyslew.moonstation14.ms14.power.topology.PowerTopology;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded client cache populated only by server chunk-watch snapshots. */
public final class CableVisualClientCache {
    public static final int MAX_CHUNKS = 256;
    public static final int MAX_CACHED_RECORDS = 65_536;
    public static final int MAX_VISIBLE_RECORDS = 2048;
    public static final int MAX_RECORD_PROBES = 8192;
    private final LinkedHashMap<Long, ChunkVisual> chunks = new LinkedHashMap<>(16, .75f, true);
    private final Map<CableFaceNode, Boolean> topology = new java.util.HashMap<>();
    private ResourceLocation dimension;
    private int cachedRecords;

    public boolean apply(CableVisualPayload payload) {
        if (dimension != null && !dimension.equals(payload.dimension())) clearChunks();
        dimension = payload.dimension();
        long key = ChunkPos.asLong(payload.chunkX(), payload.chunkZ());
        ChunkVisual previous = chunks.get(key);
        if (previous != null && payload.revision() <= previous.revision) return false;
        if (!payload.resetSnapshot()) return false;
        if (previous != null) {
            cachedRecords -= previous.records.size();
            removeTopology(payload.chunkX(), payload.chunkZ(), previous.records);
        }
        chunks.put(key, new ChunkVisual(payload.revision(), payload.records(), false));
        cachedRecords += payload.records().size();
        addTopology(payload.chunkX(), payload.chunkZ(), payload.records());
        while (chunks.size() > MAX_CHUNKS || cachedRecords > MAX_CACHED_RECORDS) {
            Map.Entry<Long, ChunkVisual> eldest = chunks.entrySet().iterator().next();
            cachedRecords -= eldest.getValue().records.size();
            ChunkPos evicted = new ChunkPos(eldest.getKey());
            removeTopology(evicted.x, evicted.z, eldest.getValue().records);
            chunks.remove(eldest.getKey());
        }
        return true;
    }

    public List<VisibleRecord> visible(ResourceLocation dimension, double cameraX, double cameraY, double cameraZ,
                                       double radius, int budget) {
        if (budget <= 0 || budget > MAX_VISIBLE_RECORDS) throw new IllegalArgumentException("visible budget outside bounds");
        if (!java.util.Objects.equals(this.dimension, dimension)) return List.of();
        double radiusSq = radius * radius;
        var result = new java.util.ArrayList<VisibleRecord>(Math.min(budget, 256));
        int probes = 0;
        for (Map.Entry<Long, ChunkVisual> entry : chunks.entrySet()) {
            ChunkPos chunk = new ChunkPos(entry.getKey());
            double minX = chunk.getMinBlockX(), minZ = chunk.getMinBlockZ();
            double dx = Math.max(Math.max(minX - cameraX, 0), cameraX - (minX + 16));
            double dz = Math.max(Math.max(minZ - cameraZ, 0), cameraZ - (minZ + 16));
            if (dx * dx + dz * dz > radiusSq) continue;
            for (CableChunkData.Record record : entry.getValue().records) {
                if (++probes > MAX_RECORD_PROBES) return List.copyOf(result);
                double x = minX + record.x() + .5, y = record.y() + .5, z = minZ + record.z() + .5;
                double ax = x - cameraX, ay = y - cameraY, az = z - cameraZ;
                if (ax * ax + ay * ay + az * az <= radiusSq) {
                    result.add(new VisibleRecord(x, y, z, record));
                    if (result.size() >= budget) return List.copyOf(result);
                }
            }
        }
        return List.copyOf(result);
    }

    public void clear() { clearChunks(); }
    public void clearDimension() { clearChunks(); dimension = null; }
    public boolean removeChunk(ResourceLocation unloadDimension, int x, int z) {
        if (!java.util.Objects.equals(dimension, unloadDimension)) return false;
        long key = ChunkPos.asLong(x, z);
        ChunkVisual removed = chunks.get(key);
        if (removed != null) {
            cachedRecords -= removed.records.size();
            removeTopology(x, z, removed.records);
            chunks.put(key, new ChunkVisual(removed.revision, List.of(), true));
        }
        return true;
    }
    public int chunkCount() { return chunks.size(); }
    public int cachedRecordCount() { return cachedRecords; }
    /** True only when a server-authored snapshot (including an authoritative empty one) is cached. */
    public boolean hasUsableSnapshot(ResourceLocation dimension, int chunkX, int chunkZ) {
        if (!java.util.Objects.equals(this.dimension, dimension)) return false;
        ChunkVisual visual = chunks.get(ChunkPos.asLong(chunkX, chunkZ));
        return visual != null && !visual.tombstone;
    }
    /** Topology lookup is independent of render visibility/budget and only uses authoritative snapshots. */
    public List<CableFaceNode> topologyNeighbors(CableFaceNode node) {
        var found = new java.util.ArrayList<CableFaceNode>(PowerTopology.MAX_CABLE_NEIGHBORS);
        for (CableFaceNode candidate : CableVisualAdjacency.possibleNeighbors(node))
            if (topology.containsKey(candidate) && PowerTopology.cableEdge(node, candidate).isPresent()) found.add(candidate);
        return List.copyOf(found);
    }

    private void addTopology(int chunkX, int chunkZ, List<CableChunkData.Record> records) {
        int minX = chunkX << 4, minZ = chunkZ << 4;
        for (CableChunkData.Record record : records)
            topology.put(new CableFaceNode(new net.minecraft.core.BlockPos(minX + record.x(), record.y(), minZ + record.z()),
                    record.face(), record.tier()), Boolean.TRUE);
    }
    private void removeTopology(int chunkX, int chunkZ, List<CableChunkData.Record> records) {
        int minX = chunkX << 4, minZ = chunkZ << 4;
        for (CableChunkData.Record record : records)
            topology.remove(new CableFaceNode(new net.minecraft.core.BlockPos(minX + record.x(), record.y(), minZ + record.z()),
                    record.face(), record.tier()));
    }

    private void clearChunks() { chunks.clear(); topology.clear(); cachedRecords = 0; }
    private record ChunkVisual(long revision, List<CableChunkData.Record> records, boolean tombstone) { }
    public record VisibleRecord(double x, double y, double z, CableChunkData.Record record) { }
}
