package com.juicyslew.moonstation14.ms14.hands.quarantine;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.juicyslew.moonstation14.ms14.hands.quarantine.QuarantineState.*;
import static org.junit.jupiter.api.Assertions.*;

class QuarantineStateTest {
    private static final UUID ACCOUNT = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PARK_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID RESTORE_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    private QuarantineState parked() {
        QuarantineState state = QuarantineState.create(ACCOUNT, 1);
        state = state.begin(PARK_ID, 1, Intent.PARK).state();
        state = state.confirm(PARK_ID, 1, Step.SNAPSHOT_PREPARED).state();
        state = state.confirm(PARK_ID, 1, Step.CLEAR_AUTHORIZED).state();
        return state.confirm(PARK_ID, 1, Step.CLEAR_CONFIRMED).state();
    }

    @Test
    void successfulParkAndRestoreRequireDurabilityProofAndVerifiedRestore() {
        QuarantineState original = QuarantineState.create(ACCOUNT, 1);
        Result begun = original.begin(PARK_ID, 1, Intent.PARK);
        assertTrue(begun.accepted());
        assertEquals(Phase.PREPARING, begun.state().phase());
        assertEquals(Access.NONE, begun.state().access());
        Result duplicateBegin = begun.state().begin(PARK_ID, 1, Intent.PARK);
        assertTrue(duplicateBegin.duplicate());
        assertSame(begun.state(), duplicateBegin.state());

        Result prematureAuthorization = begun.state().confirm(PARK_ID, 1, Step.CLEAR_AUTHORIZED);
        assertEquals(Rejection.MISSING_SNAPSHOT_PROOF, prematureAuthorization.rejection());
        assertSame(begun.state(), prematureAuthorization.state());

        QuarantineState readyToClear = begun.state()
                .confirm(PARK_ID, 1, Step.SNAPSHOT_PREPARED).state()
                .confirm(PARK_ID, 1, Step.CLEAR_AUTHORIZED).state();
        assertTrue(readyToClear.snapshotPrepared());
        assertTrue(readyToClear.clearAuthorized());
        QuarantineState parked = readyToClear.confirm(PARK_ID, 1, Step.CLEAR_CONFIRMED).state();
        assertEquals(Phase.PARKED, parked.phase());
        assertEquals(Access.BODY_ONLY, parked.access());

        QuarantineState restoring = parked.begin(RESTORE_ID, 1, Intent.RESTORE).state();
        assertEquals(Access.NONE, restoring.access());
        Result unverified = restoring.confirm(RESTORE_ID, 1, Step.CLEAR_CONFIRMED);
        assertEquals(Rejection.FORBIDDEN_STEP, unverified.rejection());
        QuarantineState restored = restoring.confirm(RESTORE_ID, 1, Step.RESTORE_VERIFIED).state();
        assertEquals(Phase.CREATIVE_AVAILABLE, restored.phase());
        assertEquals(Access.CREATIVE_VANILLA, restored.access());
    }

    @Test
    void failedPersistOrClearNeverAuthorizesClearOrDropsAccountOwnership() {
        QuarantineState initial = QuarantineState.create(ACCOUNT, 1);
        QuarantineState preparing = initial.begin(PARK_ID, 1, Intent.PARK).state();
        Result persistFailed = preparing.confirm(PARK_ID, 1, Step.CLEAR_AUTHORIZED);
        assertEquals(Rejection.MISSING_SNAPSHOT_PROOF, persistFailed.rejection());
        assertSame(preparing, persistFailed.state());
        assertFalse(preparing.clearAuthorized());
        assertTrue(preparing.accountOwnershipRetained());

        QuarantineState prepared = preparing.confirm(PARK_ID, 1, Step.SNAPSHOT_PREPARED).state();
        Result clearFailed = prepared.confirm(PARK_ID, 1, Step.CLEAR_CONFIRMED);
        assertEquals(Rejection.CLEAR_NOT_AUTHORIZED, clearFailed.rejection());
        assertSame(prepared, clearFailed.state());
        assertEquals(Phase.PREPARING, prepared.phase());
        assertTrue(prepared.accountOwnershipRetained());
    }

    @Test
    void duplicatesAreIdempotentAndStaleGenerationOrTransitionIsRejected() {
        QuarantineState initial = QuarantineState.create(ACCOUNT, 4);
        QuarantineState preparing = initial.begin(PARK_ID, 4, Intent.PARK).state();
        Result duplicateIntent = preparing.begin(PARK_ID, 4, Intent.PARK);
        assertTrue(duplicateIntent.accepted());
        assertTrue(duplicateIntent.duplicate());
        assertSame(preparing, duplicateIntent.state());

        QuarantineState prepared = preparing.confirm(PARK_ID, 4, Step.SNAPSHOT_PREPARED).state();
        Result duplicateProof = prepared.confirm(PARK_ID, 4, Step.SNAPSHOT_PREPARED);
        assertTrue(duplicateProof.duplicate());
        assertSame(prepared, duplicateProof.state());

        assertEquals(Rejection.STALE_GENERATION,
                prepared.confirm(PARK_ID, 3, Step.CLEAR_AUTHORIZED).rejection());
        assertEquals(Rejection.STALE_TRANSITION,
                prepared.confirm(RESTORE_ID, 4, Step.CLEAR_AUTHORIZED).rejection());
        assertSame(prepared, prepared.confirm(PARK_ID, 3, Step.CLEAR_AUTHORIZED).state());

        QuarantineState terminal = parked();
        Result duplicateCompletedIntent = terminal.begin(PARK_ID, 1, Intent.PARK);
        assertTrue(duplicateCompletedIntent.duplicate());
        assertSame(terminal, duplicateCompletedIntent.state());
    }

