package com.juicyslew.moonstation14.ms14.atmos.world;

import net.minecraft.core.BlockPos;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.core.ImmutableAtmosphereBoundary;
import com.juicyslew.moonstation14.ms14.atmos.core.LindaGasSharing;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AtmospherePatchKernelSelectionTest {
    @Test
    void onlyVerifiedExteriorFacesInVacuumDimensionsSelectSpaceFlow() {
        for (boolean vacuumDimension : new boolean[] {false, true}) {
            assertEquals(AtmosphereService.PatchKernel.NORMAL_EQUALIZATION,
                    AtmosphereService.chooseKernelForPatch(Set.of(), vacuumDimension));
            assertEquals(vacuumDimension ? AtmosphereService.PatchKernel.SPACE_FLOW
                            : AtmosphereService.PatchKernel.NORMAL_EQUALIZATION,
                    AtmosphereService.chooseKernelForPatch(Set.of(new BlockPos(4, 10, 2)), vacuumDimension));
        }
    }

    @Test
    void changedBreathablePatchRemainsEligibleForSameCycleLinda() {
        BlockPos finite = new BlockPos(0, 1, 0);
        BlockPos exterior = finite.east();
        assertEquals(AtmosphereService.PatchKernel.NORMAL_EQUALIZATION,
                AtmosphereService.chooseKernelForPatch(Set.of(exterior), false));
        AtmosphereService.WorkQueue queue = new AtmosphereService.WorkQueue();
        Set<BlockPos> handled = new java.util.HashSet<>();
        AtmosphereService.markMonstermosPatchHandled(List.of(finite), handled, queue, false);
        AtmosphereCycle cycle = new AtmosphereCycle();
        cycle.startLinda(queue.snapshotPositions());
        assertEquals(Set.of(finite), cycle.lindaSources(), "normal Monstermos cells are eligible immediately");

        var exchange = ImmutableAtmosphereBoundary.exchange(GasMixture.vacuum(),
                GasMixture.breathableAir(), 0.125, false);
        assertTrue(exchange.finiteAfter().moles(GasType.OXYGEN) > 0.0);
        assertTrue(exchange.finiteAfter().moles(GasType.NITROGEN) > 0.0);
    }

    @Test
    void patchTopologyMustRemainFaceConnectedBeforeCommit() {
        BlockPos first = new BlockPos(0, 0, 0);
        BlockPos second = new BlockPos(1, 0, 0);
        BlockPos third = new BlockPos(5, 0, 0);
        assertTrue(AtmosphereService.isConnected(List.of(first, second),
                Map.of(first, Set.of(second), second, Set.of(first))));
        assertFalse(AtmosphereService.isConnected(List.of(first, third),
                Map.of(first, Set.of(), third, Set.of())));
    }

    @Test
    void lindaSpaceSourceRetainsEveryImmutableExteriorFaceAfterSpaceFlowHandling() {
        BlockPos finite = new BlockPos(0, 1, 0);
        Set<BlockPos> spaceHandled = Set.of(finite);
        Map<BlockPos, GasMixture> live = new HashMap<>();
        GasMixture finiteGas = new GasMixture(Map.of(GasType.OXYGEN, 8.0), 293.15);
        live.put(finite, finiteGas);
        Map<BlockPos, GasMixture> archive = new HashMap<>(live);
        Map<BlockPos, Set<BlockPos>> adjacency = new HashMap<>();
        adjacency.put(finite, new java.util.LinkedHashSet<>());
        GasMixture vacuum = GasMixture.vacuum();
        for (BlockPos exterior : List.of(finite.above(), finite.below(), finite.east()))
            AtmosphereService.connectImmutableBoundary(archive, adjacency, finite, exterior, vacuum);

        var result = LindaGasSharing.process(live, archive, adjacency, Set.of(finite), Set.of(), 1);

        assertTrue(spaceHandled.contains(finite), "Space-flow diagnostics remain independent of LINDA");
        assertEquals(3, result.sharedPairs().size(), "every distinct loaded immutable face participates");
        assertTrue(result.mixtures().get(finite).moles(GasType.OXYGEN) < finiteGas.moles(GasType.OXYGEN));
    }

    @Test
    void lindaImmutableBoundaryLedgerUsesCommittedFiniteBeforeAfterDelta() {
        BlockPos finite = new BlockPos(0, 1, 0);
        GasMixture before = new GasMixture(Map.of(GasType.OXYGEN, 2.0), 293.15);
        GasMixture exported = new GasMixture(Map.of(GasType.OXYGEN, 1.5), 280.0);
        var export = AtmosphereService.lindaBoundaryExchange(Map.of(finite, before), Map.of(finite, exported));
        assertEquals(0.5, export.speciesMoles().get(GasType.OXYGEN), 1e-6);
        assertEquals(before.thermalEnergy() - exported.thermalEnergy(), export.energyJoules(), 1e-6);

        var imported = AtmosphereService.lindaBoundaryExchange(Map.of(finite, GasMixture.vacuum()),
                Map.of(finite, before));
        assertTrue(imported.speciesMoles().get(GasType.OXYGEN) < 0.0);
    }

    @Test
    void failedPatchWriteRollsBackEarlierWritesAndDoesNotPublishLedger() {
        BlockPos first = new BlockPos(0, 0, 0);
        BlockPos second = new BlockPos(1, 0, 0);
        GasMixture beforeState = new GasMixture(Map.of(com.juicyslew.moonstation14.ms14.atmos.core.GasType.OXYGEN, 10.0), 300);
        GasMixture afterState = GasMixture.vacuum();
        Map<BlockPos, GasMixture> before = Map.of(first, beforeState, second, beforeState);
        Map<BlockPos, GasMixture> after = Map.of(first, afterState, second, afterState);
        Map<BlockPos, GasMixture> world = new HashMap<>(before);
        List<BlockPos> writes = new ArrayList<>();
        boolean[] publishLedger = {false};

        boolean committed = AtmosphereService.applyPatchTransaction(List.of(first, second), before, after,
                (pos, state) -> {
                    writes.add(pos);
                    if (pos.equals(second) && state == afterState) return false;
                    world.put(pos, state);
                    return true;
                }, () -> publishLedger[0] = true);

        assertFalse(committed);
        assertEquals(before, world);
        assertFalse(publishLedger[0]);
        assertEquals(List.of(first, second, first), writes,
                "successful earlier writes must be restored after the later write fails");
    }

    @Test
    void spaceAndNormalMonstermosSourcesRemainEligibleForSameCycleLinda() {
        BlockPos patched = new BlockPos(0, 0, 0);
        BlockPos otherPatched = new BlockPos(0, 1, 0);
        BlockPos outsidePatch = new BlockPos(1, 0, 0);
        AtmosphereService.WorkQueue queue = new AtmosphereService.WorkQueue();
        Set<BlockPos> handled = new java.util.HashSet<>();
        AtmosphereService.markMonstermosPatchHandled(List.of(patched), handled, queue, false);
        AtmosphereService.markMonstermosPatchHandled(List.of(otherPatched), handled, queue, true);
        AtmosphereService.markMonstermosPatchHandled(List.of(patched, otherPatched), handled, queue, true);
        queue.offer(outsidePatch);
        assertEquals(Set.of(patched, otherPatched), handled,
                "Space Monstermos handled bookkeeping remains available for diagnostics");
        AtmosphereCycle cycle = new AtmosphereCycle();
        cycle.startLinda(queue.snapshotPositions());
        assertEquals(Set.of(patched, otherPatched, outsidePatch), cycle.lindaSources(),
                "Space and normal Monstermos handled cells are not filtered from LINDA");
    }

    @Test
    void normalAndBoundaryPatchesBothRequeueForSameCycleLinda() {
        BlockPos normal = new BlockPos(4, 2, 1);
        BlockPos boundary = new BlockPos(5, 2, 1);
        AtmosphereService.WorkQueue queue = new AtmosphereService.WorkQueue();
        Set<BlockPos> handled = new java.util.HashSet<>();
        AtmosphereService.markMonstermosPatchHandled(List.of(normal), handled, queue, false);
        assertTrue(handled.isEmpty(), "normal finite patches are eligible for same-cycle LINDA/heat stages");
        AtmosphereService.markMonstermosPatchHandled(List.of(boundary), handled, queue, true);
        assertEquals(Set.of(boundary), handled, "boundary bookkeeping is retained for diagnostics");
        AtmosphereCycle cycle = new AtmosphereCycle();
        cycle.startLinda(queue.snapshotPositions());
        assertEquals(Set.of(normal, boundary), cycle.lindaSources());
    }

    @Test
    void throwingPatchWriterRollsBackPriorWritesAndDoesNotPublishLedger() {
        BlockPos first = new BlockPos(0, 0, 0);
        BlockPos second = new BlockPos(1, 0, 0);
        BlockPos third = new BlockPos(2, 0, 0);
        GasMixture beforeState = new GasMixture(Map.of(com.juicyslew.moonstation14.ms14.atmos.core.GasType.OXYGEN, 10.0), 300);
        GasMixture afterState = GasMixture.vacuum();
        Map<BlockPos, GasMixture> before = Map.of(first, beforeState, second, beforeState, third, beforeState);
        Map<BlockPos, GasMixture> after = Map.of(first, afterState, second, afterState, third, afterState);
        Map<BlockPos, GasMixture> world = new HashMap<>(before);
        boolean[] publishLedger = {false};
        RuntimeException writeFailure = new RuntimeException("second write failed");

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> AtmosphereService.applyPatchTransaction(List.of(first, second, third), before, after,
                        (pos, state) -> {
                            if (pos.equals(second) && state == afterState) throw writeFailure;
                            world.put(pos, state);
                            return true;
                        }, () -> publishLedger[0] = true));

        assertTrue(thrown == writeFailure, "the original write failure should propagate after rollback");
        assertEquals(before, world);
        assertFalse(publishLedger[0]);
        assertEquals(beforeState, world.get(first), "the first write should have been rolled back");
    }

    @Test
    void rollbackFailureIsExplicitAndPreservesOriginalWriteFailure() {
        BlockPos first = new BlockPos(0, 0, 0);
        BlockPos second = new BlockPos(1, 0, 0);
        GasMixture beforeState = new GasMixture(Map.of(com.juicyslew.moonstation14.ms14.atmos.core.GasType.OXYGEN, 10.0), 300);
        GasMixture afterState = GasMixture.vacuum();
        RuntimeException writeFailure = new RuntimeException("write failed");

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> AtmosphereService.applyPatchTransaction(List.of(first, second),
                        Map.of(first, beforeState, second, beforeState), Map.of(first, afterState, second, afterState),
                        (pos, state) -> {
                            if (pos.equals(second) && state == afterState) throw writeFailure;
                            if (pos.equals(first) && state == beforeState) throw new RuntimeException("rollback failed");
                            return true;
                        }, () -> { }));

        assertTrue(thrown.getMessage().contains("rollback failed"));
        assertEquals(1, thrown.getSuppressed().length);
        assertTrue(thrown.getSuppressed()[0] == writeFailure);
    }

}
