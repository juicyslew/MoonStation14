package com.juicyslew.moonstation14.ms14.ghost_roles.client;

import com.juicyslew.moonstation14.ms14.ghost_roles.network.GhostRolePayloads;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class GhostRoleMenuStateTest {
    @Test void rejectsStaleContextAndUncorrelatedReplies() {
        var state=new GhostRoleMenuState();var ghost=UUID.randomUUID();var context=new GhostRolePayloads.Context(ghost,4);
        state.open(context);
        var entry=new GhostRolePayloads.Entry(UUID.randomUUID(),"mouse","Mouse",true,true);
        assertFalse(state.snapshot(new GhostRolePayloads.Snapshot(new GhostRolePayloads.Context(ghost,3),List.of(entry))));
        assertTrue(state.snapshot(new GhostRolePayloads.Snapshot(context,List.of(entry))));
        UUID correlation=state.beginRequest();assertNotNull(correlation);assertNull(state.beginRequest());
        assertFalse(state.result(new GhostRolePayloads.Result(context,UUID.randomUUID(),false,"stale")));
        assertTrue(state.result(new GhostRolePayloads.Result(context,correlation,false,"denied")));
        assertEquals("denied",state.status());assertNull(state.pending());
        state.close();assertFalse(state.activeFor(context));
    }

    @Test void emptySnapshotClearsRefreshAndMissingReplyTimesOutWithoutRestarting() {
        var state=new GhostRoleMenuState();var context=new GhostRolePayloads.Context(UUID.randomUUID(),1);
        state.open(context);state.refresh(10);
        assertTrue(state.snapshot(new GhostRolePayloads.Snapshot(context,List.of())));
        assertFalse(state.refreshing());assertTrue(state.entries().isEmpty());
        state.refresh(20);state.refresh(80);state.tick(119);assertTrue(state.refreshing());
        state.tick(120);assertFalse(state.refreshing());assertTrue(state.status().contains("No response"));
        assertFalse(state.snapshot(new GhostRolePayloads.Snapshot(new GhostRolePayloads.Context(context.ghost(),2),List.of())));
        assertFalse(state.refreshing());state.close();
    }

    @Test void staleCorrelationCannotAcceptTeleportLookAndMatchingCorrelationCan() {
        var state=new GhostRoleMenuState();var context=new GhostRolePayloads.Context(UUID.randomUUID(),4);
        state.open(context);var pending=state.beginRequest();
        var look=new GhostRolePayloads.TeleportLook(90,-12);
        assertFalse(state.result(new GhostRolePayloads.Result(context,UUID.randomUUID(),true,"stale",look)));
        assertTrue(state.result(new GhostRolePayloads.Result(context,pending,true,"accepted",look)));
        assertNull(state.pending());
    }

    @Test void teleportLookRejectsNonFiniteOrOutOfRangeAngles() {
        assertThrows(IllegalArgumentException.class, () -> new GhostRolePayloads.TeleportLook(Float.NaN, 0));
        assertThrows(IllegalArgumentException.class, () -> new GhostRolePayloads.TeleportLook(181, 0));
        assertThrows(IllegalArgumentException.class, () -> new GhostRolePayloads.TeleportLook(0, 91));
    }

    @Test void initialRefreshDeadlineStartsAtOpenTickAndExplicitRetryGetsItsOwnDeadline() {
        var state=new GhostRoleMenuState();var context=new GhostRolePayloads.Context(UUID.randomUUID(),1);
        state.open(context,10_000);state.tick(10_099);assertTrue(state.refreshing());
        state.tick(10_100);assertFalse(state.refreshing());
        state.refresh(20_000);state.refresh(20_050);state.tick(20_099);assertTrue(state.refreshing());
        state.tick(20_100);assertFalse(state.refreshing());
        state.refresh(30_000);assertTrue(state.refreshing());state.tick(30_100);assertFalse(state.refreshing());
    }
}
