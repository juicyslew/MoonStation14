package com.juicyslew.moonstation14.ms14.hands.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Synthetic input/pure sequence policy only; does not claim real ServerPlayer packet authorization. */
class BodyHandActionPolicyTest {
    @Test void directBodyHostActionsAppendLocationsWithoutChangingOldPackets() {
        for (var action : new BodyHandActionRequest.Action[]{BodyHandActionRequest.Action.GRID_BODY_WORN_TO_HELD,
                BodyHandActionRequest.Action.GRID_BODY_HELD_TO_WORN, BodyHandActionRequest.Action.GRID_BODY_HELD_TO_HELD}) {
            String from = action == BodyHandActionRequest.Action.GRID_BODY_WORN_TO_HELD ? "belt" : "left";
            String to = action == BodyHandActionRequest.Action.GRID_BODY_HELD_TO_WORN ? "back" : "right";
            var intent = new BodyHandActionRequest(action, "left", 7, 2, 9, null, "from-token", "child",
                    1, 0, 90, 0, 0, 0, "to-token", from, to);
            var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            BodyHandActionRequest.STREAM_CODEC.encode(buf, intent);
            assertEquals(intent, BodyHandActionRequest.STREAM_CODEC.decode(buf));
            assertFalse(buf.isReadable());
            assertThrows(IllegalArgumentException.class, () -> new BodyHandActionRequest(action, "left", 7, 2, 9,
                    null, "from-token", "child", 1, 0, 90, 0, 0, 0, "from-token", from, to));
            var trailing = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            BodyHandActionRequest.STREAM_CODEC.encode(trailing, intent);
            trailing.writeByte(1);
            assertThrows(IllegalArgumentException.class, () -> BodyHandActionRequest.STREAM_CODEC.decode(trailing));
        }
    }
    @Test void crossHostIntentAppendsOnlyDestinationTokenAndBoundsGeometry() {
        for (var action : new BodyHandActionRequest.Action[]{BodyHandActionRequest.Action.GRID_CROSS_BELT_TO_BACK,
                BodyHandActionRequest.Action.GRID_CROSS_BACK_TO_BELT}) {
            var request = new BodyHandActionRequest(action, "left", 7, 2, 9, null,
                    "source", "child", 1, 1, 90, 0, 0, 270, "destination");
            var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            BodyHandActionRequest.STREAM_CODEC.encode(buf, request);
            assertEquals(request, BodyHandActionRequest.STREAM_CODEC.decode(buf));
            assertFalse(buf.isReadable());
            assertThrows(IllegalArgumentException.class, () -> new BodyHandActionRequest(action, "left", 7, 2, 9,
                    null, "source", "child", 1, 1, 90, 0, 0, 270, "source"));
            assertThrows(IllegalArgumentException.class, () -> new BodyHandActionRequest(action, "left", 7, 2, 9,
                    null, "source", "child", 8, 0, 90, 0, 0, 270, "destination"));
            assertThrows(IllegalArgumentException.class, () -> new BodyHandActionRequest(action, "left", 7, 2, 9,
                    null, "source", "child", 1, 1, 90, 0, 0, 45, "destination"));
            var truncated = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            BodyHandActionRequest.STREAM_CODEC.encode(truncated, request);
            truncated.writerIndex(truncated.writerIndex() - 1);
            assertThrows(RuntimeException.class, () -> BodyHandActionRequest.STREAM_CODEC.decode(truncated));
        }
    }
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
            if (action == BodyHandActionRequest.Action.TRANSFER_HAND || action.name().startsWith("GRID_") || action.name().startsWith("HELD_GRID_")
                    || action.name().startsWith("SMART_") || action == BodyHandActionRequest.Action.PICKUP_SELECTED
                    || action == BodyHandActionRequest.Action.USE_TARGET || action == BodyHandActionRequest.Action.ALTERNATE_TARGET
                    || action == BodyHandActionRequest.Action.TOGGLE_PULL || action == BodyHandActionRequest.Action.STOP_PULL
                    || action == BodyHandActionRequest.Action.SIP_PUDDLE) continue;
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

