package com.juicyslew.moonstation14.ms14.power.graph;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.power.cable.CableChunkData;
import com.juicyslew.moonstation14.ms14.power.cable.CableStorage;
import com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Server-thread owner of per-world loaded cable topology. */
public final class PowerGraphService {
    public static final int CHUNK_REFRESHES_PER_TICK = 4;
    public static final int GRAPH_NODES_PER_TICK = 256;
    private static final Map<ServerLevel, LevelState> STATES = new WeakHashMap<>();
    private PowerGraphService() { }

    public static void register(IEventBus bus) { bus.register(PowerGraphService.class); }

    /** Mutation notification: edits coalesce by chunk and make all cached answers fail closed. */
    public static void noteChanged(ServerLevel level, ChunkPos chunk) {
        LevelState state = STATES.get(level);
        if (state != null) {
            state.topologyGeneration++;
            state.pending.add(chunk);
        }
    }

    /** O(1), read-only freshness token for cached topology-derived samples. */
    public static long topologyGeneration(ServerLevel level) {
        LevelState state = STATES.get(level);
        return state == null ? 0 : state.topologyGeneration;
    }

    /** True only when no mutations are queued and the bounded graph rebuild is complete. */
    public static boolean topologyReady(ServerLevel level) {
        LevelState state = STATES.get(level);
        return state != null && state.pending.isEmpty() && !state.graph.isDirty();
    }

    /** Compact read-only topology/build state for diagnostics; does not query or load chunks. */
    public static String diagnosticSnapshot(ServerLevel level) {
        LevelState state = STATES.get(level);
        if (state == null) return "powerGraph{uninitialized}";
        LoadedPowerGraph.BuildProgress progress = state.graph.buildProgress();
        return "powerGraph{pending=" + state.pending.size() + ",nodes=" + progress.loadedNodes()
                + ",loadedChunks=" + progress.loadedChunks() + ",dirty=" + progress.dirty()
                + ",revision=" + progress.revision() + ",indexed=" + progress.indexed()
                + ",buildChunk=" + progress.chunkCursor() + ",buildNode=" + progress.nodeCursor()
                + ",indexedNodes=" + progress.indexedNodes() + ",frontier=" + progress.frontierNodes()
                + ",visited=" + progress.visitedNodes() + "}";
    }

    public static LoadedPowerGraph.NodeState state(ServerLevel level, CableFaceNode node) {
        LevelState state = STATES.get(level);
        return state == null || !state.pending.isEmpty()
                ? new LoadedPowerGraph.NodeState(LoadedPowerGraph.Knowledge.UNKNOWN, -1)
                : state.graph.state(node);
    }

    /** Read-only graph internals for focused runtime diagnostics; never loads chunks. */
    public static LoadedPowerGraph.NodeDiagnostics diagnostics(ServerLevel level, CableFaceNode node) {
        LevelState state = STATES.get(level);
        if (state == null) return new LoadedPowerGraph.NodeDiagnostics(
                new LoadedPowerGraph.NodeState(LoadedPowerGraph.Knowledge.UNKNOWN, -1),
                true, false, java.util.List.of());
        return state.graph.diagnostics(node);
    }

    /** Bounded indexed lookup for lamp extension receivers. Null means graph knowledge is incomplete. */
    public static java.util.List<CableFaceNode> nodesNear(ServerLevel level, BlockPos center, int radius,
                                                          com.juicyslew.moonstation14.ms14.power.cable.CableTier tier) {
        LevelState state = STATES.get(level);
        if (state == null || !state.pending.isEmpty()) return null;
        if (!ensureLoadedFootprint(level, state, center, radius)) return null;
        return state.graph.nodesNear(center, radius, tier);
    }

    /** Cached loaded-known APC extension receiver for lamps and local ventilation devices. */
    public static CableFaceNode nearestApcExtensionNode(ServerLevel level, BlockPos center) {
        return nearestApcExtensionNode(level, center, 3);
    }

    /** Prototype-configured receiver reach; cache identity includes the radius. */
    public static CableFaceNode nearestApcExtensionNode(ServerLevel level, BlockPos center, int radius) {
        if (radius < 1 || radius > 16) return null;
        LevelState state = STATES.get(level);
        if (state == null || !state.pending.isEmpty()) return null;
        if (!ensureLoadedFootprint(level, state, center, radius)) return null;
        long revision = state.graph.revision();
        CachedLampNode cached = state.lampChoices.get(center);
        if (cached != null && cached.revision == revision && cached.radius == radius) return cached.node;
        java.util.List<CableFaceNode> nearby = state.graph.nodesNear(center, radius,
                com.juicyslew.moonstation14.ms14.power.cable.CableTier.APC);
        if (nearby == null) return null;
        CableFaceNode nearest = nearestKnownLampNode(state.graph, center, nearby);
        state.lampChoices.put(center.immutable(), new CachedLampNode(revision, radius, nearest));
        return nearest;
    }

