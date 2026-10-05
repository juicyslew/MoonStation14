package com.juicyslew.moonstation14.ms14.hands.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BodyWorldGridActionPolicyTest {
    private static RegistryFriendlyByteBuf buffer() { return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY); }

    @Test void modesRoundTripAndRejectTrailingOrMalformedFields() {
        UUID target = UUID.randomUUID(), nonce = UUID.randomUUID();
        var store = new BodyWorldGridAction(BodyWorldGridAction.Mode.STORE, 1, 1, target, nonce, 2, 0,
                "left", "child", -1, -1, -1, 2, 1, 90);
        var take = new BodyWorldGridAction(BodyWorldGridAction.Mode.TAKE, 1, 2, target, nonce, 2, 1,
                "right", "child", 2, 1, 90, -1, -1, -1);
        var move = new BodyWorldGridAction(BodyWorldGridAction.Mode.MOVE, 1, 3, target, nonce, 2, 2,
                null, "child", 2, 1, 90, 1, 1, 0);
        for (var action : new BodyWorldGridAction[] {store, take, move}) {
            var buf = buffer(); BodyWorldGridAction.STREAM_CODEC.encode(buf, action);
            assertEquals(action, BodyWorldGridAction.STREAM_CODEC.decode(buf));
            var trailing = buffer(); BodyWorldGridAction.STREAM_CODEC.encode(trailing, action); trailing.writeByte(7);
            assertThrows(IllegalArgumentException.class, () -> BodyWorldGridAction.STREAM_CODEC.decode(trailing));
        }
        assertThrows(IllegalArgumentException.class, () -> new BodyWorldGridAction(
                BodyWorldGridAction.Mode.STORE, 1, 1, target, nonce, 0, 0, "left", "child", 0, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new BodyWorldGridAction(
                BodyWorldGridAction.Mode.MOVE, 1, 1, target, nonce, 0, 0, "left", "child", 0, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new BodyWorldGridAction(
                BodyWorldGridAction.Mode.TAKE, 1, 1, target, nonce, 0, -1, "left", "child", 0, 0, 0, -1, -1, -1));
        var truncated = buffer(); truncated.writeEnum(BodyWorldGridAction.Mode.MOVE);
        assertThrows(RuntimeException.class, () -> BodyWorldGridAction.STREAM_CODEC.decode(truncated));
        var flag = buffer(); BodyWorldGridAction.STREAM_CODEC.encode(flag, store);
        // Mode byte + epoch + sequence + two UUIDs + hand and host revision.
        flag.setByte(1 + 1 + 1 + 16 + 16 + 1 + 1, 2);
        assertThrows(IllegalArgumentException.class, () -> BodyWorldGridAction.STREAM_CODEC.decode(flag));
    }

    @Test void actionGateIsSeparateAndRejectsReplayAndRateBurst() {
        var query = new BodyWorldGridService.Gate();
        var action = new BodyWorldGridService.ActionGate();
        assertTrue(query.admit(1, 1, 10));
        assertNull(action.reject(1, 1, 10));
        assertEquals(BodyWorldGridActionResult.Reason.REPLAY, action.reject(1, 1, 10));
        assertNull(action.reject(1, 2, 10));
        assertNull(action.reject(1, 3, 10));
        assertEquals(BodyWorldGridActionResult.Reason.RATE_LIMITED, action.reject(1, 4, 10));
        assertNull(action.reject(1, 4, 11));
    }

    @Test void responseCorrelatesNonceAndRevisionAndRejectsTrailingBytes() {
        var response = new BodyWorldGridActionResult(3, 4, UUID.randomUUID(), UUID.randomUUID(), 2,
                BodyWorldGridActionResult.Reason.OK);
        var buf = buffer(); BodyWorldGridActionResult.STREAM_CODEC.encode(buf, response);
        assertEquals(response, BodyWorldGridActionResult.STREAM_CODEC.decode(buf));
        var trailing = buffer(); BodyWorldGridActionResult.STREAM_CODEC.encode(trailing, response); trailing.writeByte(1);
        assertThrows(IllegalArgumentException.class, () -> BodyWorldGridActionResult.STREAM_CODEC.decode(trailing));
    }

    @Test void refreshedHostSnapshotAcceptsIncrementedRevisionButNeverNegative() {
        UUID id = UUID.randomUUID();
        var snapshot = new BodyWorldGridSnapshot(1, 1, BodyWorldGridSnapshot.Reason.OK,
                id, UUID.randomUUID(), 1, 0, UUID.randomUUID(), "bag", 7, 4, List.of(), UUID.randomUUID(), 2);
        var buf = buffer(); BodyWorldGridSnapshot.STREAM_CODEC.encode(buf, snapshot);
        assertEquals(snapshot, BodyWorldGridSnapshot.STREAM_CODEC.decode(buf));
        assertThrows(IllegalArgumentException.class, () -> new BodyWorldGridSnapshot(1, 1,
                BodyWorldGridSnapshot.Reason.OK, id, UUID.randomUUID(), 1, 0, UUID.randomUUID(),
                "bag", 7, 4, List.of(), UUID.randomUUID(), -1));
    }
}
