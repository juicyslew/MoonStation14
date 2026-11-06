package com.juicyslew.moonstation14.entities;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LoosePersistenceWatchTest {
    private LooseMotionDiagnostics.PersistenceWatch watch;
    @AfterEach void closeWatch() { if (watch != null) watch.close(); }

    @Test void exactOpaqueScopeAndUuidFilterAndZeroCounts() {
        Object scope = new Object(), otherScope = new Object();
        UUID id = UUID.randomUUID(), other = UUID.randomUUID();
        watch = LooseMotionDiagnostics.watchPersistenceForTest(scope, List.of(id, UUID.randomUUID(), UUID.randomUUID()));
        var tag = new CompoundTag(); tag.putString("Pos", "natural");
        watch.capture(otherScope, id, LooseMotionDiagnostics.PersistenceWatch.Phase.SAVE, "p", "v", tag, true, false, "null");
        watch.capture(scope, other, LooseMotionDiagnostics.PersistenceWatch.Phase.SAVE, "p", "v", tag, true, false, "null");
        var snapshot = watch.snapshot();
        assertTrue(snapshot.events().isEmpty());
        assertEquals(3, snapshot.counts().size());
        assertTrue(snapshot.counts().values().stream().allMatch(c -> c.equals(new LooseMotionDiagnostics.PersistenceWatch.Counts(0, 0, 0, 0, 0, 0))));
        assertTrue(snapshot.coverageComplete());
    }

    @Test void phasesFieldsSnapshotsAreImmutableAndMissingOrWrongTypesAreVisible() {
        Object scope = new Object(); UUID id = UUID.randomUUID();
        watch = LooseMotionDiagnostics.watchPersistenceForTest(scope, List.of(id));
        CompoundTag tag = new CompoundTag(); tag.putString("Pos", "wrong-type"); tag.putString("LooseSettled", "also-wrong");
        tag.putString("Item", "known-stack");
        watch.capture(scope, id, LooseMotionDiagnostics.PersistenceWatch.Phase.SAVE, "pos", "delta", tag, true, false, "null");
        watch.capture(scope, id, LooseMotionDiagnostics.PersistenceWatch.Phase.READ, "pos2", "delta2", new CompoundTag(), false, true, "DISCARDED");
        watch.capture(scope, id, LooseMotionDiagnostics.PersistenceWatch.Phase.JOIN, "pos3", "delta3", null, false, false, "null");
        watch.captureObservation(scope, id, LooseMotionDiagnostics.PersistenceWatch.Phase.POST_JOIN, "pos4", "delta4",
                "settled=true frame=frame storage=point lookup=same-ref reagents=water:500", true, false, "null");
        watch.captureObservation(scope, id, LooseMotionDiagnostics.PersistenceWatch.Phase.IMPACT_GATE, "pos5", "delta5",
                "threshold=0.5 approach=1 requested=0,-1,0 accepted=0,-0.2,0 hits=false,true,false fresh=true", true, false, "null");
        watch.captureObservation(scope, id, LooseMotionDiagnostics.PersistenceWatch.Phase.SPILL, "pos6", "delta6",
                "outcome=SPILL_RESULT_PARTIAL_KEPT landing=0,1,0 remainder=water:100", true, false, "null");
        tag.putString("Item", "mutated-after-capture");
        var snapshot = watch.snapshot();
        assertEquals(new LooseMotionDiagnostics.PersistenceWatch.Counts(1, 1, 1, 1, 1, 1), snapshot.counts().get(id));
        assertTrue(snapshot.events().get(0).fields().matches("(?s).*Pos\\[\\d+\\]=.*"));
        assertTrue(snapshot.events().get(0).fields().contains("Pos[") && snapshot.events().get(0).fields().contains("wrong-type"), snapshot.events().get(0).fields());
        assertTrue(snapshot.events().get(0).fields().matches("(?s).*LooseSettled\\[\\d+\\]=.*"));
        assertTrue(snapshot.events().get(0).fields().contains("LooseSettled[") && snapshot.events().get(0).fields().contains("also-wrong"), snapshot.events().get(0).fields());
        assertTrue(snapshot.events().get(0).fields().contains("LooseFrameOrientation=MISSING"));
        assertTrue(snapshot.events().get(0).fields().contains("Item[") && snapshot.events().get(0).fields().contains("known-stack"), snapshot.events().get(0).fields());
        assertEquals("tag=NOT_APPLICABLE", snapshot.events().get(2).fields());
        assertTrue(snapshot.events().get(3).fields().contains("storage=point"));
        assertTrue(snapshot.events().get(4).fields().contains("threshold=0.5"));
        assertTrue(snapshot.events().get(5).fields().contains("SPILL_RESULT_PARTIAL_KEPT"));
        assertFalse(snapshot.events().get(1).nonempty());
        assertTrue(snapshot.events().get(1).removed());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.events().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.counts().clear());
        assertTrue(snapshot.coverageComplete());
    }

    @Test void eventBudgetAndTruncationExplicitlyInvalidateCoverage() {
        Object scope = new Object(); UUID id = UUID.randomUUID();
        watch = LooseMotionDiagnostics.watchPersistenceForTest(scope, List.of(id));
        CompoundTag tag = new CompoundTag(); tag.putString("Item", "x".repeat(3000));
        for (int i = 0; i < LooseMotionDiagnostics.PERSISTENCE_CAPACITY + 1; i++) watch.capture(scope, id, LooseMotionDiagnostics.PersistenceWatch.Phase.SAVE,
                "p", "v", tag, true, false, "null");
        var snapshot = watch.snapshot();
        assertEquals(LooseMotionDiagnostics.PERSISTENCE_CAPACITY, snapshot.events().size());
        assertTrue(snapshot.overflow());
        assertTrue(snapshot.fieldCaptureIncomplete());
        assertFalse(snapshot.coverageComplete());
        assertTrue(snapshot.events().getFirst().fields().contains("<TRUNCATED>"));
    }

    @Test void postJoinObservationRequiresJoinAndLostIntervalIsExplicit() {
        Object scope = new Object(); UUID id = UUID.randomUUID();
        watch = LooseMotionDiagnostics.watchPersistenceForTest(scope, List.of(id));
        watch.captureObservation(scope, id, LooseMotionDiagnostics.PersistenceWatch.Phase.POST_JOIN,
                "p", "v", "must-not-appear", true, false, "null");
        assertTrue(watch.snapshot().events().isEmpty());
        watch.capture(scope, id, LooseMotionDiagnostics.PersistenceWatch.Phase.JOIN, "p", "v", null, true, false, "null");
        watch.markPostJoinCoverageLost(id, "motion-watch-capacity-eviction");
        watch.captureObservation(scope, id, LooseMotionDiagnostics.PersistenceWatch.Phase.POST_JOIN,
                "p", "v", "x".repeat(3000), true, false, "null");
        var snapshot = watch.snapshot();
        assertFalse(snapshot.coverageComplete());
        assertTrue(snapshot.fieldCaptureIncomplete());
        assertTrue(snapshot.postJoinCoverageLost());
        assertTrue(snapshot.events().getLast().fields().contains("<TRUNCATED>"));
        assertTrue(snapshot.coverageReasons().getFirst().contains("motion-watch-capacity-eviction"));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.coverageReasons().clear());
    }

    @Test void loadedScopeExitLosesCoverageButCapturedRemovalIsTerminalComplete() {
        Object scope = new Object(); UUID id = UUID.randomUUID();
        watch = LooseMotionDiagnostics.watchPersistenceForTest(scope, List.of(id));
        watch.capture(scope, id, LooseMotionDiagnostics.PersistenceWatch.Phase.JOIN, "p", "v", null, true, false, "null");
        watch.captureObservation(scope, id, LooseMotionDiagnostics.PersistenceWatch.Phase.POST_JOIN,
                "p", "v", "lookup=null loaded=true", true, false, "null");
        watch.finishPostJoin(id, "entity-position-no-longer-loaded", true);
        var departed = watch.snapshot();
        assertFalse(departed.coverageComplete());
        assertEquals("entity-position-no-longer-loaded", departed.postJoinEnd().get(id));
        watch.close();

        UUID removedId = UUID.randomUUID();
        watch = LooseMotionDiagnostics.watchPersistenceForTest(scope, List.of(removedId));
        watch.capture(scope, removedId, LooseMotionDiagnostics.PersistenceWatch.Phase.JOIN, "p", "v", null, true, false, "null");
        watch.captureObservation(scope, removedId, LooseMotionDiagnostics.PersistenceWatch.Phase.POST_JOIN,
                "p", "v", "lookup=null removed=true", true, true, "UNLOADED_TO_CHUNK");
        watch.finishPostJoin(removedId, "captured-removal:UNLOADED_TO_CHUNK", false);
        var removed = watch.snapshot();
        assertTrue(removed.coverageComplete());
        assertEquals("captured-removal:UNLOADED_TO_CHUNK", removed.postJoinEnd().get(removedId));
    }

    @Test void clippedImpactCandidatesKeepExactThresholdEligibilityAndCounts() {
        Object scope = new Object(); UUID id = UUID.randomUUID();
        watch = LooseMotionDiagnostics.watchPersistenceForTest(scope, List.of(id));
        double[] speeds = {.499, .5, .501};
        boolean[] expected = {false, true, true};
        for (int i = 0; i < speeds.length; i++) {
            boolean eligible = LooseMotionDiagnostics.impactEligible(speeds[i], .5, true);
            assertEquals(expected[i], eligible);
            watch.captureObservation(scope, id, LooseMotionDiagnostics.PersistenceWatch.Phase.IMPACT_GATE,
                    "position", "velocity", "clippedOwnTravel=true breakOnImpact=true threshold=0.5 approach="
                            + speeds[i] + " eligible=" + eligible + " requested=0,-1,0 accepted=0,-0.2,0",
                    true, false, "null");
        }
        assertFalse(LooseMotionDiagnostics.impactEligible(2, .5, false));
        watch.captureObservation(scope, id, LooseMotionDiagnostics.PersistenceWatch.Phase.IMPACT_GATE,
                "position", "velocity", "clippedOwnTravel=true breakOnImpact=false threshold=NOT_EVALUATED eligible=false",
                true, false, "null");
        var snapshot = watch.snapshot();
        assertEquals(4, snapshot.counts().get(id).impacts());
        assertTrue(snapshot.events().get(0).fields().contains("eligible=false"));
        assertTrue(snapshot.events().get(1).fields().contains("eligible=true"));
        assertTrue(snapshot.events().get(2).fields().contains("eligible=true"));
        assertTrue(snapshot.events().get(3).fields().contains("threshold=NOT_EVALUATED"));
        assertTrue(snapshot.coverageComplete());
    }

    @Test void closeIsTerminalAndStaleCloseCannotClearReplacement() {
        Object scope = new Object(); UUID id = UUID.randomUUID();
        watch = LooseMotionDiagnostics.watchPersistenceForTest(scope, List.of(id));
        var old = watch; old.close();
        var replacement = LooseMotionDiagnostics.watchPersistenceForTest(scope, List.of(id));
        watch = replacement;
        old.close();
        replacement.capture(scope, id, LooseMotionDiagnostics.PersistenceWatch.Phase.READ, "p", "v", new CompoundTag(), false, false, "null");
        assertEquals(1, replacement.snapshot().events().size());
        assertTrue(old.snapshot().closed());
    }
}
