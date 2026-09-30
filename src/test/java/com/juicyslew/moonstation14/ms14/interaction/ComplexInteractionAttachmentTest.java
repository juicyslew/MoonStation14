package com.juicyslew.moonstation14.ms14.interaction;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ComplexInteractionAttachmentTest {
    @Test
    void enabledAndRevokedTombstonesRoundTripWithoutDefaultGrant() {
        var incidental = new ComplexInteractionAttachment(false, false);
        assertFalse(incidental.initialized());
        assertFalse(incidental.enabled());
        assertTrue(ComplexInteractionAttachment.CODEC.encodeStart(JsonOps.INSTANCE, incidental).error().isPresent());
        for (boolean enabled : new boolean[]{true, false}) {
            var state = new ComplexInteractionAttachment(true, enabled);
            assertEquals(state, ComplexInteractionAttachment.CODEC.parse(JsonOps.INSTANCE,
                    ComplexInteractionAttachment.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow()).getOrThrow());
        }
        assertTrue(ComplexInteractionAttachment.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"initialized\":false,\"enabled\":true}")).error().isPresent());
        assertTrue(ComplexInteractionAttachment.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"enabled\":true}")).error().isPresent());
        assertTrue(ComplexInteractionAttachment.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"initialized\":false,\"enabled\":false}")).error().isPresent());
    }
}
