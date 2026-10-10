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

    @Test void genericEquipmentActionCarriesBoundedSlotId() {
        var request = new BodyHandActionRequest(BodyHandActionRequest.Action.EQUIP_SLOT, "left", 1, 2, 3,
                null, "source-token", null, -1, -1, -1, -1, -1, -1, null, "pocket1", null);
        var encoded = buffer();
        BodyHandActionRequest.STREAM_CODEC.encode(encoded, request);
        assertEquals(request, BodyHandActionRequest.STREAM_CODEC.decode(encoded));
        assertThrows(IllegalArgumentException.class, () -> new BodyHandActionRequest(
                BodyHandActionRequest.Action.EQUIP_SLOT, "left", 1, 2, 3, null, "source-token",
                null, -1, -1, -1, -1, -1, -1, null, "x".repeat(com.juicyslew.moonstation14.ms14.hands.HandState.MAX_ID_LENGTH + 1), null));
    }

    @Test void moveEquipmentWireShapeAcceptsClientZeroedGridFields() {
        var request = new BodyHandActionRequest(BodyHandActionRequest.Action.MOVE_EQUIPMENT, "left", 1, 2, 3,
                null, "source-token", null, 0, 0, 0, 0, 0, 0, null, "pocket1", "id");
        var encoded = buffer();
        BodyHandActionRequest.STREAM_CODEC.encode(encoded, request);
        assertEquals(request, BodyHandActionRequest.STREAM_CODEC.decode(encoded));
        assertThrows(IllegalArgumentException.class, () -> new BodyHandActionRequest(
                BodyHandActionRequest.Action.MOVE_EQUIPMENT, "left", 1, 2, 3, null, "source-token",
                null, -1, -1, -1, -1, -1, -1, null, "pocket1", "id"));
        assertThrows(IllegalArgumentException.class, () -> new BodyHandActionRequest(
                BodyHandActionRequest.Action.MOVE_EQUIPMENT, "left", 1, 2, 3, null, "source-token",
                null, 0, 0, 1, 0, 0, 0, null, "pocket1", "id"));
    }

    @Test void gridBodyToEquipmentRoundTripsAndValidatesSlotAndUnusedHostToken() {
        var action = BodyHandActionRequest.Action.GRID_BODY_TO_EQUIPMENT;
        var request = new BodyHandActionRequest(action, "left", 1, 2, 3, null, "host-token",
                "child-token", 0, 0, 0, 0, 0, 0, null, "back", "head");
        var encoded = buffer();
        BodyHandActionRequest.STREAM_CODEC.encode(encoded, request);
        assertEquals(request, BodyHandActionRequest.STREAM_CODEC.decode(encoded));

        assertThrows(IllegalArgumentException.class, () -> new BodyHandActionRequest(action, "left", 1, 2, 3,
                null, "host-token", "child-token", 0, 0, 0, 0, 0, 0, null, "back", "not-a-slot"));
        assertThrows(IllegalArgumentException.class, () -> new BodyHandActionRequest(action, "left", 1, 2, 3,
                null, "host-token", "child-token", 0, 0, 0, 0, 0, 0, "occupied-token", "back", "head"));
    }
}
