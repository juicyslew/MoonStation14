package com.juicyslew.moonstation14.ms14.shuttle.fixture;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ShuttleFixtureShipIdsTest {
    @Test void displayAndResolutionHandleLoadedPrefixCollisions() {
        UUID first = UUID.fromString("12345678-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("12345678-1000-0000-0000-000000000002");
        List<UUID> loaded = List.of(first, second);

        assertEquals("ship-123456780", ShuttleFixtureShipIds.display(loaded, first));
        assertEquals("ship-123456781", ShuttleFixtureShipIds.display(loaded, second));
        assertNull(ShuttleFixtureShipIds.resolve(loaded, "ship-12345678"));
        assertEquals(first, ShuttleFixtureShipIds.resolve(loaded, "ship-123456780"));
        assertEquals(second, ShuttleFixtureShipIds.resolve(loaded, "ship-123456781"));
        assertEquals(first, ShuttleFixtureShipIds.resolve(loaded, first.toString()));
        assertNull(ShuttleFixtureShipIds.resolve(loaded, "ship-deadbeef"));
    }
}
