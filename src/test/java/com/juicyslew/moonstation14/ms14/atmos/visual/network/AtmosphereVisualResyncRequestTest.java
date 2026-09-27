package com.juicyslew.moonstation14.ms14.atmos.visual.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AtmosphereVisualResyncRequestTest {
    @Test
    void acceptsWorldCoordinateLimitsAndRejectsUntrustedExtremeCoordinates() {
        assertDoesNotThrow(() -> new AtmosphereVisualResyncRequest(
                AtmosphereVisualResyncRequest.MAX_CHUNK_COORDINATE,
                -AtmosphereVisualResyncRequest.MAX_CHUNK_COORDINATE));
        assertThrows(IllegalArgumentException.class, () -> new AtmosphereVisualResyncRequest(
                Integer.MAX_VALUE, 0));
        assertThrows(IllegalArgumentException.class, () -> new AtmosphereVisualResyncRequest(
                0, Integer.MIN_VALUE));
    }

    @Test
    void codecRoundTripsAndRejectsOutOfBoundsOrOverlengthRequests() {
        var request = new AtmosphereVisualResyncRequest(-12, 34);
        RegistryFriendlyByteBuf encoded = buffer();
        try {
            AtmosphereVisualResyncRequest.STREAM_CODEC.encode(encoded, request);
            assertEquals(request, AtmosphereVisualResyncRequest.STREAM_CODEC.decode(encoded));
        } finally {
            encoded.release();
        }

        RegistryFriendlyByteBuf invalid = buffer();
        try {
            invalid.writeInt(Integer.MAX_VALUE);
            invalid.writeInt(0);
            assertThrows(IllegalArgumentException.class,
                    () -> AtmosphereVisualResyncRequest.STREAM_CODEC.decode(invalid));
        } finally {
            invalid.release();
        }

        RegistryFriendlyByteBuf trailing = buffer();
        try {
            trailing.writeInt(0);
            trailing.writeInt(0);
            trailing.writeByte(1);
            assertThrows(IllegalArgumentException.class,
                    () -> AtmosphereVisualResyncRequest.STREAM_CODEC.decode(trailing));
        } finally {
            trailing.release();
        }
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }
}
