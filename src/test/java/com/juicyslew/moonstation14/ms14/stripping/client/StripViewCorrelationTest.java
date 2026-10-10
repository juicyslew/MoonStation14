package com.juicyslew.moonstation14.ms14.stripping.client;

import com.juicyslew.moonstation14.ms14.stripping.StripViewSnapshot;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class StripViewCorrelationTest {
    private static StripViewSnapshot view(UUID target, long epoch, long seq, int entity) {
        return new StripViewSnapshot(epoch,seq,target,ResourceLocation.withDefaultNamespace("overworld"),entity,1,
                null,List.of(),StripViewSnapshot.Status.AVAILABLE,true);
    }
    private static StripViewSnapshot view(UUID target,long epoch,long seq,int entity,long rev,StripViewSnapshot.Status status,boolean canAct){
        return new StripViewSnapshot(epoch,seq,target,ResourceLocation.withDefaultNamespace("overworld"),entity,rev,
                null,List.of(),status,canAct);
    }

    @Test void ignoresOldSequenceAndClosesOnEntityIncarnationChange() {
        var gate = new StripViewCorrelation(); var target = UUID.randomUUID();
        gate.begin(target,4,10);
        assertFalse(gate.accept(view(target,4,9,22)));
        assertTrue(gate.accept(view(target,4,10,22)));
        assertEquals(11,gate.querySequence(11));
        assertEquals(0,gate.querySequence(12));
        assertFalse(gate.accept(view(target,4,11,23)));
        assertFalse(gate.open());
    }

    @Test void closedSessionCannotBeReopenedByDelayedQuery() {
        var gate = new StripViewCorrelation(); var target = UUID.randomUUID();
        gate.begin(target,4,10); gate.close();
        assertFalse(gate.accept(view(target,4,10,22)));
        assertEquals(0,gate.querySequence(11));
    }

    @Test void queryIsSingleFlightAndRetargetInvalidatesOutstandingReply() {
        var gate=new StripViewCorrelation();var first=UUID.randomUUID();var second=UUID.randomUUID();
        gate.begin(first,7,30);assertTrue(gate.accept(view(first,7,30,4)));
        assertEquals(31,gate.querySequence(31));assertEquals(0,gate.querySequence(32));
        gate.begin(second,7,33);
        assertFalse(gate.accept(view(first,7,31,4)));
        assertTrue(gate.open());assertEquals(second,gate.target());
    }

    @Test void acceptsSameSequencePushAfterReplyIncludingUnavailableState() {
        var gate=new StripViewCorrelation();var target=UUID.randomUUID();gate.begin(target,2,5);
        assertTrue(gate.accept(view(target,2,5,19,8,StripViewSnapshot.Status.AVAILABLE,true)));
        assertFalse(gate.inFlight());
        assertTrue(gate.accept(view(target,2,5,19,9,StripViewSnapshot.Status.UNAVAILABLE,false)));
        assertEquals(9,gate.targetRevision());
        assertFalse(gate.accept(view(target,2,5,19,8,StripViewSnapshot.Status.AVAILABLE,true)));
    }

    @Test void olderPushCannotReleaseOrOverwriteNewerQuery() {
        var gate=new StripViewCorrelation();var target=UUID.randomUUID();gate.begin(target,2,5);
        assertTrue(gate.accept(view(target,2,5,19,8,StripViewSnapshot.Status.AVAILABLE,true)));
        assertEquals(6,gate.querySequence(6));
        assertFalse(gate.accept(view(target,2,5,19,9,StripViewSnapshot.Status.UNAVAILABLE,false)));
        assertTrue(gate.inFlight());assertEquals(8,gate.targetRevision());
        assertTrue(gate.accept(view(target,2,6,19,10,StripViewSnapshot.Status.AVAILABLE,true)));
        assertFalse(gate.inFlight());assertEquals(10,gate.targetRevision());
    }

    @Test void rejectsWrongContextOlderRevisionAndIncarnationButAllowsAbsentUnavailableTarget() {
        var gate=new StripViewCorrelation();var target=UUID.randomUUID();gate.begin(target,2,5);
        assertTrue(gate.accept(view(target,2,5,19,8,StripViewSnapshot.Status.AVAILABLE,true)));
        assertFalse(gate.accept(view(target,3,5,19,9,StripViewSnapshot.Status.AVAILABLE,true)));
        assertFalse(gate.accept(view(UUID.randomUUID(),2,5,19,9,StripViewSnapshot.Status.AVAILABLE,true)));
        assertFalse(gate.accept(view(target,2,5,19,7,StripViewSnapshot.Status.AVAILABLE,true)));
        assertTrue(gate.accept(view(target,2,5,0,9,StripViewSnapshot.Status.UNAVAILABLE,false)));
        assertEquals(19,gate.entityId());
        assertEquals(6,gate.querySequence(6));
        assertTrue(gate.accept(view(target,2,6,0,0,StripViewSnapshot.Status.UNAVAILABLE,false)));
        assertEquals(9,gate.targetRevision());
        assertEquals(7,gate.querySequence(7));
        assertFalse(gate.accept(view(target,2,7,20,10,StripViewSnapshot.Status.AVAILABLE,true)));
        assertFalse(gate.open());
    }

    @Test void serverCloseClosesSessionAndAllLaterPushesAreRejected() {
        var gate=new StripViewCorrelation();var target=UUID.randomUUID();gate.begin(target,2,5);
        assertTrue(gate.accept(view(target,2,5,0,0,StripViewSnapshot.Status.CLOSED,false)));
        assertFalse(gate.open());
        assertFalse(gate.accept(view(target,2,5,19,8,StripViewSnapshot.Status.AVAILABLE,true)));
    }

    @Test void localRangeCloseRejectsInFlightQueryReplyAndStaleServerClose() {
        var gate=new StripViewCorrelation();var target=UUID.randomUUID();
        gate.begin(target,2,5);
        assertTrue(gate.accept(view(target,2,5,19,8,StripViewSnapshot.Status.AVAILABLE,true)));
        assertEquals(6,gate.querySequence(6));
        gate.close();
        assertFalse(gate.accept(view(target,2,6,19,9,StripViewSnapshot.Status.AVAILABLE,true)));
        assertFalse(gate.accept(view(target,2,5,0,8,StripViewSnapshot.Status.CLOSED,false)));
        assertFalse(gate.open());
    }
}
