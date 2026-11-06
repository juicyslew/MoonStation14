package com.juicyslew.moonstation14.ms14.shuttle.sable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery;
import static org.junit.jupiter.api.Assertions.*;

class FrameAnchorStateTest {
    private static final UUID FRAME = UUID.fromString("c8a4436e-0479-45ca-a6d8-2269973f1d51");
    private static final FrameAnchor VALUE = new FrameAnchor(FRAME, new Vec3(12.25, 4.5, -8.75));

    @Test void physicalCompositionCommitsAndRequiresExactCurrentIdentityAndPoint() {
        var c = new FrameAnchorState.PhysicalComposition(); Object level = new Object(), owner = new Object();
        Vec3 world = new Vec3(4, 5, 6); var after = new LoadedFrameRayQuery.ActorPoint(world, VALUE.storagePoint(), FRAME);
        c.observe(level, owner, world, after, true, true);
        assertEquals(VALUE, c.candidate(level, owner, world)); assertTrue(c.requiresCommittedPoint());
        assertNull(c.candidate(level, new Object(), world)); assertNull(c.candidate(new Object(), owner, world));
        assertNull(c.candidate(level, owner, new Vec3(4, 5.00001, 6)));
    }

    @Test void noFreshPreservesOnlySameExactPoseAndVerifiedWorldClears() {
        var c = new FrameAnchorState.PhysicalComposition(); Object level = new Object(), owner = new Object();
        Vec3 world = new Vec3(1, 2, 3), movedLocal = new Vec3(90, 80, 70);
        c.observe(level, owner, world, new LoadedFrameRayQuery.ActorPoint(world, VALUE.storagePoint(), FRAME), true, true);
        c.observe(level, owner, world, new LoadedFrameRayQuery.ActorPoint(new Vec3(9, 9, 9), movedLocal, FRAME), false, false);
        assertEquals(VALUE, c.candidate(level, owner, world));
        c.observe(level, null, world, new LoadedFrameRayQuery.ActorPoint(world, world, null), true, false);
        assertNull(c.candidate(level, owner, world)); assertFalse(c.requiresCommittedPoint()); assertNull(c.point());
    }

    @Test void unknownAndChangedIdentityOrWorldInvalidateWithoutFallback() {
        var c = new FrameAnchorState.PhysicalComposition(); Object level = new Object(), owner = new Object();
        Vec3 world = new Vec3(1, 2, 3); var frame = new LoadedFrameRayQuery.ActorPoint(world, VALUE.storagePoint(), FRAME);
        c.observe(level, owner, world, frame, true, true);
        c.observe(level, owner, world, null, true, true);
        assertNull(c.candidate(level, owner, world)); assertTrue(c.invalidated()); assertTrue(c.requiresCommittedPoint());
        c.observe(level, owner, world, frame, true, true);
        c.observe(new Object(), owner, world, frame, false, false);
        assertNull(c.candidate(level, owner, world));
    }

    @Test void freshValidatedOwnerChangeCanCommitAndMalformedProofCannot() {
        var c = new FrameAnchorState.PhysicalComposition(); Object level = new Object(), first = new Object(), next = new Object();
        Vec3 world = new Vec3(1, 2, 3); var frame = new LoadedFrameRayQuery.ActorPoint(world, VALUE.storagePoint(), FRAME);
        c.observe(level, first, world, frame, true, true);
        c.observe(level, next, world, frame, true, true);
        assertEquals(VALUE, c.candidate(level, next, world)); assertNull(c.candidate(level, first, world));
        assertEquals(2, c.sequence());
        c.observe(level, next, new Vec3(Double.NaN, 2, 3), frame, true, true);
        assertNull(c.candidate(level, next, world));
    }

    @Test void worldDepartureClearsEvenWithoutFreshCollisionAndFrameRequiresLevel() {
        var c = new FrameAnchorState.PhysicalComposition(); Object level = new Object(), owner = new Object();
        Vec3 world = new Vec3(1, 2, 3); var frame = new LoadedFrameRayQuery.ActorPoint(world, VALUE.storagePoint(), FRAME);
        c.observe(level, owner, world, frame, true, true);
        c.observe(level, null, world, new LoadedFrameRayQuery.ActorPoint(world, world, null), false, false);
        assertNull(c.point()); assertFalse(c.requiresCommittedPoint());
        c.observe(null, owner, world, frame, true, true);
        assertNull(c.point()); assertTrue(c.invalidated());
    }

    @Test void physicalCommitPhaseAdmitsNormalWorldButNotPendingInitialization() {
        assertTrue(FrameAnchorState.mayCommit(false));
        assertFalse(FrameAnchorState.mayCommit(true));
    }

