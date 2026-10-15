package com.juicyslew.moonstation14.ms14.botany.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GrinderSessionServiceTest {
    @Test void publishesPowerTransitionsWithoutRevisionChange() {
        assertTrue(GrinderSessionService.shouldPublish(4, false, 4, true));
        assertTrue(GrinderSessionService.shouldPublish(4, true, 4, false));
        assertFalse(GrinderSessionService.shouldPublish(4, true, 4, true));
        assertTrue(GrinderSessionService.shouldPublish(5, true, 4, true));
        assertTrue(GrinderSessionService.shouldPublish(4, false, 4, null));
    }
}
