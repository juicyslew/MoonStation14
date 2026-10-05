package com.juicyslew.moonstation14.ms14.atmos.piping.ui;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class VentilationControlRequestTest {
    private static final UUID SESSION = UUID.randomUUID();

    @Test void acceptsBoundedRequest() {
        assertDoesNotThrow(() -> new VentilationControlRequest(0, SESSION, 1, 0,
                VentilationControlRequest.Operation.QUERY, false, 0));
    }

    @Test void rejectsNullSessionAndInvalidBounds() {
        assertThrows(IllegalArgumentException.class, () -> request(null, 0));
        assertThrows(IllegalArgumentException.class, () -> request(SESSION, -1));
        assertThrows(IllegalArgumentException.class, () -> request(SESSION,
                com.juicyslew.moonstation14.ms14.atmos.core.GasType.values().length));
    }

    private static VentilationControlRequest request(UUID session, int index) {
        return new VentilationControlRequest(0, session, 1, 0,
                VentilationControlRequest.Operation.SET_FILTER_GAS, true, index);
    }

}
