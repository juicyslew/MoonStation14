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
    @Test void rackCaptureUsesOrdinaryZModifierAdmission() {
        assertTrue(HeldUseRequestDispatch.mayCaptureRackPress(true,false,false,false));
        assertFalse(HeldUseRequestDispatch.mayCaptureRackPress(true,false,true,false));
        assertFalse(HeldUseRequestDispatch.mayCaptureRackPress(true,false,false,true));
        assertFalse(HeldUseRequestDispatch.mayCaptureRackPress(true,true,false,false));
        assertFalse(HeldUseRequestDispatch.mayCaptureRackPress(false,false,false,false));
    }
    @Test void rackReplayUsesRealDispatchPolicyAndFreshProofForEachDistinctPress() {
        UUID account=UUID.randomUUID(), body=UUID.randomUUID();
        String dimension=ResourceLocation.withDefaultNamespace("overworld").toString();
        var policy=new BodyHandClientPolicy(); policy.bind(account,body,7);
        var queue=new HeldUseRequestDispatch.RackIntentQueue();
        var left=new BodyHandStateSnapshot.Hand("left","rack-token","moonstation14:weapon_pistol_mk58",1);
        var right=new BodyHandStateSnapshot.Hand("right",null,null,0);
        long q=policy.query(1);
        assertTrue(policy.accept(new BodyHandStateSnapshot(0,q,BodyHandStateSnapshot.Reason.OK,account,body,7,20,"left",List.of(left,right),List.of())));
        var requests=new java.util.ArrayList<BodyHandActionRequest>();
        long rack=HeldUseRequestDispatch.dispatch(policy,policy.snapshot(),left,2,0,requests::add,ignored->{});
        assertTrue(rack>0);
        for(int i=0;i<3;i++) assertTrue(queue.capture(true,body,7,dimension,20,"left","rack-token",3+i));
        assertTrue(policy.result(new com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult(7,rack,true,
                com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.OK,false,0,null,null,null,0),8));
        for(int i=0;i<3;i++) {
            long base=10+10*i;
            long refresh=policy.query(base);
            assertTrue(policy.accept(new BodyHandStateSnapshot(0,refresh,BodyHandStateSnapshot.Reason.OK,account,body,7,21+i,"left",List.of(left,right),List.of())));
            int before=requests.size();
            assertTrue(queue.dispatchReady(true,true,base+2,body,7,dimension,policy.snapshot(),
                    () -> HeldUseRequestDispatch.dispatch(policy,policy.snapshot(),left,base+2,0,requests::add,ignored->{})>0));
            assertEquals(before+1,requests.size());
            var replay=requests.getLast();
            assertEquals(21+i,replay.expectedRevision());
            assertEquals("left",replay.handId());
            assertEquals("rack-token",replay.expectedToken());
            if(i<2) assertTrue(policy.result(new com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult(7,replay.sequence(),true,
                    com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.OK,false,0,null,null,null,0),base+3));
        }
        assertEquals(4,requests.size());
        assertEquals(0,queue.size());
    }
    @Test void deferredRackPressesAreFifoOnceOnlyAndRequireFreshAcceptedIdentityProof() {
        UUID body = UUID.randomUUID();
        var dimension = ResourceLocation.withDefaultNamespace("overworld").toString();
        var queue = new HeldUseRequestDispatch.RackIntentQueue();
        for (int i = 0; i < 3; i++)
            assertTrue(queue.capture(true, body, 7, dimension, 20, "left", "gun-token", 10 + i));
        assertFalse(queue.capture(false, body, 7, dimension, 20, "left", "gun-token", 13),
                "wield, battery mode and timed use are never queueable");
        var stale = rackProof(body, 7, 19, "left", "gun-token");
        assertNull(queue.takeReady(true, true, 20, body, 7, dimension, stale));
        var fresh = rackProof(body, 7, 21, "left", "gun-token");
        assertNotNull(queue.takeReady(true, true, 20, body, 7, dimension, fresh));
        assertEquals(2, queue.size());
        assertNotNull(queue.takeReady(true, true, 20, body, 7, dimension, fresh));
        assertNotNull(queue.takeReady(true, true, 20, body, 7, dimension, fresh));
        assertNull(queue.takeReady(true, true, 20, body, 7, dimension, fresh), "each press is consumed once");
        assertEquals(0, queue.size());
    }

    @Test void deferredRackIntentsAreDroppedOnRejectionContextOrHeldIdentityChange() {
        UUID body = UUID.randomUUID();
        String dimension = ResourceLocation.withDefaultNamespace("overworld").toString();
        var queue = new HeldUseRequestDispatch.RackIntentQueue();
        assertTrue(queue.capture(true, body, 4, dimension, 8, "left", "token", 1));
        assertNull(queue.takeReady(false, true, 2, body, 4, dimension, rackProof(body, 4, 9, "left", "token")));
        assertEquals(0, queue.size());
        assertTrue(queue.capture(true, body, 4, dimension, 8, "left", "token", 3));
        assertNull(queue.takeReady(true, false, 4, body, 4, dimension, rackProof(body, 4, 9, "left", "token")));
        assertTrue(queue.capture(true, body, 4, dimension, 8, "left", "token", 5));
        assertNull(queue.takeReady(true, true, 6, body, 4, dimension, rackProof(body, 4, 9, "right", "token")));
        assertEquals(0, queue.size());
        assertTrue(queue.capture(true, body, 4, dimension, 8, "left", "token", 7));
        assertNull(queue.takeReady(true, true, 47, body, 4, dimension, rackProof(body, 4, 9, "left", "token")),
                "intent expires at the bounded input TTL");
    }

    @Test void rackIntentRemainsUntilProductionDispatchActuallySucceeds() {
        UUID body=UUID.randomUUID(); String dimension=ResourceLocation.withDefaultNamespace("overworld").toString();
        var queue=new HeldUseRequestDispatch.RackIntentQueue();
        var proof=rackProof(body,3,12,"left","token");
        assertTrue(queue.capture(true,body,3,dimension,10,"left","token",1));
        int[] sends={0};
        assertFalse(queue.dispatchReady(true,true,2,body,3,dimension,proof,()->{sends[0]++;return false;}));
        assertEquals(1,queue.size());
        assertTrue(queue.dispatchReady(true,true,3,body,3,dimension,proof,()->{sends[0]++;return true;}));
        assertEquals(0,queue.size());
        assertEquals(2,sends[0]);
        assertFalse(queue.dispatchReady(true,true,4,body,3,dimension,proof,()->{sends[0]++;return true;}));
        assertEquals(2,sends[0]);
    }

    private static BodyHandStateSnapshot rackProof(UUID body, long epoch, long revision, String active, String token) {
        return new BodyHandStateSnapshot(0, 1, BodyHandStateSnapshot.Reason.OK, UUID.randomUUID(), body,
                epoch, revision, active, List.of(new BodyHandStateSnapshot.Hand("left", token, "test:gun", 1),
                new BodyHandStateSnapshot.Hand("right", null, null, 0)), List.of());
    }

    @Test void configuredSpawnItemsUseIsInstantWithoutChangingOrdinaryDurations() {
        assertEquals(0, HeldUseRequestDispatch.predictionDuration(true, 20));
        assertEquals(20, HeldUseRequestDispatch.predictionDuration(false, 20));
        assertEquals(0, HeldUseRequestDispatch.predictionDuration(false, 0));
    }

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
