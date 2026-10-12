package com.juicyslew.moonstation14.ms14.medical_observer;

import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObserverChunkTicketsTest {
    private static final UUID KEY = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void ticketMustMoveWithViewAndRetainsConfiguredRadiusAndOwnerKey() {
        ChunkPos before = new ChunkPos(0, 0);
        ChunkPos after = new ChunkPos(1, 0);

        assertTrue(ObserverChunkTickets.sameTicket(before, 10, KEY, before, 10, KEY));
        assertFalse(ObserverChunkTickets.sameTicket(before, 10, KEY, after, 10, KEY));
        assertFalse(ObserverChunkTickets.sameTicket(after, 10, KEY, after, 11, KEY));
        assertFalse(ObserverChunkTickets.sameTicket(after, 10, KEY, after, 10, UUID.randomUUID()));
    }
}
