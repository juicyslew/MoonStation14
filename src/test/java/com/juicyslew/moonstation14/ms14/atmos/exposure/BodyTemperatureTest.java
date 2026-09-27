package com.juicyslew.moonstation14.ms14.atmos.exposure;

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

class BodyTemperatureTest {
    @Test
    void componentAndAttachmentCodecsRoundTrip() {
        BodyTemperatureAttachment value = new BodyTemperatureAttachment(new BodyTemperatureComponent(298.123456789));
        var json = BodyTemperatureAttachment.CODEC.encodeStart(JsonOps.INSTANCE, value).getOrThrow();
        assertEquals(value, BodyTemperatureAttachment.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        assertEquals(value, BodyTemperatureAttachment.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"kelvin\":298.123456789}")).getOrThrow());

        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            BodyTemperatureAttachment.STREAM_CODEC.encode(buffer, value);
            assertEquals(value, BodyTemperatureAttachment.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void valuesAreFiniteAndBounded() {
        assertEquals(2.7, new BodyTemperatureComponent(2.7).kelvin());
        assertEquals(20000, new BodyTemperatureComponent(20000).kelvin());
        for (String invalid : new String[]{"NaN", "Infinity", "-Infinity", "2.69", "20000.01"}) {
            assertTrue(BodyTemperatureComponent.CODEC.parse(JsonOps.INSTANCE,
                    JsonParser.parseString("{\"kelvin\":" + invalid + "}")).error().isPresent(), invalid);
        }
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
                2.69, 20000.01}) {
            assertThrows(IllegalArgumentException.class, () -> new BodyTemperatureComponent(invalid));
        }
    }

    @Test
    void defaultsAreMeaningfulAndMutationsCreateImmutableSnapshots() {
        BodyTemperatureAttachment attachment = new BodyTemperatureAttachment();
        assertEquals(310.15, attachment.kelvin());
        assertFalse(attachment.isEmpty());

        BodyTemperatureComponent before = attachment.toComponent();
        attachment.setKelvin(305.25);
        assertEquals(310.15, before.kelvin());
        assertEquals(305.25, attachment.toComponent().kelvin());
        assertFalse(before.equals(attachment.toComponent()));
    }
}
