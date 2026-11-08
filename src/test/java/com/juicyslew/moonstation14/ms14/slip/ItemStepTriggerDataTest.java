package com.juicyslew.moonstation14.ms14.slip;

import com.mojang.serialization.JsonOps;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ItemStepTriggerDataTest {
    @Test
    void defaultsAndPeelOverrideDecodeAndRoundTrip() {
        assertEquals(new ItemStepTriggerData(true, .3, 3.5, false),
                ItemStepTriggerData.CODEC.codec().parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow(message -> new AssertionError(message)));
        JsonObject peel = new JsonObject();
        peel.addProperty("intersection_ratio", .2);
        ItemStepTriggerData decoded = ItemStepTriggerData.CODEC.codec().parse(JsonOps.INSTANCE, peel)
                .getOrThrow(message -> new AssertionError(message));
        assertEquals(new ItemStepTriggerData(true, .2, 3.5, false), decoded);
        assertEquals(decoded, ItemStepTriggerData.CODEC.codec().parse(JsonOps.INSTANCE,
                ItemStepTriggerData.CODEC.codec().encodeStart(JsonOps.INSTANCE, decoded).getOrThrow(message -> new AssertionError(message)))
                .getOrThrow(message -> new AssertionError(message)));
    }

    @Test
    void rejectsInvalidRatioAndSpeedIncludingNonFiniteValues() {
        assertThrows(IllegalArgumentException.class, () -> new ItemStepTriggerData(true, -.1, 1, false));
        assertThrows(IllegalArgumentException.class, () -> new ItemStepTriggerData(true, 1.1, 1, false));
        assertThrows(IllegalArgumentException.class, () -> new ItemStepTriggerData(true, Double.NaN, 1, false));
        assertThrows(IllegalArgumentException.class, () -> new ItemStepTriggerData(true, .2, Double.POSITIVE_INFINITY, false));
        assertThrows(IllegalArgumentException.class, () -> new ItemStepTriggerData(true, .2, -1, false));
    }
}
