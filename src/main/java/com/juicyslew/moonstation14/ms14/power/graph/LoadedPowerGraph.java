package com.juicyslew.moonstation14.ms14.power.graph;

import com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode;
import com.juicyslew.moonstation14.ms14.power.topology.DevicePort;
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
    private final Map<Object, List<DevicePort>> devicePorts = new LinkedHashMap<>();
    private final Map<CableFaceNode, Integer> components = new HashMap<>();
    private final ArrayDeque<CableFaceNode> frontier = new ArrayDeque<>();
    private final Set<CableFaceNode> queued = new HashSet<>();
    private final Set<CableFaceNode> visited = new HashSet<>();
    private final Set<Long> loadedChunks = new HashSet<>();
    private final Set<Long> potentialNeighborChunks = new HashSet<>();
    private final Map<Integer, ComponentInfo> componentInfo = new HashMap<>();
    private List<CableFaceNode> ordered = List.of();
    private Set<CableFaceNode> nodeIndex = Set.of();
    private List<List<CableFaceNode>> buildChunks = List.of();
    private final List<CableFaceNode> buildingNodes = new ArrayList<>();
    private Set<CableFaceNode> buildingIndex = new HashSet<>();
    private final Map<CableFaceNode, Set<CableFaceNode>> busNeighbors = new HashMap<>();
    private final Map<CableFaceNode, Set<Long>> busPotentialChunks = new HashMap<>();
    private final List<DevicePort> buildingPorts = new ArrayList<>();
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
    /** Read-only explanation of a node's current fail-closed graph state. */
    public record NodeDiagnostics(NodeState state, boolean rebuilding, boolean nodeIndexed,
                                  List<net.minecraft.world.level.ChunkPos> unloadedPotentialNeighborChunks) { }
    public record FrontierAttribution(net.minecraft.world.level.ChunkPos chunk, int sourceCount,
                                      List<CableFaceNode> sources, boolean truncated,
                                      int busSourceCount, List<CableFaceNode> busSources,
                                      boolean busSourcesTruncated) { }
    public record ComponentDiagnostics(NodeState state, int memberCount,
                                       List<FrontierAttribution> missingFrontiers,
                                       int missingChunkCount, boolean chunksTruncated,
                                       int membersExamined, boolean membersTruncated,
                                       int frontierEntriesExamined, boolean frontierTruncated) { }
    public record BuildProgress(boolean dirty, long revision, boolean indexed, int chunkCursor,
                                int nodeCursor, int indexedNodes, int frontierNodes, int visitedNodes,
                                int loadedNodes, int loadedChunks) { }

    /** Compact, read-only progress snapshot for diagnostics. */
    public BuildProgress buildProgress() {
        return new BuildProgress(dirty, revision, indexed, buildChunkCursor, buildNodeCursor,
                buildingIndex.size(), frontier.size(), visited.size(), loadedNodeCount(), loadedChunks.size());
    }

    /** Chunk keys are opaque so the geometry core remains independent of a world. */
    public void replaceChunk(Object chunkKey, Collection<CableFaceNode> nodes) {
        replaceChunk(chunkKey, nodes, List.of());
    }

    public void replaceChunk(Object chunkKey, Collection<CableFaceNode> nodes, Collection<DevicePort> ports) {
        List<CableFaceNode> replacement = List.copyOf(nodes);
        List<DevicePort> replacementPorts = List.copyOf(ports);
        List<CableFaceNode> previous = chunks.get(chunkKey);
        List<DevicePort> previousPorts = devicePorts.get(chunkKey);
        if (replacement.equals(previous) && replacementPorts.equals(previousPorts)) return;
        // Empty space is represented by loadedChunks, not by an empty cable chunk.
        // Preserve replaceChunk's loaded-space contract for Minecraft chunk keys.
        if (previous == null && replacement.isEmpty() && replacementPorts.isEmpty()) {
            if (chunkKey instanceof net.minecraft.world.level.ChunkPos pos) setChunkLoaded(pos, true);
            return;
        }
        chunks.put(chunkKey, replacement);
        devicePorts.put(chunkKey, replacementPorts);
        if (chunkKey instanceof net.minecraft.world.level.ChunkPos pos) loadedChunks.add(pos.toLong());
        else for (CableFaceNode node : nodes) loadedChunks.add(chunkKey(node.host().getX() >> 4, node.host().getZ() >> 4));
        invalidate();
    }

    public void removeChunk(Object chunkKey) {
        List<CableFaceNode> removed = chunks.remove(chunkKey);
        devicePorts.remove(chunkKey);
        if (removed == null) {
            if (chunkKey instanceof net.minecraft.world.level.ChunkPos pos) setChunkLoaded(pos, false);
            return;
        }
        if (chunkKey instanceof net.minecraft.world.level.ChunkPos pos) loadedChunks.remove(pos.toLong());
        else if (removed != null) for (CableFaceNode node : removed)
            loadedChunks.remove(chunkKey(node.host().getX() >> 4, node.host().getZ() >> 4));
        invalidate();
    }

    /** Tracks loaded space independently from cable-bearing chunks. */
    /** Updates loaded-space knowledge without restarting connectivity work. Returns true if a
     * currently-built cable component references this chunk as a possible geometric neighbor. */
    public boolean setChunkLoaded(net.minecraft.world.level.ChunkPos pos, boolean loaded) {
        boolean changed = loaded ? loadedChunks.add(pos.toLong()) : loadedChunks.remove(pos.toLong());
        if (!changed) return false;
        boolean relevant = potentialNeighborChunks.contains(pos.toLong());
        if (relevant) revision++;
        return relevant;
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
        busNeighbors.clear();
        busPotentialChunks.clear();
        buildingPorts.clear();
        potentialNeighborChunks.clear();
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
                if (buildChunks.isEmpty() && buildChunkCursor == 0 && buildNodeCursor == 0) {
                    buildChunks = new ArrayList<>(chunks.values());
                    devicePorts.values().forEach(buildingPorts::addAll);
                }
                while (buildChunkCursor < buildChunks.size()
                        && buildNodeCursor >= buildChunks.get(buildChunkCursor).size()) {
                    buildChunkCursor++;
                    buildNodeCursor = 0;
                }
                if (buildChunkCursor >= buildChunks.size()) {
                    if (!buildingPorts.isEmpty()) {
                        DevicePort port = buildingPorts.remove(0);
                        List<CableFaceNode> contacts = PowerTopology.deviceContacts(port).stream()
                                .filter(buildingIndex::contains).toList();
                        for (CableFaceNode contact : contacts) {
                            Set<CableFaceNode> neighbors = busNeighbors.computeIfAbsent(contact, ignored -> new HashSet<>());
                            for (CableFaceNode other : contacts) if (!other.equals(contact)) neighbors.add(other);
                        }
                        for (CableFaceNode candidate : PowerTopology.deviceContacts(port)) {
                            long key = chunkKey(candidate.host().getX() >> 4, candidate.host().getZ() >> 4);
                            for (CableFaceNode contact : contacts)
                                busPotentialChunks.computeIfAbsent(contact, ignored -> new HashSet<>()).add(key);
                        }
                        processed++;
                        continue;
                    }
                    ordered = buildingNodes;
                    nodeIndex = buildingIndex;
                    indexed = true;
                    continue;
                }
                CableFaceNode builtNode = buildChunks.get(buildChunkCursor).get(buildNodeCursor++);
                if (buildingIndex.add(builtNode)) {
                    buildingNodes.add(builtNode);
                    for (CableFaceNode candidate : potentialNeighbors(builtNode))
                        potentialNeighborChunks.add(chunkKey(candidate.host().getX() >> 4,
                                candidate.host().getZ() >> 4));
                }
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
                info.potentialNeighborChunks.add(key);
            }
            info.potentialNeighborChunks.addAll(busPotentialChunks.getOrDefault(current, Set.of()));
            Set<CableFaceNode> adjacent = new HashSet<>(nearby(current));
            adjacent.addAll(busNeighbors.getOrDefault(current, Set.of()));
            for (CableFaceNode candidate : adjacent)
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
        return info == null || info.potentialNeighborChunks.stream().anyMatch(key -> !loadedChunks.contains(key))
                ? new NodeState(Knowledge.UNKNOWN, id) : new NodeState(Knowledge.KNOWN, id);
    }

    /** Resolve the one component joined by this authoritative loaded-port bus. */
    public NodeState state(DevicePort port) {
        if (port == null || dirty || !indexed) return new NodeState(Knowledge.UNKNOWN, -1);
        for (CableFaceNode candidate : PowerTopology.deviceContacts(port)) {
            if (!nodeIndex.contains(candidate)) continue;
            return state(candidate);
        }
        return new NodeState(Knowledge.UNKNOWN, -1);
    }

    /** Bounded, rotating scan of one component's stored frontier. Each visited key
     * counts against limit, including keys already known loaded. */
    public List<net.minecraft.world.level.ChunkPos> missingFrontierBatch(CableFaceNode node, int limit) {
        if (limit < 1) throw new IllegalArgumentException();
        Integer id = dirty ? null : components.get(node);
        ComponentInfo info = id == null ? null : componentInfo.get(id);
        if (info == null || info.potentialNeighborChunks.isEmpty()) return List.of();
        var keys = info.potentialNeighborChunks;
        var result = new ArrayList<net.minecraft.world.level.ChunkPos>(limit);
        int examined = 0;
        var iterator = info.probeCursor == null ? keys.iterator()
                : keys.tailSet(info.probeCursor, false).iterator();
        if (!iterator.hasNext()) iterator = keys.iterator();
        long last = info.probeCursor == null ? 0 : info.probeCursor;
        while (examined < limit && iterator.hasNext()) {
            long key = iterator.next();
            if (info.probeCursor != null && key == info.probeCursor && examined > 0) break;
            last = key;
            examined++;
            if (!loadedChunks.contains(key)) result.add(new net.minecraft.world.level.ChunkPos(key));
        }
        if (examined == keys.size()) info.probeCursor = null;
        else info.probeCursor = last;
        return List.copyOf(result);
    }

    /** Reports stored component frontier data; does not infer neighbors or change graph state. */
    public NodeDiagnostics diagnostics(CableFaceNode node) {
        NodeState current = state(node);
        Integer id = components.get(node);
        ComponentInfo info = id == null ? null : componentInfo.get(id);
        List<net.minecraft.world.level.ChunkPos> missing = new ArrayList<>();
        if (info != null) for (long key : info.potentialNeighborChunks)
            if (!loadedChunks.contains(key)) missing.add(new net.minecraft.world.level.ChunkPos(key));
        missing.sort(java.util.Comparator.comparingInt((net.minecraft.world.level.ChunkPos pos) -> pos.x)
                .thenComparingInt(pos -> pos.z));
        return new NodeDiagnostics(current, dirty, indexed && nodeIndex.contains(node), List.copyOf(missing));
    }

    /** Read-only bounded attribution from the exact stored component frontier and its cable/bus sources. */
    public ComponentDiagnostics componentDiagnostics(CableFaceNode node, int maxMembers, int maxFrontierEntries,
                                                       int maxChunks, int maxSources) {
        if (maxMembers < 1 || maxFrontierEntries < 1 || maxChunks < 1 || maxSources < 1)
            throw new IllegalArgumentException("positive diagnostic bounds required");
        NodeState current = state(node);
        Integer id = dirty ? null : components.get(node);
        ComponentInfo info = id == null ? null : componentInfo.get(id);
        if (info == null) return new ComponentDiagnostics(current, 0, List.of(), 0, false, 0, false, 0, false);
        var members = new HashSet<CableFaceNode>();
        var queue = new ArrayDeque<CableFaceNode>();
        members.add(node); queue.add(node);
        while (!queue.isEmpty() && members.size() < maxMembers) {
            CableFaceNode member = queue.removeFirst();
            for (CableFaceNode adjacent : connectedNeighbors(member)) {
                if (members.size() >= maxMembers) break;
                if (components.getOrDefault(adjacent, -1) == id && members.add(adjacent)) queue.addLast(adjacent);
            }
        }
        boolean membersTruncated = !queue.isEmpty();
        var orderedMembers = members.stream().sorted(nodeOrder()).toList();
        var missing = new java.util.TreeSet<net.minecraft.world.level.ChunkPos>(java.util.Comparator
                .comparingInt((net.minecraft.world.level.ChunkPos pos) -> pos.x).thenComparingInt(pos -> pos.z));
        int frontierEntriesExamined = 0;
        for (long key : info.potentialNeighborChunks) {
            if (frontierEntriesExamined >= maxFrontierEntries) break;
            frontierEntriesExamined++;
            if (!loadedChunks.contains(key)) missing.add(new net.minecraft.world.level.ChunkPos(key));
        }
        boolean frontierTruncated = frontierEntriesExamined < info.potentialNeighborChunks.size();
        var selected = missing.stream().limit(maxChunks).toList();
        var selectedKeys = selected.stream().map(net.minecraft.world.level.ChunkPos::toLong).collect(java.util.stream.Collectors.toSet());
        var geometrySources = new HashMap<Long, java.util.TreeSet<CableFaceNode>>();
        var busSourcesByChunk = new HashMap<Long, java.util.TreeSet<CableFaceNode>>();
        var geometryCounts = new HashMap<Long, Integer>();
        var busCounts = new HashMap<Long, Integer>();
        var outputOrder = nodeOrder();
        for (CableFaceNode member : orderedMembers) {
            var memberGeometryChunks = new HashSet<Long>();
            var memberBusChunks = new HashSet<Long>();
            for (CableFaceNode candidate : potentialNeighbors(member)) {
                long key = chunkKey(candidate.host().getX() >> 4, candidate.host().getZ() >> 4);
                if (selectedKeys.contains(key)) memberGeometryChunks.add(key);
            }
            for (long key : busPotentialChunks.getOrDefault(member, Set.of()))
                if (selectedKeys.contains(key)) memberBusChunks.add(key);
            var allSourceChunks = new HashSet<>(memberGeometryChunks); allSourceChunks.addAll(memberBusChunks);
            for (long key : allSourceChunks) {
                addBoundedSource(geometrySources, key, member, outputOrder, maxSources);
                geometryCounts.merge(key, 1, Integer::sum);
            }
            for (long key : memberBusChunks) {
                addBoundedSource(busSourcesByChunk, key, member, outputOrder, maxSources);
                busCounts.merge(key, 1, Integer::sum);
            }
        }
        var attributions = new ArrayList<FrontierAttribution>();
        for (var chunk : selected) {
            long key = chunk.toLong();
            var geometry = geometrySources.getOrDefault(key, new java.util.TreeSet<>(outputOrder));
            var bus = busSourcesByChunk.getOrDefault(key, new java.util.TreeSet<>(outputOrder));
            attributions.add(new FrontierAttribution(chunk, geometryCounts.getOrDefault(key, 0), List.copyOf(geometry),
                    geometryCounts.getOrDefault(key, 0) > geometry.size(), busCounts.getOrDefault(key, 0),
                    List.copyOf(bus), busCounts.getOrDefault(key, 0) > bus.size()));
        }
        int observedMissing = missing.size();
        boolean chunksTruncated = missing.size() > maxChunks || frontierTruncated;
        return new ComponentDiagnostics(current, orderedMembers.size(), List.copyOf(attributions), observedMissing,
                chunksTruncated, orderedMembers.size(), membersTruncated, frontierEntriesExamined, frontierTruncated);
    }

    private java.util.Comparator<CableFaceNode> nodeOrder() {
        return java.util.Comparator.comparingInt((CableFaceNode n) -> n.host().getX())
                .thenComparingInt(n -> n.host().getY()).thenComparingInt(n -> n.host().getZ())
                .thenComparingInt(n -> n.face().ordinal());
    }

    private static void addBoundedSource(Map<Long, java.util.TreeSet<CableFaceNode>> sources, long key,
                                         CableFaceNode node, java.util.Comparator<CableFaceNode> order, int limit) {
        var sample = sources.computeIfAbsent(key, ignored -> new java.util.TreeSet<>(order));
        sample.add(node);
        if (sample.size() > limit) sample.pollLast();
    }

    private List<CableFaceNode> connectedNeighbors(CableFaceNode node) {
        Set<CableFaceNode> adjacent = new HashSet<>(nearby(node));
        adjacent.addAll(busNeighbors.getOrDefault(node, Set.of()));
        return adjacent.stream().filter(candidate -> components.getOrDefault(candidate, -1)
                .equals(components.getOrDefault(node, -2))).toList();
    }

    public int loadedNodeCount() { return chunks.values().stream().mapToInt(List::size).sum(); }
    public long revision() { return revision; }

    /** Whether this chunk is represented in the loaded-space index. */
    public boolean isChunkLoaded(net.minecraft.world.level.ChunkPos pos) {
        return loadedChunks.contains(pos.toLong());
    }

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
        final java.util.NavigableSet<Long> potentialNeighborChunks = new java.util.TreeSet<>();
        Long probeCursor;
    }

}

