package com.juicyslew.moonstation14.ms14.slip;

import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** JVM-safe checks for the production query's unavailable boundary and immutable result contract. */
class SharedPuddleContactQueryTest {
    @Test void nullActorIsUnavailableRatherThanAnEmptyWorld() {
        var bounds = new AABB(0, 0, 0, 1, 1, 1);
        var result = SharedPuddleContactQuery.query(null, bounds);

        assertEquals(SharedPuddleContactQuery.State.UNAVAILABLE, result.state());
        assertFalse(result.available());
        assertNull(result.frameId());
        assertTrue(result.contacts().isEmpty());
    }

    @Test void feetBandNullActorIsUnavailable() {
        var result = SharedPuddleContactQuery.queryFeetBand(null);

        assertEquals(SharedPuddleContactQuery.State.UNAVAILABLE, result.state());
        assertNull(result.frameId());
        assertTrue(result.contacts().isEmpty());
    }

    @Test void resultDefensivelyCopiesContacts() {
        var contacts = new ArrayList<SharedPuddleContactQuery.Contact>();
        var result = new SharedPuddleContactQuery.Result(
                SharedPuddleContactQuery.State.AVAILABLE, UUID.randomUUID(), contacts);
        contacts.add(null);

        assertTrue(result.contacts().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> result.contacts().add(null));
    }
}
