package com.juicyslew.moonstation14.ms14.power.graph;

import com.juicyslew.moonstation14.ms14.power.cable.CableChunkData;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode;
import com.juicyslew.moonstation14.ms14.power.topology.DevicePort;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class PowerGraphTest {
    @Test void diagnosticTraceIsOptInBoundedSharedAndReadOnly() throws Exception {
        PowerGraphService.LevelState state = new PowerGraphService.LevelState();
        ChunkPos first = new ChunkPos(40, 0);
        assertTrue(PowerGraphService.admitFullFrontier(state, first, true));
        assertEquals("", PowerGraphService.diagnosticTrace(state), "disabled tracing has no events");
        long generation = state.topologyGeneration;
        int queued = state.pending.size();

        AutoCloseable firstLease = PowerGraphService.acquireDiagnosticTrace(state);
        AutoCloseable secondLease = PowerGraphService.acquireDiagnosticTrace(state);
        firstLease.close();
        firstLease.close();
        for (int i = 0; i < 40; i++)
            assertTrue(PowerGraphService.admitFullFrontier(state, new ChunkPos(50 + i, 0), true));
        assertFalse(PowerGraphService.ensureLoadedFootprint(state, new BlockPos(160, 40, 32), 0, ignored -> true));
        String snapshot = PowerGraphService.diagnosticTrace(state);
        assertTrue(snapshot.contains("reason=frontier-admit"));
        assertTrue(snapshot.contains("reason=footprint-admit"));
        assertFalse(snapshot.contains("seq=9,"), "ring evicts oldest events");
        assertTrue(snapshot.contains("seq=10,"));
        assertEquals(generation + 41, state.topologyGeneration);
        assertEquals(queued + 41, state.pending.size());

        long beforeRead = state.topologyGeneration;
        int pendingBeforeRead = state.pending.size();
        assertEquals(snapshot, PowerGraphService.diagnosticTrace(state));
        assertEquals(beforeRead, state.topologyGeneration);
        assertEquals(pendingBeforeRead, state.pending.size());
        secondLease.close();
        secondLease.close();
        PowerGraphService.admitFullFrontier(state, new ChunkPos(100, 0), true);
        assertEquals(snapshot, PowerGraphService.diagnosticTrace(state), "closed leases stop recording");

        PowerGraphService.LevelState enabled = new PowerGraphService.LevelState();
        PowerGraphService.LevelState disabled = new PowerGraphService.LevelState();
        AutoCloseable enabledLease = PowerGraphService.acquireDiagnosticTrace(enabled);
        for (int i = 0; i < 12; i++) {
            ChunkPos pos = new ChunkPos(200 + i, 0);
            PowerGraphService.admitFullFrontier(enabled, pos, true);
            PowerGraphService.admitFullFrontier(disabled, pos, true);
        }
        assertEquals(disabled.topologyGeneration, enabled.topologyGeneration);
        assertEquals(disabled.pending, enabled.pending);
        assertEquals(disabled.graph.revision(), enabled.graph.revision());
        enabledLease.close();
    }

    @Test void callerFrameCaptureSkipsOnlyPowerGraphServiceFramesAndPreservesExternalOrder() {
        String frames = traceCallerFrameOuter();
        assertFalse(frames.contains(PowerGraphService.class.getName()));
        assertTrue(frames.contains("traceCallerFrameInner"));
        assertTrue(frames.contains("traceCallerFrameOuter"));
        assertTrue(frames.indexOf("traceCallerFrameInner") < frames.indexOf("traceCallerFrameOuter"), frames);
    }

    private static String traceCallerFrameOuter() { return traceCallerFrameInner(); }
    private static String traceCallerFrameInner() { return PowerGraphService.callerFrames(); }

    @Test void diagnosticTraceAttributesDeferredUnavailableAndFullReconciliation() throws Exception {
        PowerGraphService.LevelState state = new PowerGraphService.LevelState();
        ChunkPos pos = new ChunkPos(7, 0);
        CableFaceNode node = new CableFaceNode(new BlockPos(112, 40, 8), Direction.UP, CableTier.HV);
        ChunkPos neighborChunk = new ChunkPos(6, 0);
        CableFaceNode neighbor = new CableFaceNode(new BlockPos(111, 40, 8), Direction.UP, CableTier.HV);
        state.graph.replaceChunk(neighborChunk, List.of(neighbor));
        state.graph.setChunkLoaded(neighborChunk, true);
        finish(state.graph, 256);
        assertEquals(LoadedPowerGraph.Knowledge.UNKNOWN, state.graph.state(neighbor).knowledge(),
                "the neighboring cable's possible continuation makes the unavailable chunk relevant");
        AutoCloseable lease = PowerGraphService.acquireDiagnosticTrace(state);
        PowerGraphService.deferFullChunkLoad(state, pos);
        state.pending.add(pos);
        assertEquals(1, PowerGraphService.refreshQueuedChunks(state, ignored -> null));
        String unavailable = PowerGraphService.diagnosticTrace(state);
        assertTrue(unavailable.contains("reason=load-deferred"));
        assertTrue(unavailable.contains("reason=unavailable-reconciliation"));
        assertTrue(unavailable.contains("reason=unavailable-eviction"));
        assertTrue(state.deferredLoads.contains(pos));
        assertFalse(state.pending.contains(pos));
        assertEquals(LoadedPowerGraph.Knowledge.UNKNOWN, state.graph.state(neighbor).knowledge());

        long beforeFull = state.topologyGeneration;
        List<ChunkPos> fullAttempts = new ArrayList<>();
        assertEquals(PowerGraphService.CHUNK_REFRESHES_PER_TICK, PowerGraphService.refreshQueuedChunks(state,
                queried -> {
                    fullAttempts.add(queried);
                    return new PowerGraphService.ChunkContents(List.of(node), List.of());
                }));
        assertEquals(List.of(pos, pos, pos, pos), fullAttempts,
                "existing bounded validation may refresh the same available chunk repeatedly within its batch");
        assertEquals(beforeFull + 2, state.topologyGeneration,
                "only first boundary admission and first indexed contents change generation");
        String admitted = PowerGraphService.diagnosticTrace(state);
        assertTrue(admitted.contains("reason=full-boundary-admit"));
        assertTrue(admitted.contains("reason=full-index-content-change"));
        assertEquals(1, admitted.split("reason=full-boundary-admit", -1).length - 1);
        assertEquals(1, admitted.split("reason=full-index-content-change", -1).length - 1);
        assertFalse(state.deferredLoads.contains(pos));
        assertEquals(LoadedPowerGraph.Knowledge.KNOWN, state.graph.state(node).knowledge());
        assertFalse(PowerGraphService.processChunkUnload(state, pos, new Object(), new Object(), true));
        assertTrue(PowerGraphService.diagnosticTrace(state).contains("reason=chunk-unload-ignored-stale"));
        assertTrue(PowerGraphService.processChunkUnload(state, pos, null, new Object(), false));
        assertTrue(PowerGraphService.diagnosticTrace(state).contains("reason=chunk-unload,"));
        lease.close();
    }

    @Test void preFullFootprintQueriesDoNotQueueOrOscillateAndFullAdmissionReconcilesOnce() {
        PowerGraphService.LevelState state = new PowerGraphService.LevelState();
        ChunkPos chunk = new ChunkPos(0, 0);
        BlockPos center = new BlockPos(8, 8, 8);
        List<CableFaceNode> nodes = new ArrayList<>();
        for (int i = 0; i < 58; i++)
            nodes.add(new CableFaceNode(new BlockPos(8, 20 + i * 2, 8), Direction.UP, CableTier.HV));

        // A cache-resident currently-loading chunk is not admitted FULL. Repeated
        // receiver preparation/query cycles must stay unknown without bookkeeping churn.
        for (int i = 0; i < 12; i++) {
            assertFalse(PowerGraphService.ensureLoadedFootprint(state, center, 0, ignored -> false));
            assertTrue(state.pending.isEmpty());
            assertEquals(0, state.topologyGeneration);
            assertFalse(state.graph.isChunkLoaded(chunk));
            assertEquals(LoadedPowerGraph.Knowledge.UNKNOWN, state.graph.state(nodes.getFirst()).knowledge());
        }

        assertFalse(PowerGraphService.ensureLoadedFootprint(state, center, 0, ignored -> true));
        assertEquals(Set.of(chunk), state.pending);
        assertEquals(1, state.topologyGeneration);
        assertFalse(PowerGraphService.ensureLoadedFootprint(state, center, 0, ignored -> true));
        assertEquals(1, state.topologyGeneration, "repeated admission cannot requeue an admitted chunk");

        // Exercise the existing owner state/queue/graph reconciliation semantics.
        state.graph.setChunkLoaded(chunk, true);
        state.graph.replaceChunk(chunk, nodes);
        state.pending.remove(chunk);
        finish(state.graph, 256);
        assertEquals(LoadedPowerGraph.Knowledge.KNOWN, state.graph.state(nodes.getFirst()).knowledge());
        state.graph.removeChunk(chunk);
        state.graph.setChunkLoaded(chunk, false);
        assertEquals(LoadedPowerGraph.Knowledge.UNKNOWN, state.graph.state(nodes.getFirst()).knowledge());
    }

    @Test void deferredNonFullMutationReleasesQueueHeadAndDoesNotStarveFullMutation() {
        PowerGraphService.LevelState state = new PowerGraphService.LevelState();
        ChunkPos early = new ChunkPos(1, 0);
        List<ChunkPos> initialReady = List.of(new ChunkPos(2, 0), new ChunkPos(3, 0),
                new ChunkPos(4, 0), new ChunkPos(5, 0), new ChunkPos(6, 0));
        CableFaceNode stale = new CableFaceNode(new BlockPos(17, 40, 8), Direction.UP, CableTier.HV);
        DevicePort stalePort = new DevicePort(new BlockPos(18, 40, 8), Direction.NORTH, CableTier.HV);
        state.indexed.put(early, List.of(stale));
        state.indexedPorts.put(early, List.of(stalePort));
        state.graph.replaceChunk(early, List.of(stale), List.of(stalePort));
        state.graph.setChunkLoaded(early, true);
        finish(state.graph, 256);
        state.pending.add(early); // real device mutation predates early load callback
        state.pending.addAll(initialReady);
        PowerGraphService.deferFullChunkLoad(state, early);

        var contents = new java.util.HashMap<ChunkPos, PowerGraphService.ChunkContents>();
        for (ChunkPos pos : initialReady) {
            CableFaceNode node = new CableFaceNode(new BlockPos(pos.getMinBlockX() + 1, 40, 8), Direction.UP, CableTier.HV);
            contents.put(pos, new PowerGraphService.ChunkContents(List.of(node), List.of()));
        }
        java.util.Set<ChunkPos> unavailable = new java.util.HashSet<>(java.util.Set.of(early));
        List<ChunkPos> firstBatch = new ArrayList<>();
        java.util.function.Function<ChunkPos, PowerGraphService.ChunkContents> lookup = pos ->
                lookupContents(pos, unavailable, contents, firstBatch);

        assertEquals(PowerGraphService.CHUNK_REFRESHES_PER_TICK,
                PowerGraphService.refreshQueuedChunks(state, lookup));
        assertEquals(List.of(early, initialReady.get(0), initialReady.get(1), initialReady.get(2)), firstBatch);
        assertFalse(state.pending.contains(early));
        assertEquals(initialReady.get(3), state.pending.iterator().next(), "next mutation remains past budget");
        assertTrue(state.indexed.get(initialReady.get(0)).size() == 1, "ready work was actually indexed in this batch");
        assertFalse(state.indexed.containsKey(early));
        assertFalse(state.indexedPorts.containsKey(early));
        assertFalse(state.graph.isChunkLoaded(early));
        assertEquals(LoadedPowerGraph.Knowledge.UNKNOWN, state.graph.state(stale).knowledge());
        assertTrue(state.deferredLoads.contains(early));
        assertTrue(state.pending.isEmpty() || !state.pending.contains(early));
        assertFalse(state.validation.isEmpty(), "deferred marker is queued for retry after the batch");

        // Sustained unavailable retries cannot prevent fresh mutation work from admission.
        for (int tick = 0; tick < 100; tick++) {
            ChunkPos work = new ChunkPos(20 + tick, 0);
            CableFaceNode node = new CableFaceNode(new BlockPos(work.getMinBlockX() + 1, 40, 8), Direction.UP, CableTier.HV);
            contents.put(work, new PowerGraphService.ChunkContents(List.of(node), List.of()));
            state.pending.add(work);
            assertTrue(PowerGraphService.refreshQueuedChunks(state, lookup) <= PowerGraphService.CHUNK_REFRESHES_PER_TICK);
            assertFalse(state.pending.contains(early));
            assertTrue(state.indexed.containsKey(work), "each later mutation makes progress despite deferred A");
            assertTrue(state.deferredLoads.contains(early));
            assertEquals(LoadedPowerGraph.Knowledge.UNKNOWN, state.graph.state(stale).knowledge());
        }

        // FULL arrival is discovered through the existing deferred validation queue,
        // and the same production batch applies its contents and resolves the marker.
        unavailable.remove(early);
        contents.put(early, new PowerGraphService.ChunkContents(List.of(stale), List.of(stalePort)));
        for (int tick = 0; tick < 100 && state.deferredLoads.contains(early); tick++)
            PowerGraphService.refreshQueuedChunks(state, lookup);
        assertFalse(state.deferredLoads.contains(early));
        assertTrue(state.graph.isChunkLoaded(early));
        assertEquals(List.of(stale), state.indexed.get(early));
        assertEquals(List.of(stalePort), state.indexedPorts.get(early));
        assertEquals(LoadedPowerGraph.Knowledge.KNOWN, state.graph.state(stale).knowledge());

        // Actual unload cleanup, rather than the unavailable retry path, retires an aborted marker.
        PowerGraphService.deferFullChunkLoad(state, early);
        assertTrue(PowerGraphService.processChunkUnload(state, early, null, new Object(), false));
        assertFalse(state.deferredLoads.contains(early));
    }

    private static PowerGraphService.ChunkContents lookupContents(
            ChunkPos pos, java.util.Set<ChunkPos> unavailable,
            java.util.Map<ChunkPos, PowerGraphService.ChunkContents> contents, List<ChunkPos> observed) {
        observed.add(pos);
        return unavailable.contains(pos) ? null : contents.get(pos);
    }

    @Test void devicePortResolvesOneComponentAcrossPhysicalContactsAndFailsClosedOnSplit() {
        LoadedPowerGraph graph = new LoadedPowerGraph();
        ChunkPos chunk = new ChunkPos(0, 0);
        BlockPos device = new BlockPos(8, 8, 8);
        DevicePort port = new DevicePort(device, Direction.NORTH, CableTier.MV);
        CableFaceNode floor = new CableFaceNode(device.below(), Direction.UP, CableTier.MV);
        CableFaceNode north = new CableFaceNode(device.north(), Direction.SOUTH, CableTier.MV);
        assertTrue(com.juicyslew.moonstation14.ms14.power.topology.PowerTopology.cableEdge(floor, north).isPresent());
        CableFaceNode ceiling = new CableFaceNode(device.above(), Direction.DOWN, CableTier.MV);
        assertTrue(com.juicyslew.moonstation14.ms14.power.topology.PowerTopology.cableEdge(floor, ceiling).isEmpty());
        graph.replaceChunk(chunk, List.of(floor, ceiling));
        finish(graph, 16);
        assertNotEquals(graph.state(floor).componentId(), graph.state(ceiling).componentId());
        graph.replaceChunk(chunk, List.of(floor, ceiling), List.of(port));
        finish(graph, 16);
        assertEquals(LoadedPowerGraph.Knowledge.KNOWN, graph.state(port).knowledge());
        assertEquals(graph.state(floor).componentId(), graph.state(ceiling).componentId(), "a published port joins its physical contacts");
        graph.replaceChunk(chunk, List.of(floor, ceiling));
        finish(graph, 16);
        assertNotEquals(graph.state(floor).componentId(), graph.state(ceiling).componentId(), "removing the port removes the bus edge");

        CableFaceNode east = new CableFaceNode(device.east(), Direction.WEST, CableTier.MV);
        assertTrue(com.juicyslew.moonstation14.ms14.power.topology.PowerTopology.cableEdge(floor, east).isPresent());
        graph.replaceChunk(chunk, List.of(floor, east), List.of(port));
        finish(graph, 16);
        assertEquals(LoadedPowerGraph.Knowledge.KNOWN, graph.state(port).knowledge());

        DevicePort apc = new DevicePort(device, Direction.NORTH, CableTier.APC);
        graph.replaceChunk(chunk, List.of(new CableFaceNode(device.relative(Direction.NORTH), Direction.SOUTH, CableTier.APC)));
        finish(graph, 16);
        assertEquals(LoadedPowerGraph.Knowledge.KNOWN, graph.state(apc).knowledge());
    }

    @Test void deviceContactRemainsUnknownAcrossUnloadedNetworkFrontierUntilSpaceIsIndexed() {
        LoadedPowerGraph graph = new LoadedPowerGraph();
        BlockPos device = new BlockPos(15, 8, 8);
        CableFaceNode contact = new CableFaceNode(device.below(), Direction.UP, CableTier.HV);
        ChunkPos home = new ChunkPos(0, 0);
        ChunkPos frontier = new ChunkPos(1, 0);
        graph.replaceChunk(home, List.of(contact));
        graph.replaceChunk(frontier, List.of());
        finish(graph, 16);
        DevicePort port = new DevicePort(device, Direction.NORTH, CableTier.HV);
        assertEquals(LoadedPowerGraph.Knowledge.KNOWN, graph.state(port).knowledge());

        graph.removeChunk(frontier);
        finish(graph, 16);
        assertEquals(LoadedPowerGraph.Knowledge.UNKNOWN, graph.state(port).knowledge());

        graph.replaceChunk(frontier, List.of());
        finish(graph, 16);
        assertEquals(LoadedPowerGraph.Knowledge.KNOWN, graph.state(port).knowledge());
    }

    @Test void unloadingDevicePortChunkRemovesBusAndReloadRestoresLoadedKnowledge() {
        LoadedPowerGraph graph = new LoadedPowerGraph();
        BlockPos device = new BlockPos(15, 8, 8);
        DevicePort port = new DevicePort(device, Direction.NORTH, CableTier.HV);
        CableFaceNode contact = new CableFaceNode(device.east(), Direction.WEST, CableTier.HV);
        ChunkPos deviceChunk = new ChunkPos(0, 0);
        ChunkPos cableChunk = new ChunkPos(1, 0);
        graph.replaceChunk(deviceChunk, List.of(), List.of(port));
        graph.replaceChunk(cableChunk, List.of(contact));
        finish(graph, 16);
        assertEquals(LoadedPowerGraph.Knowledge.KNOWN, graph.state(port).knowledge());

        graph.removeChunk(deviceChunk);
        finish(graph, 16);
        assertEquals(LoadedPowerGraph.Knowledge.UNKNOWN, graph.state(contact).knowledge());

        graph.replaceChunk(deviceChunk, List.of(), List.of(port));
        finish(graph, 16);
        assertEquals(LoadedPowerGraph.Knowledge.KNOWN, graph.state(port).knowledge());
    }

    @Test void unknownAbsentChunkRemovalDoesNotRestartGraph() {
        LoadedPowerGraph graph = new LoadedPowerGraph();
        CableFaceNode cable = node(4, CableTier.HV);
        ChunkPos cableChunk = new ChunkPos(0, 0);
        graph.replaceChunk(cableChunk, List.of(cable));
        finish(graph, 16);
        long revision = graph.revision();

        graph.removeChunk(new ChunkPos(2, 0));

        assertFalse(graph.isDirty());
        assertEquals(revision, graph.revision());
        assertEquals(LoadedPowerGraph.Knowledge.UNKNOWN, graph.state(cable).knowledge());
    }

    @Test void loadedEmptyNeighborRemovalRestoresUnknownFrontier() {
        LoadedPowerGraph graph = new LoadedPowerGraph();
        ChunkPos cableChunk = new ChunkPos(0, 0);
        ChunkPos neighbor = new ChunkPos(1, 0);
        CableFaceNode cable = new CableFaceNode(new BlockPos(15, 0, 8), Direction.UP, CableTier.HV);
        graph.replaceChunk(cableChunk, List.of(cable));
        graph.replaceChunk(neighbor, List.of());
        finish(graph, 16);
        assertEquals(LoadedPowerGraph.Knowledge.KNOWN, graph.state(cable).knowledge());

        graph.removeChunk(neighbor);
        finish(graph, 16);
        assertEquals(LoadedPowerGraph.Knowledge.UNKNOWN, graph.state(cable).knowledge());

        graph.replaceChunk(neighbor, List.of());
        finish(graph, 16);
        assertEquals(LoadedPowerGraph.Knowledge.KNOWN, graph.state(cable).knowledge());
    }

    @Test void distantEmptyChunkChurnPreservesBoundedBuildProgressButCableEditRestartsIt() {
        LoadedPowerGraph graph = new LoadedPowerGraph();
        List<CableFaceNode> nodes = new ArrayList<>();
        for (int x = 0; x < 600; x++) nodes.add(node(x, CableTier.HV));
        ChunkPos cableChunk = new ChunkPos(0, 0);
        graph.replaceChunk(cableChunk, nodes);
        graph.process(256);
        var before = graph.buildProgress();
        assertTrue(before.dirty());

        ChunkPos distant = new ChunkPos(100, 100);
        assertFalse(graph.setChunkLoaded(distant, true));
        assertFalse(graph.setChunkLoaded(distant, false));
        var after = graph.buildProgress();
        assertEquals(before.revision(), after.revision());
        assertEquals(before.nodeCursor(), after.nodeCursor());
        assertEquals(before.visitedNodes(), after.visitedNodes());

        graph.replaceChunk(cableChunk, nodes.subList(0, nodes.size() - 1));
        assertTrue(graph.isDirty());
        assertTrue(graph.revision() > before.revision());
        assertEquals(0, graph.buildProgress().nodeCursor());
    }

    @Test void boundaryInterestIsTrackedBeforeComponentBfsVisitsNodes() {
        LoadedPowerGraph graph = new LoadedPowerGraph();
        List<CableFaceNode> nodes = new ArrayList<>();
        for (int index = 0; index < 300; index++)
            nodes.add(new CableFaceNode(new BlockPos(15, index * 3, 8), Direction.UP, CableTier.HV));
        graph.replaceChunk(new ChunkPos(0, 0), nodes);
        graph.process(256);
        var before = graph.buildProgress();
        assertFalse(before.indexed(), "component BFS has not started");

        ChunkPos neighbor = new ChunkPos(1, 0);
        assertTrue(graph.setChunkLoaded(neighbor, true), "indexed cable has a potential edge into the neighbor");
        long afterLoadRevision = graph.revision();
        assertEquals(before.nodeCursor(), graph.buildProgress().nodeCursor(), "boundary update preserves index work");
        assertTrue(graph.setChunkLoaded(neighbor, false));
        assertTrue(graph.revision() > afterLoadRevision, "unload also advances boundary freshness");

        finish(graph, 256);
        assertEquals(LoadedPowerGraph.Knowledge.UNKNOWN, graph.state(nodes.getFirst()).knowledge());
        assertTrue(graph.setChunkLoaded(neighbor, true));
        assertEquals(LoadedPowerGraph.Knowledge.KNOWN, graph.state(nodes.getFirst()).knowledge());
        assertTrue(graph.setChunkLoaded(neighbor, false));
        assertEquals(LoadedPowerGraph.Knowledge.UNKNOWN, graph.state(nodes.getFirst()).knowledge());
    }

    @Test void earlyChunkLoadIsDeferredUntilFullReconciliationWithoutMutationQueue() {
        PowerGraphService.LevelState state = new PowerGraphService.LevelState();
        ChunkPos pos = new ChunkPos(12, -4);

        PowerGraphService.deferFullChunkLoad(state, pos);
        assertTrue(state.deferredLoads.contains(pos));
        assertTrue(state.pending.isEmpty(), "pre-FULL event must not block topology readiness as a mutation");
        assertTrue(state.validation.poll().equals(pos), "deferred load uses bounded validation rotation");

        // The validation callback seeing this same position in FULL cache reconciles it.
        PowerGraphService.resolveFullChunkLoad(state, pos);
        assertFalse(state.deferredLoads.contains(pos));
    }

    @Test void blockEventFilterIgnoresRepeatedNonCablePositionsAndFindsCableHosts() {
        CableChunkData data = new CableChunkData();
        data.put(4, 8, 8, Direction.UP, CableTier.HV);
        BlockPos cableHost = new BlockPos(4, 8, 8);
        BlockPos ordinaryBlock = new BlockPos(5, 8, 8);

        for (int notification = 0; notification < 20; notification++)
            assertFalse(PowerGraphService.hasCableHostRecord(data, ordinaryBlock));
        assertTrue(PowerGraphService.hasCableHostRecord(data, cableHost));
        assertFalse(PowerGraphService.hasCableHostRecord(null, cableHost));
    }

    @Test void blockEventFilterProbesAllFacesAtSignedWorldCoordinates() {
        CableChunkData data = new CableChunkData();
        Direction[] faces = {Direction.DOWN, Direction.UP, Direction.NORTH,
                Direction.SOUTH, Direction.WEST, Direction.EAST};
        BlockPos positive = new BlockPos(20, 8, 24);
        BlockPos signed = new BlockPos(-12, -32, -8);

        for (Direction face : faces) data.put(4, 8, 8, face, CableTier.HV);
        data.put(4, -32, 8, Direction.EAST, CableTier.MV);

        assertTrue(PowerGraphService.hasCableHostRecord(data, positive));
        assertTrue(PowerGraphService.hasCableHostRecord(data, signed));
        assertFalse(PowerGraphService.hasCableHostRecord(data, new BlockPos(-11, -32, -8)));
        assertFalse(PowerGraphService.hasCableHostRecord(data, new BlockPos(-12, -31, -8)));
    }

    @Test void blockEventFilterFindsAnyTierOnAnAmbiguousSharedFace() {
        CableChunkData data = new CableChunkData();
        BlockPos sharedHost = new BlockPos(20, 8, 24);
        assertTrue(data.put(4, 8, 8, Direction.UP, CableTier.HV));
        assertTrue(data.put(4, 8, 8, Direction.UP, CableTier.MV));
        assertTrue(data.put(4, 8, 8, Direction.UP, CableTier.APC));
        assertTrue(PowerGraphService.hasCableHostRecord(data, sharedHost));
    }

    @Test void sameFaceTierNodesRemainSeparateElectricalNetworks() {
        LoadedPowerGraph graph = new LoadedPowerGraph();
        BlockPos sharedHost = new BlockPos(4, 8, 8);
        CableFaceNode hv = new CableFaceNode(sharedHost, Direction.UP, CableTier.HV);
        CableFaceNode mv = new CableFaceNode(sharedHost, Direction.UP, CableTier.MV);
        CableFaceNode apc = new CableFaceNode(sharedHost, Direction.UP, CableTier.APC);
        graph.replaceChunk(new ChunkPos(sharedHost), List.of(hv, mv, apc));
        finish(graph, 16);
        assertNotEquals(graph.state(hv).componentId(), graph.state(mv).componentId());
        assertNotEquals(graph.state(hv).componentId(), graph.state(apc).componentId());
        assertNotEquals(graph.state(mv).componentId(), graph.state(apc).componentId());
    }

    @Test void chainAndCycleBuildInBoundedFairSlicesAndRemainTierSeparated() {
        LoadedPowerGraph graph = new LoadedPowerGraph();
        List<CableFaceNode> chain = new ArrayList<>();
        for (int x = 0; x < 600; x++) chain.add(node(x, CableTier.HV));
        graph.replaceChunk("station", chain);
        assertEquals(LoadedPowerGraph.Knowledge.UNKNOWN, graph.state(chain.get(0)).knowledge());
        int total = 0;
        while (graph.isDirty()) {
            int work = graph.process(17);
            assertTrue(work <= 17);
            total += work;
        }
        assertEquals(1200, total);
        assertEquals(graph.state(chain.get(0)).componentId(), graph.state(chain.get(599)).componentId());
        assertEquals(LoadedPowerGraph.Knowledge.UNKNOWN, graph.state(node(100, CableTier.MV)).knowledge());

        List<CableFaceNode> loop = List.of(node(0, CableTier.APC), node(1, CableTier.APC),
                new CableFaceNode(new BlockPos(1, 0, 1), Direction.UP, CableTier.APC),
                new CableFaceNode(new BlockPos(0, 0, 1), Direction.UP, CableTier.APC));
        graph.replaceChunk("cycle", loop);
        finish(graph, 100);
        assertEquals(graph.state(loop.get(0)).componentId(), graph.state(loop.get(3)).componentId());
    }

    @Test void editsAndChunkSeamsFailClosedThenSplitAndMergeRebuild() {
        LoadedPowerGraph graph = new LoadedPowerGraph();
        CableFaceNode left = node(15, CableTier.HV);
        CableFaceNode right = node(16, CableTier.HV);
        ChunkPos leftChunk = new ChunkPos(0, 0);
        ChunkPos rightChunk = new ChunkPos(1, 0);
        graph.replaceChunk(leftChunk, List.of(left));
        graph.replaceChunk(rightChunk, List.of(right));
        finish(graph, 8);
        assertEquals(graph.state(left).componentId(), graph.state(right).componentId());

        graph.replaceChunk(rightChunk, List.of());
        assertEquals(LoadedPowerGraph.Knowledge.UNKNOWN, graph.state(left).knowledge());
        finish(graph, 8);
        assertNotEquals(graph.state(left).componentId(), graph.state(right).componentId());

        graph.replaceChunk(rightChunk, List.of(right));
        finish(graph, 8);
        assertEquals(graph.state(left).componentId(), graph.state(right).componentId());
        graph.removeChunk(rightChunk);
        assertEquals(LoadedPowerGraph.Knowledge.UNKNOWN, graph.state(left).knowledge());
        finish(graph, 8);
        assertEquals(LoadedPowerGraph.Knowledge.UNKNOWN, graph.state(right).knowledge());
    }

    @Test void repeatedBenignRefreshesDoNotInvalidateUnchangedCableHosts() {
        LoadedPowerGraph graph = new LoadedPowerGraph();
        ChunkPos chunk = new ChunkPos(0, 0);
        CableFaceNode cableHost = new CableFaceNode(new BlockPos(4, 8, 8), Direction.UP, CableTier.HV);
        graph.replaceChunk(chunk, List.of(cableHost));
        finish(graph, 8);
        int component = graph.state(cableHost).componentId();

        // Ordinary non-cable notifications and host state changes (such as a tile
        // finish property update) refresh the attachment to the same cable snapshot.
        for (int refresh = 0; refresh < 20; refresh++) {
            graph.replaceChunk(chunk, List.of(cableHost));
            assertFalse(graph.isDirty());
            assertEquals(LoadedPowerGraph.Knowledge.KNOWN, graph.state(cableHost).knowledge());
            assertEquals(component, graph.state(cableHost).componentId());
        }

        // A real cable-node difference still invalidates every cached answer.
        graph.replaceChunk(chunk, List.of());
        assertTrue(graph.isDirty());
        assertEquals(LoadedPowerGraph.Knowledge.UNKNOWN, graph.state(cableHost).knowledge());
        finish(graph, 8);
    }

    @Test void nearbyCableLookupIsRadiusBoundedTierSpecificAndRequiresLoadedNeighborhood() {
        LoadedPowerGraph graph = new LoadedPowerGraph();
        BlockPos center = new BlockPos(8, 10, 8);
        CableFaceNode within = new CableFaceNode(new BlockPos(8, 13, 8), Direction.UP, CableTier.APC);
        CableFaceNode boundary = new CableFaceNode(new BlockPos(11, 10, 8), Direction.WEST, CableTier.APC);
        CableFaceNode wrongTier = new CableFaceNode(new BlockPos(8, 11, 8), Direction.UP, CableTier.MV);
        ChunkPos chunk = new ChunkPos(center);
        graph.replaceChunk(chunk, List.of(within, boundary, wrongTier));
        graph.setChunkLoaded(new ChunkPos(1, 0), true);
        graph.setChunkLoaded(new ChunkPos(0, 1), true);
        graph.setChunkLoaded(new ChunkPos(1, 1), true);
        finish(graph, 32);

        List<CableFaceNode> nearby = graph.nodesNear(center, 3, CableTier.APC);
        assertNotNull(nearby);
        assertEquals(List.of(within, boundary), nearby.stream()
                .sorted(java.util.Comparator.comparingDouble((CableFaceNode node) -> center.distSqr(node.host()))
                        .thenComparingInt(node -> node.host().getX()).thenComparingInt(node -> node.host().getY())
                        .thenComparingInt(node -> node.host().getZ()).thenComparingInt(node -> node.face().ordinal()))
                .toList());
        assertFalse(nearby.contains(wrongTier));
        assertNull(graph.nodesNear(new BlockPos(30, 10, 8), 3, CableTier.APC),
                "an unloaded chunk intersecting the receiver radius is unknown");
    }

    @Test void lampReceiverPrefersNearestKnownComponentOverCloserUnknownComponent() {
        LoadedPowerGraph graph = new LoadedPowerGraph();
        BlockPos center = new BlockPos(12, 0, 8);
        List<CableFaceNode> unknownChain = List.of(apcNode(12, 0, 8), apcNode(13, 0, 8),
                apcNode(14, 0, 8), apcNode(15, 0, 8));
        CableFaceNode known = new CableFaceNode(new BlockPos(12, 0, 10), Direction.UP, CableTier.APC);
        List<CableFaceNode> graphNodes = new ArrayList<>(unknownChain);
        graphNodes.add(known);
        graph.replaceChunk(new ChunkPos(0, 0), graphNodes);
        finish(graph, 32);

        List<CableFaceNode> nearby = graph.nodesNear(center, 3, CableTier.APC);
        assertNotNull(nearby, "the candidate search itself is within loaded chunk space");
        assertEquals(LoadedPowerGraph.Knowledge.UNKNOWN, graph.state(unknownChain.get(0)).knowledge(),
                "the closer component has an unloaded continuation beyond the search area");
        assertEquals(LoadedPowerGraph.Knowledge.KNOWN, graph.state(known).knowledge());
        assertEquals(known, PowerGraphService.nearestKnownLampNode(graph, center, nearby));
    }

    @Test void lampReceiverFailsClosedWithoutKnownCandidatesAndBreaksDistanceTiesStably() {
        LoadedPowerGraph unknownGraph = new LoadedPowerGraph();
        CableFaceNode unknown = apcNode(15, 0, 8);
        unknownGraph.replaceChunk(new ChunkPos(0, 0), List.of(unknown));
        finish(unknownGraph, 8);
        BlockPos center = new BlockPos(12, 0, 8);
        List<CableFaceNode> unknownCandidates = unknownGraph.nodesNear(center, 3, CableTier.APC);
        assertNotNull(unknownCandidates);
        assertNull(PowerGraphService.nearestKnownLampNode(unknownGraph, center, unknownCandidates));

        LoadedPowerGraph tieGraph = new LoadedPowerGraph();
        CableFaceNode lower = new CableFaceNode(new BlockPos(11, 0, 8), Direction.UP, CableTier.APC);
        CableFaceNode upper = new CableFaceNode(new BlockPos(13, 0, 8), Direction.UP, CableTier.APC);
        tieGraph.replaceChunk(new ChunkPos(0, 0), List.of(upper, lower));
        finish(tieGraph, 8);
        List<CableFaceNode> tiedCandidates = tieGraph.nodesNear(center, 3, CableTier.APC);
        assertNotNull(tiedCandidates);
        assertEquals(lower, PowerGraphService.nearestKnownLampNode(tieGraph, center, tiedCandidates));
        assertEquals(lower, PowerGraphService.nearestKnownLampNode(tieGraph, center,
                List.of(upper, lower)), "candidate insertion order does not affect equal-distance selection");
    }

    @Test void unloadedGeometricSeamIsUnknownAndLoadRecoversWithoutInventingMissingCable() {
        LoadedPowerGraph graph = new LoadedPowerGraph();
        CableFaceNode left = new CableFaceNode(new BlockPos(15, 0, 8), Direction.UP, CableTier.HV);
        ChunkPos leftChunk = new ChunkPos(0, 0);
        ChunkPos rightChunk = new ChunkPos(1, 0);
        graph.replaceChunk(leftChunk, List.of(left));
        finish(graph, 8);
        assertEquals(LoadedPowerGraph.Knowledge.UNKNOWN, graph.state(left).knowledge());

        // A loaded neighboring chunk with no cable closes the uncertainty frontier.
        graph.setChunkLoaded(rightChunk, true);
        finish(graph, 8);
        assertEquals(LoadedPowerGraph.Knowledge.KNOWN, graph.state(left).knowledge());

        CableFaceNode right = new CableFaceNode(new BlockPos(16, 0, 8), Direction.UP, CableTier.HV);
        graph.replaceChunk(rightChunk, List.of(right));
        finish(graph, 8);
        assertEquals(graph.state(left).componentId(), graph.state(right).componentId());
        graph.removeChunk(rightChunk);
        finish(graph, 8);
        assertEquals(LoadedPowerGraph.Knowledge.UNKNOWN, graph.state(left).knowledge());

        graph.replaceChunk(rightChunk, List.of(right));
        finish(graph, 8);
        assertEquals(graph.state(left).componentId(), graph.state(right).componentId());
        assertEquals(LoadedPowerGraph.Knowledge.KNOWN, graph.state(left).knowledge());
    }

    @Test void removingIndexedEmptyFrontierInvalidatesThenRebuildsUnknownFrontier() {
        LoadedPowerGraph graph = new LoadedPowerGraph();
        CableFaceNode host = new CableFaceNode(new BlockPos(15, 0, 8), Direction.UP, CableTier.HV);
        ChunkPos hostChunk = new ChunkPos(0, 0);
        ChunkPos emptyNeighbor = new ChunkPos(1, 0);
        graph.replaceChunk(hostChunk, List.of(host));
        graph.replaceChunk(emptyNeighbor, List.of());
        finish(graph, 8);
        assertEquals(LoadedPowerGraph.Knowledge.KNOWN, graph.state(host).knowledge());

        graph.removeChunk(emptyNeighbor);
        assertEquals(LoadedPowerGraph.Knowledge.UNKNOWN, graph.state(host).knowledge());
        finish(graph, 8);
        var rebuilt = graph.diagnostics(host);
        assertEquals(LoadedPowerGraph.Knowledge.UNKNOWN, rebuilt.state().knowledge());
        assertTrue(rebuilt.nodeIndexed());
        assertFalse(rebuilt.rebuilding());
        assertTrue(rebuilt.unloadedPotentialNeighborChunks().contains(emptyNeighbor));
    }

    @Test void currentContinuousVentAndInclusiveExtensionReachAdmitTheActualApcContact() {
        var origin=new BlockPos(40,64,40);
        var layout=com.juicyslew.moonstation14.ms14.atmos.teg.fixture.TegContinuousFuelPlan.layout(origin);
        var vent=layout.devicePosition(com.juicyslew.moonstation14.ms14.atmos.teg.fixture.TegContinuousFuelPlan.DeviceRole.INJECTOR);
        var cablePlan=com.juicyslew.moonstation14.ms14.atmos.teg.fixture.TegContinuousPowerPlan.cables(origin);
        var contact=new CableFaceNode(origin.offset(6,1,16),Direction.UP,CableTier.APC);
        assertTrue(cablePlan.contains(contact));
        int resolvedReach=com.juicyslew.moonstation14.ms14.atmos.piping.prototype.VentilationPrototypeTest
                .resolvedCatalog().get(com.juicyslew.moonstation14.ms14.atmos.piping.prototype.ModVentilationDevices.VENT)
                .extensionCableReceiver().localReachBlocks();
        int actualDistance=Math.abs(vent.getY()-contact.host().getY());
        assertEquals(1,actualDistance,"current injector geometry is one block above its actual floor cable contact");
        assertTrue(actualDistance<=resolvedReach,"current live contact remains within resolved receiver reach");
        LoadedPowerGraph graph=new LoadedPowerGraph();
        var byChunk=cablePlan.stream().collect(java.util.stream.Collectors.groupingBy(node->new ChunkPos(node.host())));
        byChunk.forEach(graph::replaceChunk);
        for(ChunkPos chunk:byChunk.keySet())for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)graph.setChunkLoaded(new ChunkPos(chunk.x+x,chunk.z+z),true);
        finish(graph,256);
        var candidates=graph.nodesNear(vent,resolvedReach,CableTier.APC);
        assertNotNull(candidates);
        assertTrue(candidates.contains(contact),"resolved extension radius admits actual floor contact");
        assertEquals(contact,PowerGraphService.nearestKnownLampNode(graph,vent,candidates));
        assertEquals(LoadedPowerGraph.Knowledge.KNOWN,graph.state(contact).knowledge());
        var operatingApc=com.juicyslew.moonstation14.ms14.atmos.teg.fixture.TegContinuousPowerPlan.equipment(layout).operatingApc();
        var outputNode=cablePlan.stream().filter(node->node.tier()==CableTier.APC
                &&node.host().equals(operatingApc.relative(Direction.EAST))).findFirst().orElseThrow();
        assertEquals(graph.state(outputNode).componentId(),graph.state(contact).componentId(),
                "current injector extension selects an APC node on the operating APC component");
        var removed=new LoadedPowerGraph();byChunk.forEach((pos,nodes)->removed.replaceChunk(pos,nodes.stream().filter(node->!node.equals(contact)).toList()));
        for(ChunkPos chunk:byChunk.keySet())for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)removed.setChunkLoaded(new ChunkPos(chunk.x+x,chunk.z+z),true);
        finish(removed,256);
        assertFalse(removed.nodesNear(vent,resolvedReach,CableTier.APC).contains(contact));
        assertFalse(graph.nodesNear(vent,actualDistance-1,CableTier.APC).contains(contact),
                "one-short current geometry radius excludes the contact");
        var maximumReachReceiver=contact.host().above(resolvedReach);
        assertEquals(resolvedReach,Math.abs(maximumReachReceiver.getY()-contact.host().getY()));
        var maximumCandidates=graph.nodesNear(maximumReachReceiver,resolvedReach,CableTier.APC);
        assertTrue(maximumCandidates.contains(contact),"original resolved three-block inclusive reach still admits a maximum-distance contact");
        assertEquals(contact,PowerGraphService.nearestKnownLampNode(graph,maximumReachReceiver,maximumCandidates));
        assertFalse(graph.nodesNear(maximumReachReceiver,resolvedReach-1,CableTier.APC).contains(contact),
                "one-short maximum reach excludes the maximum-distance contact");
    }

    @Test void staleChunkUnloadPreservesReplacementStateAndRealUnloadCleansIt() {
        PowerGraphService.LevelState state = new PowerGraphService.LevelState();
        ChunkPos pos = new ChunkPos(-1, -668060);
        CableFaceNode cable = new CableFaceNode(new BlockPos(pos.getMinBlockX() + 8, 0,
                pos.getMinBlockZ() + 8), Direction.UP, CableTier.HV);
        state.graph.replaceChunk(pos, List.of(cable));
        finish(state.graph, 32);
        assertEquals(LoadedPowerGraph.Knowledge.KNOWN, state.graph.state(cable).knowledge());
        state.indexed.put(pos, List.of(cable));
        DevicePort port = new DevicePort(cable.host().above(), Direction.NORTH, CableTier.HV);
        state.indexedPorts.put(pos, List.of(port));
        state.graph.replaceChunk(pos, List.of(cable), List.of(port));
        finish(state.graph, 32);
        state.pending.add(pos);
        state.validation.schedule(pos);
        state.deferredLoads.add(pos);
        BlockPos lamp = new BlockPos(pos.getMinBlockX(), 0, pos.getMinBlockZ());
        state.lampChoices.put(lamp, new PowerGraphService.CachedLampNode(state.graph.revision(), 3, cable));
        long generation = state.topologyGeneration;
        Object unloading = new Object();
        Object replacement = new Object();

        assertFalse(PowerGraphService.processChunkUnload(state, pos, replacement, unloading, true));
        assertTrue(state.graph.isChunkLoaded(pos));
        assertEquals(LoadedPowerGraph.Knowledge.KNOWN, state.graph.state(cable).knowledge());
        assertTrue(state.pending.contains(pos));
        assertEquals(pos, state.validation.poll());
        assertTrue(state.deferredLoads.contains(pos));
        assertTrue(state.indexed.containsKey(pos));
        assertTrue(state.indexedPorts.containsKey(pos));
        assertTrue(state.lampChoices.containsKey(lamp));
        assertEquals(generation, state.topologyGeneration);

        assertTrue(PowerGraphService.processChunkUnload(state, pos, null, unloading, true),
                "an absent current chunk still follows genuine-unload cleanup");
        assertFalse(state.graph.isChunkLoaded(pos));
        assertEquals(LoadedPowerGraph.Knowledge.UNKNOWN, state.graph.state(cable).knowledge());
        assertFalse(state.pending.contains(pos));
        assertNull(state.validation.poll());
        assertFalse(state.deferredLoads.contains(pos));
        assertFalse(state.indexed.containsKey(pos));
        assertFalse(state.indexedPorts.containsKey(pos));
        assertFalse(state.lampChoices.containsKey(lamp));
        assertTrue(state.topologyGeneration > generation);
    }

    @Test void diagnosticsExposeStoredMissingFrontierAndClearWhenNeighborLoads() {
        LoadedPowerGraph graph = new LoadedPowerGraph();
        CableFaceNode left = new CableFaceNode(new BlockPos(15, 0, 8), Direction.UP, CableTier.HV);
        ChunkPos rightChunk = new ChunkPos(1, 0);
        graph.replaceChunk(new ChunkPos(0, 0), List.of(left));
        var rebuilding = graph.diagnostics(left);
        assertTrue(rebuilding.rebuilding());
        assertFalse(rebuilding.nodeIndexed());
        finish(graph, 8);
        var incomplete = graph.diagnostics(left);
        assertEquals(LoadedPowerGraph.Knowledge.UNKNOWN, incomplete.state().knowledge());
        assertTrue(incomplete.nodeIndexed());
        assertEquals(List.of(rightChunk), incomplete.unloadedPotentialNeighborChunks());
        graph.setChunkLoaded(rightChunk, true);
        assertTrue(graph.diagnostics(left).unloadedPotentialNeighborChunks().isEmpty());
        assertEquals(LoadedPowerGraph.Knowledge.KNOWN, graph.diagnostics(left).state().knowledge());
    }

    @Test void componentFrontierAttributionIsBoundedDeterministicAndReadOnly() {
        LoadedPowerGraph graph = new LoadedPowerGraph();
        CableFaceNode left = new CableFaceNode(new BlockPos(15, 0, 8), Direction.UP, CableTier.HV);
        ChunkPos home = new ChunkPos(0, 0), frontier = new ChunkPos(1, 0);
        graph.replaceChunk(home, List.of(left));
        finish(graph, 8);
        long revision = graph.revision();
        var before = graph.buildProgress();
        var first = graph.componentDiagnostics(left, 8, 8, 1, 1);
        var second = graph.componentDiagnostics(left, 8, 8, 1, 1);
        assertEquals(LoadedPowerGraph.Knowledge.UNKNOWN, first.state().knowledge());
        assertEquals(1, first.memberCount());
        assertEquals(1, first.missingChunkCount());
        assertFalse(first.chunksTruncated());
        assertEquals(frontier, first.missingFrontiers().getFirst().chunk());
        assertEquals(List.of(left), first.missingFrontiers().getFirst().sources());
        assertEquals(first, second);
        assertEquals(revision, graph.revision());
        assertEquals(before, graph.buildProgress());
        assertEquals(LoadedPowerGraph.Knowledge.UNKNOWN, graph.state(left).knowledge());
        graph.setChunkLoaded(frontier, true);
        var resolved = graph.componentDiagnostics(left, 8, 8, 1, 1);
        assertEquals(LoadedPowerGraph.Knowledge.KNOWN, resolved.state().knowledge());
        assertTrue(resolved.missingFrontiers().isEmpty());
    }

    @Test void componentDiagnosticsCapsTraversalAndFrontierWorkWithoutMutatingGraph() throws Exception {
        LoadedPowerGraph graph = new LoadedPowerGraph();
        List<CableFaceNode> chain = new ArrayList<>();
        for (int x = 0; x < 5000; x++) chain.add(node(x, CableTier.HV));
        graph.replaceChunk("bounded-chain", chain);
        finish(graph, 1024);
        CableFaceNode first = chain.getFirst();
        var infoField = LoadedPowerGraph.class.getDeclaredField("componentInfo");
        infoField.setAccessible(true);
        @SuppressWarnings("unchecked") var infos = (java.util.Map<Integer, Object>) infoField.get(graph);
        int component = graph.state(first).componentId();
        Object info = infos.get(component);
        var frontierField = info.getClass().getDeclaredField("potentialNeighborChunks");
        frontierField.setAccessible(true);
        @SuppressWarnings("unchecked") var frontier = (java.util.NavigableSet<Long>) frontierField.get(info);
        for (int i = 0; i < 100; i++) frontier.add(new ChunkPos(100 + i, 0).toLong());
        long revision = graph.revision();
        var before = graph.buildProgress();
        var detail = graph.componentDiagnostics(first, 17, 5, 2, 3);
        assertEquals(17, detail.membersExamined());
        assertTrue(detail.membersTruncated());
        assertEquals(5, detail.frontierEntriesExamined());
        assertTrue(detail.frontierTruncated());
        assertEquals(5, detail.missingChunkCount());
        assertTrue(detail.chunksTruncated());
        assertEquals(2, detail.missingFrontiers().size());
        assertEquals(LoadedPowerGraph.Knowledge.UNKNOWN, detail.state().knowledge());
        assertEquals(revision, graph.revision());
        assertEquals(before, graph.buildProgress());
        assertEquals(LoadedPowerGraph.Knowledge.UNKNOWN, graph.state(first).knowledge());
    }

    @Test void fullEmptyComponentFrontierIsReconciledFromApCReceiverQueryButUnloadedStaysUnknown() {
        PowerGraphService.LevelState state = new PowerGraphService.LevelState();
        BlockPos device = new BlockPos(15, 8, 8);
        CableFaceNode contact = new CableFaceNode(device.below(), Direction.UP, CableTier.APC);
        DevicePort port = new DevicePort(device, Direction.DOWN, CableTier.APC);
        ChunkPos home = new ChunkPos(0, 0), frontier = new ChunkPos(1, 0);
        state.graph.replaceChunk(home, List.of(contact), List.of(port));
        finish(state.graph, 32);
        assertEquals(LoadedPowerGraph.Knowledge.UNKNOWN, state.graph.state(port).knowledge());

        long generation = state.topologyGeneration;
        PowerGraphService.requestMissingFrontier(state, contact, pos -> false, 8);
        assertTrue(state.pending.isEmpty(), "a non-FULL chunk is not admitted or forced");
        assertEquals(generation, state.topologyGeneration);
        assertEquals(LoadedPowerGraph.Knowledge.UNKNOWN, state.graph.state(port).knowledge());

        PowerGraphService.requestMissingFrontier(state, contact, frontier::equals, 8);
        assertEquals(Set.of(frontier), state.pending);
        assertEquals(LoadedPowerGraph.Knowledge.UNKNOWN, state.graph.state(port).knowledge(),
                "frontier admission queues normal reconciliation; it does not fabricate known state");
        PowerGraphService.refreshQueuedChunks(state, pos -> pos.equals(frontier)
                ? new PowerGraphService.ChunkContents(List.of(), List.of()) : null);
        assertTrue(state.graph.isChunkLoaded(frontier), "FULL empty chunk enters graph loaded-space index");
        assertEquals(LoadedPowerGraph.Knowledge.KNOWN, state.graph.state(port).knowledge());

        long rebuiltGeneration = state.topologyGeneration;
        long rebuiltRevision = state.graph.revision();
        PowerGraphService.requestMissingFrontier(state, contact, frontier::equals, 8);
        assertTrue(state.pending.isEmpty(), "already reconciled FULL frontier is not queued again");
        assertEquals(rebuiltGeneration, state.topologyGeneration,
                "query after normal-owner rebuild does not schedule redundant topology work");
        assertEquals(rebuiltRevision, state.graph.revision(),
                "query after normal-owner rebuild leaves graph knowledge unchanged");
        assertEquals(LoadedPowerGraph.Knowledge.KNOWN, state.graph.state(port).knowledge());

        state.graph.removeChunk(frontier);
        state.graph.setChunkLoaded(frontier, false);
        assertEquals(LoadedPowerGraph.Knowledge.UNKNOWN, state.graph.state(port).knowledge(),
                "actual unload returns the same component to fail-closed UNKNOWN");
    }

    @Test void boundedFrontierAdmissionQueuesOnlyFullChunksAndRotatesPastAbsentPrefix() {
        LoadedPowerGraph graph = new LoadedPowerGraph();
        List<CableFaceNode> chain = new ArrayList<>();
        for (int x = 15; x <= 15 + 12 * 16; x++)
            chain.add(new CableFaceNode(new BlockPos(x, 0, 8), Direction.UP, CableTier.HV));
        for (int x = 0; x <= 12; x++) {
            int chunkX = x;
            graph.replaceChunk(new ChunkPos(x, 0), chain.stream()
                    .filter(node -> (node.host().getX() >> 4) == chunkX).toList());
        }
        finish(graph, 4096);
        CableFaceNode first = chain.get(0);
        assertEquals(LoadedPowerGraph.Knowledge.UNKNOWN, graph.state(first).knowledge());
        PowerGraphService.LevelState state = new PowerGraphService.LevelState();
        state.graph.replaceChunk(new ChunkPos(0, 0), List.of(first));
        // Exercise the production queue-admission seam: absent frontiers remain untouched,
        // while repeated full admission changes generation exactly once.
        ChunkPos missing = new ChunkPos(20, 0);
        assertFalse(PowerGraphService.admitFullFrontier(state, missing, false));
        long before = state.topologyGeneration;
        assertTrue(PowerGraphService.admitFullFrontier(state, missing, true));
        assertEquals(before + 1, state.topologyGeneration);
        assertFalse(PowerGraphService.admitFullFrontier(state, missing, true));
        assertEquals(before + 1, state.topologyGeneration);

        // Scans are limited to eight stored keys; repeated queries advance rather than
        // starving a later loaded key behind an absent prefix.
        List<ChunkPos> firstBatch = graph.missingFrontierBatch(first, 8);
        List<ChunkPos> laterBatch = graph.missingFrontierBatch(first, 8);
        assertTrue(firstBatch.size() <= 8);
        assertTrue(laterBatch.size() <= 8);
        assertFalse(firstBatch.containsAll(laterBatch));
        ChunkPos laterFull = laterBatch.get(0);
        assertFalse(PowerGraphService.admitFullFrontier(state, laterFull, false));
        assertTrue(PowerGraphService.admitFullFrontier(state, laterFull, true));
    }

    @Test void packedMinimumFrontierKeyIsNotMistakenForUnsetCursor() throws Exception {
        LoadedPowerGraph graph = new LoadedPowerGraph();
        CableFaceNode cable = node(15, CableTier.HV);
        graph.replaceChunk(new ChunkPos(0, 0), List.of(cable));
        finish(graph, 16);
        int component = graph.state(cable).componentId();
        var infosField = LoadedPowerGraph.class.getDeclaredField("componentInfo");
        infosField.setAccessible(true);
        var infos = (java.util.Map<?, ?>) infosField.get(graph);
        Object info = infos.get(component);
        var frontierField = info.getClass().getDeclaredField("potentialNeighborChunks");
        frontierField.setAccessible(true);
        @SuppressWarnings("unchecked") var frontier = (java.util.Set<Long>) frontierField.get(info);
        frontier.clear();
        frontier.add(Long.MIN_VALUE);
        frontier.add(1L);

        var batch = graph.missingFrontierBatch(cable, 1);
        assertEquals(List.of(new ChunkPos(Long.MIN_VALUE)), batch);
        PowerGraphService.LevelState state = new PowerGraphService.LevelState();
        assertTrue(PowerGraphService.admitFullFrontier(state, batch.get(0), true));
        long generation = state.topologyGeneration;
        assertFalse(PowerGraphService.admitFullFrontier(state, batch.get(0), true));
        assertEquals(generation, state.topologyGeneration);
        assertEquals(List.of(new ChunkPos(1, 0)), graph.missingFrontierBatch(cable, 1));
    }

    @Test void periodicValidationRotatesAcrossMoreThanFourChunksWithoutInvalidatingKnownGraph() {
        LoadedPowerGraph graph = new LoadedPowerGraph();
        ChunkValidationScheduler scheduler = new ChunkValidationScheduler();
        List<CableFaceNode> nodes = new ArrayList<>();
        for (int x = 0; x < 9; x++) {
            ChunkPos chunk = new ChunkPos(x, 0);
            CableFaceNode node = new CableFaceNode(new BlockPos(x * 16 + 8, 4, 8), Direction.UP, CableTier.HV);
            nodes.add(node);
            graph.replaceChunk(chunk, List.of(node));
            scheduler.schedule(chunk);
        }
        finish(graph, 64);
        for (CableFaceNode node : nodes)
            assertEquals(LoadedPowerGraph.Knowledge.KNOWN, graph.state(node).knowledge());

        // A tick can validate at most four chunks. Checks rotate rather than being
        // copied into mutation-pending work, which would dirty the entire graph.
        for (int tick = 0; tick < 3; tick++) {
            for (int refreshed = 0; refreshed < PowerGraphService.CHUNK_REFRESHES_PER_TICK; refreshed++) {
                ChunkPos chunk = scheduler.poll();
                assertNotNull(chunk);
                scheduler.schedule(chunk);
            }
            assertFalse(graph.isDirty());
            for (CableFaceNode node : nodes)
                assertEquals(LoadedPowerGraph.Knowledge.KNOWN, graph.state(node).knowledge());
        }
    }

    @Test void disconnectedNodesAndCyclesDoNotCauseStationWideWorkEachTick() {
        LoadedPowerGraph graph = new LoadedPowerGraph();
        List<CableFaceNode> isolated = new ArrayList<>();
        for (int x = 0; x < 1000; x++) isolated.add(new CableFaceNode(new BlockPos(x * 3, 4, 0), Direction.UP, CableTier.MV));
        graph.replaceChunk("sparse", isolated);
        assertEquals(31, graph.process(31));
        assertTrue(graph.isDirty());
        assertEquals(31, graph.process(31));
        assertTrue(graph.isDirty());
        int ticks = 2;
        while (graph.isDirty()) { assertTrue(graph.process(31) <= 31); ticks++; }
        assertTrue(ticks >= 32);
        assertEquals(1000, isolated.stream().map(n -> graph.state(n).componentId()).distinct().count());
    }

    @Test void longChainQueueDeduplicationKeepsExpansionLinear() {
        LoadedPowerGraph graph = new LoadedPowerGraph();
        List<CableFaceNode> chain = new ArrayList<>();
        for (int x = 0; x < 4096; x++) chain.add(node(x, CableTier.HV));
        graph.replaceChunk("long", chain);
        int work = 0;
        while (graph.isDirty()) work += graph.process(128);
        assertTrue(work <= chain.size() * 3, "indexing plus one bounded-degree visit per node");
        assertEquals(graph.state(chain.get(0)).componentId(), graph.state(chain.get(chain.size() - 1)).componentId());
    }

    private static CableFaceNode node(int x, CableTier tier) {
        return new CableFaceNode(new BlockPos(x, 0, 0), Direction.UP, tier);
    }

    private static CableFaceNode apcNode(int x, int y, int z) {
        return new CableFaceNode(new BlockPos(x, y, z), Direction.UP, CableTier.APC);
    }

    private static void finish(LoadedPowerGraph graph, int budget) {
        while (graph.isDirty()) assertTrue(graph.process(budget) <= budget);
    }
}
