package com.juicyslew.moonstation14.ms14.actions.client;

import com.juicyslew.moonstation14.ms14.actions.BodyActionResult;
import com.juicyslew.moonstation14.ms14.actions.BodyActionSnapshot;
import com.juicyslew.moonstation14.ms14.actions.prototype.ActionPrototype;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class BodyActionClientStateTest {
    private static final UUID BODY = UUID.randomUUID();
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("test", path); }
    private BodyActionSnapshot snapshot(UUID body, long epoch, long revision, long time, boolean enabled, boolean active, long end) {
        var entry = new BodyActionSnapshot.Entry(id("jump"), "provider", "Jump", id("icon"), Optional.empty(),
                ActionPrototype.Kind.TOGGLE, enabled, active, end, 4, "", 0);
        return new BodyActionSnapshot(body, epoch, revision, time, List.of(entry));
    }
    @Test void rejectsIdentityAndStaleRevisions() {
        var state = new BodyActionClientState(); state.control(BODY, 1);
        assertFalse(state.accept(snapshot(UUID.randomUUID(), 1, 9, 0, true, false, 0)));
        assertFalse(state.accept(snapshot(BODY, 2, 9, 0, true, false, 0)));
        assertTrue(state.accept(snapshot(BODY, 1, 2, 0, true, false, 0)));
        assertFalse(state.accept(snapshot(BODY, 1, 2, 0, false, false, 0)));
    }
    @Test void controlSwitchResetsSnapshotAndPendingAction() {
        var state = new BodyActionClientState(); state.control(BODY, 1);
        state.accept(snapshot(BODY, 1, 1, 0, true, false, 0)); state.begin(id("jump"), "provider");
        state.control(UUID.randomUUID(), 2);
        assertNull(state.snapshot()); assertFalse(state.pending()); assertNull(state.feedback());
    }
    @Test void cooldownUsesServerTimePlusLocalElapsed() {
        var source = snapshot(BODY, 1, 1, 100, true, false, 130);
        assertEquals(20, BodyActionClientState.remainingCooldown(source, source.actions().getFirst(), 10));
        assertEquals(0, BodyActionClientState.remainingCooldown(source, source.actions().getFirst(), 30));
    }
    @Test void denialClearsPendingWithoutChangingAuthorityAndExplicitRetryGetsNewSequence() {
        var state = new BodyActionClientState(); state.control(BODY, 1);
        var authoritative = snapshot(BODY, 1, 1, 0, true, false, 0); state.accept(authoritative);
        long seq = state.begin(id("jump"), "provider");
        assertTrue(state.snapshot().actions().getFirst().enabled()); assertFalse(state.snapshot().actions().getFirst().active());
        assertTrue(state.accept(new BodyActionResult(BodyActionResult.Status.DENIED, "no", seq, Optional.empty())));
        assertEquals("no", state.feedback()); assertFalse(state.pending()); assertEquals(authoritative, state.snapshot());
        assertEquals(0, state.remainingCooldown(authoritative.actions().getFirst(), 100));
        assertTrue(state.snapshot().actions().getFirst().enabled()); assertFalse(state.snapshot().actions().getFirst().active());
        assertFalse(state.pending()); // denial itself does not submit another request
        long retrySequence = state.begin(id("jump"), "provider");
        assertTrue(retrySequence > seq);
        assertTrue(state.pending());
    }
    @Test void wrongBodyResultSnapshotDoesNotConsumePendingOrFeedbackAndSequenceSurvivesControlSwitch() {
        var state = new BodyActionClientState(); state.control(BODY, 1);
        state.accept(snapshot(BODY, 1, 1, 0, true, false, 0));
        long first = state.begin(id("jump"), "provider");
        UUID nextBody = UUID.randomUUID(); state.control(nextBody, 2);
        state.accept(snapshot(nextBody, 2, 1, 0, true, false, 0));
        long next = state.begin(id("jump"), "provider");
        assertTrue(next > first);
        assertFalse(state.accept(new BodyActionResult(BodyActionResult.Status.DENIED, "stale", first,
                Optional.of(snapshot(BODY, 1, 2, 0, true, false, 0)))));
        assertTrue(state.pending()); assertNull(state.feedback());
        assertTrue(state.accept(new BodyActionResult(BodyActionResult.Status.DENIED, "no", next, Optional.empty())));
        assertEquals("no", state.feedback()); assertFalse(state.pending());
    }
    @Test void beginRequiresEnabledActionAndProviderInCurrentSnapshot() {
        var state = new BodyActionClientState(); state.control(BODY, 1);
        state.accept(snapshot(BODY, 1, 1, 0, false, false, 0));
        assertEquals(0, state.begin(id("jump"), "provider"));
        assertEquals(0, state.begin(id("missing"), "provider"));
        assertEquals(0, state.begin(id("jump"), "wrong-provider"));
    }
    @Test void acceptedActionResultsReanchorRepeatedCooldownCyclesAndGuardRollback() {
        var state = new BodyActionClientState(); state.control(BODY, 1);
        state.accept(snapshot(BODY, 1, 1, 100, true, false, 300), 10_000);
        var entry = state.snapshot().actions().getFirst();
        assertEquals(150, state.remainingCooldown(entry, 10_050));
        assertEquals(0, state.remainingCooldown(entry, 10_200));
        var seq = state.begin(id("jump"), "provider");
        assertTrue(state.accept(new BodyActionResult(BodyActionResult.Status.ACCEPTED, "", seq,
                Optional.of(snapshot(BODY, 1, 2, 500, true, false, 700))), 10_200));
        entry = state.snapshot().actions().getFirst();
        assertEquals(200, state.remainingCooldown(entry, 10_200));
        assertEquals(150, state.remainingCooldown(entry, 10_250));
        assertEquals(0, state.remainingCooldown(entry, 10_400));
        assertFalse(state.accept(snapshot(BODY, 1, 2, 500, true, false, 700), 10_000));
        assertEquals(0, state.remainingCooldown(entry, 10_000)); // rollback cannot increase remaining time
        seq = state.begin(id("jump"), "provider");
        assertTrue(state.accept(new BodyActionResult(BodyActionResult.Status.ACCEPTED, "", seq,
                Optional.of(snapshot(BODY, 1, 3, 900, true, false, 1100))), 10_400));
        entry = state.snapshot().actions().getFirst();
        assertEquals(200, state.remainingCooldown(entry, 10_400));
        var denied = state.begin(id("jump"), "provider");
        assertTrue(state.accept(new BodyActionResult(BodyActionResult.Status.DENIED, "denied", denied,
                Optional.empty()), 10_450));
        assertEquals(150, state.remainingCooldown(entry, 10_450)); // result lacking snapshot did not reanchor
        state.control(BODY, 2); assertNull(state.snapshot());
        state.accept(snapshot(BODY, 2, 1, 0, true, false, 200), 1);
        assertEquals(200, state.remainingCooldown(state.snapshot().actions().getFirst(), 1));
        state.worldChanged(); assertNull(state.snapshot());
    }
}
