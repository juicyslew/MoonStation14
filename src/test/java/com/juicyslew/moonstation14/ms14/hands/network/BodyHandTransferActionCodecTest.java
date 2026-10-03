package com.juicyslew.moonstation14.ms14.hands.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BodyHandTransferActionCodecTest {
    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }

    private static BodyHandActionRequest transfer(String destination) {
        return new BodyHandActionRequest(BodyHandActionRequest.Action.TRANSFER_HAND, "left", 1, 2, 3,
                null, "source-token", null, -1, -1, -1, -1, -1, -1, null, null, destination);
    }

    @Test void transferRoundTripsDestinationAndRejectsInvalidOrTrailingPayload() {
        var request = transfer("right");
        var encoded = buffer();
        BodyHandActionRequest.STREAM_CODEC.encode(encoded, request);
        assertEquals(request, BodyHandActionRequest.STREAM_CODEC.decode(encoded));

        assertThrows(IllegalArgumentException.class, () -> transfer(""));
        assertThrows(IllegalArgumentException.class, () -> transfer(null));

        var missing = buffer();
        missing.writeEnum(BodyHandActionRequest.Action.TRANSFER_HAND);
        missing.writeUtf("left"); missing.writeVarLong(1); missing.writeVarLong(2); missing.writeVarLong(3);
        missing.writeUtf("source-token");
        assertThrows(RuntimeException.class, () -> BodyHandActionRequest.STREAM_CODEC.decode(missing));

        var trailing = buffer();
        BodyHandActionRequest.STREAM_CODEC.encode(trailing, request); trailing.writeByte(1);
        assertThrows(IllegalArgumentException.class, () -> BodyHandActionRequest.STREAM_CODEC.decode(trailing));
    }

    @Test void oldActionWireShapeRemainsUnaffected() {
        var request = new BodyHandActionRequest(BodyHandActionRequest.Action.DROP, "left", 1, 2, 3,
                null, "source-token");
        var encoded = buffer();
        BodyHandActionRequest.STREAM_CODEC.encode(encoded, request);
        assertEquals(request, BodyHandActionRequest.STREAM_CODEC.decode(encoded));
    }
}
