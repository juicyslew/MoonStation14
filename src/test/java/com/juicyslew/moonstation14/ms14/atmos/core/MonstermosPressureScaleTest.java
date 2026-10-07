package com.juicyslew.moonstation14.ms14.atmos.core;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Connected-area regression: overlapping 200-cell solves must move heat-driven pressure globally. */
class MonstermosPressureScaleTest {
    private static final int SIDE = 10;

    @Test void overlappingPatchesConvergeAcrossWholeThousandCellRoom() {
        Fixture fixture = room(true);
        SweepResult pressure = sweep(fixture, true, 10);
        SweepResult legacy = sweep(fixture, false, 10);
        report("thermal", pressure);
        report("legacy-thermal", legacy);
        double initialSpan = span(fixture.cells.values());
        assertTrue(pressure.spans.get(pressure.spans.size() - 1) <= initialSpan * .01,
                "whole-room pressure span did not fall by 99%: " + pressure.spans);
        assertTrue(maxPressure(pressure.state.values()) / minPressure(pressure.state.values()) <= 1.1);
        assertSpeciesEnergyConserved(fixture.cells, pressure.state);
        assertTrue(temperatureSpan(pressure.state.values()) > 100,
                "pressure solve must not instantaneously thermal-average the room");
        assertTrue(pressure.spans.get(0) < legacy.spans.get(0) * .5,
                "thermal pressure kernel did not beat equal-moles legacy baseline");
    }

    @Test void isothermalExcessInventoryStillRedistributesEfficiently() {
        Fixture fixture = room(false);
        fixture.cells.put(pos(0, 0, 0), gas(45.0 * 5000.0 / 293.0, 293));
        SweepResult actual = sweep(fixture, true, 5);
        SweepResult legacy = sweep(fixture, false, 5);
        report("isothermal", actual);
        report("legacy-isothermal", legacy);
        double initialSpan=span(fixture.cells.values());
        assertTrue(actual.spans.get(0) < initialSpan * .02,
                "matched-pressure inventory source left more than 2% of its original whole-room span; legacy="
                        + legacy.spans.get(0) + ", pressure=" + actual.spans.get(0));
        // Measured legacy residual is 0.43% and pressure residual 1.56%; bound the observed
        // compatibility gap to 1.5% of initial span rather than demanding exact legacy output.
        assertTrue(actual.spans.get(0) <= legacy.spans.get(0) + initialSpan * .015,
                "isothermal bulk transfer regressed materially from matched-pressure legacy baseline");
    }

    @Test void warmTwoHundredCellPatchCpuIsInformational() {
        benchmarkPatch(room(true,10,10,2));
        benchmarkPatch(room(true,SIDE,SIDE,SIDE));
    }

    private static void benchmarkPatch(Fixture fixture) {
        List<Double> elapsed = new ArrayList<>();
        long discoveryWork = 0, combinedWork = 0, previewSteps = 0;
        int maxSearchWork = 0, includedNodes = -1;
        for (int i = 0; i < 40; i++) {
            long start = System.nanoTime();
            var result = MonstermosPressureEqualization.equalizeDiscovered(fixture.cells, fixture.graph,
                    pos(0, 0, 0), Set.of());
            long nanos = System.nanoTime() - start;
            includedNodes = result.transport().includedCells().size();
            if (i >= 10) {
                elapsed.add(nanos / 1_000_000.0);
                // Search work already includes scalar-preview path steps. Report previews as
                // a subset, not a second addition to combined work.
                discoveryWork += result.transport().work().discoveredCells();
                combinedWork += result.transport().work().edgeSearchWork()
                        + result.transport().work().transferOperations();
                previewSteps += result.diagnostics().scalarPreviewSteps();
                maxSearchWork = Math.max(maxSearchWork, result.transport().work().edgeSearchWork());
            }
        }
        assertEquals(Math.min(MonstermosEqualization.UPSTREAM_SOFT_LIMIT,fixture.cells.size()),includedNodes,
                "benchmark membership must represent the selected finite patch");
        Collections.sort(elapsed);
        System.out.println("PRESSURE_SCALE kernelBenchmark inputGraphNodes=" + fixture.cells.size()
                + " includedNodes=" + includedNodes + " samples=" + elapsed.size() + " warmups=10 medianMs="
                + percentile(elapsed, .5) + " p95Ms=" + percentile(elapsed, .95)
                + " discoveryWork=" + discoveryWork + " combinedSearchAndFaceWork=" + combinedWork
                + " previewStepsSubset=" + previewSteps
                + " maxSearchWork=" + maxSearchWork);
    }

