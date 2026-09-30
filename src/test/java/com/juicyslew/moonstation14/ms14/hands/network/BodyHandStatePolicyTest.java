package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.ms14.hands.HandState;
import com.juicyslew.moonstation14.item.ModItems;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Pure packet shape and replay policy, not a connected-player acceptance fixture. */
class BodyHandStatePolicyTest {
    @Test void bootstrapAndReplayAreBoundedByRequestedEpoch() {
        var gate = new BodyHandStateService.QueryGate();
        assertNull(gate.admit(0, 1, 20));
        assertEquals(BodyHandStateSnapshot.Reason.REPLAY, gate.admit(0, 1, 20));
        assertNull(gate.admit(7, 1, 20));
        assertNull(gate.admit(0, 2, 20));
        assertEquals(BodyHandStateSnapshot.Reason.RATE_LIMITED, gate.admit(7, 2, 20));
        assertNull(gate.admit(7, 2, 21));
        assertEquals(BodyHandStateSnapshot.Reason.REPLAY, gate.admit(7, 1, 22));
    }

    @Test void rejectsMalformedAndUnboundedPackets() {
        assertThrows(IllegalArgumentException.class, () -> new BodyHandStateQuery(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> new BodyHandStateQuery(0, 0));
        assertThrows(IllegalArgumentException.class, () -> new BodyHandStateSnapshot.Hand("", null, null, 0));
        assertThrows(IllegalArgumentException.class, () -> new BodyHandStateSnapshot.Hand("a", "t", "minecraft:stone", 100));
        assertThrows(IllegalArgumentException.class, () -> new BodyHandStateSnapshot.Hand("a", null, "minecraft:stone", 1));
        var ids = new ArrayList<BodyHandStateSnapshot.Hand>();
        for (int i = 0; i <= HandState.MAX_HANDS; i++)
            ids.add(new BodyHandStateSnapshot.Hand("hand" + i, null, null, 0));
        assertThrows(IllegalArgumentException.class, () -> ok(ids, "hand0"));
        assertThrows(IllegalArgumentException.class, () -> ok(List.of(ids.getFirst(), ids.getFirst()), "hand0"));
        assertThrows(IllegalArgumentException.class, () -> ok(List.of(ids.getFirst()), "other"));
        assertThrows(IllegalArgumentException.class, () -> ok(List.of(
                new BodyHandStateSnapshot.Hand("left", "same", "minecraft:stone", 1),
                new BodyHandStateSnapshot.Hand("right", "same", "minecraft:stone", 1)), "left"));
    }

    @Test void retainsPrototypeOrderAndAccountBinding() {
        var snapshot = ok(List.of(new BodyHandStateSnapshot.Hand("right", null, null, 0),
                new BodyHandStateSnapshot.Hand("left", "token", "minecraft:stone", 3)), "right");
        assertEquals(List.of("right", "left"), snapshot.hands().stream().map(BodyHandStateSnapshot.Hand::id).toList());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.hands().clear());
        assertNotNull(snapshot.accountId());
        assertNotNull(snapshot.bodyId());
        var encoded = buffer();
        BodyHandStateSnapshot.STREAM_CODEC.encode(encoded, snapshot);
        assertEquals(snapshot, BodyHandStateSnapshot.STREAM_CODEC.decode(encoded));
        var query = new BodyHandStateQuery(0, 2);
        var requestBuffer = buffer();
        BodyHandStateQuery.STREAM_CODEC.encode(requestBuffer, query);
        assertEquals(query, BodyHandStateQuery.STREAM_CODEC.decode(requestBuffer));
    }

    @Test void codecRejectsOversizedAndMalformedFields() {
        var query = buffer();
        query.writeVarLong(-1);
        query.writeVarLong(1);
        assertThrows(IllegalArgumentException.class, () -> BodyHandStateQuery.STREAM_CODEC.decode(query));
        var oversized = header(HandState.MAX_HANDS + 1);
        assertThrows(IllegalArgumentException.class, () -> BodyHandStateSnapshot.STREAM_CODEC.decode(oversized));
        var duplicate = header(2);
        duplicate.writeUtf("same");
        duplicate.writeBoolean(false);
        duplicate.writeBoolean(false);
        duplicate.writeUtf("same");
        duplicate.writeBoolean(false);
        duplicate.writeBoolean(false);
        duplicate.writeVarInt(0);
        assertThrows(IllegalArgumentException.class, () -> BodyHandStateSnapshot.STREAM_CODEC.decode(duplicate));
        var invalidCount = header(1);
        invalidCount.writeUtf("right");
        invalidCount.writeBoolean(true);
        invalidCount.writeUtf("token");
        invalidCount.writeUtf("minecraft:stone");
        invalidCount.writeVarInt(0);
        invalidCount.writeBoolean(false);
        assertThrows(IllegalArgumentException.class, () -> BodyHandStateSnapshot.STREAM_CODEC.decode(invalidCount));
    }

