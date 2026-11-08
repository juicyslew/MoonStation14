package com.juicyslew.moonstation14.ms14.power.graph;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.power.cable.CableChunkData;
import com.juicyslew.moonstation14.ms14.power.cable.CableStorage;
import com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode;
import com.juicyslew.moonstation14.ms14.power.topology.DevicePort;
import com.juicyslew.moonstation14.ms14.power.topology.PowerTopology;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
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
            long before = state.topologyGeneration++;
            state.pending.add(chunk);
            trace(state, "noteChanged", chunk, before, state.topologyGeneration,
                    state.traceLeases.get() == 0 ? "" : callerFrames());
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
                + ",visited=" + progress.visitedNodes() + "}" + traceSnapshot(state);
    }

    /** Read-only receiver-footprint diagnostics; unlike receiver lookups this never reconciles or loads chunks. */
    public static String diagnosticSnapshot(ServerLevel level,BlockPos center,int radius,
                                             com.juicyslew.moonstation14.ms14.power.cable.CableTier tier) {
        if(center==null||radius<0||radius>16||tier==null)return "receiverGraph{invalid-query}";
        LevelState state=STATES.get(level);
        if(state==null)return "receiverGraph{uninitialized,center="+center.toShortString()+",radius="+radius+"}";
        int minChunkX=(center.getX()-radius)>>4,maxChunkX=(center.getX()+radius)>>4;
        int minChunkZ=(center.getZ()-radius)>>4,maxChunkZ=(center.getZ()+radius)>>4;
        java.util.List<String> footprint=new java.util.ArrayList<>();
        for(int cx=minChunkX;cx<=maxChunkX;cx++)for(int cz=minChunkZ;cz<=maxChunkZ;cz++){
            ChunkPos chunk=new ChunkPos(cx,cz);
            boolean full=fullChunk(level,cx,cz)!=null;
            footprint.add(chunk.toString()+"{full="+full+",graphLoaded="+state.graph.isChunkLoaded(chunk)
                    +",pending="+state.pending.contains(chunk)+"}");
        }
        java.util.List<CableFaceNode> nearby=state.pending.isEmpty()
                ?state.graph.nodesNear(center,radius,tier):null;
        String nodes=nearby==null?"unknown":nearby.stream().limit(8)
                .map(node->node.host().toShortString()+"/"+node.face()+"="+state.graph.state(node)).toList().toString()
                +" count="+nearby.size();
        return "receiverGraph{center="+center.toShortString()+",tier="+tier+",radius="+radius
                +",generation="+state.topologyGeneration+",pendingChunks="+state.pending.size()
                +",graphDirty="+state.graph.isDirty()+",graphIndexed="+state.graph.buildProgress().indexed()
                +",footprint="+footprint+",nearbyNodes="+nodes+"}" + traceSnapshot(state);
    }

    public static LoadedPowerGraph.NodeState state(ServerLevel level, CableFaceNode node) {
        LevelState state = STATES.get(level);
        if (state == null || !state.pending.isEmpty())
            return new LoadedPowerGraph.NodeState(LoadedPowerGraph.Knowledge.UNKNOWN, -1);
        LoadedPowerGraph.NodeState answer = state.graph.state(node);
        if (answer.knowledge() != LoadedPowerGraph.Knowledge.UNKNOWN) return answer;
        // A component can have a distant unloaded frontier absent from the loaded
        // index. Probe a bounded rotating graph-owned batch; normal ticks reconcile it.
        requestMissingFrontier(state, node, pos -> fullChunk(level, pos.x, pos.z) != null);
        return new LoadedPowerGraph.NodeState(LoadedPowerGraph.Knowledge.UNKNOWN, answer.componentId());
    }

    /** Canonical read-only device-port lookup; preserves the graph's UNKNOWN semantics. */
    public static LoadedPowerGraph.NodeState state(ServerLevel level, DevicePort port) {
        if (port == null) return new LoadedPowerGraph.NodeState(LoadedPowerGraph.Knowledge.UNKNOWN, -1);
        BlockPos device = port.device();
        if (!prepareLoadedFootprint(level, device, 1))
            return new LoadedPowerGraph.NodeState(LoadedPowerGraph.Knowledge.UNKNOWN, -1);
        LevelState levelState = STATES.get(level);
        if (levelState == null || !levelState.pending.isEmpty())
            return new LoadedPowerGraph.NodeState(LoadedPowerGraph.Knowledge.UNKNOWN, -1);
        LoadedPowerGraph.NodeState answer = levelState.graph.state(port);
        if (answer.knowledge() == LoadedPowerGraph.Knowledge.UNKNOWN && answer.componentId() >= 0) {
            for (CableFaceNode contact : PowerTopology.deviceContacts(port)) {
                if (levelState.graph.state(contact).componentId() == answer.componentId()) {
                    requestMissingFrontier(levelState, contact, pos -> fullChunk(level, pos.x, pos.z) != null);
                    break;
                }
            }
        }
        return answer;
    }

    private static final int FOREGROUND_FRONTIER_PROBES = 8;

    private static void requestMissingFrontier(LevelState state, CableFaceNode node,
                                               java.util.function.Predicate<ChunkPos> isFull) {
        requestMissingFrontier(state, node, isFull, FOREGROUND_FRONTIER_PROBES);
    }

    static void requestMissingFrontier(LevelState state, CableFaceNode node,
                                       java.util.function.Predicate<ChunkPos> isFull, int probeLimit) {
        if (probeLimit < 1) throw new IllegalArgumentException("positive frontier probe limit required");
        for (ChunkPos pos : state.graph.missingFrontierBatch(node, probeLimit))
            admitFullFrontier(state, pos, isFull.test(pos));
    }

    /** Scoped opt-in for bounded per-level attribution; closing one fixture cannot disable another. */
    public static AutoCloseable acquireDiagnosticTrace(ServerLevel level) {
        return acquireDiagnosticTrace(STATES.computeIfAbsent(level, ignored -> new LevelState()));
    }

    static AutoCloseable acquireDiagnosticTrace(LevelState state) {
        state.traceLeases.incrementAndGet();
        return new TraceLease(state);
    }

    static String callerFrames() {
        return StackWalker.getInstance().walk(frames -> frames
                .filter(frame -> frame.getClassName().startsWith("com.juicyslew.moonstation14."))
                .filter(frame -> !frame.getClassName().equals(PowerGraphService.class.getName()))
                .limit(3).map(frame -> frame.getClassName() + "." + frame.getMethodName()
                        + ":" + frame.getLineNumber()).collect(java.util.stream.Collectors.joining(" <- ")));
    }

    private static void incrementGeneration(LevelState state, String reason, ChunkPos chunk, String details) {
        long before = state.topologyGeneration++;
        trace(state, reason, chunk, before, state.topologyGeneration, details);
    }

    private static void trace(LevelState state, String reason, ChunkPos chunk, long before, long after, String details) {
        if (state.traceLeases.get() == 0) return;
        if (state.trace.size() == 32) state.trace.removeFirst();
        state.trace.addLast("seq=" + (++state.traceSequence) + ",refresh=" + state.traceCycle
                + ",gen=" + before + "->" + after + ",reason=" + reason + ",chunk=" + chunk
                + (details.isEmpty() ? "" : "," + details));
    }

    private static String identity(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName() + "@"
                + Integer.toHexString(System.identityHashCode(value));
    }

    static String diagnosticTrace(LevelState state) { return traceSnapshot(state); }

    private static String traceSnapshot(LevelState state) {
        return state.trace.isEmpty() ? "" : ",events=" + state.trace;
    }

    static boolean admitFullFrontier(LevelState state, ChunkPos pos, boolean isFull) {
        if (!isFull || !state.pending.add(pos)) return false;
        long before = state.topologyGeneration++;
        trace(state, "frontier-admit", pos, before, state.topologyGeneration, "FULL=true");
        return true;
    }

    /** Read-only graph internals for focused runtime diagnostics; never loads chunks. */
    public static LoadedPowerGraph.NodeDiagnostics diagnostics(ServerLevel level, CableFaceNode node) {
        LevelState state = STATES.get(level);
        if (state == null) return new LoadedPowerGraph.NodeDiagnostics(
                new LoadedPowerGraph.NodeState(LoadedPowerGraph.Knowledge.UNKNOWN, -1),
                true, false, java.util.List.of());
        return state.graph.diagnostics(node);
    }

    /** Bounded read-only explanation of the receiver's stored component closure. */
    public static String componentDiagnosticSnapshot(ServerLevel level, CableFaceNode node) {
        LevelState state = STATES.get(level);
        if (state == null) return "componentGraph{uninitialized}";
        var detail = state.graph.componentDiagnostics(node, 4096, 4096, 16, 32);
        var missing = detail.missingFrontiers().stream().map(frontier -> frontier.chunk() + "{sources="
                + frontier.sourceCount() + ",samples=" + frontier.sources() + ",truncated="
                + frontier.truncated() + ",busSources=" + frontier.busSourceCount() + ",busSamples="
                + frontier.busSources() + ",busTruncated=" + frontier.busSourcesTruncated()
                + ",full=" + (fullChunk(level, frontier.chunk().x, frontier.chunk().z) != null)
                + ",graphLoaded=" + state.graph.isChunkLoaded(frontier.chunk()) + "}").toList();
        return "componentGraph{node=" + node + ",component=" + detail.state().componentId() + ",knowledge="
                + detail.state().knowledge() + ",membersObserved=" + detail.memberCount() + ",membersTruncated="
                + detail.membersTruncated() + ",missingChunksObserved=" + detail.missingChunkCount()
                + ",chunksTruncated=" + detail.chunksTruncated() + ",frontierEntriesExamined="
                + detail.frontierEntriesExamined() + ",frontierTruncated=" + detail.frontierTruncated() + ",frontiers="
                + missing + "}";
    }

    /** Bounded indexed lookup for lamp extension receivers. Null means graph knowledge is incomplete. */
    public static java.util.List<CableFaceNode> nodesNear(ServerLevel level, BlockPos center, int radius,
                                                          com.juicyslew.moonstation14.ms14.power.cable.CableTier tier) {
        LevelState state = STATES.get(level);
        if (state == null || !state.pending.isEmpty()) return null;
        if (!ensureLoadedFootprint(level, state, center, radius)) return null;
        return state.graph.nodesNear(center, radius, tier);
    }

    /** Reconcile a solve's receiver footprint without loading chunks. Call for every receiver before querying graph knowledge. */
    public static boolean prepareLoadedFootprint(ServerLevel level, BlockPos center, int radius) {
        LevelState state = STATES.get(level);
        if (state == null) return false;
        ensureLoadedFootprint(level, state, center, radius);
        return state.pending.isEmpty();
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
        return ensureLoadedFootprint(state, center, radius,
                pos -> fullChunk(level, pos.x, pos.z) != null);
    }

    /** Testable shared admission path; caller lookup must represent actual non-forcing FULL admission. */
    static boolean ensureLoadedFootprint(LevelState state, BlockPos center, int radius,
                                         java.util.function.Predicate<ChunkPos> fullAdmission) {
        if (center == null || radius < 0 || radius > 16) return false;
        int minChunkX = (center.getX() - radius) >> 4;
        int maxChunkX = (center.getX() + radius) >> 4;
        int minChunkZ = (center.getZ() - radius) >> 4;
        int maxChunkZ = (center.getZ() + radius) >> 4;
        boolean complete = true;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
            ChunkPos pos = new ChunkPos(cx, cz);
            if (state.graph.isChunkLoaded(pos)) continue;
            if (!fullAdmission.test(pos)) {
                complete = false;
                continue;
            }
            if (state.pending.add(pos)) {
                long before = state.topologyGeneration++;
                trace(state, "footprint-admit", pos, before, state.topologyGeneration, "FULL=true");
            }
            complete = false;
        }
        return complete;
    }

    /** Non-forcing admission predicate shared with bounded reconciliation: only FULL chunks count as loaded. */
    private static LevelChunk fullChunk(ServerLevel level, int x, int z) {
        return level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false) instanceof LevelChunk chunk
                ? chunk : null;
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
        // Load callbacks may run before FULL admission. Defer all bookkeeping to the
        // bounded post-tick FULL-only reconciler; never publish partial-load knowledge here.
        deferFullChunkLoad(state, pos);
        trace(state, "chunk-load-deferred", pos, state.topologyGeneration, state.topologyGeneration,
                "eventChunk=" + identity(event.getChunk()));
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            LevelState state = STATES.get(level);
            if (state != null) {
                ChunkPos pos = event.getChunk().getPos();
                boolean unloadedPowerDevice = event.getChunk() instanceof LevelChunk chunk
                        && chunk.getBlockEntities().values().stream().anyMatch(be ->
                        be instanceof com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity
                                || be instanceof com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity
                                || be instanceof com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmBlockEntity
                                || be instanceof com.juicyslew.moonstation14.ms14.doors.AirlockBlockEntity
                                || be instanceof com.juicyslew.moonstation14.ms14.disposal.unit.DisposalUnitBlockEntity);
                // Identity guard only: this lookup must not admit topology or query FULL
                // from inside unload notification; it protects a newer chunk incarnation.
                processChunkUnload(state, pos, level.getChunkSource().getChunkNow(pos.x, pos.z),
                        event.getChunk(), unloadedPowerDevice);
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

    // Publish topology work before PowerRuntime samples this post-tick phase.
    @net.neoforged.bus.api.SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPostServerTick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            LevelState state = STATES.get(level);
            if (state == null) continue;
            refreshQueuedChunks(state, pos -> readFullChunk(level, pos));
        }
    }

    /** The single bounded mutation/validation queue owner used by production and focused JVM regressions. */
    static int refreshQueuedChunks(LevelState state,
                                   java.util.function.Function<ChunkPos, ChunkContents> fullLookup) {
        state.traceCycle++;
        int refreshed = 0;
        var deferredRetry = new java.util.ArrayList<ChunkPos>();
        while (refreshed < CHUNK_REFRESHES_PER_TICK
                && (!state.pending.isEmpty() || !state.validation.isEmpty())) {
            ChunkPos pos;
            if (!state.pending.isEmpty()) {
                pos = state.pending.iterator().next();
                state.validation.remove(pos);
            } else {
                pos = state.validation.poll();
            }
            ChunkContents contents = fullLookup.apply(pos);
            if (contents == null) {
                trace(state, "unavailable-reconciliation", pos, state.topologyGeneration,
                        state.topologyGeneration, "deferred=" + state.deferredLoads.contains(pos));
                if (handleUnavailableChunk(state, pos)) deferredRetry.add(pos);
            } else {
                resolveFullChunkLoad(state, pos);
                state.pending.remove(pos);
                if (state.graph.setChunkLoaded(pos, true)) incrementGeneration(state, "full-boundary-admit", pos,
                        "FULL contents admitted");
                var nodes = contents.nodes();
                var ports = contents.ports();
                java.util.List<CableFaceNode> oldNodes = state.indexed.get(pos);
                java.util.List<DevicePort> oldPorts = state.indexedPorts.get(pos);
                if (oldNodes == null || !oldNodes.equals(nodes) || !java.util.Objects.equals(oldPorts, ports)) {
                    state.indexed.put(pos, java.util.List.copyOf(nodes));
                    state.indexedPorts.put(pos, java.util.List.copyOf(ports));
                    state.graph.replaceChunk(pos, nodes, ports);
                    if (!nodes.isEmpty() || !ports.isEmpty() || oldNodes != null || oldPorts != null)
                        incrementGeneration(state, "full-index-content-change", pos,
                                "nodes=" + (oldNodes == null ? "absent" : oldNodes.size() + "->" + nodes.size())
                                        + ",ports=" + (oldPorts == null ? "absent" : oldPorts.size() + "->" + ports.size()));
                }
                state.validation.schedule(pos);
            }
            refreshed++;
        }
        for (ChunkPos pos : deferredRetry) state.validation.schedule(pos);
        state.graph.process(GRAPH_NODES_PER_TICK);
        return refreshed;
    }

    private static ChunkContents readFullChunk(ServerLevel level, ChunkPos pos) {
        LevelChunk chunk = fullChunk(level, pos.x, pos.z);
        if (chunk == null) return null;
        CableChunkData data = chunk.getExistingDataOrNull(ModDataAttachments.POWER_CABLE_CHUNK.get());
        var nodes = new java.util.ArrayList<CableFaceNode>();
        if (data != null) {
            for (CableChunkData.Record record : data.snapshot()) {
                BlockPos host = new BlockPos(pos.getMinBlockX() + record.x(), record.y(),
                        pos.getMinBlockZ() + record.z());
                if (CableStorage.validateGraphRecord(level, chunk, host, record.face(), record.tier(), record.host()))
                    nodes.add(new CableFaceNode(host, record.face(), record.tier()));
            }
        }
        var ports = new java.util.ArrayList<DevicePort>();
        for (var be : chunk.getBlockEntities().values()) {
            if (be instanceof com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity powerDevice)
                ports.addAll(powerDevice.ports());
            else if (be instanceof com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntimeNode runtimeNode)
                ports.addAll(runtimeNode.powerRuntimePorts());
        }
        return new ChunkContents(nodes, ports);
    }

    record ChunkContents(java.util.List<CableFaceNode> nodes, java.util.List<DevicePort> ports) { }

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
        trace(state, "load-deferred", pos, state.topologyGeneration, state.topologyGeneration, "FULL admission postponed");
    }

    static void resolveFullChunkLoad(LevelState state, ChunkPos pos) { state.deferredLoads.remove(pos); }

    /** Drops stale loaded knowledge; an early load marker survives only as validation work, never pending work. */
    static boolean handleUnavailableChunk(LevelState state, ChunkPos pos) {
        state.pending.remove(pos);
        if (state.deferredLoads.contains(pos)) {
            // Keep/requeue the bounded deferred validation marker, but release the
            // mutation queue head so unrelated chunks can use this tick's budget.
            state.validation.remove(pos);
            evictUnavailableChunk(state, pos);
            return true;
        }
        state.validation.remove(pos);
        state.deferredLoads.remove(pos);
        evictUnavailableChunk(state, pos);
        return false;
    }

    private static void evictUnavailableChunk(LevelState state, ChunkPos pos) {
        state.indexed.remove(pos);
        state.indexedPorts.remove(pos);
        long revision = state.graph.revision();
        state.graph.removeChunk(pos);
        state.graph.setChunkLoaded(pos, false);
        if (state.graph.revision() != revision) incrementGeneration(state, "unavailable-eviction", pos,
                "revision=" + revision + "->" + state.graph.revision());
        trace(state, "unavailable-eviction", pos, state.topologyGeneration, state.topologyGeneration,
                "deferred=" + state.deferredLoads.contains(pos));
    }

    static boolean processChunkUnload(LevelState state, ChunkPos pos, Object current, Object unloading,
                                      boolean unloadedPowerDevice) {
        // An unload notification for an old chunk incarnation can arrive after a
        // replacement has already entered the FULL cache. It must not erase the
        // replacement's loaded-space knowledge or queued reconciliation.
        if (current != null && current != unloading) {
            trace(state, "chunk-unload-ignored-stale", pos, state.topologyGeneration, state.topologyGeneration,
                    "current=" + identity(current) + ",eventChunk=" + identity(unloading));
            return false;
        }
        resolveFullChunkLoad(state, pos);
        state.pending.remove(pos);
        state.validation.remove(pos);
        state.indexed.remove(pos);
        state.indexedPorts.remove(pos);
        state.lampChoices.keySet().removeIf(lampPos -> new ChunkPos(lampPos).equals(pos));
        long revision = state.graph.revision();
        state.graph.removeChunk(pos);
        state.graph.setChunkLoaded(pos, false);
        if (state.graph.revision() != revision || unloadedPowerDevice)
            incrementGeneration(state, "chunk-unload", pos,
                    "revision=" + revision + "->" + state.graph.revision() + ",device=" + unloadedPowerDevice);
        trace(state, "chunk-unload", pos, state.topologyGeneration, state.topologyGeneration,
                "current=" + identity(current) + ",eventChunk=" + identity(unloading));
        return true;
    }

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
        final Map<ChunkPos, java.util.List<DevicePort>> indexedPorts = new java.util.LinkedHashMap<>();
        final Map<BlockPos, CachedLampNode> lampChoices = new java.util.HashMap<>();
        final ArrayDeque<String> trace = new ArrayDeque<>(32);
        final java.util.concurrent.atomic.AtomicInteger traceLeases = new java.util.concurrent.atomic.AtomicInteger();
        long traceSequence;
        long traceCycle;
        long topologyGeneration;
    }

    private static final class TraceLease implements AutoCloseable {
        private final LevelState state;
        private final java.util.concurrent.atomic.AtomicBoolean closed = new java.util.concurrent.atomic.AtomicBoolean();
        private TraceLease(LevelState state) { this.state = state; }
        @Override public void close() {
            if (closed.compareAndSet(false, true)) state.traceLeases.updateAndGet(count -> Math.max(0, count - 1));
        }
    }

    /** Diagnostic details are appended to the existing snapshot without forcing world queries. */
    record CachedLampNode(long revision, int radius, CableFaceNode node) { }
}
