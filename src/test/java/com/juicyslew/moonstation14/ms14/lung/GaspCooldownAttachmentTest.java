package com.juicyslew.moonstation14.ms14.lung;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GaspCooldownAttachmentTest {
    @Test
    void consumesAttemptsAcrossSaturationChangesAndAllowsAtCooldownBoundary() {
        GaspCooldownAttachment cooldown = new GaspCooldownAttachment();
        assertEquals(java.util.OptionalLong.empty(), cooldown.lastAttemptTick());
        assertTrue(cooldown.consume(10, 160));
        assertEquals(java.util.OptionalLong.of(10), cooldown.lastAttemptTick());
        assertFalse(cooldown.consume(169, 160));
        assertEquals(java.util.OptionalLong.of(10), cooldown.lastAttemptTick());
        // Whether saturation recovered between ticks does not reset attempt state.
        assertTrue(cooldown.consume(170, 160));
        assertEquals(java.util.OptionalLong.of(170), cooldown.lastAttemptTick());
        assertFalse(cooldown.consume(170, 160));
        assertEquals(java.util.OptionalLong.of(170), cooldown.lastAttemptTick());
    }
}