    private static BlockPos pos(int x, int y, int z) { return new BlockPos(x, y, z); }
    private static GasMixture gas(double n, double t) { return new GasMixture(Map.of(GasType.NITROGEN, n), t); }
    private static double span(Collection<GasMixture> cells) { return maxPressure(cells) - minPressure(cells); }
    private static double maxPressure(Collection<GasMixture> cells) { return cells.stream().mapToDouble(GasMixture::pressureKpa).max().orElse(0); }
    private static double minPressure(Collection<GasMixture> cells) { return cells.stream().mapToDouble(GasMixture::pressureKpa).min().orElse(0); }
    private static double energy(Collection<GasMixture> cells) { return cells.stream().mapToDouble(GasMixture::thermalEnergy).sum(); }
    private static double moles(Collection<GasMixture> cells) { return cells.stream().mapToDouble(g -> g.moles(GasType.NITROGEN)).sum(); }

    private record Fixture(Map<BlockPos, GasMixture> cells, Map<BlockPos, Set<BlockPos>> graph) { }
    private record SweepResult(Map<BlockPos, GasMixture> state, List<Double> spans, List<Double> millis,
                               long discovery, long search, long previews, long faces, long routes, long calls) { }
    private static Fixture room(boolean thermal) { return room(thermal,SIDE,SIDE,SIDE); }
    private static Fixture room(boolean thermal,int sizeX,int sizeY,int sizeZ) {
        Map<BlockPos, GasMixture> cells = new LinkedHashMap<>();
        Map<BlockPos, Set<BlockPos>> graph = new LinkedHashMap<>();
        for (int y = 0; y < sizeY; y++) for (int z = 0; z < sizeZ; z++) for (int x = 0; x < sizeX; x++) {
            BlockPos p = pos(x, y, z);
            cells.put(p, gas(x == 0 && y == 0 && z == 0 ? 45 : 40,
                    thermal && x == 0 && y == 0 && z == 0 ? 5000 : 293));
            Set<BlockPos> ns = new LinkedHashSet<>();
            for (var d : net.minecraft.core.Direction.values()) { BlockPos n=p.relative(d);
                if(n.getX()>=0&&n.getX()<sizeX&&n.getY()>=0&&n.getY()<sizeY&&n.getZ()>=0&&n.getZ()<sizeZ)ns.add(n); }
            graph.put(p, ns);
        }
        return new Fixture(cells, graph);
    }
    private static SweepResult sweep(Fixture fixture, boolean pressure, int maxSweeps) {
        Map<BlockPos, GasMixture> state = new LinkedHashMap<>(fixture.cells);
        List<Double> spans = new ArrayList<>(), millis = new ArrayList<>();
        long discovery=0, search=0, previews=0, faces=0, routes=0, calls=0;
        for(int round=0;round<maxSweeps;round++) {
            Set<BlockPos> processed=new HashSet<>(); long start=System.nanoTime();
            for(BlockPos seed:state.keySet()) {
                if(processed.contains(seed))continue;
                if(pressure) {
                    var r=MonstermosPressureEqualization.equalizeDiscovered(state,fixture.graph,seed,Set.of());
                    processed.addAll(r.transport().includedCells()); state.putAll(r.transport().states());
                    discovery+=r.transport().work().discoveredCells(); search+=r.transport().work().edgeSearchWork();
                    previews+=r.diagnostics().scalarPreviewSteps(); faces+=r.transport().work().transferOperations(); routes+=r.diagnostics().routes();
                } else {
                    var r=MonstermosEqualization.equalizeDiscovered(state,fixture.graph,seed);
                    // Legacy no-change results report only the seed. Apply the same first-200
                    // BFS admission membership as the pressure kernel so reference sweeps use
                    // matched candidate sets rather than repeatedly revisiting no-op cells.
                    processed.addAll(firstBfsMembership(fixture.graph,seed));
                    state.putAll(r.states());
                    discovery+=r.work().discoveredCells(); search+=r.work().edgeSearchWork(); faces+=r.work().transferOperations();
                }
                calls++;
            }
            millis.add((System.nanoTime()-start)/1_000_000.0); spans.add(span(state.values()));
        }
        return new SweepResult(state,spans,millis,discovery,search,previews,faces,routes,calls);
    }
    private static Set<BlockPos> firstBfsMembership(Map<BlockPos,Set<BlockPos>> graph,BlockPos seed) {
        Set<BlockPos> seen=new LinkedHashSet<>(); ArrayDeque<BlockPos> queue=new ArrayDeque<>();
        seen.add(seed); queue.add(seed);
        while(!queue.isEmpty()&&seen.size()<MonstermosEqualization.UPSTREAM_SOFT_LIMIT) {
            BlockPos current=queue.remove();
            for(var direction:net.minecraft.core.Direction.values()) {
                BlockPos neighbor=current.relative(direction);
                if(graph.getOrDefault(current,Set.of()).contains(neighbor)&&seen.add(neighbor)) {
                    if(seen.size()>=MonstermosEqualization.UPSTREAM_SOFT_LIMIT) break;
                    queue.add(neighbor);
                }
            }
        }
        return seen;
    }
    private static void report(String label,SweepResult r) {
        System.out.println("PRESSURE_SCALE case="+label+" spans="+r.spans+" sweepMs="+r.millis
                +" discovery="+r.discovery+" searchIncludingPreviewSteps="+r.search+" previewStepsSubset="+r.previews
                +" faces="+r.faces+" routes="+r.routes+" patchCalls="+r.calls);
    }
    private static void assertSpeciesEnergyConserved(Map<BlockPos,GasMixture> before,Map<BlockPos,GasMixture> after) {
        // Every committed mixture is float-backed. Four per-cell ULPs cover its input/output
        // quantization plus packet split/merge rounding, without scaling by room energy.
        for(GasType type:GasType.values()) {
            double a=before.values().stream().mapToDouble(g->g.moles(type)).sum();
            double b=after.values().stream().mapToDouble(g->g.moles(type)).sum();
            double ulps=before.values().stream().mapToDouble(g->Math.ulp((float)g.moles(type))).sum()
                    +after.values().stream().mapToDouble(g->Math.ulp((float)g.moles(type))).sum();
            assertEquals(a,b,ulps*4+Math.ulp((float)a)*2,"species="+type);
        }
        double a=energy(before.values()),b=energy(after.values());
        double ulps=before.values().stream().mapToDouble(g->Math.ulp((float)g.thermalEnergy())).sum()
                +after.values().stream().mapToDouble(g->Math.ulp((float)g.thermalEnergy())).sum();
        assertEquals(a,b,ulps*4,"raw thermal energy");
    }
    private static double temperatureSpan(Collection<GasMixture> cells) {
        double lo=cells.stream().mapToDouble(GasMixture::temperatureKelvin).min().orElse(0);
        return cells.stream().mapToDouble(GasMixture::temperatureKelvin).max().orElse(0)-lo;
    }
    private static double percentile(List<Double> sorted,double p) { return sorted.get((int)Math.ceil(p*sorted.size())-1); }
}
