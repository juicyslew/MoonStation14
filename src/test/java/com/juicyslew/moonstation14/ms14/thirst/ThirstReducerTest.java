package com.juicyslew.moonstation14.ms14.thirst;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import com.juicyslew.moonstation14.ms14.activity.EntityActivity;

import static org.junit.jupiter.api.Assertions.*;

class ThirstReducerTest {
    @Test void thresholdBoundariesUseInclusiveUpstreamClassification() {
        assertEquals(ThirstReducer.Level.DEAD, ThirstReducer.classify(0f));
        assertEquals(ThirstReducer.Level.PARCHED, ThirstReducer.classify(.01f));
        assertEquals(ThirstReducer.Level.PARCHED, ThirstReducer.classify(150f));
        assertEquals(ThirstReducer.Level.THIRSTY, ThirstReducer.classify(150.01f));
        assertEquals(ThirstReducer.Level.THIRSTY, ThirstReducer.classify(300f));
        assertEquals(ThirstReducer.Level.OKAY, ThirstReducer.classify(300.01f));
        assertEquals(ThirstReducer.Level.OKAY, ThirstReducer.classify(450f));
        assertEquals(ThirstReducer.Level.OVER_HYDRATED, ThirstReducer.classify(450.01f));
        assertEquals(ThirstReducer.Level.OVER_HYDRATED, ThirstReducer.classify(600f));
    }

    @Test void satiationIsFiniteAndBounded() {
        assertEquals(451.5f, ThirstReducer.satiate(450f, 1.5f, 1f).after());
        assertEquals(453f, ThirstReducer.satiate(450f, 1.5f, 2f).after());
        assertEquals(600f, ThirstReducer.satiate(599f, 5f, 1f).after());
        assertEquals(0f, ThirstReducer.satiate(1f, -5f, 1f).after());
        assertFalse(ThirstReducer.satiate(500f, Float.MAX_VALUE, Float.MAX_VALUE).valid());
        assertFalse(ThirstReducer.satiate(500f, Float.NaN, 1f).valid());
        assertFalse(ThirstReducer.satiate(Float.POSITIVE_INFINITY, 1f, 1f).valid());
    }

    @Test void oneSecondDecayUsesTheCurrentClassificationAndStopsAtZero() {
        assertEquals(599.88f, ThirstReducer.decayOneSecond(600f).after(), .0001f);
        assertEquals(449.9f, ThirstReducer.decayOneSecond(450f).after(), .0001f);
        assertEquals(299.92f, ThirstReducer.decayOneSecond(300f).after(), .0001f);
        assertEquals(149.94f, ThirstReducer.decayOneSecond(150f).after(), .0001f);
        assertEquals(0f, ThirstReducer.decayOneSecond(0f).after());
        assertFalse(ThirstReducer.decayOneSecond(0f).changed());
    }

    @Test void initialValueIsValidatedForSingleInitializationByCaller() {
        assertEquals(310, ThirstReducer.initialValue(310));
        assertEquals(448, ThirstReducer.initialValue(448));
        assertThrows(IllegalArgumentException.class, () -> ThirstReducer.initialValue(309));
        assertThrows(IllegalArgumentException.class, () -> ThirstReducer.initialValue(449));
    }

    @Test void initializationAndActivityUseAbsentStateSeparatelyFromDefaultThirst() {
        assertEquals(ThirstComponent.MAX_THIRST, ThirstComponent.DEFAULT.thirst());
        assertTrue(ThirstReducer.INITIAL_MIN_INCLUSIVE <= ThirstReducer.initialValue(310));
        assertTrue(ThirstReducer.initialValue(448) < ThirstReducer.INITIAL_MAX_EXCLUSIVE);
        assertEquals(ThirstReducer.Level.THIRSTY, ThirstReducer.classify(250f));
        assertFalse(ThirstReducer.decayOneSecond(0f).changed());
        assertTrue(ThirstReducer.decayOneSecond(1f).changed());
        assertEquals(20, EntityActivity.THIRST.tickInterval());
        int dueTicks = 0;
        for (int tick = 0; tick < 20; tick++) {
            if (EntityActivity.THIRST.isDue(tick, 7)) dueTicks++;
        }
        assertEquals(1, dueTicks, "thirst has exactly one scheduler opportunity per 20 ticks");
    }

    @Test void codecsRoundTripAndRejectInvalidValuesWhilePersistingNominalDefault() {
        ThirstAttachment value = new ThirstAttachment(ThirstComponent.DEFAULT);
        assertFalse(value.isEmpty(), "initialized default must be persisted");
        var encoded = ThirstAttachment.CODEC.encodeStart(JsonOps.INSTANCE, value).getOrThrow();
        assertEquals(value, ThirstAttachment.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
        assertEquals(value, ThirstAttachment.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"thirst\":600}")).getOrThrow());
        for (String invalid : new String[]{"NaN", "Infinity", "-1", "601"}) {
            assertTrue(ThirstComponent.CODEC.parse(JsonOps.INSTANCE,
                    JsonParser.parseString("{\"thirst\":" + invalid + "}")).error().isPresent(), invalid);
        }
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            ThirstAttachment.STREAM_CODEC.encode(buffer, new ThirstAttachment(new ThirstComponent(321.5f)));
            assertEquals(321.5f, ThirstAttachment.STREAM_CODEC.decode(buffer).thirst());
        } finally {
            buffer.release();
        }
    }
}
