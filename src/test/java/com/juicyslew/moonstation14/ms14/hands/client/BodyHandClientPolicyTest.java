package com.juicyslew.moonstation14.ms14.hands.client;

import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BodyHandClientPolicyTest {
    private static final UUID ACCOUNT = UUID.randomUUID(), BODY = UUID.randomUUID();

    private static BodyHandStateSnapshot snapshot(long requested, long sequence, UUID account, UUID body, long epoch) {
        return new BodyHandStateSnapshot(requested, sequence, BodyHandStateSnapshot.Reason.OK,
                account, body, epoch, 3, "left", List.of(new BodyHandStateSnapshot.Hand("left", null, null, 0)));
    }

    private static BodyHandActionResult result(long epoch, long sequence, BodyHandActionResult.Reason reason) {
        return new BodyHandActionResult(epoch, sequence, reason == BodyHandActionResult.Reason.OK,
                reason, false, 0, null, null, null, 0);
    }

    @Test void hudHidesOldHandThroughActionAndRefreshWithoutSyncingText() {
        var policy = new BodyHandClientPolicy();
        policy.bind(ACCOUNT, BODY, 7);
        assertNull(policy.hudSnapshot());
        assertNull(policy.feedback());
        long initial = policy.query(1);
        assertTrue(policy.accept(snapshot(0, initial, ACCOUNT, BODY, 7)));
        assertNotNull(policy.hudSnapshot());

        long action = policy.action(2);
        assertNull(policy.hudSnapshot());
        assertNull(policy.feedback());
        policy.noItemUnderCursor(2); // repeated G while the first action is unresolved
        assertNull(policy.feedback());
        assertEquals(0, policy.query(2));
        assertFalse(policy.result(result(8, action, BodyHandActionResult.Reason.OK), 3));
        assertNull(policy.hudSnapshot());
        assertFalse(policy.result(result(7, action + 1, BodyHandActionResult.Reason.OK), 3));
        assertTrue(policy.result(result(7, action, BodyHandActionResult.Reason.OK), 4));
        assertNull(policy.hudSnapshot());
        assertNull(policy.feedback());
        assertEquals(0, policy.action(4));

        long refresh = policy.query(4);
        assertFalse(policy.accept(snapshot(0, initial, ACCOUNT, BODY, 7)));
        assertFalse(policy.accept(snapshot(7, refresh, ACCOUNT, BODY, 7)));
        assertNull(policy.hudSnapshot());
        assertTrue(policy.accept(snapshot(0, refresh, ACCOUNT, BODY, 7)));
        assertNotNull(policy.hudSnapshot());
        assertFalse(policy.result(result(7, action, BodyHandActionResult.Reason.DENIED), 5));
        assertNull(policy.feedback());
        assertTrue(policy.action(5) > action);
    }

    @Test void timedOutActionRetriesQueryWithoutTransientStatus() {
        var policy = new BodyHandClientPolicy();
        policy.bind(ACCOUNT, BODY, 7);
        assertTrue(policy.accept(snapshot(0, policy.query(1), ACCOUNT, BODY, 7)));
        long action = policy.action(2);
        policy.timeout(82);
        assertNull(policy.hudSnapshot());
        assertNull(policy.feedback());
        assertFalse(policy.result(result(7, action, BodyHandActionResult.Reason.OK), 83));
        long retry = policy.query(83);
        assertTrue(retry > 0);
        assertTrue(policy.accept(snapshot(0, retry, ACCOUNT, BODY, 7)));
        assertNotNull(policy.hudSnapshot());
    }

    @Test void rejectionRemainsReadableWithoutStaleHandAndExpires() {
        var policy = new BodyHandClientPolicy();
        policy.bind(ACCOUNT, BODY, 7);
        assertTrue(policy.accept(snapshot(0, policy.query(1), ACCOUNT, BODY, 7)));
        long action = policy.action(2);
        assertTrue(policy.result(result(7, action, BodyHandActionResult.Reason.RECOVERY_REQUIRED), 10));
        assertNull(policy.hudSnapshot());
        assertEquals("Hands: RECOVERY_REQUIRED", policy.feedback());
        long refresh = policy.query(11);
        assertTrue(policy.accept(snapshot(0, refresh, ACCOUNT, BODY, 7)));
        policy.timeout(69);
        assertEquals("Hands: RECOVERY_REQUIRED", policy.feedback());
        policy.timeout(70);
        assertNull(policy.feedback());
        long denied = policy.action(71);
        assertTrue(policy.result(result(7, denied, BodyHandActionResult.Reason.DENIED), 72));
        assertNull(policy.hudSnapshot());
        assertEquals("Hands: server DENIED", policy.feedback());
        policy.timeout(132);
        assertNull(policy.feedback());
    }

    @Test void noInitialSnapshotNoActionAndStrictQueryCorrelation() {
        var policy = new BodyHandClientPolicy();
        policy.bind(ACCOUNT, BODY, 7);
        assertEquals(0, policy.action(1));
        long query = policy.query(1);
        assertEquals(0, policy.queryEpoch());
        assertFalse(policy.accept(snapshot(7, query, ACCOUNT, BODY, 7)));
        assertNull(policy.snapshot());
        assertEquals(0, policy.action(2));
        long retry = policy.query(41);
        assertFalse(policy.accept(snapshot(0, query, ACCOUNT, BODY, 7)));
        assertFalse(policy.accept(snapshot(0, retry, ACCOUNT, BODY, 8)));
        long next = policy.query(81);
        assertFalse(policy.accept(snapshot(0, next, ACCOUNT, UUID.randomUUID(), 7)));
        long valid = policy.query(121);
        assertTrue(policy.accept(snapshot(0, valid, ACCOUNT, BODY, 7)));
        assertTrue(policy.action(122) > 0);
    }

    @Test void actionReplayAndRefreshBarrier() {
        var policy = new BodyHandClientPolicy();
        policy.bind(ACCOUNT, BODY, 7);
        assertTrue(policy.accept(snapshot(0, policy.query(1), ACCOUNT, BODY, 7)));
        long action = policy.action(2);
        assertEquals(0, policy.action(2));
        assertFalse(policy.result(result(8, action, BodyHandActionResult.Reason.OK), 2));
        assertFalse(policy.result(result(7, action + 1, BodyHandActionResult.Reason.OK), 2));
        assertTrue(policy.result(result(7, action, BodyHandActionResult.Reason.DENIED), 2));
        assertEquals("Hands: server DENIED", policy.feedback());
        assertNull(policy.snapshot());
        assertFalse(policy.result(result(7, action, BodyHandActionResult.Reason.OK), 3));
        assertEquals(0, policy.action(3));
        long fresh = policy.query(3);
        assertEquals(0, policy.queryEpoch()); // bootstrap until a full snapshot returns
        assertTrue(policy.accept(snapshot(0, fresh, ACCOUNT, BODY, 7)));
        assertEquals(action + 1, policy.action(4));
    }

    @Test void chatSafeHudLayoutAndCameraGate() {
        var position = BodyHandClientPolicy.handHudPosition(320, 240, 140, 9);
        assertEquals(new BodyHandClientPolicy.HudPosition(6, 54), position);
        assertTrue(position.y() + 11 < 100); // above the bottom-left chat region
        assertEquals(3, BodyHandClientPolicy.handHudPosition(100, 120, 95, 9).x());
        assertNull(BodyHandClientPolicy.handHudPosition(320, 90, 140, 9));
        assertFalse(BodyHandClientPolicy.showAimCue(false, true, true));
        assertFalse(BodyHandClientPolicy.showAimCue(true, false, true));
        assertFalse(BodyHandClientPolicy.showAimCue(true, true, false));
        assertTrue(BodyHandClientPolicy.showAimCue(true, true, true));
    }

    @Test void missingLocalCandidateIsTemporaryAndDistinctFromServerRejection() {
        var policy = new BodyHandClientPolicy();
        policy.noItemUnderCursor(1);
        assertNull(policy.feedback());
        policy.bind(ACCOUNT, BODY, 7);
        policy.noItemUnderCursor(2);
        assertNull(policy.feedback());
        assertTrue(policy.accept(snapshot(0, policy.query(3), ACCOUNT, BODY, 7)));
        policy.noItemUnderCursor(4);
        assertEquals("Hands: no item under cursor", policy.feedback());
        policy.timeout(64);
        assertNull(policy.feedback());
        long action = policy.action(65);
        assertTrue(policy.result(result(7, action, BodyHandActionResult.Reason.DENIED), 65));
        assertEquals("Hands: server DENIED", policy.feedback());
    }

    @Test void screenSuspensionKeepsReplayHighWaterAndDisablesKeysUntilRebound() {
        var policy = new BodyHandClientPolicy();
        policy.bind(ACCOUNT, BODY, 7);
        long first = policy.query(1);
        assertTrue(policy.accept(snapshot(0, first, ACCOUNT, BODY, 7)));
        long action = policy.action(2);
        assertTrue(policy.result(result(7, action, BodyHandActionResult.Reason.OK), 3));
        long beforeScreen = policy.query(3);
        policy.suspend(); // screen/camera transient: queued clicks and responses cannot trigger an action
        assertFalse(policy.accept(snapshot(0, beforeScreen, ACCOUNT, BODY, 7)));
        assertEquals(0, policy.query(43));
        assertEquals(0, policy.action(43));
        policy.bind(ACCOUNT, BODY, 7);
        long afterScreen = policy.query(44);
        assertTrue(afterScreen > beforeScreen);
        // Same gate, same bootstrap epoch: the fresh query is not a replay.
        assertTrue(policy.accept(snapshot(0, afterScreen, ACCOUNT, BODY, 7)));
        assertTrue(policy.action(45) > action);
    }

    @Test void resetAndEpochOrBodySwitchDiscardsPendingState() {
        var policy = new BodyHandClientPolicy();
        policy.bind(ACCOUNT, BODY, 7);
        long query = policy.query(1);
        UUID otherBody = UUID.randomUUID();
        policy.bind(ACCOUNT, otherBody, 7);
        assertFalse(policy.accept(snapshot(0, query, ACCOUNT, BODY, 7)));
        long switched = policy.query(2);
        assertTrue(switched > query);
        assertTrue(policy.accept(snapshot(0, switched, ACCOUNT, otherBody, 7)));
        long switchedAction = policy.action(2);
        assertTrue(switchedAction > 0);
        assertTrue(policy.result(result(7, switchedAction, BodyHandActionResult.Reason.OK), 3));
        policy.bind(ACCOUNT, BODY, 8);
        assertEquals(0, policy.action(3));
        long nextEpoch = policy.query(3);
        assertTrue(nextEpoch > switched);
        assertFalse(policy.accept(snapshot(0, switched, ACCOUNT, otherBody, 7)));
        assertTrue(policy.accept(snapshot(0, nextEpoch, ACCOUNT, BODY, 8)));
        assertTrue(policy.action(4) > switchedAction);
        policy.reset();
        assertNull(policy.snapshot());
        assertEquals(0, policy.query(4));
        assertEquals(0, policy.action(4));
        policy.bind(ACCOUNT, BODY, 8);
        long newConnection = policy.query(5);
        assertNotEquals(nextEpoch, newConnection);
        assertFalse(policy.accept(snapshot(0, nextEpoch, ACCOUNT, BODY, 8)));
        assertTrue(policy.accept(snapshot(0, newConnection, ACCOUNT, BODY, 8)));
    }
}
