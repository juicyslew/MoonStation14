package com.juicyslew.moonstation14.ms14.actions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BodyActionRuntimeStateTest {
    @Test
    void cooldownExpiresAtEndTick() {
        var state = new BodyActionRuntimeState();
        state.setCooldownEnd("fire", "hand", 12);

        assertTrue(state.isOnCooldown("fire", "hand", 11));
        assertFalse(state.isOnCooldown("fire", "hand", 12));
        assertEquals(0, state.cooldownEnd("other", "hand"));
    }

    @Test
    void snapshotRevisionChangesOnlyForDifferentFingerprint() {
        var state = new BodyActionRuntimeState();
        assertEquals(1, state.updateSnapshotFingerprint("grant-enabled"));
        assertEquals(1, state.updateSnapshotFingerprint("grant-enabled"));
        assertEquals(2, state.updateSnapshotFingerprint("grant-disabled"));
        assertEquals(2, state.snapshotRevision());
    }

    @Test
    void requestSequenceDeduplicatesWithinEpochAndResetsAcrossEpochs() {
        var state = new BodyActionRuntimeState();
        assertTrue(state.acceptRequestSequence(4, 7));
        assertFalse(state.acceptRequestSequence(4, 7));
        assertFalse(state.acceptRequestSequence(4, 6));
        assertTrue(state.acceptRequestSequence(5, 1));
        assertFalse(state.acceptRequestSequence(5, 1));
    }

    @Test
    void requestSequenceFreshnessIsScopedToControllerAndEpoch() {
        var state = new BodyActionRuntimeState();
        var firstOwner = java.util.UUID.randomUUID();
        var newOwner = java.util.UUID.randomUUID();
        assertTrue(state.acceptRequestSequence(firstOwner, 4, 12));
        assertTrue(state.acceptRequestSequence(newOwner, 4, 1));
        assertFalse(state.acceptRequestSequence(newOwner, 4, 1));
        assertFalse(state.acceptRequestSequence(newOwner, 4, 0));
        assertTrue(state.acceptRequestSequence(newOwner, 4, 2));
    }
}
