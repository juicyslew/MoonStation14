package com.juicyslew.moonstation14.ms14.ingestion;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class IngestionCommittedTest {
    @Test
    void payloadRequiresBodyAndPositiveControlEpoch() {
        var body = UUID.randomUUID();
        assertEquals(new IngestionCommitted(body, 7), new IngestionCommitted(body, 7));
        assertThrows(IllegalArgumentException.class, () -> new IngestionCommitted(null, 7));
        assertThrows(IllegalArgumentException.class, () -> new IngestionCommitted(body, 0));
        assertThrows(IllegalArgumentException.class, () -> new IngestionCommitted(body, -1));
        assertEquals("moonstation14:ingestion_committed", IngestionCommitted.TYPE.id().toString());
        var hint = new IngestionCommitted(body, 7);
        assertTrue(hint.matchesAuthority(body, 7));
        assertFalse(hint.matchesAuthority(UUID.randomUUID(), 7));
        assertFalse(hint.matchesAuthority(body, 8));
    }
}
