package com.juicyslew.moonstation14.ms14.ghost_roles.server;

import com.juicyslew.moonstation14.ms14.ghost_roles.client.GhostRoleMenuState;
import com.juicyslew.moonstation14.ms14.ghost_roles.network.GhostRolePayloads;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class GhostRoleClientTransportTest {
    @Test void ghostRoleEntryRequiresAtLeastOneSupportedAction() {
        var validTeleportOnly = new GhostRolePayloads.Entry(new UUID(0, 1), "a-filler-0", "Filler", true, false, false);
        assertTrue(validTeleportOnly.teleport());
        assertFalse(validTeleportOnly.takeover());
        assertThrows(IllegalArgumentException.class,
                () -> new GhostRolePayloads.Entry(new UUID(0, 1), "a-filler-0", "Filler", false, false, false));
    }

    @Test void registeredClientReceiveDispatchesSnapshotAndResultToInstalledClientHandler() {
        var state=new GhostRoleMenuState();var context=new GhostRolePayloads.Context(UUID.randomUUID(),3);
        state.open(context);var correlation=state.beginRequest();
        com.juicyslew.moonstation14.ms14.ghost_roles.client.GhostRoleNetworking.installClientHandler((payload, ignored) -> {
            if(payload instanceof GhostRolePayloads.Snapshot snapshot) state.snapshot(snapshot);
            if(payload instanceof GhostRolePayloads.Result result) state.result(result);
        });
        var entry=new GhostRolePayloads.Entry(UUID.randomUUID(),"role","Role",true,false);
        GhostRoleNetworking.receiveRegisteredClientPayload(new GhostRolePayloads.Snapshot(context,List.of(entry)),null);
        GhostRoleNetworking.receiveRegisteredClientPayload(new GhostRolePayloads.Result(context,correlation,true,"accepted"),null);
        assertEquals(List.of(entry),state.entries());assertEquals("accepted",state.status());
    }

    @Test void snapshotRetainsServerDevelopmentLabelWithoutChangingOrdinaryEntries() {
        var context = new GhostRolePayloads.Context(UUID.randomUUID(), 4);
        var dev = new GhostRolePayloads.Entry(UUID.randomUUID(), "moonstation14:bat", "Bat", false, true, true);
        var ordinary = new GhostRolePayloads.Entry(UUID.randomUUID(), "mouse", "Mouse", true, true);
        var snapshot = new GhostRolePayloads.Snapshot(context, List.of(dev, ordinary));
        assertTrue(snapshot.entries().get(0).development());
        assertFalse(snapshot.entries().get(1).development());
    }
}
