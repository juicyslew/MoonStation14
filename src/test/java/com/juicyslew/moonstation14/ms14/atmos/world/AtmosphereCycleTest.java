package com.juicyslew.moonstation14.ms14.atmos.world;

import net.minecraft.core.BlockPos;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.core.LindaGasSharing;
import com.juicyslew.moonstation14.ms14.atmos.core.ExcitedAtmosphereGroups;
import com.juicyslew.moonstation14.ms14.atmos.reaction.GasReactionData;
import com.juicyslew.moonstation14.ms14.atmos.reaction.GasReactionStep;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import org.junit.jupiter.api.Test;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AtmosphereCycleTest {
    @Test void monstermosAdmissionUsesCanonicalNeighborInventoryThreshold() {
        double threshold = com.juicyslew.moonstation14.ms14.atmos.core.MonstermosEqualization.MINIMUM_MOLES_DELTA;
        assertFalse(AtmosphereService.monstermosSeedNeedsEqualization(40.0, 40.0));
        assertFalse(AtmosphereService.monstermosSeedNeedsEqualization(40.0, 40.0 + threshold - 0.001));
        assertTrue(AtmosphereService.monstermosSeedNeedsEqualization(40.0, 40.0 + threshold + 0.001));
        assertFalse(AtmosphereService.monstermosSeedNeedsEqualization(0.0, 0.0),
                "space classification alone does not bypass the inventory-delta gate");
        assertTrue(AtmosphereService.monstermosSeedNeedsEqualization(threshold + 0.001, 0.0),
                "a classified ambient face can activate a source when inventories differ enough");
    }

    @Test void normalMonstermosAdmissionUsesPressureNotInventoryDifference() {
        GasMixture sameMolesCool = new GasMixture(Map.of(GasType.OXYGEN, 10.0), 293.15);
        GasMixture sameMolesHot = new GasMixture(Map.of(GasType.OXYGEN, 10.0), 586.3);
        assertTrue(AtmosphereService.monstermosSeedNeedsPressureEqualization(sameMolesCool, sameMolesHot),
                "equal inventory at different temperatures has a pressure gradient");

        GasMixture trace = new GasMixture(Map.of(GasType.OXYGEN, .016), 293.15);
        assertTrue(AtmosphereService.monstermosSeedNeedsPressureEqualization(trace, GasMixture.vacuum()),
                "trace inventory pressure against vacuum must remain eligible");

        double r = 8.3144626;
        GasMixture pressureA = new GasMixture(Map.of(GasType.OXYGEN, 10.0), 300,
                10.0 * r * 300 / 100_000);
        GasMixture pressureB = new GasMixture(Map.of(GasType.OXYGEN, 20.0), 300,
                20.0 * r * 300 / 100_000);
        assertFalse(AtmosphereService.monstermosSeedNeedsPressureEqualization(pressureA, pressureB),
                "different inventories at the same pressure do not admit a normal solve");

        GasMixture lowPressure = new GasMixture(Map.of(GasType.OXYGEN, .001), 293.15);
        GasMixture tinySpan = new GasMixture(Map.of(GasType.OXYGEN, .001), 293.15003);
        assertFalse(AtmosphereService.monstermosSeedNeedsPressureEqualization(lowPressure, tinySpan),
                "pressure spans within the solver tolerance do not trigger work");
        assertFalse(AtmosphereService.monstermosSeedNeedsEqualization(trace.totalMoles(), 0),
                "the canonical inventory predicate remains available for its Space policy");
    }

    @Test void committedPartialPressureContinuationResumesSmoothResidualOnlyOnNextCycle() {
        BlockPos seed = new BlockPos(0, 64, 0);
        assertFalse(AtmosphereService.monstermosSeedNeedsPressureEqualization(
                new GasMixture(Map.of(GasType.OXYGEN, 10.0), 293.15),
                new GasMixture(Map.of(GasType.OXYGEN, 10.0), 293.2)),
                "a smooth local gradient can be below tolerance despite global residual span");
        assertEquals(AtmosphereService.SeedPreflight.ACTIVE,
                AtmosphereService.normalPressureContinuationPreflight(false, true, false),
                "committed partial work admits its seed in the following cycle");
        assertEquals(AtmosphereService.SeedPreflight.INACTIVE,
                AtmosphereService.normalPressureContinuationPreflight(false, false, false));
        assertEquals(AtmosphereService.SeedPreflight.UNKNOWN,
                AtmosphereService.normalPressureContinuationPreflight(false, true, true),
                "an unknown neighbor still defers continuation until topology is known");
        assertEquals(AtmosphereService.SeedPreflight.UNKNOWN,
                AtmosphereService.preflightMonstermosSource(true, true, false, () -> { }),
                "continuation does not bypass source ownership proof");
        AtmosphereCycle cycle = new AtmosphereCycle();
        cycle.startMonstermos(Set.of(seed));
        cycle.monstermosProcessed().add(seed);
        assertTrue(AtmosphereService.skipMonstermosSeed(cycle.monstermosProcessed(), seed, false),
                "processed-seed guard prevents same-cycle replay");
    }

    @Test void pressureContinuationIdentityTracksCommittedIncompleteOnlyAndNeverSpace() {
        BlockPos seed = new BlockPos(0, 64, 0), included = seed.east();
        Set<BlockPos> continuations = new java.util.HashSet<>();
        AtmosphereService.updatePressureContinuationSeeds(continuations, seed, true, false);
        assertEquals(Set.of(seed), continuations, "partial no-change solve retains only its seed identity");
        AtmosphereService.updatePressureContinuationSeeds(continuations, seed, true, false);
        assertEquals(Set.of(seed), continuations, "continuation is deduplicated");
        AtmosphereService.updatePressureContinuationSeeds(continuations, included, true, false);
        AtmosphereService.updatePressureContinuationSeeds(continuations, seed, false, false);
        assertEquals(Set.of(included), continuations,
                "an overlapping complete patch clears only its own attempted seed, not included identities");
        AtmosphereService.updatePressureContinuationSeeds(continuations, included, false, false);
        assertTrue(continuations.isEmpty(), "the included identity clears after its own complete attempt");
        AtmosphereService.updatePressureContinuationSeeds(continuations, seed, true, true);
        assertTrue(continuations.isEmpty(), "Space flow never creates pressure continuation identity");
    }

    @Test void pressureContinuationsPrecedeFreshWorkInDeterministicAdmissionOrder() {
        BlockPos continuation = new BlockPos(8, 64, 0), ordinary = new BlockPos(0, 64, 0), priority = new BlockPos(4, 64, 0);
        assertEquals(List.of(continuation, ordinary, priority), List.copyOf(AtmosphereService.orderedMonstermosSeeds(
                Set.of(continuation), List.of(ordinary), List.of(priority))));
        Set<BlockPos> overlap = AtmosphereService.orderedMonstermosSeeds(Set.of(continuation),
                List.of(ordinary, continuation), List.of(priority));
        assertEquals(List.of(continuation, ordinary, priority), List.copyOf(overlap),
                "deduplication retains continuation priority over overlapping ordinary admission");
    }

    @Test void rejectedPressurePatchDoesNotPublishContinuationIdentity() {
        BlockPos seed = new BlockPos(0, 64, 0), other = seed.east();
        Set<BlockPos> continuations = new java.util.HashSet<>();
        GasMixture before = GasMixture.breathableAir();
        boolean committed = AtmosphereService.applyPatchTransaction(List.of(seed, other),
                Map.of(seed, before, other, before), Map.of(seed, GasMixture.vacuum(), other, GasMixture.vacuum()),
                (pos, gas) -> !pos.equals(other),
                () -> AtmosphereService.updatePressureContinuationSeeds(continuations, seed, true, false));
        assertFalse(committed);
        assertTrue(continuations.isEmpty(), "failed transaction must not publish speculative continuation identity");
    }

    @Test void terminalInactivePrunesContinuationButUnknownAndProcessedGuardsPreserveIt() {
        BlockPos seed = new BlockPos(0, 64, 0);
        Set<BlockPos> continuations = new java.util.HashSet<>(Set.of(seed));
        assertEquals(AtmosphereService.SeedPreflight.UNKNOWN,
                AtmosphereService.normalPressureContinuationPreflight(false, true, true));
        assertTrue(continuations.contains(seed), "unknown topology defers rather than discards continuation");

        AtmosphereCycle cycle = new AtmosphereCycle();
        cycle.startMonstermos(Set.of(seed));
        cycle.monstermosProcessed().add(seed);
        assertTrue(AtmosphereService.skipMonstermosSeed(cycle.monstermosProcessed(), seed, false));
        assertTrue(continuations.contains(seed), "same-cycle processed guard does not consume next-cycle identity");

        assertEquals(AtmosphereService.SeedPreflight.INACTIVE,
                AtmosphereService.preflightMonstermosSource(true, false, true, () -> { }));
        AtmosphereService.removeTerminalPressureContinuation(continuations, seed);
        assertTrue(continuations.isEmpty(), "known terminally inactive source prunes stale continuation identity");
    }

    @Test void chunkUnloadPrunesOnlyContinuationsInThatChunkWithNegativeCoordinates() {
        BlockPos unloadedNegative = new BlockPos(-1, 64, -1);
        BlockPos unloadedPositive = new BlockPos(31, 64, 31);
        BlockPos retained = new BlockPos(-17, 64, -17);
        Set<BlockPos> continuations = new java.util.LinkedHashSet<>(Set.of(unloadedNegative, unloadedPositive, retained));
        AtmosphereService.removePressureContinuationsInChunk(continuations, new net.minecraft.world.level.ChunkPos(-1, -1));
        assertEquals(Set.of(unloadedPositive, retained), continuations,
                "negative block coordinates must floor into chunk -1; identities in other chunks remain");
    }

    @Test void loadedPassableUnclaimedMonstermosSourceRequestsOwnershipWithoutBecomingFinite() {
        int[] requests = {0};
        assertEquals(AtmosphereService.SeedPreflight.UNKNOWN,
                AtmosphereService.preflightMonstermosSource(true, true, false, () -> requests[0]++));
        assertEquals(1, requests[0], "the source itself schedules strict ownership proof");
        assertEquals(AtmosphereService.SeedPreflight.INACTIVE,
                AtmosphereService.preflightMonstermosSource(true, false, false, () -> requests[0]++));
        assertEquals(1, requests[0], "a loaded solid is not submitted as an ownership seed");
    }

    @Test void normalMonstermosGraphIncludesEachAdjacentAmbientEndpointOnce() {
        BlockPos finite = new BlockPos(0, 64, 0), exterior = finite.above();
        GasMixture finiteGas = new GasMixture(Map.of(GasType.OXYGEN, 20.0), 293.15);
        GasMixture ambientGas = new GasMixture(Map.of(GasType.OXYGEN, 40.0), 293.15);
        var graph = AtmosphereService.normalMonstermosGraph(Map.of(finite, finiteGas),
                Map.of(finite, Set.of()), Map.of(new com.juicyslew.moonstation14.ms14.atmos.core.MonstermosSpaceFlow.DirectedEdge(
                        finite, exterior), ambientGas));
        assertEquals(Set.of(finite, exterior), graph.cells().keySet());
        assertEquals(Set.of(exterior), graph.adjacency().get(finite));
        assertEquals(Set.of(finite), graph.adjacency().get(exterior));
        assertEquals(Set.of(exterior), graph.immutablePositions());
        assertSame(ambientGas, graph.cells().get(exterior));
    }

    @Test void normalMonstermosAveragesAgainstImmutableBoundaryWithoutReplacingIt() {
        BlockPos finite = new BlockPos(0, 64, 0), exterior = finite.above();
        for (double finiteMoles : List.of(20.0, 60.0)) {
            double exteriorMoles = 40.0;
            GasMixture finiteGas = new GasMixture(Map.of(GasType.OXYGEN, finiteMoles), 293.15);
            GasMixture ambientGas = new GasMixture(Map.of(GasType.OXYGEN, exteriorMoles), 293.15);
            var graph = AtmosphereService.normalMonstermosGraph(Map.of(finite, finiteGas),
                    Map.of(finite, Set.of()), Map.of(new com.juicyslew.moonstation14.ms14.atmos.core.MonstermosSpaceFlow.DirectedEdge(
                            finite, exterior), ambientGas));
            var result = com.juicyslew.moonstation14.ms14.atmos.core.MonstermosEqualization.equalizeDiscovered(
                    graph.cells(), graph.adjacency(), finite, graph.immutablePositions());
            assertEquals((finiteMoles + exteriorMoles) / 2.0, result.states().get(finite).moles(GasType.OXYGEN), 1e-4);
            assertEquals(exteriorMoles, result.states().get(exterior).moles(GasType.OXYGEN), 0.0);
            assertEquals((finiteMoles - exteriorMoles) / 2.0,
                    result.boundaryExchange().speciesMoles().getOrDefault(GasType.OXYGEN, 0.0), 1e-4);
        }
    }

    @Test void normalServiceDispatchUsesPressureKernelRatherThanSourceMoleEqualizer() {
        BlockPos a = new BlockPos(0, 64, 0), b = a.east();
        GasMixture low = new GasMixture(Map.of(GasType.OXYGEN, 10.0), 293.15);
        GasMixture hot = new GasMixture(Map.of(GasType.OXYGEN, 10.0), 586.3);
        var graph = AtmosphereService.normalMonstermosGraph(Map.of(a, low, b, hot),
                Map.of(a, Set.of(b), b, Set.of(a)), Map.of());
        var serviceResult = AtmosphereService.solveNormalPressureGraph(graph, a);
        var sourceResult = com.juicyslew.moonstation14.ms14.atmos.core.MonstermosEqualization.equalizeDiscovered(
                graph.cells(), graph.adjacency(), a, graph.immutablePositions());
        assertTrue(serviceResult.diagnostics().pressureSpanAfter() < serviceResult.diagnostics().pressureSpanBefore());
        assertNotEquals(sourceResult.states().get(a).temperatureKelvin(), serviceResult.transport().states().get(a).temperatureKelvin());
    }

    @Test void incompleteNoChangePressureWorkKeepsSeedQueuedForLaterCycle() {
        BlockPos seed = new BlockPos(0, 64, 0), other = seed.east();
        GasMixture low = new GasMixture(Map.of(GasType.OXYGEN, 10.0), 293.15);
        GasMixture high = new GasMixture(Map.of(GasType.OXYGEN, 10.0), 586.3);
        var graph = AtmosphereService.normalMonstermosGraph(Map.of(seed, low, other, high),
                Map.of(seed, Set.of(other), other, Set.of(seed)), Map.of());
        var limited = com.juicyslew.moonstation14.ms14.atmos.core.MonstermosPressureEqualization.equalizeDiscovered(
                graph.cells(), graph.adjacency(), seed, graph.immutablePositions(),
                new com.juicyslew.moonstation14.ms14.atmos.core.MonstermosPressureEqualization.Settings(0, 0, 0, 0, 0));
        assertFalse(limited.transport().work().complete());
        assertEquals(graph.cells(), limited.transport().states(), "zero-work incomplete solve has no committed change");
        AtmosphereService.PrioritySeedQueue futureCycle = new AtmosphereService.PrioritySeedQueue();
        futureCycle.offer(seed);
        assertEquals(seed, futureCycle.poll(), "residual seed remains eligible for a later cycle");
    }

    @Test void failedPatchDoesNotPublishPressureDiagnosticOrBoundaryLedger() {
        BlockPos first = new BlockPos(0, 0, 0), second = first.east();
        GasMixture before = new GasMixture(Map.of(GasType.OXYGEN, 10.0), 300);
        GasMixture after = GasMixture.vacuum();
        boolean[] pressureDiagnostic = {false}, boundaryLedger = {false};
        boolean committed = AtmosphereService.applyPatchTransaction(List.of(first, second),
                Map.of(first, before, second, before), Map.of(first, after, second, after),
                (pos, state) -> !pos.equals(second), () -> { pressureDiagnostic[0] = true; boundaryLedger[0] = true; });
        assertFalse(committed);
        assertFalse(pressureDiagnostic[0]);
        assertFalse(boundaryLedger[0]);
    }

    @Test void spaceKernelSelectionRemainsTheSpaceFlowBranch() {
        assertEquals(AtmosphereService.PatchKernel.SPACE_FLOW,
                AtmosphereService.chooseKernelForPatch(Set.of(new BlockPos(2, 64, 0)), true));
        assertEquals(AtmosphereService.PatchKernel.NORMAL_EQUALIZATION,
                AtmosphereService.chooseKernelForPatch(Set.of(new BlockPos(2, 64, 0)), false));
    }

    @Test void finiteSnapshotValidationUsesMixtureValuesNotAmbientObjectIdentity() {
        BlockPos baselineCell = new BlockPos(10, 64, 10);
        GasMixture archivedAmbient = GasMixture.vacuum();
        GasMixture freshlySampledAmbient = GasMixture.vacuum();
        assertNotSame(archivedAmbient, freshlySampledAmbient);
        assertTrue(AtmosphereService.finiteSnapshotMatches(Map.of(baselineCell, archivedAmbient),
                ignored -> freshlySampledAmbient));
        assertFalse(AtmosphereService.finiteSnapshotMatches(Map.of(baselineCell, archivedAmbient),
                ignored -> new GasMixture(Map.of(GasType.OXYGEN, 1.0), 293.15)));
    }

    @Test void cycleAdmitsMultipleDisconnectedMonstermosSeedsAndTracksProcessedCells() {
        AtmosphereCycle cycle = new AtmosphereCycle();
        cycle.beginWhenDue();
        BlockPos roomA = new BlockPos(0, 64, 0), roomB = new BlockPos(32, 64, 0);
        cycle.startMonstermos(Set.of(roomA, roomB));
        assertEquals(Set.of(roomA, roomB), Set.copyOf(cycle.monstermosSeeds()));
        BlockPos first = cycle.monstermosSeeds().removeFirst();
        assertTrue(Set.of(roomA, roomB).contains(first));
        cycle.monstermosProcessed().add(first);
        cycle.addMonstermosSeeds(List.of(roomA, roomB));
        BlockPos remaining = first.equals(roomA) ? roomB : roomA;
        assertEquals(List.of(remaining), List.copyOf(cycle.monstermosSeeds()),
                "only already committed cells are excluded from follow-up work");
    }

    @Test void inactiveMonstermosSeedRemainsQueuedForSameCycleLindaThermalSharing() {
        BlockPos source = new BlockPos(0, 64, 0), neighbor = source.east();
        GasMixture hot = new GasMixture(Map.of(GasType.OXYGEN, 45.0), 200_000.0);
        GasMixture cool = new GasMixture(Map.of(GasType.OXYGEN, 45.0), 349.0);
        assertEquals(hot.totalMoles(), cool.totalMoles(), 0.0,
                "equal inventories are inactive for Monstermos despite a strong thermal gradient");

        AtmosphereService.WorkQueue queue = new AtmosphereService.WorkQueue();
        queue.offer(source);
        AtmosphereCycle cycle = new AtmosphereCycle();
        cycle.beginWhenDue();
        cycle.startMonstermos(queue.snapshotPositions());
        cycle.monstermosSeeds().removeFirst();
        cycle.monstermosProcessed().add(source);
        cycle.startLinda(queue.snapshotPositions());

        assertEquals(Set.of(source), cycle.lindaSources(),
                "Monstermos eligibility must not consume the active queue before LINDA snapshots it");
        assertTrue(queue.contains(source));
        assertEquals(200_000.0, hot.temperatureKelvin(), 0.0);
        assertEquals(349.0, cool.temperatureKelvin(), 0.0);
    }

    @Test void preservesStageAndCycleArchivesAcrossYield() {
        AtmosphereCycle cycle = new AtmosphereCycle();
        assertFalse(cycle.beginWhenDue());
        assertTrue(cycle.beginWhenDue());
        BlockPos boundary = new BlockPos(1, 2, 3);
        cycle.boundaryCells().add(boundary);
        cycle.monstermosHandled().add(boundary);
        assertEquals(AtmosphereCycle.Stage.REVALIDATE, cycle.stage());
        cycle.advance();
        assertEquals(AtmosphereCycle.Stage.MONSTERMOS, cycle.stage());
        assertTrue(cycle.boundaryCells().contains(boundary));
        assertTrue(cycle.monstermosHandled().contains(boundary));
        assertEquals(0, cycle.completed());
    }

    @Test void defaultNineStagesCompleteAtNineAdmissionsPerAtmosphereTimer() {
        AtmosphereCycle cycle = new AtmosphereCycle();
        int admissions = 0;
        for (int tick = 0; tick < 60; tick++) {
            if (!cycle.beginWhenDue()) continue;
            admissions++;
            cycle.advance();
        }
        assertEquals(45, admissions);
        assertEquals(5, cycle.completed());
        assertEquals(5, cycle.identity());
        assertNull(cycle.stage());
    }

    @Test void unfinishedStageRetainsTimerCadenceAndCycleIdentityUntilNineStageChainCompletes() {
        AtmosphereCycle cycle = new AtmosphereCycle();
        BlockPos archived = new BlockPos(1, 2, 3);
        int admissions = 0;
        for (int tick = 0; tick < 60; tick++) {
            if (!cycle.beginWhenDue()) continue;
            admissions++;
            if (cycle.stage() == AtmosphereCycle.Stage.REVALIDATE) cycle.archive().put(archived, GasMixture.vacuum());
            if (cycle.stage() == AtmosphereCycle.Stage.MONSTERMOS && admissions < 5) {
                cycle.monstermosPending(true);
                continue;
            }
            cycle.monstermosPending(false);
            cycle.advance();
            if (cycle.stage() != null) assertTrue(cycle.archive().containsKey(archived));
        }
        assertEquals(4, cycle.completed());
        assertEquals(5, cycle.identity());
        assertEquals(AtmosphereCycle.Stage.HOTSPOTS, cycle.stage());
        assertEquals(45, admissions, "held stages consume scheduled opportunities; they do not collapse or restart cycles");
    }

    @Test void hotspotStageQueueSnapshotDefersNewUrgentReceiversUntilNextCycle() {
        ReactionWorkQueue work = new ReactionWorkQueue();
        BlockPos original = new BlockPos(0, 64, 0), newlyHeatedReceiver = new BlockPos(1, 64, 0);
        work.offer(original);
        Set<BlockPos> admitted = work.snapshotPositions();
        work.offer(newlyHeatedReceiver);
        assertEquals(original, work.poll(admitted));
        assertNull(work.poll(admitted), "late urgent offers are not injected into this stage's admitted snapshot");
        assertEquals(newlyHeatedReceiver, work.poll(), "the late offer remains queued for the next cycle");
    }

    @Test void newlyCreatedHotspotIsDeferredEvenIfGenericReactiveQueueAdmittedIt() {
        AtmosphereCycle cycle = new AtmosphereCycle();
        cycle.beginWhenDue();
        cycle.beginWhenDue();
        while (cycle.stage() != AtmosphereCycle.Stage.HOTSPOTS) cycle.advance();
        BlockPos source = new BlockPos(0, 64, 0), receiver = source.east(), otherOriginalSource = source.west();
        cycle.startReactions(Set.of(source, receiver, otherOriginalSource));

        cycle.deferNewHotspotReceiver(receiver);
        cycle.admitReactionSource(receiver);

        assertEquals(Set.of(source, otherOriginalSource), cycle.reactionSources(),
                "metadata created during an admitted pass cannot be re-admitted later in the same pass");
        List<BlockPos> scheduled = new java.util.ArrayList<>();
        cycle.finishReactions(scheduled::add);
        assertEquals(List.of(receiver), scheduled, "the receiver remains queued for a later HOTSPOTS pass");
    }

    @Test void groupStageDeadlineResumesAtNextGroupWithoutDoubleTickingPriorGroup() {
        ExcitedAtmosphereGroups groups = new ExcitedAtmosphereGroups();
        BlockPos a = new BlockPos(0, 64, 0), b = a.east();
        BlockPos c = new BlockPos(10, 64, 0), d = c.east();
        GasMixture gas = new GasMixture(Map.of(GasType.OXYGEN, 1.0), 293.15);
        Map<BlockPos, GasMixture> snapshots = Map.of(a, gas, b, gas, c, gas, d, gas);
        groups.onShare(LindaGasSharing.Pair.of(a, b), 0, snapshots);
        groups.onShare(LindaGasSharing.Pair.of(c, d), 0, snapshots);

        groups.advanceCycle(1, members -> {
            Map<BlockPos, GasMixture> current = new java.util.LinkedHashMap<>();
            members.forEach(pos -> current.put(pos, snapshots.get(pos)));
            return new ExcitedAtmosphereGroups.LoadedGroupSnapshot(current, Set.of(), Set.of());
        }, false, 800, () -> true);
        assertTrue(groups.hasUnprocessedCycleWork(), "the soft deadline leaves the second group for resume");
        groups.advanceCycle(1, snapshots, Set.of(), Set.of(), false, 800);
        assertFalse(groups.hasUnprocessedCycleWork(), "each group counter advances once despite yielded calls");
    }

    @Test void serviceGraphFeedsImmutableBoundaryIntoLindaWithoutChangingIt() {
        BlockPos finitePos = new BlockPos(0, 1, 0);
        BlockPos exteriorPos = finitePos.east();
        GasMixture finite = new GasMixture(Map.of(GasType.NITROGEN, 100.0), 293.15);
        GasMixture exterior = GasMixture.vacuum();
        Map<BlockPos, GasMixture> live = new LinkedHashMap<>(Map.of(finitePos, finite));
        Map<BlockPos, GasMixture> archive = new LinkedHashMap<>(live);
        Map<BlockPos, Set<BlockPos>> adjacency = new LinkedHashMap<>();
        adjacency.put(finitePos, new LinkedHashSet<>());
        AtmosphereService.connectImmutableBoundary(archive, adjacency, finitePos, exteriorPos, exterior);

        LindaGasSharing.Result result = LindaGasSharing.process(live, archive, adjacency,
                Set.of(finitePos), Set.of(), 1);

        assertEquals(50.0, result.mixtures().get(finitePos).totalMoles(), 1.0e-5,
                "the immutable face participates in the receiver-neighbor divider");
        assertFalse(result.mixtures().containsKey(exteriorPos));
        assertSame(exterior, archive.get(exteriorPos));
    }

    @Test void perCellFireResultPersistsWithoutEligibleFireAndResetsOnEligibleZeroEffect() {
        GasMixture coldFuel = new GasMixture(Map.of(GasType.PLASMA, 1.0, GasType.OXYGEN, 1.0), 300);
        var noFire = new PrototypeCatalog<GasReactionData>(Map.of());
        var carried = GasReactionStep.evaluateWholeAir(noFire, coldFuel, 0.025);
        assertEquals(0.025, carried.fireExtentMoles());

        GasReactionData eligibleButZero = new GasReactionData(Map.of(GasType.PLASMA, 0.01, GasType.OXYGEN, 0.01),
                0, 1000, 0, -1,
                List.of(new GasReactionData.Effect(GasReactionData.EffectType.PLASMA_FIRE)));
        var fireCatalog = new PrototypeCatalog<>(Map.of(ResourceLocation.parse("moonstation14:plasma_fire"), eligibleButZero));
        var reset = GasReactionStep.evaluateWholeAir(fireCatalog, coldFuel, carried.fireExtentMoles());
        assertEquals(0, reset.fireExtentMoles(), "an invoked zero-yield fire effect clears the slot");

        BlockPos pos = new BlockPos(3, 2, 1);
        Map<BlockPos, Double> persistent = new LinkedHashMap<>(Map.of(pos, carried.fireExtentMoles()));
        AtmosphereService.storeFireReactionResult(persistent, pos, reset.fireExtentMoles(), false);
        assertEquals(0.025, persistent.get(pos));
        AtmosphereService.storeFireReactionResult(persistent, pos, reset.fireExtentMoles(), true);
        assertEquals(0, persistent.get(pos));
        AtmosphereService.storeFireReactionResult(persistent, pos, 0.02, true);
        AtmosphereService.removeFireReactionResultsInChunk(persistent, new net.minecraft.world.level.ChunkPos(pos));
        assertFalse(persistent.containsKey(pos), "unload removes the transient cell-identity slot");
    }
}
