package com.juicyslew.moonstation14.ms14.power.graph;

import com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode;
import com.juicyslew.moonstation14.ms14.power.topology.PowerTopology;
import net.minecraft.core.BlockPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Incrementally-built, fail-closed cable topology for the currently loaded chunks. */
public final class LoadedPowerGraph {
    private final Map<Object, List<CableFaceNode>> chunks = new LinkedHashMap<>();
    private final Map<CableFaceNode, Integer> components = new HashMap<>();
    private final ArrayDeque<CableFaceNode> frontier = new ArrayDeque<>();
    private final Set<CableFaceNode> queued = new HashSet<>();
    private final Set<CableFaceNode> visited = new HashSet<>();
    private final Set<Long> loadedChunks = new HashSet<>();
    private final Map<Integer, ComponentInfo> componentInfo = new HashMap<>();
    private List<CableFaceNode> ordered = List.of();
    private Set<CableFaceNode> nodeIndex = Set.of();
    private List<List<CableFaceNode>> buildChunks = List.of();
    private final List<CableFaceNode> buildingNodes = new ArrayList<>();
    private Set<CableFaceNode> buildingIndex = new HashSet<>();
    private int buildChunkCursor;
    private int buildNodeCursor;
    private boolean indexed;
    private int componentId;
    private int seedCursor;
    private int queueHighWater;
    private long revision;
    private boolean dirty = true;

    public enum Knowledge { UNKNOWN, KNOWN }
    public record NodeState(Knowledge knowledge, int componentId) { }

    /** Chunk keys are opaque so the geometry core remains independent of a world. */
    public void replaceChunk(Object chunkKey, Collection<CableFaceNode> nodes) {
        List<CableFaceNode> replacement = List.copyOf(nodes);
        if (replacement.equals(chunks.get(chunkKey))) return;
        chunks.put(chunkKey, replacement);
        if (chunkKey instanceof net.minecraft.world.level.ChunkPos pos) loadedChunks.add(pos.toLong());
        else for (CableFaceNode node : nodes) loadedChunks.add(chunkKey(node.host().getX() >> 4, node.host().getZ() >> 4));
        invalidate();
    }

    public void removeChunk(Object chunkKey) {
        List<CableFaceNode> removed = chunks.remove(chunkKey);
        if (chunkKey instanceof net.minecraft.world.level.ChunkPos pos) loadedChunks.remove(pos.toLong());
        else if (removed != null) for (CableFaceNode node : removed)
            loadedChunks.remove(chunkKey(node.host().getX() >> 4, node.host().getZ() >> 4));
        invalidate();
    }

    /** Tracks loaded space independently from cable-bearing chunks. */
    public void setChunkLoaded(net.minecraft.world.level.ChunkPos pos, boolean loaded) {
        boolean changed = loaded ? loadedChunks.add(pos.toLong()) : loadedChunks.remove(pos.toLong());
        if (changed) invalidate();
    }

    public void invalidate() {
        revision++;
        dirty = true;
        ordered = List.of();
        nodeIndex = Set.of();
        buildChunks = List.of();
        buildingNodes.clear();
        buildingIndex = new HashSet<>();
        buildChunkCursor = 0;
        buildNodeCursor = 0;
        indexed = false;
        components.clear();
        frontier.clear();
        queued.clear();
        visited.clear();
        componentInfo.clear();
        componentId = 0;
        seedCursor = 0;
        queueHighWater = 0;
    }

    /** Performs at most {@code nodeBudget} graph expansion units. */
    public int process(int nodeBudget) {
        if (nodeBudget < 0) throw new IllegalArgumentException("nodeBudget must not be negative");
        if (!dirty || nodeBudget == 0) return 0;
        int processed = 0;
        while (processed < nodeBudget) {
            if (!indexed) {
                if (buildChunks.isEmpty() && buildChunkCursor == 0 && buildNodeCursor == 0)
                    buildChunks = new ArrayList<>(chunks.values());
                while (buildChunkCursor < buildChunks.size()
                        && buildNodeCursor >= buildChunks.get(buildChunkCursor).size()) {
                    buildChunkCursor++;
                    buildNodeCursor = 0;
                }
                if (buildChunkCursor >= buildChunks.size()) {
                    ordered = buildingNodes;
                    nodeIndex = buildingIndex;
                    indexed = true;
                    continue;
                }
                CableFaceNode builtNode = buildChunks.get(buildChunkCursor).get(buildNodeCursor++);
                if (buildingIndex.add(builtNode)) buildingNodes.add(builtNode);
                processed++;
                continue;
            }
            if (frontier.isEmpty()) {
                while (seedCursor < ordered.size() && visited.contains(ordered.get(seedCursor))) seedCursor++;
                if (seedCursor == ordered.size()) { dirty = false; break; }
                CableFaceNode seed = ordered.get(seedCursor++);
                frontier.add(seed);
                queued.add(seed);
                queueHighWater = Math.max(queueHighWater, frontier.size());
                componentId++;
                componentInfo.put(componentId, new ComponentInfo());
            }
            CableFaceNode current = frontier.removeFirst();
            queued.remove(current);
            processed++;
            if (!visited.add(current)) continue;
            components.put(current, componentId);
            ComponentInfo info = componentInfo.get(componentId);
            info.loadedChunks.add(chunkKey(current.host().getX() >> 4, current.host().getZ() >> 4));
            for (CableFaceNode candidate : potentialNeighbors(current)) {
                long key = chunkKey(candidate.host().getX() >> 4, candidate.host().getZ() >> 4);
                if (!loadedChunks.contains(key)) info.unknownFrontier.add(key);
            }
            for (CableFaceNode candidate : nearby(current))
                if (!visited.contains(candidate) && queued.add(candidate)) {
                    frontier.addLast(candidate);
                    queueHighWater = Math.max(queueHighWater, frontier.size());
                }
        }
        if (indexed && frontier.isEmpty() && visited.size() == ordered.size()) dirty = false;
        return processed;
    }

