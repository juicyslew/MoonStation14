package com.juicyslew.moonstation14.ms14.combat;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DisarmFlightNetworkingTest {
    private final UUID actor = UUID.randomUUID(), victim = UUID.randomUUID();
    @Test void includesInvolvedControlledBodiesAndNearbyObserversButNotUnrelatedDistantPlayers() {
        assertTrue(DisarmFlightNetworking.include(UUID.randomUUID(), victim, actor, victim, 100_000));
        assertTrue(DisarmFlightNetworking.include(actor, null, actor, victim, 100_000));
        assertTrue(DisarmFlightNetworking.include(UUID.randomUUID(), null, actor, victim, 64 * 64));
        assertFalse(DisarmFlightNetworking.include(UUID.randomUUID(), UUID.randomUUID(), actor, victim, 64 * 64 + 1));
    }
    @Test void controlledBodyAndNearbyOverlapStillHasOneRecipientThroughLinkedHashSet() {
        var set = new java.util.LinkedHashSet<UUID>(); var p = UUID.randomUUID();
        if (DisarmFlightNetworking.include(p, victim, actor, victim, 1)) set.add(p);
        if (DisarmFlightNetworking.include(p, null, actor, victim, 1)) set.add(p);
        assertEquals(1, set.size());
    }
    @Test void publisherActorRoleKernelRequiresDirectOrControlledActorAndNeverVictimOrObserver() {
        assertTrue(DisarmFlightNetworking.actorRole(true,false));
        assertTrue(DisarmFlightNetworking.actorRole(false,true)); // result of canonical controlled-body lookup
        assertFalse(DisarmFlightNetworking.actorRole(false,false)); // victim-only or observer
    }
}
