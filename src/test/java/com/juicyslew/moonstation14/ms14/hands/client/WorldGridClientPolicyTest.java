package com.juicyslew.moonstation14.ms14.hands.client;

import com.juicyslew.moonstation14.ms14.hands.network.BodyWorldGridSnapshot;
import com.juicyslew.moonstation14.ms14.hands.network.BodyWorldGridAction;
import com.juicyslew.moonstation14.ms14.hands.network.BodyWorldGridActionResult;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot;
import com.juicyslew.moonstation14.ms14.hands.network.BodyWorldCrossAction;
import com.juicyslew.moonstation14.ms14.hands.network.BodyWorldCrossActionResult;
import com.juicyslew.moonstation14.ms14.hands.network.BodyWorldWorldCrossActionResult;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class WorldGridClientPolicyTest {
    private static BodyHandStateSnapshot hand(UUID account, UUID body, long revision) {
        return new BodyHandStateSnapshot(1, 1, BodyHandStateSnapshot.Reason.OK, account, body, 1, revision,
                "left", List.of(new BodyHandStateSnapshot.Hand("left", "held", "minecraft:stick", 1),
                        new BodyHandStateSnapshot.Hand("right", null, null, 0)));
    }

    @Test void actionRequiresMatchingProofAndBothPostResultSettlements() {
        var p = new WorldGridClientPolicy(); var account = UUID.randomUUID(); var body = UUID.randomUUID();
        var target = UUID.randomUUID(); var nonce = UUID.randomUUID();
        p.bind(account, body, 1);
        var open = p.query(target, 1).orElseThrow();
        p.accept(ok(open.sequence(), account, body, 1, target, nonce), 1);
        var proof = hand(account, body, 1);
        assertTrue(p.action(target, proof, BodyWorldGridAction.Mode.STORE, "left", "held",
                -1, -1, -1, 0, 0, 0, 2).isEmpty());
        p.settledHand(proof);
        var sent = p.action(target, proof, BodyWorldGridAction.Mode.STORE, "left", "held",
                -1, -1, -1, 0, 0, 0, 2).orElseThrow();
        assertEquals(nonce, sent.session()); assertEquals(0, sent.hostRevision());
        assertFalse(p.canAct(target));
        assertFalse(p.result(new BodyWorldGridActionResult(1, sent.sequence(), target, UUID.randomUUID(), 1,
                BodyWorldGridActionResult.Reason.OK)));
        assertTrue(p.result(new BodyWorldGridActionResult(1, sent.sequence(), target, nonce, 1,
                BodyWorldGridActionResult.Reason.OK)));
        p.handQueryIssued();
        p.settledHand(hand(account, body, 2));
        assertFalse(p.canAct(target));
        var refresh = p.query(target, 2, 0, 3).orElseThrow();
        var updated = new BodyWorldGridSnapshot(1, refresh.sequence(), BodyWorldGridSnapshot.Reason.OK,
                account, body, 1, 2, target, "bag", 7, 4, List.of(), nonce, 1);
        assertTrue(p.accept(updated, 4));
        assertTrue(p.canAct(target));
        assertTrue(p.action(target, proof, BodyWorldGridAction.Mode.STORE, "left", "held",
                -1, -1, -1, 1, 0, 0, 5).isEmpty());
    }

    @Test void closedOrReboundSessionsIgnoreLateResults() {
        var p = new WorldGridClientPolicy(); var a = UUID.randomUUID(); var b = UUID.randomUUID();
        var target = UUID.randomUUID(); var nonce = UUID.randomUUID();
        p.bind(a, b, 1);
        var q = p.query(target, 1).orElseThrow(); p.accept(ok(q.sequence(), a, b, 1, target, nonce), 1);
        p.settledHand(hand(a, b, 1));
        var action = p.action(target, hand(a, b, 1), BodyWorldGridAction.Mode.STORE, "left", "held",
                -1, -1, -1, 0, 0, 0, 2).orElseThrow();
        p.close(target);
        var result = new BodyWorldGridActionResult(1, action.sequence(), target, nonce, 1,
                BodyWorldGridActionResult.Reason.OK);
        assertFalse(p.result(result)); assertNull(p.view(target));
        p.bind(a, UUID.randomUUID(), 2);
        assertFalse(p.result(result));
    }
    private static BodyWorldGridSnapshot ok(long seq, UUID account, UUID body, long epoch, UUID target, UUID session) {
        return new BodyWorldGridSnapshot(epoch, seq, BodyWorldGridSnapshot.Reason.OK, account, body, epoch, 1,
                target, "bag", 7, 4, List.of(), session, 0);
    }

    @Test void capsDistinctTargetsAndCloseFreesSlotWithoutAllowingLateOpen() {
        var policy = new WorldGridClientPolicy(); UUID account=UUID.randomUUID(), body=UUID.randomUUID();
        policy.bind(account, body, 1);
        UUID[] targets={UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID()};
        var opens = new java.util.ArrayList<com.juicyslew.moonstation14.ms14.hands.network.BodyWorldGridQuery>();
        for (int i=0;i<3;i++) opens.add(policy.query(targets[i], 1).orElseThrow());
        assertTrue(policy.query(targets[3],1).isEmpty());
        policy.close(targets[0]);
        var fourth=policy.query(targets[3],1).orElseThrow();
        assertFalse(policy.accept(ok(opens.get(0).sequence(),account,body,1,targets[0],UUID.randomUUID()), 2));
        assertTrue(policy.accept(ok(opens.get(1).sequence(),account,body,1,targets[1],UUID.randomUUID()), 2));
        assertTrue(policy.accept(ok(opens.get(2).sequence(),account,body,1,targets[2],UUID.randomUUID()), 2));
        assertTrue(policy.accept(ok(fourth.sequence(),account,body,1,targets[3],UUID.randomUUID()), 2));
        assertEquals(3,policy.views().size());
    }

    @Test void rebindClearsStateAndRefreshRequiresExactSessionAndIdentity() {
        var p=new WorldGridClientPolicy(); UUID a=UUID.randomUUID(), b=UUID.randomUUID(), body=UUID.randomUUID(), target=UUID.randomUUID();
        p.bind(a,body,1); var open=p.query(target,1).orElseThrow(); UUID session=UUID.randomUUID();
        assertTrue(p.accept(ok(open.sequence(),a,body,1,target,session),10));
        var refresh=p.query(target,1).orElseThrow();
        assertFalse(p.accept(ok(refresh.sequence(),a,body,1,target,UUID.randomUUID()),11));
        assertTrue(p.view(target)!=null);
        p.bind(b,body,2); assertNull(p.view(target));
        var next=p.query(target,1).orElseThrow();
        assertTrue(next.sequence()>refresh.sequence());
        assertFalse(p.accept(ok(next.sequence(),a,body,2,target,UUID.randomUUID()),12));
    }

    @Test void snapshotsRemainDisplayedButBecomeNonActionableWhenOld() {
        var p=new WorldGridClientPolicy(); UUID a=UUID.randomUUID(),body=UUID.randomUUID(),target=UUID.randomUUID();
        p.bind(a,body,1); var q=p.query(target,0).orElseThrow(); var session = UUID.randomUUID();
        assertTrue(p.accept(ok(q.sequence(),a,body,1,target,session),0));
        assertTrue(p.view(target).actionsEnabled());
        assertFalse(p.canAct(target)); // no settled hand proof
        p.advance(WorldGridClientPolicy.MAX_AGE_TICKS/2); assertTrue(p.view(target).stale());
        p.advance(WorldGridClientPolicy.MAX_AGE_TICKS); assertNotNull(p.view(target));
        assertTrue(p.view(target).stale()); assertFalse(p.canAct(target));
        var refresh = p.query(target, 0, 0, WorldGridClientPolicy.MAX_AGE_TICKS).orElseThrow();
        assertEquals(session, refresh.session());
        assertTrue(p.accept(ok(refresh.sequence(), a, body, 1, target, session), WorldGridClientPolicy.MAX_AGE_TICKS + 1));
        assertFalse(p.view(target).stale());
    }

    @Test void pendingOpensShareQuotaWithWornAndHeldWindows() {
        var p = new WorldGridClientPolicy(); var account = UUID.randomUUID(); var body = UUID.randomUUID();
        p.bind(account, body, 1);
        var first = UUID.randomUUID(); var second = UUID.randomUUID(); var third = UUID.randomUUID();
        var open = p.query(first, 1, 2, 1).orElseThrow();
        assertEquals(1, p.pendingOpens());
        assertTrue(p.query(second, 1, 2, 1).isEmpty());
        assertTrue(p.accept(ok(open.sequence(), account, body, 1, first, UUID.randomUUID()), 2));
        assertTrue(p.query(third, 1, 2, 3).isEmpty());
        p.close(first);
        assertTrue(p.query(second, 1, 2, 4).isPresent());
    }

    @Test void transientRefreshFailuresRetainStaleViewAndMaterialFailuresClose() {
        for (var reason : List.of(BodyWorldGridSnapshot.Reason.STALE, BodyWorldGridSnapshot.Reason.NOT_READY,
                BodyWorldGridSnapshot.Reason.REPLAY, BodyWorldGridSnapshot.Reason.RATE_LIMITED,
                BodyWorldGridSnapshot.Reason.LIMIT)) {
            var p = new WorldGridClientPolicy(); var a=UUID.randomUUID(); var b=UUID.randomUUID(); var target=UUID.randomUUID();
            p.bind(a,b,1); var open=p.query(target,1).orElseThrow(); var session=UUID.randomUUID();
            p.accept(ok(open.sequence(),a,b,1,target,session),1);
            var refresh=p.query(target,1,0,2).orElseThrow();
            p.accept(BodyWorldGridSnapshot.rejected(refresh,reason),3);
            assertNotNull(p.view(target)); assertTrue(p.view(target).stale()); assertFalse(p.canAct(target));
        }
        for (var reason : List.of(BodyWorldGridSnapshot.Reason.UNAVAILABLE, BodyWorldGridSnapshot.Reason.NO_AUTHORITY)) {
            var p = new WorldGridClientPolicy(); var a=UUID.randomUUID(); var b=UUID.randomUUID(); var target=UUID.randomUUID();
            p.bind(a,b,1); var open=p.query(target,1).orElseThrow();
            p.accept(ok(open.sequence(),a,b,1,target,UUID.randomUUID()),1);
            var refresh=p.query(target,1,0,2).orElseThrow();
            p.accept(BodyWorldGridSnapshot.rejected(refresh,reason),3);
            assertNull(p.view(target));
        }
    }

    @Test void canceledAndRejectedOpensNeverResurrectAndLateGrantIsClosed() {
        var p = new WorldGridClientPolicy(); var account = UUID.randomUUID(); var body = UUID.randomUUID();
        var target = UUID.randomUUID(); var session = UUID.randomUUID();
        p.bind(account, body, 1);
        var first = p.query(target, 1, 0, 0).orElseThrow();
        p.close(target);
        var late = ok(first.sequence(), account, body, 1, target, session);
        var close = p.lateClose(late).orElseThrow();
        assertEquals(target, close.target()); assertEquals(session, close.session());
        assertFalse(p.accept(late, 1)); assertNull(p.view(target));
        var second = p.query(target, 1, 0, 2).orElseThrow();
        assertTrue(p.accept(BodyWorldGridSnapshot.rejected(second, BodyWorldGridSnapshot.Reason.NOT_READY), 3));
        assertNull(p.view(target));
        assertEquals(0, p.pendingOpens());
        var third = p.query(target, 1, 0, 4).orElseThrow();
        p.advance(104);
        assertEquals(0, p.pendingOpens());
        assertTrue(p.lateClose(ok(third.sequence(), account, body, 1, target, UUID.randomUUID())).isPresent());
    }

    @Test void pendingOpenCorrelationRequiresCurrentEpochSequenceAndTarget() {
        var p = new WorldGridClientPolicy(); var account = UUID.randomUUID(); var body = UUID.randomUUID();
        var target = UUID.randomUUID(); p.bind(account, body, 2);
        var current = p.query(target, 1).orElseThrow();
        var wrongSequence = ok(current.sequence() + 1, account, body, 2, target, UUID.randomUUID());
        var oldEpoch = ok(current.sequence(), account, body, 1, target, UUID.randomUUID());
        assertFalse(p.matchesPendingOpen(wrongSequence));
        assertFalse(p.matchesPendingOpen(oldEpoch));
        assertTrue(p.hasPendingOpen(target));
        assertTrue(p.matchesPendingOpen(BodyWorldGridSnapshot.rejected(current, BodyWorldGridSnapshot.Reason.NO_AUTHORITY)));
        assertTrue(p.hasPendingOpen(target));
    }

    @Test void materialClassificationAndUnmatchedRepliesPreservePendingRequest() {
        assertTrue(WorldGridClientPolicy.materiallyUnavailable(BodyWorldGridSnapshot.Reason.UNAVAILABLE));
        assertTrue(WorldGridClientPolicy.materiallyUnavailable(BodyWorldGridSnapshot.Reason.NO_AUTHORITY));
        assertFalse(WorldGridClientPolicy.materiallyUnavailable(BodyWorldGridSnapshot.Reason.NOT_READY));
        assertFalse(WorldGridClientPolicy.materiallyUnavailable(BodyWorldGridSnapshot.Reason.RATE_LIMITED));
        var p = new WorldGridClientPolicy(); var a = UUID.randomUUID(); var b = UUID.randomUUID();
        var target = UUID.randomUUID(); p.bind(a, b, 2);
        var request = p.query(target, 1).orElseThrow();
        var wrongSequence = BodyWorldGridSnapshot.rejected(request, BodyWorldGridSnapshot.Reason.NOT_READY);
        assertFalse(p.accept(new BodyWorldGridSnapshot(wrongSequence.requestedEpoch(), request.sequence() + 1,
                wrongSequence.reason(), null, null, 0, 0, null, null, 0, 0, List.of(), null, 0), 2));
        assertTrue(p.hasPendingOpen(target));
        assertTrue(p.accept(BodyWorldGridSnapshot.rejected(request, BodyWorldGridSnapshot.Reason.NOT_READY), 3));
        assertFalse(p.hasPendingOpen(target));
    }

    @Test void cancelingPendingOpenForSameTargetClosesLateGrant() {
        var p = new WorldGridClientPolicy(); var account = UUID.randomUUID(); var body = UUID.randomUUID();
        var target = UUID.randomUUID(); var session = UUID.randomUUID();
        p.bind(account, body, 1);
        var opening = p.query(target, 1, 0, 1).orElseThrow();
        assertTrue(p.hasPendingOpen(target));
        p.close(target);
        assertFalse(p.hasPendingOpen(target));
        var lateClose = p.lateClose(ok(opening.sequence(), account, body, 1, target, session)).orElseThrow();
        assertEquals(target, lateClose.target());
        assertEquals(session, lateClose.session());
        assertNull(p.view(target));
    }

    @Test void refreshesCannotRollBackOrChangeEntriesWithoutRevisionBump() {
        var p = new WorldGridClientPolicy(); var a=UUID.randomUUID(); var b=UUID.randomUUID();
        var target=UUID.randomUUID(); var session=UUID.randomUUID(); p.bind(a,b,1);
        var open=p.query(target,1).orElseThrow();
        assertTrue(p.accept(new BodyWorldGridSnapshot(1,open.sequence(),BodyWorldGridSnapshot.Reason.OK,a,b,1,1,
                target,"bag",7,4,List.of(),session,5),1));
        var newer=p.query(target,1).orElseThrow();
        assertTrue(p.accept(new BodyWorldGridSnapshot(1,newer.sequence(),BodyWorldGridSnapshot.Reason.OK,a,b,1,1,
                target,"bag",7,4,List.of(),session,7),2));
        var older=p.query(target,1).orElseThrow();
        assertFalse(p.accept(new BodyWorldGridSnapshot(1,older.sequence(),BodyWorldGridSnapshot.Reason.OK,a,b,1,1,
                target,"bag",7,4,List.of(),session,6),3));
        var changed=p.query(target,1).orElseThrow();
        var entry=new BodyWorldGridSnapshot.Entry("token","minecraft:stick",1,0,0,0);
        assertFalse(p.accept(new BodyWorldGridSnapshot(1,changed.sequence(),BodyWorldGridSnapshot.Reason.OK,a,b,1,1,
                target,"bag",7,4,List.of(entry),session,7),4));
        assertEquals(7,p.view(target).hostRevision());
    }

    @Test void worldEntriesKeepWireOrderAndReorderRequiresActionRevision() {
        var p = new WorldGridClientPolicy(); var a = UUID.randomUUID(); var b = UUID.randomUUID();
        var target = UUID.randomUUID(); var session = UUID.randomUUID(); p.bind(a, b, 1);
        var first = List.of(new BodyWorldGridSnapshot.Entry("a", "minecraft:stick", 1, 0, 0, 0),
                new BodyWorldGridSnapshot.Entry("b", "minecraft:stick", 1, 1, 0, 0),
                new BodyWorldGridSnapshot.Entry("c", "minecraft:stick", 1, 2, 0, 0));
        var open = p.query(target, 1).orElseThrow();
        assertTrue(p.accept(new BodyWorldGridSnapshot(1, open.sequence(), BodyWorldGridSnapshot.Reason.OK, a, b,
                1, 1, target, "bag", 7, 4, first, session, 5), 1));
        assertEquals(List.of("a", "b", "c"), p.view(target).entries().stream().map(BodyWorldGridSnapshot.Entry::token).toList());
        assertThrows(UnsupportedOperationException.class, () -> p.view(target).entries().clear());

        var reordered = List.of(first.get(2), first.get(1), first.get(0));
        var unchangedRevision = p.query(target, 1).orElseThrow();
        assertFalse(p.accept(new BodyWorldGridSnapshot(1, unchangedRevision.sequence(), BodyWorldGridSnapshot.Reason.OK,
                a, b, 1, 1, target, "bag", 7, 4, reordered, session, 5), 2));

        var proof = hand(a, b, 1); p.settledHand(proof);
        var action = p.action(target, proof, BodyWorldGridAction.Mode.STORE, "left", "held", -1, -1, -1,
                3, 0, 0, 3).orElseThrow();
        assertTrue(p.result(new BodyWorldGridActionResult(1, action.sequence(), target, session, 6,
                BodyWorldGridActionResult.Reason.OK)));
        p.handQueryIssued(); p.settledHand(hand(a, b, 2));
        var refresh = p.query(target, 2, 0, 4).orElseThrow();
        assertTrue(p.accept(new BodyWorldGridSnapshot(1, refresh.sequence(), BodyWorldGridSnapshot.Reason.OK,
                a, b, 1, 2, target, "bag", 7, 4, reordered, session, 6), 4));
        assertEquals(List.of("c", "b", "a"), p.view(target).entries().stream().map(BodyWorldGridSnapshot.Entry::token).toList());
    }

    @Test void preActionRefreshCannotPromoteViewWhileActionIsPending() {
        var p=new WorldGridClientPolicy(); var a=UUID.randomUUID(); var b=UUID.randomUUID();
        var target=UUID.randomUUID(); var session=UUID.randomUUID(); p.bind(a,b,1);
        var open=p.query(target,1).orElseThrow(); p.accept(ok(open.sequence(),a,b,1,target,session),1);
        var proof=hand(a,b,1); p.settledHand(proof);
        var action=p.action(target,proof,BodyWorldGridAction.Mode.STORE,"left","held",-1,-1,-1,0,0,0,2).orElseThrow();
        assertTrue(p.view(target) == null || p.view(target).stale());
        var preAction=p.query(target,1,0,1).orElseThrow();
        assertFalse(p.accept(ok(preAction.sequence(),a,b,1,target,session),3));
        assertTrue(p.view(target).stale()); assertFalse(p.canAct(target));
    }

    @Test void crossTransfersShareActionGateAndRequireCorrelatedFreshSettlements() {
        for (var direction : BodyWorldCrossAction.Direction.values()) {
            var p = new WorldGridClientPolicy(); var a=UUID.randomUUID(); var b=UUID.randomUUID();
            var target=UUID.randomUUID(); var session=UUID.randomUUID(); p.bind(a,b,1);
            var open=p.query(target,1).orElseThrow(); assertTrue(p.accept(ok(open.sequence(),a,b,1,target,session),1));
            var proof=hand(a,b,1); p.settledHand(proof);
            var cross=p.crossAction(target,proof,direction,true,"armor","host","child",0,0,0,1,0,90,2).orElseThrow();
            assertEquals(1,cross.sequence()); assertEquals(direction,cross.direction());
            assertTrue(p.view(target).stale());
            assertTrue(p.action(target,proof,BodyWorldGridAction.Mode.STORE,"left","held",-1,-1,-1,0,0,0,3).isEmpty());
            assertFalse(p.crossResult(new BodyWorldCrossActionResult(1,cross.sequence(),target,UUID.randomUUID(),2,
                    BodyWorldCrossActionResult.Reason.OK)));
            assertFalse(p.crossResult(new BodyWorldCrossActionResult(1,cross.sequence()+1,target,session,2,
                    BodyWorldCrossActionResult.Reason.OK)));
            assertTrue(p.crossResult(new BodyWorldCrossActionResult(1,cross.sequence(),target,session,2,
                    BodyWorldCrossActionResult.Reason.OK)));
            p.handQueryIssued(); p.settledHand(hand(a,b,2));
            assertFalse(p.canAct(target));
            var refresh=p.query(target,2,0,4).orElseThrow();
            assertTrue(p.accept(new BodyWorldGridSnapshot(1,refresh.sequence(),BodyWorldGridSnapshot.Reason.OK,a,b,1,2,
                    target,"bag",7,4,List.of(),session,2),5));
            assertTrue(p.canAct(target));
            assertFalse(p.crossResult(new BodyWorldCrossActionResult(1,cross.sequence(),target,session,2,
                    BodyWorldCrossActionResult.Reason.OK)));
        }
    }

    @Test void crossTransferRejectsStaleProofAndTimesOut() {
        var p=new WorldGridClientPolicy(); var a=UUID.randomUUID(); var b=UUID.randomUUID();
        var target=UUID.randomUUID(); var session=UUID.randomUUID(); p.bind(a,b,1);
        var open=p.query(target,1).orElseThrow(); p.accept(ok(open.sequence(),a,b,1,target,session),1);
        var proof=hand(a,b,1); p.settledHand(proof);
        assertTrue(p.crossAction(target,hand(a,b,2),BodyWorldCrossAction.Direction.WORLD_TO_BODY,true,
                "armor","host","child",0,0,0,0,0,0,2).isEmpty());
        var cross=p.crossAction(target,proof,BodyWorldCrossAction.Direction.BODY_TO_WORLD,false,
                "armor","host","child",0,0,0,0,0,0,2).orElseThrow();
        p.advance(2+WorldGridClientPolicy.MAX_AGE_TICKS);
        assertFalse(p.crossResult(new BodyWorldCrossActionResult(1,cross.sequence(),target,session,2,
                BodyWorldCrossActionResult.Reason.OK)));
        assertTrue(p.view(target) == null || p.view(target).stale());
    }

    @Test void worldToWorldRequiresTwoFreshSessionsCorrelatedResultAndBothRefreshes() {
        var p=new WorldGridClientPolicy(); var a=UUID.randomUUID(); var b=UUID.randomUUID();
        var source=UUID.randomUUID(); var destination=UUID.randomUUID(); var ss=UUID.randomUUID(); var ds=UUID.randomUUID();
        p.bind(a,b,1);
        var q1=p.query(source,1).orElseThrow(); var q2=p.query(destination,1).orElseThrow();
        assertTrue(p.accept(ok(q1.sequence(),a,b,1,source,ss),1));
        assertTrue(p.accept(ok(q2.sequence(),a,b,1,destination,ds),1));
        var proof=hand(a,b,1); p.settledHand(proof);
        assertTrue(p.worldCrossAction(source,UUID.randomUUID(),proof,"child",0,0,0,1,0,0,2).isEmpty());
        var action=p.worldCrossAction(source,destination,proof,"child",0,0,0,1,0,0,2).orElseThrow();
        assertEquals(1,action.sequence()); assertTrue(p.view(source).stale()); assertTrue(p.view(destination).stale());
        assertFalse(p.worldCrossAction(source,destination,proof,"child",0,0,0,1,0,0,3).isPresent());
        assertFalse(p.worldCrossResult(new BodyWorldWorldCrossActionResult(1,action.sequence(),source,ss,2,
                destination,UUID.randomUUID(),2,BodyWorldWorldCrossActionResult.Reason.OK)));
        assertTrue(p.worldCrossResult(new BodyWorldWorldCrossActionResult(1,action.sequence(),source,ss,2,
                destination,ds,3,BodyWorldWorldCrossActionResult.Reason.OK)));
        p.handQueryIssued(); p.settledHand(hand(a,b,2));
        var refresh=p.query(source,2,0,4).orElseThrow();
        assertTrue(p.accept(new BodyWorldGridSnapshot(1,refresh.sequence(),BodyWorldGridSnapshot.Reason.OK,a,b,1,2,
                source,"bag",7,4,List.of(),ss,2),5));
        assertFalse(p.canAct(source)); assertFalse(p.canAct(destination));
        var refresh2=p.query(destination,2,0,5).orElseThrow();
        assertTrue(p.accept(new BodyWorldGridSnapshot(1,refresh2.sequence(),BodyWorldGridSnapshot.Reason.OK,a,b,1,2,
                destination,"bag",7,4,List.of(),ds,3),6));
        assertTrue(p.canAct(source)); assertTrue(p.canAct(destination));
        assertFalse(p.worldCrossResult(new BodyWorldWorldCrossActionResult(1,action.sequence(),source,ss,2,
                destination,ds,3,BodyWorldWorldCrossActionResult.Reason.OK)));
    }

    @Test void worldCrossRecoveryAndTimeoutInvalidateBothAndMalformedGeometryRejected() {
        var p=new WorldGridClientPolicy(); var a=UUID.randomUUID(); var b=UUID.randomUUID();
        var source=UUID.randomUUID(); var destination=UUID.randomUUID(); var ss=UUID.randomUUID(); var ds=UUID.randomUUID();
        p.bind(a,b,1); var q1=p.query(source,1).orElseThrow(); var q2=p.query(destination,1).orElseThrow();
        assertTrue(p.accept(ok(q1.sequence(),a,b,1,source,ss),1)); assertTrue(p.accept(ok(q2.sequence(),a,b,1,destination,ds),1));
        p.settledHand(hand(a,b,1)); var action=p.worldCrossAction(source,destination,hand(a,b,1),"child",0,0,0,1,0,0,2).orElseThrow();
        assertTrue(p.worldCrossResult(new BodyWorldWorldCrossActionResult(1,action.sequence(),source,ss,0,
                destination,ds,0,BodyWorldWorldCrossActionResult.Reason.RECOVERY_REQUIRED)));
        assertNull(p.view(source)); assertNull(p.view(destination));

        // A timed-out action invalidates both views and its late result cannot be replayed.
        ss=UUID.randomUUID(); ds=UUID.randomUUID();
        var n1=p.query(source,1).orElseThrow(); var n2=p.query(destination,1).orElseThrow();
        assertTrue(p.accept(ok(n1.sequence(),a,b,1,source,ss),4));
        assertTrue(p.accept(ok(n2.sequence(),a,b,1,destination,ds),4));
        p.settledHand(hand(a,b,1));
        var timed=p.worldCrossAction(source,destination,hand(a,b,1),"child",0,0,0,1,0,0,10).orElseThrow();
        p.advance(10+WorldGridClientPolicy.MAX_AGE_TICKS);
        assertTrue(p.view(source) == null || p.view(source).stale());
        assertTrue(p.view(destination) == null || p.view(destination).stale());
        assertFalse(p.worldCrossResult(new BodyWorldWorldCrossActionResult(1,timed.sequence(),source,ss,2,
                destination,ds,2,BodyWorldWorldCrossActionResult.Reason.OK)));
    }

    @Test void closingEitherWorldDuringDualActionCancelsAndStalesSurvivor() {
        for (boolean closeSource : List.of(true, false)) {
            var p=new WorldGridClientPolicy(); var a=UUID.randomUUID(); var b=UUID.randomUUID();
            var s=UUID.randomUUID(); var d=UUID.randomUUID(); var ss=UUID.randomUUID(); var ds=UUID.randomUUID();
            p.bind(a,b,1); var qs=p.query(s,1).orElseThrow(); var qd=p.query(d,1).orElseThrow();
            p.accept(ok(qs.sequence(),a,b,1,s,ss),1); p.accept(ok(qd.sequence(),a,b,1,d,ds),1);
            p.settledHand(hand(a,b,1)); p.worldCrossAction(s,d,hand(a,b,1),"x",0,0,0,1,0,0,2).orElseThrow();
            p.close(closeSource ? s : d);
            assertNull(p.view(closeSource ? s : d));
            assertTrue(p.view(closeSource ? d : s).stale());
            assertFalse(p.canAct(closeSource ? d : s));
        }
    }

    @Test void dualResultRequiresQueriesIssuedAfterResult() {
        var p=new WorldGridClientPolicy(); var a=UUID.randomUUID(); var b=UUID.randomUUID();
        var s=UUID.randomUUID(); var d=UUID.randomUUID(); var ss=UUID.randomUUID(); var ds=UUID.randomUUID();
        p.bind(a,b,1); var qs=p.query(s,1).orElseThrow(); var qd=p.query(d,1).orElseThrow();
        p.accept(ok(qs.sequence(),a,b,1,s,ss),1); p.accept(ok(qd.sequence(),a,b,1,d,ds),1);
        p.settledHand(hand(a,b,1)); var action=p.worldCrossAction(s,d,hand(a,b,1),"x",0,0,0,1,0,0,2).orElseThrow();
        var preS=p.query(s,1,0,3).orElseThrow(); var preD=p.query(d,1,0,3).orElseThrow();
        p.worldCrossResult(new BodyWorldWorldCrossActionResult(1,action.sequence(),s,ss,2,d,ds,2,
                BodyWorldWorldCrossActionResult.Reason.OK));
        p.handQueryIssued(); p.settledHand(hand(a,b,2));
        assertFalse(p.accept(ok(preS.sequence(),a,b,1,s,ss),4));
        assertFalse(p.accept(ok(preD.sequence(),a,b,1,d,ds),4));
        assertTrue(p.view(s).stale()); assertTrue(p.view(d).stale());
    }
}