    public NodeState state(CableFaceNode node) {
        Integer id = dirty ? null : components.get(node);
        if (id == null) return new NodeState(Knowledge.UNKNOWN, -1);
        ComponentInfo info = componentInfo.get(id);
        return info == null || !info.unknownFrontier.isEmpty()
                ? new NodeState(Knowledge.UNKNOWN, id) : new NodeState(Knowledge.KNOWN, id);
    }

    public int loadedNodeCount() { return chunks.values().stream().mapToInt(List::size).sum(); }
    public long revision() { return revision; }

    /** Returns local indexed nodes, or null when any chunk intersecting the radius is unloaded. */
    public List<CableFaceNode> nodesNear(BlockPos center, int radius, com.juicyslew.moonstation14.ms14.power.cable.CableTier tier) {
        if (center == null || tier == null || radius < 0) throw new IllegalArgumentException();
        int minChunkX = (center.getX() - radius) >> 4;
        int maxChunkX = (center.getX() + radius) >> 4;
        int minChunkZ = (center.getZ() - radius) >> 4;
        int maxChunkZ = (center.getZ() + radius) >> 4;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) for (int cz = minChunkZ; cz <= maxChunkZ; cz++)
            if (!loadedChunks.contains(chunkKey(cx, cz))) return null;
        if (dirty || !indexed) return null;
        List<CableFaceNode> result = new ArrayList<>();
        int radiusSquared = radius * radius;
        for (int x = center.getX() - radius; x <= center.getX() + radius; x++)
            for (int y = center.getY() - radius; y <= center.getY() + radius; y++)
                for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                    int dx = x - center.getX(), dy = y - center.getY(), dz = z - center.getZ();
                    if (dx * dx + dy * dy + dz * dz > radiusSquared) continue;
                    for (var face : net.minecraft.core.Direction.values()) {
                        CableFaceNode node = new CableFaceNode(new net.minecraft.core.BlockPos(x, y, z), face, tier);
                        if (nodeIndex.contains(node)) result.add(node);
                    }
                }
        result.sort(java.util.Comparator.comparingInt((CableFaceNode n) -> n.host().getX())
                .thenComparingInt(n -> n.host().getY()).thenComparingInt(n -> n.host().getZ())
                .thenComparingInt(n -> n.face().ordinal()));
        return List.copyOf(result);
    }
    public boolean isDirty() { return dirty; }
    /** Largest BFS frontier observed since the latest invalidation. */
    public int queueHighWater() { return queueHighWater; }

    private List<CableFaceNode> nearby(CableFaceNode node) {
        // Geometry contacts are local. Looking up candidates from the loaded-node index
        // avoids a station-wide scan for each expansion.
        Map<CableFaceNode, CableFaceNode> candidates = new LinkedHashMap<>();
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
            var host = node.host().offset(dx, dy, dz);
            for (var face : net.minecraft.core.Direction.values()) {
                CableFaceNode candidate = new CableFaceNode(host, face, node.tier());
                if (nodeIndex.contains(candidate)) candidates.put(candidate, candidate);
            }
        }
        return PowerTopology.cableNeighbors(node, candidates.values());
    }

    private List<CableFaceNode> potentialNeighbors(CableFaceNode node) {
        List<CableFaceNode> result = new ArrayList<>(8);
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
            var host = node.host().offset(dx, dy, dz);
            if (node.host().equals(host)) continue;
            for (var face : net.minecraft.core.Direction.values()) {
                CableFaceNode candidate = new CableFaceNode(host, face, node.tier());
                if (PowerTopology.cableEdge(node, candidate).isPresent()) result.add(candidate);
            }
        }
        return result;
    }

    private static long chunkKey(int x, int z) { return ((long) x & 0xffffffffL) | ((long) z << 32); }

    private static final class ComponentInfo {
        final Set<Long> loadedChunks = new HashSet<>();
        final Set<Long> unknownFrontier = new HashSet<>();
    }

}
