package com.juicyslew.moonstation14.ms14.hands.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BodyBlockUseRequestTest {
    @Test
    void roundTripsOptionalTokenAndRejectsMalformedTokens() {
        var empty = request(null);
        assertEquals(empty, roundTrip(empty));
        var occupied = request("item-token");
        assertEquals(occupied, roundTrip(occupied));
        assertThrows(IllegalArgumentException.class, () -> request(" "));
        assertThrows(IllegalArgumentException.class, () -> request("x".repeat(
                com.juicyslew.moonstation14.ms14.hands.ItemToken.MAX_LENGTH + 1)));
    }

    @Test
    void forgedHandStateMustMatchBothTokenAndStackPresence() {
        assertFalse(BodyHandRequestService.blockUseHandStateMatches(null, true, true, "forged"));
        assertFalse(BodyHandRequestService.blockUseHandStateMatches("forged", true, false, "forged"));
        assertFalse(BodyHandRequestService.blockUseHandStateMatches("forged", false, true, "forged"));
        assertFalse(BodyHandRequestService.blockUseHandStateMatches("forged", true, true, "other"));
        assertTrue(BodyHandRequestService.blockUseHandStateMatches(null, false, false, null));
        assertTrue(BodyHandRequestService.blockUseHandStateMatches("valid", true, true, "valid"));
    }

    private static BodyBlockUseRequest request(String token) {
        return new BodyBlockUseRequest(1, 1, 0, "main", token, BlockPos.ZERO, Direction.UP, false);
    }

    private static BodyBlockUseRequest roundTrip(BodyBlockUseRequest request) {
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        BodyBlockUseRequest.STREAM_CODEC.encode(buf, request);
        return BodyBlockUseRequest.STREAM_CODEC.decode(buf);
    }
}
