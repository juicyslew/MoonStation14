package com.juicyslew.moonstation14.ms14.power.graph;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.power.PowerSimulationGate;
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
        if (!PowerSimulationGate.isEnabled()) return;
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

    public static LoadedPowerGraph.NodeState state(ServerLevel level, CableFaceNode node) {
        if (!PowerSimulationGate.isEnabled())
            return new LoadedPowerGraph.NodeState(LoadedPowerGraph.Knowledge.UNKNOWN, -1);
        LevelState state = STATES.get(level);
        return state == null || !state.pending.isEmpty()
                ? new LoadedPowerGraph.NodeState(LoadedPowerGraph.Knowledge.UNKNOWN, -1)
                : state.graph.state(node);
    }

    /** Bounded indexed lookup for lamp extension receivers. Null means graph knowledge is incomplete. */
    public static java.util.List<CableFaceNode> nodesNear(ServerLevel level, BlockPos center, int radius,
                                                          com.juicyslew.moonstation14.ms14.power.cable.CableTier tier) {
        LevelState state = STATES.get(level);
        if (!PowerSimulationGate.isEnabled() || state == null || !state.pending.isEmpty()) return null;
        return state.graph.nodesNear(center, radius, tier);
    }

    /** Cached nearest-node choice; cache entries are invalidated by every graph revision. */
    public static CableFaceNode nearestLampNode(ServerLevel level, BlockPos center) {
        LevelState state = STATES.get(level);
        if (!PowerSimulationGate.isEnabled() || state == null || !state.pending.isEmpty()) return null;
        long revision = state.graph.revision();
        CachedLampNode cached = state.lampChoices.get(center);
        if (cached != null && cached.revision == revision) return cached.node;
        java.util.List<CableFaceNode> nearby = state.graph.nodesNear(center, 3,
                com.juicyslew.moonstation14.ms14.power.cable.CableTier.APC);
        if (nearby == null) return null;
        CableFaceNode nearest = nearestKnownLampNode(state.graph, center, nearby);
        state.lampChoices.put(center.immutable(), new CachedLampNode(revision, nearest));
        return nearest;
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
        if (PowerSimulationGate.isEnabled() && event.getLevel() instanceof ServerLevel level) {
            LevelState state = state(level);
            state.topologyGeneration++;
            state.pending.add(event.getChunk().getPos());
            state.graph.setChunkLoaded(event.getChunk().getPos(), true);
            state.graph.invalidate();
        }
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (!PowerSimulationGate.isEnabled()) return;
        if (event.getLevel() instanceof ServerLevel level) {
            LevelState state = STATES.get(level);
            if (state != null) {
                state.topologyGeneration++;
                state.pending.remove(event.getChunk().getPos());
                state.validation.remove(event.getChunk().getPos());
                state.indexed.remove(event.getChunk().getPos());
                state.lampChoices.keySet().removeIf(pos -> new ChunkPos(pos).equals(event.getChunk().getPos()));
                state.graph.removeChunk(event.getChunk().getPos());
                state.graph.setChunkLoaded(event.getChunk().getPos(), false);
            }
        }
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) { noteBlockChange(event.getLevel(), event.getPos()); }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onBlockBroken(BlockEvent.BreakEvent event) { noteBlockChange(event.getLevel(), event.getPos()); }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onNeighborNotified(BlockEvent.NeighborNotifyEvent event) {
        noteBlockChange(event.getLevel(), event.getPos());
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onPostServerTick(ServerTickEvent.Post event) {
        if (!PowerSimulationGate.isEnabled()) return;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            LevelState state = STATES.get(level);
            if (state == null) continue;
            // Block events are hints only: direct setBlock(..., 3) callers do not all
            // emit them. Periodic validation rotates independently from fail-closed
            // mutation work, and never marks the graph unknown by itself.
            int refreshed = 0;
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
                    state.pending.remove(pos);
                    state.validation.remove(pos);
                    state.indexed.remove(pos);
                    state.graph.removeChunk(pos);
                    state.graph.setChunkLoaded(pos, false);
                    refreshed++;
                    continue;
                }
                state.pending.remove(pos);
                state.graph.setChunkLoaded(pos, true);
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
                    state.topologyGeneration++;
                }
                state.validation.schedule(pos);
                refreshed++;
            }
            state.graph.process(GRAPH_NODES_PER_TICK);
        }
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) STATES.remove(level);
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) { STATES.clear(); }

    public static void onSimulationDisabled() { STATES.clear(); }

    private static LevelState state(ServerLevel level) { return STATES.computeIfAbsent(level, ignored -> new LevelState()); }

    private static void noteBlockChange(net.minecraft.world.level.LevelAccessor accessor, BlockPos pos) {
        if (!PowerSimulationGate.isEnabled()) return;
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

    private static final class LevelState {
        final LoadedPowerGraph graph = new LoadedPowerGraph();
        final Set<ChunkPos> pending = new LinkedHashSet<>();
        final ChunkValidationScheduler validation = new ChunkValidationScheduler();
        final Map<ChunkPos, java.util.List<CableFaceNode>> indexed = new java.util.LinkedHashMap<>();
        final Map<BlockPos, CachedLampNode> lampChoices = new java.util.HashMap<>();
        long topologyGeneration;
    }
    private record CachedLampNode(long revision, CableFaceNode node) { }
}