    @Test void gridActionsRoundTripRotation270AndRejectMalformedGeometry() {
        for (var action : new BodyHandActionRequest.Action[]{BodyHandActionRequest.Action.GRID_STORE_BELT,
                BodyHandActionRequest.Action.GRID_STORE_BACK, BodyHandActionRequest.Action.GRID_TAKE_BELT,
                BodyHandActionRequest.Action.GRID_TAKE_BACK}) {
            boolean store = action.name().contains("STORE");
            var request = new BodyHandActionRequest(action, "left", 7, 1, 9, null, "host-token",
                    "child-token", 2, 1, store ? 270 : -1);
            var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            BodyHandActionRequest.STREAM_CODEC.encode(buf, request);
            assertEquals(request, BodyHandActionRequest.STREAM_CODEC.decode(buf));
            assertFalse(buf.isReadable());
        }
        assertThrows(IllegalArgumentException.class, () -> new BodyHandActionRequest(
                BodyHandActionRequest.Action.GRID_STORE_BELT, "left", 7, 1, 9, null, "host", "child", 0, 0, 45));
        assertThrows(IllegalArgumentException.class, () -> new BodyHandActionRequest(
                BodyHandActionRequest.Action.GRID_TAKE_BACK, "left", 7, 1, 9, null, "host", null, 0, 0, -1));
        var truncated = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        truncated.writeEnum(BodyHandActionRequest.Action.GRID_STORE_BELT);
        truncated.writeUtf("left"); truncated.writeVarLong(7); truncated.writeVarLong(1); truncated.writeVarLong(9);
        truncated.writeUtf("host"); truncated.writeUtf("child"); truncated.writeVarInt(0);
        assertThrows(RuntimeException.class, () -> BodyHandActionRequest.STREAM_CODEC.decode(truncated));
    }

