package com.juicyslew.moonstation14.ms14.atmos.world;

import net.minecraft.core.BlockPos;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.core.ImmutableAtmosphereBoundary;
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
                            : AtmosphereService.PatchKernel.AMBIENT_FLOW,
                    AtmosphereService.chooseKernelForPatch(Set.of(new BlockPos(4, 10, 2)), vacuumDimension));
        }
    }

    @Test
    void changedBreathablePatchDefersLocalBoundaryExchangeUntilNextCycle() {
        BlockPos finite = new BlockPos(0, 1, 0);
        BlockPos exterior = finite.east();
        assertEquals(AtmosphereService.PatchKernel.AMBIENT_FLOW,
                AtmosphereService.chooseKernelForPatch(Set.of(exterior), false));
        AtmosphereService.WorkQueue queue = new AtmosphereService.WorkQueue();
        Set<BlockPos> handled = new java.util.HashSet<>();
        AtmosphereService.markMonstermosPatchHandled(List.of(finite), handled, queue);
        List<BlockPos> deferred = new ArrayList<>();
        assertTrue(AtmosphereService.deferHandledLindaSource(queue.poll(), handled, deferred));
        AtmosphereService.requeueDeferredSources(queue, deferred);
        assertEquals(finite, queue.poll(), "the finite source remains eligible for the next local pass");

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
    void boundaryCellCanExchangeOnlyOncePerCycleButDistinctOpeningCellsRemainEligible() {
        Set<BlockPos> handled = new java.util.HashSet<>();
        BlockPos oneOpeningCell = new BlockPos(0, 1, 0);
        BlockPos anotherOpeningCell = new BlockPos(1, 1, 0);
        assertTrue(AtmosphereService.claimBoundaryCellForCycle(handled, oneOpeningCell));
        assertFalse(AtmosphereService.claimBoundaryCellForCycle(handled, oneOpeningCell),
                "a second exterior face on the same finite cell must not drain again this cycle");
        assertTrue(AtmosphereService.claimBoundaryCellForCycle(handled, anotherOpeningCell),
                "distinct boundary cells still contribute to opening-area dependence");
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
    void monstermosHandledSourcesDeferWithoutBlockingOtherLindaSources() {
        BlockPos patched = new BlockPos(0, 0, 0);
        BlockPos otherPatched = new BlockPos(0, 1, 0);
        BlockPos outsidePatch = new BlockPos(1, 0, 0);
        AtmosphereService.WorkQueue queue = new AtmosphereService.WorkQueue();
        Set<BlockPos> handled = new java.util.HashSet<>();
        AtmosphereService.markMonstermosPatchHandled(List.of(patched, otherPatched), handled, queue);
        queue.offer(outsidePatch);
        List<BlockPos> deferred = new ArrayList<>();
        assertEquals(Set.of(patched, otherPatched), handled,
                "all cells of a changed Monstermos patch must be protected from same-tick LINDA work");

        BlockPos selected = null;
        while (selected == null && !queue.isEmpty()) {
            BlockPos candidate = queue.poll();
            if (!AtmosphereService.deferHandledLindaSource(candidate, handled, deferred)) selected = candidate;
        }
        assertEquals(outsidePatch, selected,
                "LINDA may process an adjacent finite source outside the Monstermos patch");

        AtmosphereService.requeueDeferredSources(queue, deferred);
        assertEquals(patched, queue.poll(), "the Monstermos patch must be eligible on the next stage");
        assertEquals(otherPatched, queue.poll(), "every changed patch cell must remain scheduled");
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

    @Test
    void excitedGroupCadenceUsesPassedAtmosphereTimeAndDefersMonstermosConflicts() {
        var firstStage = AtmosphereService.excitedCycleSchedule(4, 12, false);
        var secondStage = AtmosphereService.excitedCycleSchedule(8, firstStage.nextDue(), false);
        assertFalse(firstStage.advance());
        assertFalse(secondStage.advance());
        assertEquals(12, secondStage.nextDue());

        var due12 = AtmosphereService.excitedCycleSchedule(12, secondStage.nextDue(), false);
        assertTrue(due12.advance());
        assertEquals(24, due12.nextDue());
        var duplicateTime = AtmosphereService.excitedCycleSchedule(12, due12.nextDue(), false);
        assertFalse(duplicateTime.advance(), "repeating the supplied time must not advance twice");

        var due24 = AtmosphereService.excitedCycleSchedule(24, due12.nextDue(), false);
        assertTrue(due24.advance());
        assertEquals(36, due24.nextDue());

        var blockedDueStage = AtmosphereService.excitedCycleSchedule(12, secondStage.nextDue(), true);
        assertFalse(blockedDueStage.advance());
        assertEquals(12, blockedDueStage.nextDue(), "a conflicting Monstermos pass leaves the cycle pending");
        var delayedStage = AtmosphereService.excitedCycleSchedule(16, blockedDueStage.nextDue(), false);
        assertTrue(delayedStage.advance());
        assertEquals(28, delayedStage.nextDue());
        assertFalse(AtmosphereService.excitedCycleSchedule(16, delayedStage.nextDue(), false).advance());
    }
}
