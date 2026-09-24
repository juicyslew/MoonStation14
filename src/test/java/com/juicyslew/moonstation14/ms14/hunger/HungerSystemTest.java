package com.juicyslew.moonstation14.ms14.hunger;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HungerSystemTest {
    @Test void floatCodecsValidateAndRoundTrip() {
        HungerAttachment value = new HungerAttachment(new HungerComponent(151.5f));
        var json = HungerAttachment.CODEC.encodeStart(JsonOps.INSTANCE, value).getOrThrow();
        assertEquals(value, HungerAttachment.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        assertEquals(value, HungerAttachment.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"hunger\":151.5}")).getOrThrow());
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        HungerAttachment.STREAM_CODEC.encode(buffer, value);
        assertEquals(value.hunger(), HungerAttachment.STREAM_CODEC.decode(buffer).hunger());
        buffer.release();
        for (String invalid : new String[]{"NaN", "Infinity", "-1", "201"}) {
            assertTrue(HungerComponent.CODEC.parse(JsonOps.INSTANCE,
                    JsonParser.parseString("{\"hunger\":" + invalid + "}")).error().isPresent(), invalid);
        }
        assertThrows(IllegalArgumentException.class, () -> new HungerComponent(Float.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> new HungerComponent(-.1f));
        assertThrows(IllegalArgumentException.class, () -> new HungerComponent(200.1f));
    }

    @Test void defaultsAndReducerTransitionsPreserveFloatSemantics() {
        assertEquals(150f, HungerComponent.DEFAULT.hunger());
        assertEquals(150f, HungerSystem.defaultForAbsent(false, 0f));
        assertEquals(123.25f, HungerSystem.defaultForAbsent(true, 123.25f));
        assertEquals(151.5f, HungerReducer.satiate(150f, 1.5f, 1f).after());
        assertEquals(153f, HungerReducer.satiate(150f, 1.5f, 2f).after());
        assertEquals(148.5f, HungerReducer.satiate(150f, -1.5f, 1f).after());
        assertEquals(200f, HungerReducer.satiate(199f, 5f, 1f).after());
        assertEquals(0f, HungerReducer.satiate(1f, -5f, 1f).after());
        assertFalse(HungerReducer.satiate(150f, 0f, 1f).changed());
        assertFalse(HungerReducer.satiate(200f, 5f, 1f).changed());
        assertFalse(HungerReducer.satiate(0f, -5f, 1f).changed());
        assertFalse(HungerReducer.satiate(150f, Float.MAX_VALUE, Float.MAX_VALUE).valid());
        assertFalse(HungerReducer.satiate(150f, Float.NaN, 1f).valid());
        assertFalse(HungerReducer.satiate(Float.POSITIVE_INFINITY, 1f, 1f).valid());
    }

    @Test void streamCodecsRejectOutOfRangeAndNonfiniteFloats() {
        for (float invalidValue : new float[]{201f, Float.NaN}) {
            RegistryFriendlyByteBuf componentBuffer = new RegistryFriendlyByteBuf(
                    Unpooled.buffer(), RegistryAccess.EMPTY);
            try {
                componentBuffer.writeFloat(invalidValue);
                assertThrows(IllegalArgumentException.class,
                        () -> HungerComponent.STREAM_CODEC.decode(componentBuffer));
            } finally {
                componentBuffer.release();
            }

            RegistryFriendlyByteBuf attachmentBuffer = new RegistryFriendlyByteBuf(
                    Unpooled.buffer(), RegistryAccess.EMPTY);
            try {
                attachmentBuffer.writeFloat(invalidValue);
                assertThrows(IllegalArgumentException.class,
                        () -> HungerAttachment.STREAM_CODEC.decode(attachmentBuffer));
            } finally {
                attachmentBuffer.release();
            }
        }
    }

    @Test void ss14LevelsDecayAndInitializationBoundsAreExact() {
        assertEquals(HungerReducer.Level.DEAD, HungerReducer.classify(0f));
        assertEquals(HungerReducer.Level.STARVING, HungerReducer.classify(49.9f));
        assertEquals(HungerReducer.Level.STARVING, HungerReducer.classify(50f));
        assertEquals(HungerReducer.Level.STARVING, HungerReducer.classify(99.9f));
        assertEquals(HungerReducer.Level.PECKISH, HungerReducer.classify(100f));
        assertEquals(HungerReducer.Level.PECKISH, HungerReducer.classify(149.9f));
        assertEquals(HungerReducer.Level.OKAY, HungerReducer.classify(150f));
        assertEquals(HungerReducer.Level.OKAY, HungerReducer.classify(199.9f));
        assertEquals(HungerReducer.Level.OVERFED, HungerReducer.classify(200f));
        assertEquals(110, HungerReducer.initialValue(110));
        assertEquals(149, HungerReducer.initialValue(149));
        assertThrows(IllegalArgumentException.class, () -> HungerReducer.initialValue(150));
        assertEquals(200f - HungerReducer.BASE_DECAY_PER_SECOND * 1.2f,
                HungerReducer.decayOneSecond(200f).after());
        assertEquals(150f - HungerReducer.BASE_DECAY_PER_SECOND,
                HungerReducer.decayOneSecond(150f).after());
        assertEquals(100f - HungerReducer.BASE_DECAY_PER_SECOND * 0.8f,
                HungerReducer.decayOneSecond(100f).after());
        assertEquals(50f - HungerReducer.BASE_DECAY_PER_SECOND * 0.6f,
                HungerReducer.decayOneSecond(50f).after());
        assertFalse(HungerReducer.decayOneSecond(0f).changed());
        assertEquals(HungerReducer.Level.OKAY, HungerReducer.classify(150f));
    }

    @Test void initializedDefaultHungerIsPersisted() {
        HungerAttachment value = new HungerAttachment();
        assertTrue(HungerAttachment.CODEC.encodeStart(JsonOps.INSTANCE, value).result().isPresent());
        assertFalse(value.isEmpty());
    }
}
