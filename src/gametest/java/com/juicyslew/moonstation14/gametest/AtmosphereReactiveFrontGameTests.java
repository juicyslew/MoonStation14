package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.reaction.HotspotState;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereRuntimeTelemetry;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Bounded, service-driven observation fixture for reactive fronts; intentionally changes no physics. */
@PrefixGameTestTemplate(false)
@GameTestTags({"atmosphere"})
public final class AtmosphereReactiveFrontGameTests {
    private AtmosphereReactiveFrontGameTests() { }

    @GameTest(template = "atmos_large_empty", batch = "atmosphere_reactive_front_diagnostic", timeoutTicks = 900, templateNamespace = "moonstation14")
    public static void capturePerCellReactiveFront(GameTestHelper helper) {
        runReactiveFrontFixture(helper, false);
    }

    @GameTest(template = "atmos_large_empty", batch = "atmosphere_reactive_front_diagnostic", timeoutTicks = 900, templateNamespace = "moonstation14")
    public static void persistentHotspotDefersGroupBreakdown(GameTestHelper helper) {
        runReactiveFrontFixture(helper, true);
    }

    private static void runReactiveFrontFixture(GameTestHelper helper, boolean requireSustainedHotspot) {
        ServerLevel level = helper.getLevel();

        // 10x10 finite, sealed room: all cells are loaded and reserved by this fixture.
        for (int x = 1; x <= 12; x++) for (int z = 1; z <= 12; z++) {
            helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            helper.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
        }
        for (int i = 1; i <= 12; i++) {
            helper.setBlock(new BlockPos(1, 1, i), Blocks.STONE);
            helper.setBlock(new BlockPos(12, 1, i), Blocks.STONE);
            helper.setBlock(new BlockPos(i, 1, 1), Blocks.STONE);
            helper.setBlock(new BlockPos(i, 1, 12), Blocks.STONE);
        }
        List<BlockPos> cells = new ArrayList<>(100);
        for (int x = 2; x <= 11; x++) for (int z = 2; z <= 11; z++) {
            helper.setBlock(new BlockPos(x, 1, z), Blocks.AIR);
            cells.add(helper.absolutePos(new BlockPos(x, 1, z)));
        }
        AtmosphereService service = AtmosphereService.withVacuumDimensions(Set.of(level.dimension()));
        BlockPos source = helper.absolutePos(new BlockPos(2, 1, 6));
        service.activateCellAndNeighbors(level, source);
        int due = 0;
        while (due < 96 && cells.stream().anyMatch(p -> !"finite".equals(service.diagnosticCell(level, p).status())))
            service.tick(level, ++due);
        for (BlockPos p : cells) {
            if (!"finite".equals(service.diagnosticCell(level, p).status()))
                throw new GameTestAssertException("fixture ownership not finite at " + p);
            boolean sourceCell = p.equals(source);
            double oxygen = 18.0;
            double nitrogen = sourceCell ? 18.0 : 178.0;
            if (!service.addGas(level, p, GasType.OXYGEN, oxygen, 293.15)
                    || !service.addGas(level, p, GasType.NITROGEN, nitrogen, 293.15))
                throw new GameTestAssertException("fixture gas seed rejected at " + p);
        }
        // Add equal-mole hot oxygen/fuel; total moles match the 196 mol room cells (temperature differs).
        if (!service.addGas(level, source, GasType.OXYGEN, 80, 2000.0)
                || !service.addGas(level, source, GasType.TRITIUM, 80, 2000.0))
            throw new GameTestAssertException("source tritium seed rejected");
        service.activateCellAndNeighbors(level, source);
        double sourceMoles = service.sample(level, source).orElseThrow().totalMoles();
        double roomMoles = service.sample(level, cells.get(99)).orElseThrow().totalMoles();
        if (Math.abs(sourceMoles - roomMoles) > 0.01)
            throw new GameTestAssertException("source gas mole inventory is not balanced: source="
                    + sourceMoles + ", room=" + roomMoles);

        final String[] lastStage = {""};
        final long[] lastCycles = {-1};
        final double[] firstFarWater = {Double.NaN};
        final double[] firstFarTempDelta = {Double.NaN};
        final double[] firstGroupAverageTick = {Double.NaN};
        final int[] maximumWaterDistance = {0};
        final int[] frames = {0};
        final long startTick = level.getGameTime();
        final AtmosphereRuntimeTelemetry.Snapshot initialRuntime = service.diagnosticRuntime(level);
        final long[] lastGroupAverages = {initialRuntime == null ? 0 : initialRuntime.groupAverages()};
        final boolean[] sawGroupBreakdown = {false};
        final int[] activeProcessedHotspotCycles = {0};
        final long[] lastActiveHotspotCycle = {-1};
        final double[] previousWater = new double[cells.size()];
        final GasMixture[] previousTraceGas = new GasMixture[cells.size()];
        final HotspotState[] previousTraceHotspot = new HotspotState[cells.size()];
        final int[] previousTraceIgnitions = new int[cells.size()];
        for (int i = 0; i < cells.size(); i++)
        {
            previousTraceGas[i] = service.sample(level, cells.get(i)).orElseThrow();
            previousWater[i] = previousTraceGas[i].moles(GasType.WATER_VAPOR);
        }
        helper.startSequence().thenExecuteFor(720, () -> {
            String processedStage = service.atmosphereStage(level);
            long processedCycle = service.completedCycles(level);
            HotspotState sourceHotspotBefore = service.diagnosticHotspot(level, source).orElse(null);
            long sourceCommitStampBefore = service.diagnosticHotspotCommitStep(level, source);
            service.tick(level, level.getGameTime());
            AtmosphereRuntimeTelemetry.Snapshot runtime = service.diagnosticRuntime(level);
            String stage = processedStage;
            long cycle = processedCycle;
            double maxWater = 0, maxTemp = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < cells.size(); i++) {
                BlockPos p = cells.get(i);
                var cell = service.diagnosticCell(level, p);
                GasMixture gas = cell.gas();
                if (!"finite".equals(cell.status()) || gas == null)
                    throw new GameTestAssertException("fixture lost finite ownership at " + p);
                maxWater = Math.max(maxWater, gas.moles(GasType.WATER_VAPOR));
                maxTemp = Math.max(maxTemp, gas.temperatureKelvin());
                if (gas.moles(GasType.WATER_VAPOR) > 1.0e-6)
                    maximumWaterDistance[0] = Math.max(maximumWaterDistance[0], p.distManhattan(source));
                if (p.distManhattan(source) >= 8 && Double.isNaN(firstFarWater[0])
                        && previousWater[i] <= 1.0e-6 && gas.moles(GasType.WATER_VAPOR) > 1.0e-6) {
                    firstFarWater[0] = level.getGameTime();
                    MoonStation14.LOGGER.info("REACTIVE_FRONT_FIRST_WATER tick={} cycle={} stage={} pos={} distance={} waterMol={} T={}",
                            level.getGameTime(), cycle, stage, p, p.distManhattan(source), gas.moles(GasType.WATER_VAPOR),
                            gas.temperatureKelvin());
                }
                previousWater[i] = gas.moles(GasType.WATER_VAPOR);
            }
            GasMixture far = service.sample(level, cells.get(99)).orElseThrow();
            double baselineFarTemp = 293.15;
            if (Double.isNaN(firstFarTempDelta[0]) && far.temperatureKelvin() > baselineFarTemp + 1.0)
                firstFarTempDelta[0] = level.getGameTime();
            if (!stage.equals(lastStage[0]) || cycle != lastCycles[0]) {
                MoonStation14.LOGGER.info("REACTIVE_FRONT_TRACE tick={} stage={} cycle={} maxWaterMol={} farWaterMol={} farTK={} maxTK={} sourceIgnitions={}",
                        level.getGameTime(), stage, cycle, maxWater, far.moles(GasType.WATER_VAPOR),
                        far.temperatureKelvin(), maxTemp, service.diagnosticFirstIgnitions(level).size());
                // Bounded trace: emit per-cell rows only when gas, hotspot metadata, or ignition history changes.
                for (int i = 0; i < cells.size(); i++) {
                    BlockPos p = cells.get(i);
                    GasMixture gas = service.sample(level, p).orElse(null);
                    if (gas != null) {
                        HotspotState hotspot = service.diagnosticHotspot(level, p).orElse(null);
                        int ignitionCount = service.diagnosticIgnitions(level, p).size();
                        if (!sameGas(previousTraceGas[i], gas) || !java.util.Objects.equals(previousTraceHotspot[i], hotspot)
                                || previousTraceIgnitions[i] != ignitionCount) {
                            MoonStation14.LOGGER.info("REACTIVE_FRONT_CELL tick={} cycle={} stage={} pos={} d={} tri={} o2={} water={} T={} ignitionCount={}",
                                    level.getGameTime(), cycle, stage, p, p.distManhattan(source),
                                    gas.moles(GasType.TRITIUM), gas.moles(GasType.OXYGEN),
                                    gas.moles(GasType.WATER_VAPOR), gas.temperatureKelvin(), ignitionCount);
                            if (hotspot != null)
                                MoonStation14.LOGGER.info("REACTIVE_FRONT_HOTSPOT tick={} cycle={} pos={} volumeL={} T={} skippedFirstProcess={} bypass={}",
                                        level.getGameTime(), cycle, p, hotspot.volumeLiters(), hotspot.temperatureKelvin(),
                                        hotspot.skippedFirstProcess(), hotspot.bypass());
                        }
                        previousTraceGas[i] = gas;
                        previousTraceHotspot[i] = hotspot;
                        previousTraceIgnitions[i] = ignitionCount;
                    }
                }
                lastStage[0] = stage;
                lastCycles[0] = cycle;
            }
            if ("hotspots".equals(processedStage)) {
                HotspotState hotspot = service.diagnosticHotspot(level, source).orElse(null);
                boolean eligibleBefore = sourceHotspotBefore != null && sourceHotspotBefore.skippedFirstProcess()
                        && sourceHotspotBefore.volumeLiters() > 0.0;
                long sourceCommitStampAfter = service.diagnosticHotspotCommitStep(level, source);
                boolean committedSourceProcessing = eligibleBefore && hotspot != null
                        && hotspot.skippedFirstProcess() && sourceCommitStampAfter != Long.MIN_VALUE
                        && sourceCommitStampAfter != sourceCommitStampBefore;
                if (committedSourceProcessing
                        && lastActiveHotspotCycle[0] != processedCycle) {
                    lastActiveHotspotCycle[0] = processedCycle;
                    activeProcessedHotspotCycles[0]++;
                }
            }
            if (runtime != null && runtime.groupAverages() > lastGroupAverages[0]) {
                sawGroupBreakdown[0] = true;
                if (Double.isNaN(firstGroupAverageTick[0])) firstGroupAverageTick[0] = level.getGameTime();
                boolean sourceStillHotBeforeGroups = sourceHotspotBefore != null
                        && sourceHotspotBefore.skippedFirstProcess() && sourceHotspotBefore.volumeLiters() > 0.0;
                if ("groups".equals(processedStage)
                        && lastActiveHotspotCycle[0] == processedCycle - 1
                        && sourceStillHotBeforeGroups)
                    throw new GameTestAssertException("group average occurred in cycle " + processedCycle
                            + " while source hotspot remained active after its prior-cycle process");
                MoonStation14.LOGGER.info("REACTIVE_FRONT_GROUP_BREAKDOWN tick={} cycle={} stage={} groupAverages={} groupOps={} members={}",
                        level.getGameTime(), cycle, stage, runtime.groupAverages(), runtime.groupOperations(),
                        runtime.groupMembersCaptured());
            }
            if (runtime != null) lastGroupAverages[0] = runtime.groupAverages();
            frames[0]++;
        }).thenExecute(() -> {
            AtmosphereRuntimeTelemetry.Snapshot runtime = service.diagnosticRuntime(level);
            MoonStation14.LOGGER.info("REACTIVE_FRONT_RESULT elapsedTicks={} frames={} firstFarWaterElapsed={} firstFarTempElapsed={} firstGroupAverageElapsed={} maxWaterDistance={} activeHotspotCycles={} cycles={} groupOps={} groupMembers={} groupAverages={} groupUnavailable={} sawBreakdown={} ignitions={}",
                    level.getGameTime() - startTick, frames[0],
                    Double.isNaN(firstFarWater[0]) ? "never" : firstFarWater[0] - startTick,
                    Double.isNaN(firstFarTempDelta[0]) ? "never" : firstFarTempDelta[0] - startTick,
                    Double.isNaN(firstGroupAverageTick[0]) ? "never" : firstGroupAverageTick[0] - startTick,
                    maximumWaterDistance[0], activeProcessedHotspotCycles[0],
                    runtime == null ? -1 : runtime.completedCycles(), runtime == null ? -1 : runtime.groupOperations(),
                    runtime == null ? -1 : runtime.groupMembersCaptured(), runtime == null ? -1 : runtime.groupAverages(),
                    runtime == null ? -1 : runtime.groupUnavailable(), sawGroupBreakdown[0],
                    service.diagnosticFirstIgnitions(level).size());
            if (service.diagnosticFirstIgnitions(level).isEmpty())
                throw new GameTestAssertException("balanced hot tritium/oxygen source produced no ignition");
            if (activeProcessedHotspotCycles[0] < 2)
                throw new GameTestAssertException("source hotspot was not observed active across multiple processed cycles: "
                        + activeProcessedHotspotCycles[0]);
            if (requireSustainedHotspot && activeProcessedHotspotCycles[0] < 8)
                throw new GameTestAssertException("steady hotspot phase too short to exercise group cooldown: "
                        + activeProcessedHotspotCycles[0] + " active cycles");
            if (Double.isNaN(firstFarWater[0]))
                throw new GameTestAssertException("reactive front produced no distant water");
            if (runtime == null || runtime.groupAverages() == 0 || !sawGroupBreakdown[0])
                throw new GameTestAssertException("no excited-group breakdown observed; operations="
                        + (runtime == null ? -1 : runtime.groupOperations()) + ", averages="
                        + (runtime == null ? -1 : runtime.groupAverages()));
            if (firstFarWater[0] >= firstGroupAverageTick[0])
                throw new GameTestAssertException("distant water did not precede first group average: water="
                        + firstFarWater[0] + ", average=" + firstGroupAverageTick[0]);
            helper.succeed();
        });
    }

    private static boolean sameGas(GasMixture first, GasMixture second) {
        if (first == null || second == null) return first == second;
        if (Double.compare(first.temperatureKelvin(), second.temperatureKelvin()) != 0) return false;
        for (GasType type : GasType.values())
            if (Double.compare(first.moles(type), second.moles(type)) != 0) return false;
        return true;
    }
}