    @Test void pouchSlotsDistinguishEmptyAndOccupiedAndRejectAliasedOrOversizedChildren() {
        String pouch = ModItems.POUCH.getId().toString();
        var empty = new BodyHandStateSnapshot.Hand("left", "pouch-token", pouch, 1, true, null, null, 0);
        var occupied = new BodyHandStateSnapshot.Hand("left", "pouch-token", pouch, 1,
                true, "child-token", "minecraft:stone", 64);
        for (var slot : List.of(empty, occupied)) {
            var snapshot = ok(List.of(slot, new BodyHandStateSnapshot.Hand("right", null, null, 0)), "left");
            var buf = buffer();
            BodyHandStateSnapshot.STREAM_CODEC.encode(buf, snapshot);
            assertEquals(snapshot, BodyHandStateSnapshot.STREAM_CODEC.decode(buf));
        }
        assertThrows(IllegalArgumentException.class, () -> new BodyHandStateSnapshot.Hand(
                "left", "p", "minecraft:stone", 1, true, null, null, 0));
        assertThrows(IllegalArgumentException.class, () -> new BodyHandStateSnapshot.Hand(
                "left", "p", pouch, 1));
        assertThrows(IllegalArgumentException.class, () -> new BodyHandStateSnapshot.Hand(
                "left", "p", pouch, 1, true, "p", "minecraft:stone", 1));
        assertThrows(IllegalArgumentException.class, () -> new BodyHandStateSnapshot.Hand(
                "left", "p", pouch, 1, true, "c", "minecraft:stone", 65));
        assertThrows(IllegalArgumentException.class, () -> ok(List.of(occupied,
                new BodyHandStateSnapshot.Hand("right", "child-token", "minecraft:stone", 1)), "left"));
        var forged = header(1);
        forged.writeUtf("right");
        forged.writeBoolean(true);
        forged.writeUtf("pouch-token");
        forged.writeUtf(pouch);
        forged.writeVarInt(1);
        forged.writeBoolean(true);
        forged.writeBoolean(true);
        forged.writeUtf("child-token");
        forged.writeUtf("minecraft:stone");
        forged.writeVarInt(65);
        assertThrows(IllegalArgumentException.class, () -> BodyHandStateSnapshot.STREAM_CODEC.decode(forged));
    }

    @Test void equipmentRoundTripAndRejectsSpoofedSlotsAndAliasedTokens() {
        var hand = new BodyHandStateSnapshot.Hand("right", "hand-token", "minecraft:stone", 1);
        var belt = new BodyHandStateSnapshot.EquipmentSlot("belt", "belt-token",
                ModItems.BELT.getId().toString(), 1, "child-token", "minecraft:dirt", 64);
        var back = new BodyHandStateSnapshot.EquipmentSlot("back", null, null, 0, null, null, 0);
        var snapshot = new BodyHandStateSnapshot(0, 1, BodyHandStateSnapshot.Reason.OK,
                UUID.randomUUID(), UUID.randomUUID(), 7, 2, "right", List.of(hand), List.of(belt, back));
        var buf = buffer();
        BodyHandStateSnapshot.STREAM_CODEC.encode(buf, snapshot);
        assertEquals(snapshot, BodyHandStateSnapshot.STREAM_CODEC.decode(buf));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.equipment().clear());
        assertThrows(IllegalArgumentException.class, () -> new BodyHandStateSnapshot.EquipmentSlot(
                "head", null, null, 0, null, null, 0));
        assertThrows(IllegalArgumentException.class, () -> new BodyHandStateSnapshot.EquipmentSlot(
                "back", "x", ModItems.BELT.getId().toString(), 1, null, null, 0));
        assertThrows(IllegalArgumentException.class, () -> new BodyHandStateSnapshot.EquipmentSlot(
                "belt", "x", ModItems.BELT.getId().toString(), 1, "y", "minecraft:stone", 65));
        for (var duplicate : List.of(new BodyHandStateSnapshot.EquipmentSlot("belt", "hand-token",
                ModItems.BELT.getId().toString(), 1, null, null, 0),
                new BodyHandStateSnapshot.EquipmentSlot("belt", "belt-token",
                        ModItems.BELT.getId().toString(), 1, "hand-token", "minecraft:dirt", 1)))
            assertThrows(IllegalArgumentException.class, () -> new BodyHandStateSnapshot(0, 1,
                    BodyHandStateSnapshot.Reason.OK, UUID.randomUUID(), UUID.randomUUID(), 7, 2,
                    "right", List.of(hand), List.of(duplicate)));
        assertThrows(IllegalArgumentException.class, () -> new BodyHandStateSnapshot(0, 1,
                BodyHandStateSnapshot.Reason.OK, UUID.randomUUID(), UUID.randomUUID(), 7, 2,
                "right", List.of(hand), List.of(back, back)));
        var absent = header(1);
        absent.writeUtf("right");
        absent.writeBoolean(false);
        absent.writeBoolean(false);
        assertThrows(RuntimeException.class, () -> BodyHandStateSnapshot.STREAM_CODEC.decode(absent));
        var rejected = BodyHandStateSnapshot.rejected(new BodyHandStateQuery(0, 3),
                BodyHandStateSnapshot.Reason.NOT_READY);
        var rejectionBuf = buffer();
        BodyHandStateSnapshot.STREAM_CODEC.encode(rejectionBuf, rejected);
        assertEquals(rejected, BodyHandStateSnapshot.STREAM_CODEC.decode(rejectionBuf));
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }

    private static RegistryFriendlyByteBuf header(int size) {
        var buf = buffer();
        buf.writeVarLong(0);
        buf.writeVarLong(1);
        buf.writeEnum(BodyHandStateSnapshot.Reason.OK);
        buf.writeUUID(UUID.randomUUID());
        buf.writeUUID(UUID.randomUUID());
        buf.writeVarLong(7);
        buf.writeVarLong(0);
        buf.writeUtf("right");
        buf.writeVarInt(size);
        return buf;
    }

    private static BodyHandStateSnapshot ok(List<BodyHandStateSnapshot.Hand> hands, String active) {
        return new BodyHandStateSnapshot(0, 1, BodyHandStateSnapshot.Reason.OK,
                UUID.randomUUID(), UUID.randomUUID(), 7, 0, active, hands);
    }
}