    @Test void verifiedWorldSaveClearPreventsOldCandidateRevival() {
        var c = new FrameAnchorState.PhysicalComposition(); Object level=new Object(), owner=new Object();
        Vec3 world=new Vec3(1,2,3);
        c.observe(level,owner,world,new LoadedFrameRayQuery.ActorPoint(world,VALUE.storagePoint(),FRAME),true,true);
        c.clearWorld();
        assertNull(c.point()); assertFalse(c.requiresCommittedPoint());
        assertNull(c.candidate(level,owner,world));
    }

    @Test void readAlwaysResetsPhysicalComposition() {
        var state = new FrameAnchorState(); Object level = new Object(), owner = new Object(); Vec3 world = new Vec3(1, 2, 3);
        state.physicalComposition().observe(level, owner, world,
                new LoadedFrameRayQuery.ActorPoint(world, VALUE.storagePoint(), FRAME), true, true);
        state.read(new CompoundTag());
        assertNull(state.physicalComposition().candidate(level, owner, world));
        state.physicalComposition().observe(level, owner, world,
                new LoadedFrameRayQuery.ActorPoint(world, VALUE.storagePoint(), FRAME), true, true);
        CompoundTag bad = new CompoundTag(); bad.putString(FrameAnchor.KEY, "bad"); state.read(bad);
        assertNull(state.physicalComposition().point()); assertEquals(0, state.physicalComposition().sequence());
    }

    @Test void validRoundTripAndStrictInvalidInputs() {
        CompoundTag root = new CompoundTag(); root.put(FrameAnchor.KEY, VALUE.save());
        var state = new FrameAnchorState(); state.read(root);
        assertEquals(FrameAnchorState.Status.FRAME, state.status());
        assertEquals(VALUE, state.anchor());
        CompoundTag wrong = new CompoundTag(); wrong.putString(FrameAnchor.KEY, "bad");
        assertEquals(FrameAnchorState.Status.UNRESOLVED, load(wrong).status());
        CompoundTag missing = new CompoundTag(); missing.putUUID("Frame", FRAME); missing.putDouble("X", 1);
        CompoundTag invalid = new CompoundTag(); invalid.put(FrameAnchor.KEY, missing);
        assertEquals(FrameAnchorState.Status.UNRESOLVED, load(invalid).status());
        CompoundTag nonfinite = new CompoundTag(); CompoundTag data = VALUE.save(); data.putDouble("Y", Double.NaN);
        nonfinite.put(FrameAnchor.KEY, data);
        assertEquals(FrameAnchorState.Status.UNRESOLVED, load(nonfinite).status());
    }

    @Test void lifecycleDiagnosticBudgetIsBoundedAndOptInWithoutChangingAuthorityState() {
        var state = new FrameAnchorState();
        assertFalse(state.lifecycleDiagnosticsArmed());
        assertTrue(FrameAnchorState.diagnosticBudgetAllows(0, false));
        assertTrue(FrameAnchorState.diagnosticBudgetAllows(63, false));
        assertFalse(FrameAnchorState.diagnosticBudgetAllows(64, false));
        assertFalse(FrameAnchorState.diagnosticBudgetAllows(0, true));
        assertEquals(FrameAnchorState.Status.WORLD, state.status());
        assertFalse(state.pending());
        assertFalse(state.restoredFromFrame());
    }

    @Test void postRestoreMotionDiagnosticBudgetResetsOnReadAndRequiresSuccessfulRestoration() {
        var state = new FrameAnchorState();
        assertFalse(state.postRestoreMotionDiagnosticsArmed());
        assertTrue(FrameAnchorState.postRestoreMotionBudgetAllows(0, false));
        assertTrue(FrameAnchorState.postRestoreMotionBudgetAllows(63, false));
        assertFalse(FrameAnchorState.postRestoreMotionBudgetAllows(64, false));
        assertFalse(FrameAnchorState.postRestoreMotionBudgetAllows(0, true));
        CompoundTag tag = new CompoundTag(); tag.put(FrameAnchor.KEY, VALUE.save()); state.read(tag);
        assertFalse(state.postRestoreMotionDiagnosticsArmed());
        assertFalse(state.restoredFromFrame());
        state.read(new CompoundTag());
        assertFalse(state.postRestoreMotionDiagnosticsArmed());
    }

    @Test void postRestoreDiagnosticsArmAndCaptureAnchorOnlyAfterSuccessfulHydration() {
        CompoundTag tag = new CompoundTag(); tag.put(FrameAnchor.KEY, VALUE.save());
        var state = load(tag); state.armPostRestoreMotionDiagnostics(true, true);
        assertTrue(state.initializeAttempt(() -> true, () -> fail(), a -> { }));
        assertTrue(state.postRestoreMotionDiagnosticsArmed()); assertEquals(VALUE, state.postRestoreHydratedAnchor());
        assertEquals(1, state.nextPostRestoreMotionSample());
        state = load(tag); state.armPostRestoreMotionDiagnostics(true, true);
        assertFalse(state.initializeAttempt(() -> false, () -> { }, a -> { }));
        assertEquals(0, state.nextPostRestoreMotionSample()); assertNull(state.postRestoreHydratedAnchor());
    }