    @Test void gridMoveCarriesBothPlacementsAndRotationAndRejectsTrailingData() {
        for (int rotation : new int[]{90, 270}) {
            var request = new BodyHandActionRequest(BodyHandActionRequest.Action.GRID_MOVE_BACK, "left", 7, 1, 9,
                    null, "host-token", "child-token", 2, 1, rotation, 0, 0, 270);
            var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            BodyHandActionRequest.STREAM_CODEC.encode(buf, request);
            assertEquals(request, BodyHandActionRequest.STREAM_CODEC.decode(buf));
            assertFalse(buf.isReadable());
        }
        assertThrows(IllegalArgumentException.class, () -> new BodyHandActionRequest(
                BodyHandActionRequest.Action.GRID_MOVE_BACK, "left", 7, 1, 9, null, "host", "child", 0, 0, 90, 8, 0, 0));
        var extra = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        var request = new BodyHandActionRequest(BodyHandActionRequest.Action.GRID_MOVE_BACK, "left", 7, 1, 9,
                null, "host", "child", 1, 1, 90, 0, 0, 270);
        BodyHandActionRequest.STREAM_CODEC.encode(extra, request); extra.writeByte(1);
        assertThrows(IllegalArgumentException.class, () -> BodyHandActionRequest.STREAM_CODEC.decode(extra));
        assertTrue(BodyHandRequestService.equipmentPrecheck(BodyHandActionRequest.Action.GRID_MOVE_BACK, "host", "occupied", "host", null));
        assertFalse(BodyHandRequestService.equipmentPrecheck(BodyHandActionRequest.Action.GRID_MOVE_BACK, "wrong", "occupied", "host", null));
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

    @Test void equipmentRoutePrecheckSeparatesLegacyAndGridTransfers() {
        assertTrue(BodyHandRequestService.equipmentPrecheck(BodyHandActionRequest.Action.GRID_STORE_BACK, "host", "unique-hand", "host", null));
        assertTrue(BodyHandRequestService.equipmentPrecheck(BodyHandActionRequest.Action.GRID_STORE_BACK, "host", "unique-hand", "host", "legacy-child"));
        assertTrue(BodyHandRequestService.equipmentPrecheck(BodyHandActionRequest.Action.GRID_TAKE_BACK, "host", null, "host", null));
        assertFalse(BodyHandRequestService.equipmentPrecheck(BodyHandActionRequest.Action.GRID_STORE_BACK, "stale-host", "unique-hand", "host", null));
        assertTrue(BodyHandRequestService.equipmentPrecheck(BodyHandActionRequest.Action.GRID_TAKE_BACK, "host", "occupied", "host", "legacy-child"));
        assertTrue(BodyHandRequestService.equipmentPrecheck(BodyHandActionRequest.Action.STORE_BACK, "host", "unique-hand", "host", null));
        assertFalse(BodyHandRequestService.equipmentPrecheck(BodyHandActionRequest.Action.STORE_BACK, "host", "unique-hand", "host", "child"));
        assertTrue(BodyHandRequestService.equipmentPrecheck(BodyHandActionRequest.Action.TAKE_BACK, "host", null, "host", "child"));
        assertFalse(BodyHandRequestService.equipmentPrecheck(BodyHandActionRequest.Action.TAKE_BACK, "host", null, "host", null));
    }

    @Test void heldGridActionsRoundTripAndRejectTrailingPayload() {
        for (var action : new BodyHandActionRequest.Action[]{BodyHandActionRequest.Action.HELD_GRID_STORE,
                BodyHandActionRequest.Action.HELD_GRID_TAKE, BodyHandActionRequest.Action.HELD_GRID_MOVE}) {
            boolean store = action == BodyHandActionRequest.Action.HELD_GRID_STORE;
            boolean move = action == BodyHandActionRequest.Action.HELD_GRID_MOVE;
            var request = new BodyHandActionRequest(action, "left", 7, 2, 9, null, "host-token", "child-token",
                    2, 1, store || move ? 270 : -1, move ? 0 : -1, move ? 0 : -1, move ? 90 : -1);
            var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            BodyHandActionRequest.STREAM_CODEC.encode(buf, request);
            assertEquals(request, BodyHandActionRequest.STREAM_CODEC.decode(buf));
            assertFalse(buf.isReadable());
            var extra = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            BodyHandActionRequest.STREAM_CODEC.encode(extra, request);
            extra.writeByte(1);
            assertThrows(IllegalArgumentException.class, () -> BodyHandActionRequest.STREAM_CODEC.decode(extra));
        }
    }

    @Test void smartEquipmentActionsRoundTripWithoutClientGeometry() {
        for (var action : new BodyHandActionRequest.Action[]{BodyHandActionRequest.Action.SMART_STORE_BELT,
                BodyHandActionRequest.Action.SMART_STORE_BACK, BodyHandActionRequest.Action.SMART_TAKE_BELT,
                BodyHandActionRequest.Action.SMART_TAKE_BACK}) {
            var request = new BodyHandActionRequest(action, "left", 7, 2, 9, null, "host-token",
                    "source-or-last-child", -1, -1, -1);
            var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            BodyHandActionRequest.STREAM_CODEC.encode(buf, request);
            assertEquals(request, BodyHandActionRequest.STREAM_CODEC.decode(buf));
            assertFalse(buf.isReadable());
            buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            BodyHandActionRequest.STREAM_CODEC.encode(buf, request);
            buf.writeByte(1);
            var trailing = buf;
            assertThrows(IllegalArgumentException.class, () -> BodyHandActionRequest.STREAM_CODEC.decode(trailing));
        }
    }
    @Test void grenadeIntentsCarryOnlyTheActiveHandToken() {
        for (var action : new BodyHandActionRequest.Action[]{BodyHandActionRequest.Action.USE_HELD,
                BodyHandActionRequest.Action.THROW_ITEM}) {
            var request = new BodyHandActionRequest(action, "left", 7, 2, 9, null, "grenade-token");
            var encoded = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            BodyHandActionRequest.STREAM_CODEC.encode(encoded, request);
            assertEquals(request, BodyHandActionRequest.STREAM_CODEC.decode(encoded));
            assertThrows(IllegalArgumentException.class, () -> new BodyHandActionRequest(
                    action, "left", 7, 2, 9, UUID.randomUUID(), "grenade-token"));
            assertThrows(IllegalArgumentException.class, () -> new BodyHandActionRequest(
                    action, "left", 7, 2, 9, null, null));
            var malformed = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            malformed.writeEnum(action);
            malformed.writeUtf("left");
            malformed.writeVarLong(7);
            malformed.writeVarLong(2);
            malformed.writeVarLong(9);
            malformed.writeUtf("");
            assertThrows(IllegalArgumentException.class, () -> BodyHandActionRequest.STREAM_CODEC.decode(malformed));
            var extra = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            BodyHandActionRequest.STREAM_CODEC.encode(extra, request);
            extra.writeByte(42);
            assertThrows(IllegalArgumentException.class, () -> BodyHandActionRequest.STREAM_CODEC.decode(extra));
        }
    }

    @Test void worldActionIntentsEncodeOnlyExplicitTargetsAndRequiredTokens() {
        for (var action : new BodyHandActionRequest.Action[]{BodyHandActionRequest.Action.PICKUP_SELECTED,
                BodyHandActionRequest.Action.USE_TARGET, BodyHandActionRequest.Action.ALTERNATE_TARGET,
                BodyHandActionRequest.Action.TOGGLE_PULL}) {
            String token = action == BodyHandActionRequest.Action.PICKUP_SELECTED ? null : "held-token";
            var request = new BodyHandActionRequest(action, "left", 7, 2, 9, UUID.randomUUID(), token);
            var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            BodyHandActionRequest.STREAM_CODEC.encode(buf, request);
            assertEquals(request, BodyHandActionRequest.STREAM_CODEC.decode(buf));
            assertFalse(buf.isReadable());
        }
        var stop = new BodyHandActionRequest(BodyHandActionRequest.Action.STOP_PULL, "left", 7, 2, 9, null, null);
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        BodyHandActionRequest.STREAM_CODEC.encode(buf, stop);
        assertEquals(stop, BodyHandActionRequest.STREAM_CODEC.decode(buf));
        for (var action : new BodyHandActionRequest.Action[]{BodyHandActionRequest.Action.ALTERNATE_TARGET,
                BodyHandActionRequest.Action.TOGGLE_PULL}) {
            var nullable = new BodyHandActionRequest(action, "left", 7, 3, 9, UUID.randomUUID(), null);
            var nullableBuf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            BodyHandActionRequest.STREAM_CODEC.encode(nullableBuf, nullable);
            assertEquals(nullable, BodyHandActionRequest.STREAM_CODEC.decode(nullableBuf));
        }
    }
}