    /** Reconcile missed load bookkeeping only for chunks already present in the FULL cache. */
    private static boolean ensureLoadedFootprint(ServerLevel level, LevelState state, BlockPos center, int radius) {
        if (center == null || radius < 0 || radius > 16) return false;
        int minChunkX = (center.getX() - radius) >> 4;
        int maxChunkX = (center.getX() + radius) >> 4;
        int minChunkZ = (center.getZ() - radius) >> 4;
        int maxChunkZ = (center.getZ() + radius) >> 4;
        boolean complete = true;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
            ChunkPos pos = new ChunkPos(cx, cz);
            if (state.graph.isChunkLoaded(pos)) continue;
            if (level.getChunkSource().getChunkNow(cx, cz) == null) {
                complete = false;
                continue;
            }
            if (state.pending.add(pos)) state.topologyGeneration++;
            complete = false;
        }
        return complete;
    }

    public static CableFaceNode nearestLampNode(ServerLevel level, BlockPos center) {
        return nearestApcExtensionNode(level, center);
    }

    static CableFaceNode nearestKnownLampNode(LoadedPowerGraph graph, BlockPos center,
                                               java.util.List<CableFaceNode> candidates) {
        return candidates.stream()
                .filter(node -> graph.state(node).knowledge() == LoadedPowerGraph.Knowledge.KNOWN)
                .min(java.util.Comparator.comparingLong((CableFaceNode node) -> {
                    long dx = (long) center.getX() - node.host().getX();
                    long dy = (long) center.getY() - node.host().getY();
                    long dz = (long) center.getZ() - node.host().getZ();
                    return dx * dx + dy * dy + dz * dz;
                })
                        .thenComparingInt(node -> node.host().getX()).thenComparingInt(node -> node.host().getY())
                        .thenComparingInt(node -> node.host().getZ()).thenComparingInt(node -> node.face().ordinal()))
                .orElse(null);
    }

    public static void invalidateLampChoice(ServerLevel level, BlockPos center) {
        LevelState state = STATES.get(level);
        if (state != null) state.lampChoices.remove(center);
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        LevelState state = state(level);
        ChunkPos pos = event.getChunk().getPos();
        // The load event can precede promotion into the FULL cache. Keep only a bounded
        // validation marker; it is not loaded-space knowledge or fail-closed mutation work.
        if (!(event.getChunk() instanceof LevelChunk chunk)
                || level.getChunkSource().getChunkNow(pos.x, pos.z) != chunk) {
            deferFullChunkLoad(state, pos);
            return;
        }
        resolveFullChunkLoad(state, pos);
        boolean relevantBoundary = state.graph.setChunkLoaded(pos, true);
        if (relevantBoundary) state.topologyGeneration++;
        CableChunkData data = chunk.getExistingDataOrNull(ModDataAttachments.POWER_CABLE_CHUNK.get());
        if (data != null && !data.snapshot().isEmpty() && state.pending.add(pos))
            state.topologyGeneration++;
        else
            // Empty chunks remain in bounded periodic validation so missed direct
            // cable-record edits are eventually discovered without a global reset.
            state.validation.schedule(pos);
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            LevelState state = STATES.get(level);
            if (state != null) {
                resolveFullChunkLoad(state, event.getChunk().getPos());
                boolean unloadedPowerDevice = event.getChunk() instanceof LevelChunk chunk
                        && chunk.getBlockEntities().values().stream().anyMatch(be ->
                        be instanceof com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity
                                || be instanceof com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity
                                || be instanceof com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmBlockEntity
                                || be instanceof com.juicyslew.moonstation14.ms14.doors.AirlockBlockEntity
                                || be instanceof com.juicyslew.moonstation14.ms14.disposal.unit.DisposalUnitBlockEntity);
                state.pending.remove(event.getChunk().getPos());
                state.validation.remove(event.getChunk().getPos());
                state.indexed.remove(event.getChunk().getPos());
                state.lampChoices.keySet().removeIf(pos -> new ChunkPos(pos).equals(event.getChunk().getPos()));
                long revision = state.graph.revision();
                state.graph.removeChunk(event.getChunk().getPos());
                state.graph.setChunkLoaded(event.getChunk().getPos(), false);
                if (state.graph.revision() != revision || unloadedPowerDevice) state.topologyGeneration++;
            }
        }
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        noteBlockChange(event.getLevel(), event.getPos());
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        noteBlockChange(event.getLevel(), event.getPos());
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onNeighborNotified(BlockEvent.NeighborNotifyEvent event) {
        noteBlockChange(event.getLevel(), event.getPos());
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onPostServerTick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            LevelState state = STATES.get(level);
            if (state == null) continue;
            // Block events are hints only: direct setBlock(..., 3) callers do not all
            // emit them. Periodic validation rotates independently from fail-closed
            // mutation work, and never marks the graph unknown by itself.
            int refreshed = 0;
            var deferredRetry = new java.util.ArrayList<ChunkPos>();
            while (refreshed < CHUNK_REFRESHES_PER_TICK
                    && (!state.pending.isEmpty() || !state.validation.isEmpty())) {
                ChunkPos pos;
                boolean mutation = !state.pending.isEmpty();
                if (mutation) {
                    pos = state.pending.iterator().next();
                    state.validation.remove(pos);
                } else {
                    pos = state.validation.poll();
                }
                if (!(level.getChunkSource().getChunk(pos.x, pos.z, ChunkStatus.FULL, false) instanceof LevelChunk chunk)) {
                    if (state.deferredLoads.contains(pos)) {
                        deferredRetry.add(pos);
                        refreshed++;
                        continue;
                    }
                    state.pending.remove(pos);
                    state.validation.remove(pos);
                    state.indexed.remove(pos);
                    long revision = state.graph.revision();
                    state.graph.removeChunk(pos);
                    state.graph.setChunkLoaded(pos, false);
                    if (state.graph.revision() != revision) state.topologyGeneration++;
                    refreshed++;
                    continue;
                }
                resolveFullChunkLoad(state, pos);
                state.pending.remove(pos);
                if (state.graph.setChunkLoaded(pos, true)) state.topologyGeneration++;
                CableChunkData data = chunk.getExistingDataOrNull(ModDataAttachments.POWER_CABLE_CHUNK.get());
                var nodes = new java.util.ArrayList<CableFaceNode>();
                if (data != null) {
                    for (CableChunkData.Record record : data.snapshot()) {
                        BlockPos host = new BlockPos(pos.getMinBlockX() + record.x(), record.y(),
                                pos.getMinBlockZ() + record.z());
                        if (CableStorage.validateGraphRecord(level, chunk, host, record.face(), record.tier(), record.host())) {
                            nodes.add(new CableFaceNode(host, record.face(), record.tier()));
                        }
                    }
                }
                java.util.List<CableFaceNode> oldNodes = state.indexed.get(pos);
                if (oldNodes == null || !oldNodes.equals(nodes)) {
                    state.indexed.put(pos, java.util.List.copyOf(nodes));
                    state.graph.replaceChunk(pos, nodes);
                    if (!nodes.isEmpty() || oldNodes != null) state.topologyGeneration++;
                }
                state.validation.schedule(pos);
                refreshed++;
            }
            for (ChunkPos pos : deferredRetry) state.validation.schedule(pos);
            state.graph.process(GRAPH_NODES_PER_TICK);
        }
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) STATES.remove(level);
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) { STATES.clear(); }

    private static LevelState state(ServerLevel level) { return STATES.computeIfAbsent(level, ignored -> new LevelState()); }

    static void deferFullChunkLoad(LevelState state, ChunkPos pos) {
        state.deferredLoads.add(pos);
        state.validation.schedule(pos);
    }

    static void resolveFullChunkLoad(LevelState state, ChunkPos pos) { state.deferredLoads.remove(pos); }

    private static void noteBlockChange(net.minecraft.world.level.LevelAccessor accessor, BlockPos pos) {
        if (!(accessor instanceof ServerLevel level)) return;
        var chunk = level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4,
                ChunkStatus.FULL, false);
        if (!(chunk instanceof LevelChunk levelChunk)) return;
        CableChunkData data = levelChunk.getExistingDataOrNull(ModDataAttachments.POWER_CABLE_CHUNK.get());
        if (hasCableHostRecord(data, pos))
            noteChanged(level, levelChunk.getPos());
    }

    static boolean hasCableHostRecord(CableChunkData data, BlockPos pos) {
        if (data == null) return false;
        int x = pos.getX() & 15;
        int y = pos.getY();
        int z = pos.getZ() & 15;
        for (Direction face : Direction.values())
            for (com.juicyslew.moonstation14.ms14.power.cable.CableTier tier
                    : com.juicyslew.moonstation14.ms14.power.cable.CableTier.values())
                if (data.contains(x, y, z, face, tier)) return true;
        return false;
    }

    static final class LevelState {
        final LoadedPowerGraph graph = new LoadedPowerGraph();
        final Set<ChunkPos> pending = new LinkedHashSet<>();
        final Set<ChunkPos> deferredLoads = new LinkedHashSet<>();
        final ChunkValidationScheduler validation = new ChunkValidationScheduler();
        final Map<ChunkPos, java.util.List<CableFaceNode>> indexed = new java.util.LinkedHashMap<>();
        final Map<BlockPos, CachedLampNode> lampChoices = new java.util.HashMap<>();
        long topologyGeneration;
    }

    /** Diagnostic details are appended to the existing snapshot without forcing world queries. */
    private record CachedLampNode(long revision, int radius, CableFaceNode node) { }
}