    @Test void diagnosticsDoNotArmForWorldUnresolvedOrDisabledScopeAndActor() {
        var world = new FrameAnchorState(); world.armPostRestoreMotionDiagnostics(true, true);
        assertTrue(world.initializeAttempt(() -> true, () -> fail(), a -> { })); assertEquals(0, world.nextPostRestoreMotionSample());
        CompoundTag bad = new CompoundTag(); bad.putString(FrameAnchor.KEY, "bad");
        var unresolved = load(bad); unresolved.armPostRestoreMotionDiagnostics(true, true);
        assertFalse(unresolved.initializeAttempt(() -> fail(), () -> { }, a -> { })); assertEquals(0, unresolved.nextPostRestoreMotionSample());
        CompoundTag tag = new CompoundTag(); tag.put(FrameAnchor.KEY, VALUE.save());
        var scopeOff = load(tag); scopeOff.armPostRestoreMotionDiagnostics(false, true);
        assertTrue(scopeOff.initializeAttempt(() -> true, () -> fail(), a -> { })); assertEquals(0, scopeOff.nextPostRestoreMotionSample());
        var nonliving = load(tag); nonliving.armPostRestoreMotionDiagnostics(true, false);
        assertTrue(nonliving.initializeAttempt(() -> true, () -> fail(), a -> { })); assertEquals(0, nonliving.nextPostRestoreMotionSample());
    }

    @Test void diagnosticsResetOnReadTruncateOnceAndCanBeDisabledWithoutAuthorityChanges() {
        CompoundTag tag = new CompoundTag(); tag.put(FrameAnchor.KEY, VALUE.save());
        var state = load(tag); state.armPostRestoreMotionDiagnostics(true, true);
        assertTrue(state.initializeAttempt(() -> true, () -> fail(), a -> { }));
        for (int i = 1; i <= 64; i++) assertEquals(i, state.nextPostRestoreMotionSample());
        assertEquals(-1, state.nextPostRestoreMotionSample()); assertEquals(0, state.nextPostRestoreMotionSample());
        state.disablePostRestoreMotionDiagnostics();
        assertEquals(0, state.nextPostRestoreMotionSample()); assertTrue(state.restoredFromFrame());
        assertEquals(FrameAnchorState.Status.WORLD, state.status()); assertFalse(state.physicalComposition().requiresCommittedPoint());
        state.read(tag); assertFalse(state.postRestoreMotionDiagnosticsArmed()); assertNull(state.postRestoreHydratedAnchor());
        assertEquals(0, state.nextPostRestoreMotionSample());
    }

    @Test void absentMeansWorldAndUnresolvedRemainsPendingAcrossSaveAndReload() {
        var state = new FrameAnchorState(); state.read(new CompoundTag());
        assertEquals(FrameAnchorState.Status.WORLD, state.status());
        CompoundTag bad = new CompoundTag(); bad.putString(FrameAnchor.KEY, "corrupt"); state.read(bad);
        CompoundTag resaved = new CompoundTag(); state.write(resaved, null, null);
        assertTrue(FrameAnchor.load(resaved).present());
        assertEquals(FrameAnchorState.Status.UNRESOLVED, load(resaved).status());
    }

    @Test void validPendingSavePreservesExactAnchorWithoutLiveOwnerLookup() {
        CompoundTag original = new CompoundTag(); original.put(FrameAnchor.KEY, VALUE.save());
        var state = load(original); CompoundTag saved = new CompoundTag(); state.write(saved, null, null);
        assertEquals(VALUE, FrameAnchor.load(saved).anchor());
    }

    @Test void worldSaveRemovesStaleAnchorKey() {
        CompoundTag root = new CompoundTag(); root.put(FrameAnchor.KEY, VALUE.save());
        FrameAnchorState.writeCapture(root, new FrameAnchorCaptureService.Capture(FrameAnchorState.Status.WORLD, null, "world"));
        assertFalse(FrameAnchor.load(root).present());
    }

