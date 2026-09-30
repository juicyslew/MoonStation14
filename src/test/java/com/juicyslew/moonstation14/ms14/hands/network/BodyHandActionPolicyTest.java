package com.juicyslew.moonstation14.ms14.hands.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Synthetic input/pure sequence policy only; does not claim real ServerPlayer packet authorization. */
class BodyHandActionPolicyTest {
    @Test void rejectsMalformedIntent() {
        assertThrows(IllegalArgumentException.class, () -> new BodyHandActionRequest(
                BodyHandActionRequest.Action.PICKUP, "a", 0, 1, 0, UUID.randomUUID(), null));
        assertThrows(IllegalArgumentException.class, () -> new BodyHandActionRequest(
                BodyHandActionRequest.Action.DROP, "a", 1, 0, 0, null, "token"));
        assertThrows(IllegalArgumentException.class, () -> new BodyHandActionRequest(
                BodyHandActionRequest.Action.DROP, "a".repeat(65), 1, 1, 0, null, "token"));
        assertThrows(IllegalArgumentException.class, () -> new BodyHandActionRequest(
                BodyHandActionRequest.Action.DROP, "a", 1, 1, -1, null, "token"));
        assertThrows(IllegalArgumentException.class, () -> new BodyHandActionRequest(
                BodyHandActionRequest.Action.PICKUP, "a", 1, 1, 0, null, null));
        assertThrows(IllegalArgumentException.class, () -> new BodyHandActionRequest(
                BodyHandActionRequest.Action.DROP, "a", 1, 1, 0, UUID.randomUUID(), "token"));
    }

    @Test void rejectsReplayAndBoundsRatePerTick() {
        var gate = new BodyHandRequestService.SequenceGate();
        assertEquals(BodyHandActionResult.Reason.WRONG_EPOCH, gate.admit(7, 6, 1, 100));
        assertNull(gate.admit(7, 7, 1, 100));
        assertEquals(BodyHandActionResult.Reason.REPLAY, gate.admit(7, 7, 1, 100));
        for (int seq = 2; seq <= 6; seq++) assertNull(gate.admit(7, 7, seq, 100));
        assertEquals(BodyHandActionResult.Reason.RATE_LIMITED, gate.admit(7, 7, 7, 100));
        assertNull(gate.admit(7, 7, 7, 101));
        assertEquals(BodyHandActionResult.Reason.REPLAY, gate.admit(7, 7, 6, 102));
        assertNull(gate.admit(8, 8, 1, 102));
    }

    @Test void pouchActionsKeepTheDropShapeAndRejectClientSuppliedTargets() {
        for (var action : BodyHandActionRequest.Action.values()) {
            var request = new BodyHandActionRequest(action, "left", 7, 1, 9,
                    action == BodyHandActionRequest.Action.PICKUP ? UUID.randomUUID() : null,
                    action == BodyHandActionRequest.Action.PICKUP || action == BodyHandActionRequest.Action.SELECT_HAND
                            ? null : "pouch-token");
            var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            BodyHandActionRequest.STREAM_CODEC.encode(buf, request);
            assertEquals(request, BodyHandActionRequest.STREAM_CODEC.decode(buf));
        }
        for (var action : new BodyHandActionRequest.Action[]{BodyHandActionRequest.Action.INSERT_POUCH,
                BodyHandActionRequest.Action.EXTRACT_POUCH}) {
            assertThrows(IllegalArgumentException.class, () -> new BodyHandActionRequest(
                    action, "left", 7, 1, 9, UUID.randomUUID(), "token"));
            assertThrows(IllegalArgumentException.class, () -> new BodyHandActionRequest(
                    action, "left", 7, 1, 9, null, null));
            var malformed = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            malformed.writeEnum(action);
            malformed.writeUtf("left");
            malformed.writeVarLong(7);
            malformed.writeVarLong(1);
            malformed.writeVarLong(9);
            malformed.writeUtf("");
            assertThrows(IllegalArgumentException.class, () -> BodyHandActionRequest.STREAM_CODEC.decode(malformed));
        }
    }

    @Test void selectHandRequiresPayloadFreeIntentAndSequenceGate() {
        var select = new BodyHandActionRequest(BodyHandActionRequest.Action.SELECT_HAND,
                "right", 7, 11, 3, null, null);
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        BodyHandActionRequest.STREAM_CODEC.encode(buf, select);
        assertEquals(select, BodyHandActionRequest.STREAM_CODEC.decode(buf));
        assertFalse(buf.isReadable());
        assertThrows(IllegalArgumentException.class, () -> new BodyHandActionRequest(
                select.action(), "right", 7, 11, 3, UUID.randomUUID(), null));
        assertThrows(IllegalArgumentException.class, () -> new BodyHandActionRequest(
                select.action(), "right", 7, 11, 3, null, "token"));
        assertThrows(IllegalArgumentException.class, () -> new BodyHandActionRequest(
                select.action(), "", 7, 11, 3, null, null));
        var extra = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        BodyHandActionRequest.STREAM_CODEC.encode(extra, select);
        extra.writeByte(42);
        assertThrows(IllegalArgumentException.class, () -> BodyHandActionRequest.STREAM_CODEC.decode(extra));
        var gate = new BodyHandRequestService.SequenceGate();
        assertEquals(BodyHandActionResult.Reason.WRONG_EPOCH, gate.admit(7, 8, 11, 10));
        assertNull(gate.admit(7, 7, 11, 10));
        assertEquals(BodyHandActionResult.Reason.REPLAY, gate.admit(7, 7, 11, 10));
    }

    @Test void equipmentIntentsCarryOnlyHandAndSourceToken() {
        for (var action : new BodyHandActionRequest.Action[]{BodyHandActionRequest.Action.EQUIP_BELT,
                BodyHandActionRequest.Action.EQUIP_BACK, BodyHandActionRequest.Action.UNEQUIP_BELT,
                BodyHandActionRequest.Action.UNEQUIP_BACK, BodyHandActionRequest.Action.STORE_BELT,
                BodyHandActionRequest.Action.STORE_BACK, BodyHandActionRequest.Action.TAKE_BELT,
                BodyHandActionRequest.Action.TAKE_BACK}) {
            var request = new BodyHandActionRequest(action, "left", 7, 2, 9, null, "owner-token");
            var encoded = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            BodyHandActionRequest.STREAM_CODEC.encode(encoded, request);
            assertEquals(request, BodyHandActionRequest.STREAM_CODEC.decode(encoded));
            assertThrows(IllegalArgumentException.class, () -> new BodyHandActionRequest(
                    action, "left", 7, 2, 9, UUID.randomUUID(), "owner-token"));
            assertThrows(IllegalArgumentException.class, () -> new BodyHandActionRequest(
                    action, "left", 7, 2, 9, null, null));
            var extra = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            BodyHandActionRequest.STREAM_CODEC.encode(extra, request);
            extra.writeUtf("forged-slot");
            assertThrows(IllegalArgumentException.class, () -> BodyHandActionRequest.STREAM_CODEC.decode(extra));
        }
    }
}
