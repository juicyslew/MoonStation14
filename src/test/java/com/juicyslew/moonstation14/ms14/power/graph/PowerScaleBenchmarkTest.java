package com.juicyslew.moonstation14.ms14.power.graph;

import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Deterministic workload probe. Timings are reported, never asserted. */
final class PowerScaleBenchmarkTest {
    private static final int TICK_BUDGET = PowerGraphService.GRAPH_NODES_PER_TICK;

    @Test void stationScaleLinesJunctionsBurstEditsAndChunkReload() {
        probe("line-8k", line(8_000));
        probe("line-20k", line(20_000));
        probe("junction-8k", junction(8_000));

        List<List<CableFaceNode>> station = line(8_000);
        LoadedPowerGraph graph = new LoadedPowerGraph();
        replaceAll(graph, station);
        converge(graph);
        long editStart = System.nanoTime();
        // Three rapid chunk mutations coalesce only at the caller/service layer; graph invalidation
        // currently throws away all indexed/component state each time.
        for (int i = 0; i < 3; i++) graph.replaceChunk(new ChunkPos(i, 0), station.get(i));
        Metrics burst = converge(graph);
        report("burst-edit-8k", station.stream().mapToInt(List::size).sum(),
                System.nanoTime() - editStart, burst, graph);

        graph.removeChunk(new ChunkPos(0, 0));
        Metrics unload = converge(graph);
        report("unload-8k", graph.loadedNodeCount(), unload.elapsedNs(), unload, graph);
        graph.replaceChunk(new ChunkPos(0, 0), station.get(0));
        Metrics reload = converge(graph);
        report("reload-8k", graph.loadedNodeCount(), reload.elapsedNs(), reload, graph);
    }

    private static void probe(String name, List<List<CableFaceNode>> chunks) {
        LoadedPowerGraph graph = new LoadedPowerGraph();
        replaceAll(graph, chunks);
        Metrics metrics = converge(graph);
        report(name, graph.loadedNodeCount(), metrics.elapsedNs(), metrics, graph);
    }

    private static Metrics converge(LoadedPowerGraph graph) {
        long start = System.nanoTime();
        long processed = 0;
        int ticks = 0;
        int maxTick = 0;
        while (graph.isDirty()) {
            int work = graph.process(TICK_BUDGET);
            if (work > TICK_BUDGET) throw new AssertionError("node budget exceeded");
            processed += work;
            maxTick = Math.max(maxTick, work);
            ticks++;
        }
        return new Metrics(System.nanoTime() - start, processed, ticks, maxTick);
    }

    private static void replaceAll(LoadedPowerGraph graph, List<List<CableFaceNode>> chunks) {
        for (int x = 0; x < chunks.size(); x++) graph.replaceChunk(new ChunkPos(x, 0), chunks.get(x));
    }

    private static List<List<CableFaceNode>> line(int count) {
        Map<Integer, List<CableFaceNode>> chunks = new LinkedHashMap<>();
        for (int x = 0; x < count; x++)
            chunks.computeIfAbsent(x >> 4, ignored -> new ArrayList<>()).add(
                    new CableFaceNode(new BlockPos(x, 64, 0), Direction.UP, CableTier.HV));
        return new ArrayList<>(chunks.values());
    }

    /** Repeated four-way spokes create a deterministic high-degree workload without world access. */
    private static List<List<CableFaceNode>> junction(int count) {
        Map<Integer, List<CableFaceNode>> chunks = new LinkedHashMap<>();
        Direction[] faces = Direction.values();
        for (int i = 0; i < count; i++) {
            int x = i / faces.length;
            chunks.computeIfAbsent(x >> 4, ignored -> new ArrayList<>()).add(
                    new CableFaceNode(new BlockPos(x, 64, 0), faces[i % faces.length], CableTier.HV));
        }
        return new ArrayList<>(chunks.values());
    }

    private static void report(String name, int nodes, long elapsed, Metrics m, LoadedPowerGraph graph) {
        // Rough retained-structure allowance only, not a heap profiler measurement.
        long estimatedBytes = (long) nodes * 256;
        System.out.printf("POWER_SCALE name=%s nodes=%d elapsed_ns=%d nodes_per_tick=%d max_tick=%d ticks=%d queue_high_water=%d estimated_retained_bytes=%d%n",
                name, nodes, elapsed, m.ticks() == 0 ? 0 : m.processed() / m.ticks(),
                m.maxTick(), m.ticks(), graph.queueHighWater(), estimatedBytes);
    }

    private record Metrics(long elapsedNs, long processed, int ticks, int maxTick) { }
}
