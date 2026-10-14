package com.juicyslew.moonstation14.ms14.hands.client;

import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot;
import com.juicyslew.moonstation14.ms14.testsupport.DeterministicPacketScheduler;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionSnapshot;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation;
import com.juicyslew.moonstation14.ms14.timed_interaction.client.TimedInteractionReconciler;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class HeldUseRequestDispatchTest {
    @Test void normalAndPopupZActivationShareQuickEquipThenHeldStorageTogglePrecedence() {
        var popup = new StoragePopupWorkspace(320, 240);
        var host = StoragePopupWorkspace.HostKey.held("same-host-token");
        int[] storageActivations = {0}, ordinaryUses = {0}, quickEquips = {0};
        java.util.function.BooleanSupplier toggle = () -> {
            storageActivations[0]++;
            popup.toggle(host, true, 0, 0, 100, 80);
            return true;
        };
        assertTrue(HeldUseRequestDispatch.routeActivation(() -> false, toggle,
                () -> { ordinaryUses[0]++; return true; })); // normal body Z opens
        assertTrue(popup.window(host).visible());
        assertTrue(HeldUseRequestDispatch.routeActivation(() -> false, toggle,
                () -> { ordinaryUses[0]++; return true; })); // popup-screen Z closes same host
        assertNull(popup.window(host));
        assertEquals(2, storageActivations[0]);
        assertEquals(0, ordinaryUses[0]);

        assertTrue(HeldUseRequestDispatch.routeActivation(() -> { quickEquips[0]++; return true; }, toggle,
                () -> { ordinaryUses[0]++; return true; }));
        assertEquals(1, quickEquips[0]);
        assertEquals(2, storageActivations[0], "quick-equip retains precedence over storage activation");
        assertEquals(0, ordinaryUses[0]);

        assertTrue(HeldUseRequestDispatch.routeActivation(() -> false, () -> false,
                () -> { ordinaryUses[0]++; return true; }), "ordinary pill/beaker use remains the fallback");
        assertEquals(1, ordinaryUses[0]);
    }

    @Test void admittedHeldUseBuildsNormalPacketAndPredictsThroughProductionOwners() {
        UUID account = UUID.randomUUID(), body = UUID.randomUUID();
        var policy = new BodyHandClientPolicy();
        policy.bind(account, body, 41);
        long query = policy.query(1);
        var left = new BodyHandStateSnapshot.Hand("left", "token-held", "moonstation14:jug", 1);
        var right = new BodyHandStateSnapshot.Hand("right", null, null, 0);
        var proof = new BodyHandStateSnapshot(0, query, BodyHandStateSnapshot.Reason.OK, account, body,
                41, 8, "left", List.of(left, right), List.of());
        assertTrue(policy.accept(proof));

        var reconciler = new TimedInteractionReconciler();
        var captured = new AtomicReference<BodyHandActionRequest>();
        var dimension = ResourceLocation.withDefaultNamespace("overworld");
        var sends = new java.util.concurrent.atomic.AtomicInteger();
        var predictions = new java.util.concurrent.atomic.AtomicInteger();
        long[] sequenceHolder = {0};
        boolean routed = HeldUseRequestDispatch.routePressed(true, false, false, () -> true, () -> {
            sequenceHolder[0] = HeldUseRequestDispatch.dispatch(policy, policy.snapshot(), left, 2, 20,
                    packet -> { captured.set(packet); sends.incrementAndGet(); }, allocated -> {
                        predictions.incrementAndGet(); reconciler.predictStart(allocated, body, dimension, 20, 2);
                    });
            return sequenceHolder[0] != 0;
        });
        assertTrue(routed);
        long sequence = sequenceHolder[0];
        assertTrue(sequence > 0);
        assertFalse(HeldUseRequestDispatch.routePressed(true, false, false, () -> true, () ->
                HeldUseRequestDispatch.dispatch(policy, policy.snapshot(), left, 3, 20,
                        packet -> { captured.set(packet); sends.incrementAndGet(); }, ignored -> predictions.incrementAndGet()) != 0));
        assertEquals(1, sends.get());
        assertEquals(1, predictions.get());
        var request = captured.get();
        assertNotNull(request);
        assertEquals(BodyHandActionRequest.Action.USE_IN_HAND, request.action());
        assertEquals("left", request.handId());
        assertEquals(41, request.epoch());
        assertEquals(sequence, request.sequence());
        assertEquals(8, request.expectedRevision());
        assertEquals("token-held", request.expectedToken());
        assertEquals(1, reconciler.provisionalView(body, 3).size());

        var scheduler = new DeterministicPacketScheduler<TimedInteractionSnapshot>();
        scheduler.send(100, new TimedInteractionSnapshot(dimension, 2, body, 1, 3,
                List.of(new TimedInteractionSnapshot.View(sequence, 0, 20, TimedOperation.Phase.ACTIVE, -1, sequence))));
        var accepted = new java.util.ArrayList<TimedInteractionReconciler.Acceptance>();
        scheduler.advance(100, snapshot -> accepted.add(reconciler.accept(snapshot, dimension, body, body, dimension)));
        assertTrue(accepted.getFirst().accepted());
        assertTrue(reconciler.provisionalView(body, 100).isEmpty());

        assertTrue(policy.result(new com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult(
                41, sequence, false, com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.DENIED,
                false, 0, null, null, null, 0), 101));
        assertEquals(0, policy.action(102));
        long refresh = policy.query(103);
        assertTrue(policy.accept(new BodyHandStateSnapshot(0, refresh, BodyHandStateSnapshot.Reason.OK, account, body,
                41, 9, "left", List.of(left, right), List.of())));
        assertTrue(HeldUseRequestDispatch.routePressed(true, false, false, () -> true, () -> {
            sequenceHolder[0] = HeldUseRequestDispatch.dispatch(policy, policy.snapshot(), left, 104, 20,
                    packet -> { captured.set(packet); sends.incrementAndGet(); }, allocated -> predictions.incrementAndGet());
            return sequenceHolder[0] != 0;
        }));
        assertNotEquals(sequence, sequenceHolder[0]);
        assertEquals(2, sends.get());
        assertEquals(2, predictions.get());
    }

    @Test void currentModalAndTextAdmissionBlocksRouteWithoutIssuingRequest() {
        var policy = new BodyHandClientPolicy();
        var sent = new java.util.concurrent.atomic.AtomicInteger();
        for (boolean permitted : List.of(false, false))
            assertFalse(HeldUseRequestDispatch.routePressed(true, false, false, () -> permitted, () -> {
                sent.incrementAndGet(); return HeldUseRequestDispatch.dispatch(policy, null, null, 0, 0,
                        ignored -> { }, ignored -> { }) != 0;
            }));
        assertFalse(HeldUseRequestDispatch.routePressed(true, false, false, () -> true, () -> false));
        assertFalse(HeldUseRequestDispatch.routePressed(true, true, false, () -> true, () -> sent.incrementAndGet() > 0));
        assertFalse(HeldUseRequestDispatch.routePressed(true, false, true, () -> true, () -> sent.incrementAndGet() > 0));
        assertFalse(HeldUseRequestDispatch.routePressed(false, false, false, () -> true, () -> sent.incrementAndGet() > 0));
        assertEquals(0, sent.get());
    }

    @Test void productionRequestSeamCannotBypassMissingProofOrInvalidHeldSource() {
        var policy = new BodyHandClientPolicy();
        var any = new java.util.concurrent.atomic.AtomicInteger();
        assertEquals(0, HeldUseRequestDispatch.dispatch(policy, null, null, 1, 20, ignored -> any.incrementAndGet(), ignored -> any.incrementAndGet()));
        UUID account = UUID.randomUUID(), body = UUID.randomUUID();
        policy.bind(account, body, 2);
        long q = policy.query(1);
        var empty = new BodyHandStateSnapshot(0, q, BodyHandStateSnapshot.Reason.OK, account, body, 2, 1,
                "left", List.of(new BodyHandStateSnapshot.Hand("left", null, null, 0),
                        new BodyHandStateSnapshot.Hand("right", null, null, 0)), List.of());
        assertTrue(policy.accept(empty));
        assertEquals(0, HeldUseRequestDispatch.dispatch(policy, empty, empty.hands().getFirst(), 2, 20,
                ignored -> any.incrementAndGet(), ignored -> any.incrementAndGet()));
        assertEquals(0, any.get());
    }
}
