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
