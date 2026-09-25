package com.juicyslew.moonstation14.ms14.slip;

import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlidingStateTest {
    @Test
    void booleanComponentRoundTripsAndDefaultsToFalse() {
        assertFalse(new SlidingAttachment().sliding());
        var encoded = SlidingAttachment.CODEC.encodeStart(JsonOps.INSTANCE, enabled());
        assertTrue(encoded.result().isPresent());
        assertEquals(true, encoded.result().orElseThrow().getAsBoolean());
        var decoded = SlidingAttachment.CODEC.parse(JsonOps.INSTANCE, encoded.result().orElseThrow());
        assertTrue(decoded.result().orElseThrow().sliding());
        assertEquals(new SlidingComponent(true), enabled().toComponent());
    }

    private static SlidingAttachment enabled() {
        SlidingAttachment value = new SlidingAttachment();
        value.setSliding(true);
        return value;
    }
}
