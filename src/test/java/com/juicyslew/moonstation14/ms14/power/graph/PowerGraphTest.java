package com.juicyslew.moonstation14.ms14.power.graph;

import com.juicyslew.moonstation14.ms14.power.cable.CableChunkData;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class PowerGraphTest {
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

    @Test void staleChunkUnloadPreservesReplacementStateAndRealUnloadCleansIt() {
        PowerGraphService.LevelState state = new PowerGraphService.LevelState();
        ChunkPos pos = new ChunkPos(-1, -668060);
        CableFaceNode cable = new CableFaceNode(new BlockPos(pos.getMinBlockX() + 8, 0,
                pos.getMinBlockZ() + 8), Direction.UP, CableTier.HV);
        state.graph.replaceChunk(pos, List.of(cable));
        finish(state.graph, 32);
        assertEquals(LoadedPowerGraph.Knowledge.KNOWN, state.graph.state(cable).knowledge());
        state.indexed.put(pos, List.of(cable));
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