    @Test void currentUnknownAndFrameOwnerMismatchAreNeverWorldFallbacks() {
        CompoundTag root = new CompoundTag();
        FrameAnchorState.writeCapture(root, new FrameAnchorCaptureService.Capture(FrameAnchorState.Status.UNRESOLVED, null, "unknown"));
        assertEquals(FrameAnchorState.Status.UNRESOLVED, load(root).status());
        CompoundTag mismatch = new CompoundTag();
        FrameAnchorState.writeCapture(mismatch, new FrameAnchorCaptureService.Capture(FrameAnchorState.Status.UNRESOLVED, null, "owner-mismatch"));
        assertEquals(FrameAnchorState.Status.UNRESOLVED, load(mismatch).status());
    }

    @Test void captureWritePolicyUsesActualProofStatuses() {
        CompoundTag frame = new CompoundTag();
        FrameAnchorState.writeCapture(frame, new FrameAnchorCaptureService.Capture(FrameAnchorState.Status.FRAME, VALUE, "live"));
        assertEquals(VALUE, FrameAnchor.load(frame).anchor());
        FrameAnchorState.writeCapture(frame, new FrameAnchorCaptureService.Capture(FrameAnchorState.Status.UNRESOLVED, null, "unload-proof-missing"));
        assertEquals("unload-proof-missing", FrameAnchor.load(frame).reason());
        FrameAnchorState.writeCapture(frame, new FrameAnchorCaptureService.Capture(FrameAnchorState.Status.WORLD, null, "world"));
        assertFalse(FrameAnchor.load(frame).present());
    }

    @Test void actualWritePathDelegatesCaptureSerializationAndClearsWorldAnchor() {
        CompoundTag tag = new CompoundTag(); tag.put(FrameAnchor.KEY, VALUE.save());
        var state = new FrameAnchorState();
        // Exercise the real common policy invoked by write after capture resolution.
        FrameAnchorState.writeCapture(tag, new FrameAnchorCaptureService.Capture(FrameAnchorState.Status.WORLD, null, "world"));
        assertFalse(FrameAnchor.load(tag).present());
        FrameAnchorState.writeCapture(tag, new FrameAnchorCaptureService.Capture(FrameAnchorState.Status.FRAME, VALUE, "live"));
        assertEquals(VALUE, FrameAnchor.load(tag).anchor());
    }

    @Test void unknownWaitsWithoutHydrationAndReadyHydratesExactlyOnce() {
        CompoundTag root = new CompoundTag(); root.put(FrameAnchor.KEY, VALUE.save());
        var state = load(root);
        int[] hydration = {0}, waits = {0}, completions = {0};
        assertFalse(state.initializeAttempt(() -> { hydration[0]++; return false; }, () -> waits[0]++, a -> completions[0]++));
        assertEquals(FrameAnchorState.Status.FRAME, state.status());
        assertEquals(0, completions[0]);
        assertTrue(state.initializeAttempt(() -> { hydration[0]++; return true; }, () -> waits[0]++, a -> {
            assertEquals(VALUE, a); completions[0]++;
        }));
        assertTrue(state.initializeAttempt(() -> { hydration[0]++; return true; }, () -> waits[0]++, a -> completions[0]++));
        assertEquals(2, hydration[0]);
        assertEquals(1, completions[0]);
        assertEquals(1, waits[0]);
        assertTrue(state.restoredFromFrame());
    }

    @Test void unresolvedNeverCallsHydration() {
        var state = load(new CompoundTag());
        CompoundTag malformed = new CompoundTag(); malformed.putString(FrameAnchor.KEY, "bad"); state.read(malformed);
        int[] calls = {0};
        assertFalse(state.initializeAttempt(() -> { calls[0]++; return true; }, () -> { }, a -> { }));
        assertEquals(0, calls[0]);
        assertEquals(FrameAnchorState.Status.UNRESOLVED, state.status());
    }

    @Test void queryPendingPredicateIncludesUnresolvedAndOnlyPendingStates() {
        var state = load(new CompoundTag());
        var actor = new PendingActor(state);
        assertFalse(actor.sable$frameAnchorInitializationPending());
        CompoundTag invalid = new CompoundTag(); invalid.putString(FrameAnchor.KEY, "bad"); state.read(invalid);
        assertTrue(actor.sable$frameAnchorInitializationPending());
        assertNull(actor.sable$pendingFrameAnchor());
        CompoundTag valid = new CompoundTag(); valid.put(FrameAnchor.KEY, VALUE.save()); state.read(valid);
        assertTrue(actor.sable$frameAnchorInitializationPending());
        assertNotNull(actor.sable$pendingFrameAnchor());
        assertTrue(state.initializeAttempt(() -> true, () -> fail("unexpected wait"), a -> { }));
        assertFalse(actor.sable$frameAnchorInitializationPending());
    }

    private static FrameAnchorState load(CompoundTag tag) { var state = new FrameAnchorState(); state.read(tag); return state; }
    private record PendingActor(FrameAnchorState sable$frameAnchorState) implements FrameAnchorPending { }
}

