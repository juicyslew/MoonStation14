package com.juicyslew.moonstation14.ms14.hands.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BodyWorldWorldCrossActionCodecTest {
    private static RegistryFriendlyByteBuf buffer() { return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY); }

    @Test void intentRoundTripsAndRejectsTrailingAndInvalidData() {
        UUID source = UUID.randomUUID(), sourceNonce = UUID.randomUUID();
        UUID destination = UUID.randomUUID(), destinationNonce = UUID.randomUUID();
        var action = new BodyWorldWorldCrossAction(1, 2, source, sourceNonce, 3,
                destination, destinationNonce, 4, 5, "child", 1, 2, 90, 3, 4, 180);
        var encoded = buffer(); BodyWorldWorldCrossAction.STREAM_CODEC.encode(encoded, action);
        assertEquals(action, BodyWorldWorldCrossAction.STREAM_CODEC.decode(encoded));
        var trailing = buffer(); BodyWorldWorldCrossAction.STREAM_CODEC.encode(trailing, action); trailing.writeByte(1);
        assertThrows(IllegalArgumentException.class, () -> BodyWorldWorldCrossAction.STREAM_CODEC.decode(trailing));
        assertThrows(IllegalArgumentException.class, () -> new BodyWorldWorldCrossAction(1, 1, source, sourceNonce, 0,
                source, destinationNonce, 0, 0, "child", 0, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new BodyWorldWorldCrossAction(1, 1, source, sourceNonce, 0,
                destination, destinationNonce, 0, 0, "child", -1, 0, 0, 0, 0, 0));
        var truncated = buffer(); truncated.writeVarLong(1);
        assertThrows(RuntimeException.class, () -> BodyWorldWorldCrossAction.STREAM_CODEC.decode(truncated));
    }

    @Test void resultCorrelatesBothOwnersAndRejectsTrailingBytes() {
        var result = new BodyWorldWorldCrossActionResult(1, 2, UUID.randomUUID(), UUID.randomUUID(), 3,
                UUID.randomUUID(), UUID.randomUUID(), 4, BodyWorldWorldCrossActionResult.Reason.OK);
        var encoded = buffer(); BodyWorldWorldCrossActionResult.STREAM_CODEC.encode(encoded, result);
        assertEquals(result, BodyWorldWorldCrossActionResult.STREAM_CODEC.decode(encoded));
        var trailing = buffer(); BodyWorldWorldCrossActionResult.STREAM_CODEC.encode(trailing, result); trailing.writeByte(1);
        assertThrows(IllegalArgumentException.class, () -> BodyWorldWorldCrossActionResult.STREAM_CODEC.decode(trailing));
    }
}