    @Test
    void deathAndReconnectDoNotChangeParkedAccountOwnership() {
        QuarantineState parked = parked(); // Death has no model event and cannot drop account-owned data.
        QuarantineState reconnected = parked.reconnect(2).state();
        assertEquals(ACCOUNT, reconnected.accountId());
        assertEquals(2, reconnected.sessionGeneration());
        assertEquals(Phase.PARKED, reconnected.phase());
        assertEquals(Access.BODY_ONLY, reconnected.access());
        assertTrue(reconnected.accountOwnershipRetained());

        QuarantineState creativeAgain = reconnected.begin(RESTORE_ID, 2, Intent.RESTORE).state()
                .confirm(RESTORE_ID, 2, Step.RESTORE_VERIFIED).state();
        assertEquals(Access.CREATIVE_VANILLA, creativeAgain.access());
        assertTrue(creativeAgain.accountOwnershipRetained());
    }

    @Test
    void allCrashStagesReopenWithoutGrantingEitherOwner() {
        QuarantineState state = QuarantineState.create(ACCOUNT, 8).begin(PARK_ID, 8, Intent.PARK).state();
        for (int stage = 0; stage < 4; stage++) {
            QuarantineState recovered = QuarantineState.recover(state.journal());
            assertEquals(Phase.RECOVERY_REQUIRED, recovered.phase());
            assertEquals(Access.NONE, recovered.access());
            assertEquals(ACCOUNT, recovered.accountId());
            assertTrue(recovered.accountOwnershipRetained());
            assertEquals(Rejection.UNRESOLVED_RECOVERY,
                    recovered.begin(RESTORE_ID, 8, Intent.RESTORE).rejection());
            if (stage == 0) state = state.confirm(PARK_ID, 8, Step.SNAPSHOT_PREPARED).state();
            if (stage == 1) state = state.confirm(PARK_ID, 8, Step.CLEAR_AUTHORIZED).state();
            if (stage == 2) state = state.confirm(PARK_ID, 8, Step.CLEAR_CONFIRMED).state();
        }
    }

    @Test
    void grantsAreMutuallyExclusiveAndForbiddenTransitionsDoNotMutate() {
        QuarantineState creative = QuarantineState.create(ACCOUNT, 1);
        assertEquals(Access.CREATIVE_VANILLA, creative.access());
        assertEquals(Rejection.INVALID_TRANSITION,
                creative.begin(RESTORE_ID, 1, Intent.RESTORE).rejection());
        QuarantineState preparing = creative.begin(PARK_ID, 1, Intent.PARK).state();
        assertEquals(Access.NONE, preparing.access());
        assertEquals(Rejection.FORBIDDEN_STEP,
                preparing.confirm(PARK_ID, 1, Step.RESTORE_VERIFIED).rejection());
        QuarantineState parked = parked();
        assertEquals(Access.BODY_ONLY, parked.access());
        assertEquals(Rejection.INVALID_TRANSITION,
                parked.begin(RESTORE_ID, 1, Intent.PARK).rejection());
        assertSame(parked, parked.begin(PARK_ID, 1, Intent.PARK).state());
    }

    @Test
    void onlyCreativeAvailableIntendsVanillaAccessAndBodyNeverDoes() {
        QuarantineState creative = QuarantineState.create(ACCOUNT, 1);
        QuarantineState preparing = creative.begin(PARK_ID, 1, Intent.PARK).state();
        QuarantineState parked = parked();
        QuarantineState restoring = parked.begin(RESTORE_ID, 1, Intent.RESTORE).state();
        QuarantineState recovering = QuarantineState.recover(preparing.journal());

        assertEquals(Access.CREATIVE_VANILLA, creative.access());
        assertEquals(Access.NONE, preparing.access());
        assertEquals(Access.BODY_ONLY, parked.access());
        assertEquals(Access.NONE, restoring.access());
        assertEquals(Access.NONE, recovering.access());

        for (QuarantineState state : new QuarantineState[] {
                creative, preparing, parked, restoring, recovering
        }) {
            assertEquals(state.phase() == Phase.CREATIVE_AVAILABLE,
                    state.access() == Access.CREATIVE_VANILLA);
        }
    }
}
