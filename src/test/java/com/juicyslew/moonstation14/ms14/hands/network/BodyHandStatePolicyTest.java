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
        invalidCount.writeBoolean(false);
        assertThrows(IllegalArgumentException.class, () -> BodyHandStateSnapshot.STREAM_CODEC.decode(invalidCount));
    }

    @Test void snapshotRejectsPouchItemAndForgedLegacyBoolean() {
        assertThrows(IllegalArgumentException.class, () -> new BodyHandStateSnapshot.Hand(
                "left", "p", "moonstation14:pouch", 1));
        assertThrows(IllegalArgumentException.class, () -> new BodyHandStateSnapshot.Hand(
                "left", "bag-token", ModItems.BAG.getId().toString(), 1,
                false, "child", "moonstation14:pouch", 1));
        var forged = header(1);
        forged.writeUtf("right");
        forged.writeBoolean(true);
        forged.writeUtf("token");
        forged.writeUtf("minecraft:stone");
        forged.writeVarInt(1);
        forged.writeBoolean(true);
        forged.writeBoolean(false);
        forged.writeVarInt(0);
        forged.writeVarInt(0);
        assertThrows(IllegalArgumentException.class, () -> BodyHandStateSnapshot.STREAM_CODEC.decode(forged));
    }

    @Test void legacyBagAndBeltChildrenRoundTripWithoutBecomingPouches() {
        for (String host : List.of(ModItems.BAG.getId().toString(), ModItems.BELT.getId().toString())) {
            var held = new BodyHandStateSnapshot.Hand("right", "host-token", host, 1,
                    false, "child-token", "minecraft:diamond_pickaxe", 1);
            var snapshot = ok(List.of(held, new BodyHandStateSnapshot.Hand("left", null, null, 0)), "right");
            var buf = buffer();
            BodyHandStateSnapshot.STREAM_CODEC.encode(buf, snapshot);
            var decoded = BodyHandStateSnapshot.STREAM_CODEC.decode(buf);
            assertEquals(snapshot, decoded);
            assertFalse(decoded.hands().getFirst().pouch());
        }
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

    @Test void explicitGridHostRoundTripsRotationAndRequiresMatchingOwner() {
        var bag = new BodyHandStateSnapshot.Hand("right", "bag-token", ModItems.BAG.getId().toString(), 1);
        var grid = new BodyHandStateSnapshot.GridHost("bag-token", "bag", 7, 4,
                List.of(new BodyHandStateSnapshot.GridEntry("child", "minecraft:stone", 2, 0, 0, 270)));
        var snapshot = new BodyHandStateSnapshot(0, 1, BodyHandStateSnapshot.Reason.OK,
                UUID.randomUUID(), UUID.randomUUID(), 7, 2, "right", List.of(bag), List.of(), List.of(grid));
        var buf = buffer();
        BodyHandStateSnapshot.STREAM_CODEC.encode(buf, snapshot);
        assertEquals(snapshot, BodyHandStateSnapshot.STREAM_CODEC.decode(buf));
        assertThrows(IllegalArgumentException.class, () -> new BodyHandStateSnapshot(0, 1,
                BodyHandStateSnapshot.Reason.OK, UUID.randomUUID(), UUID.randomUUID(), 7, 2, "right",
                List.of(new BodyHandStateSnapshot.Hand("right", "other", "minecraft:stone", 1)), List.of(), List.of(grid)));
        var collidingGrid = new BodyHandStateSnapshot.GridHost("bag-token", "bag", 7, 4,
                List.of(new BodyHandStateSnapshot.GridEntry("child", "minecraft:stone", 1, 0, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> new BodyHandStateSnapshot(0, 1,
                BodyHandStateSnapshot.Reason.OK, UUID.randomUUID(), UUID.randomUUID(), 7, 2, "right",
                List.of(bag, new BodyHandStateSnapshot.Hand("left", "child", "minecraft:stone", 1)), List.of(), List.of(collidingGrid)));
        assertThrows(IllegalArgumentException.class, () -> new BodyHandStateSnapshot.GridEntry(
                "bad", "minecraft:stone", -1, 0, 0, 0));
    }

    @Test void gridValidationChecksFootprintsOwnershipAndTrailingData() {
        assertThrows(IllegalArgumentException.class, () -> new BodyHandStateSnapshot.GridHost("h", "bag", 7, 4,
                List.of(new BodyHandStateSnapshot.GridEntry("pick", "moonstation14:pickaxe", 1, 5, 0, 0))));
        assertThrows(IllegalArgumentException.class, () -> new BodyHandStateSnapshot.GridHost("h", "bag", 7, 4,
                List.of(new BodyHandStateSnapshot.GridEntry("pick", "moonstation14:pickaxe", 1, 0, 0, 0),
                        new BodyHandStateSnapshot.GridEntry("other", "minecraft:stone", 1, 1, 1, 0))));
        assertThrows(IllegalArgumentException.class, () -> new BodyHandStateSnapshot.GridHost("h", "bag", 7, 4,
                java.util.Collections.nCopies(33, new BodyHandStateSnapshot.GridEntry("x", "minecraft:stone", 1, 0, 0, 0))));
        var belt = new BodyHandStateSnapshot.EquipmentSlot("belt", "belt-token", ModItems.BELT.getId().toString(), 1, null, null, 0);
        var wrong = new BodyHandStateSnapshot.GridHost("belt-token", "bag", 7, 4, List.of());
        assertThrows(IllegalArgumentException.class, () -> new BodyHandStateSnapshot(0, 1, BodyHandStateSnapshot.Reason.OK,
                UUID.randomUUID(), UUID.randomUUID(), 7, 0, "right", List.of(new BodyHandStateSnapshot.Hand("right", null, null, 0)), List.of(belt), List.of(wrong)));
        var trailing = buffer();
        BodyHandStateSnapshot.STREAM_CODEC.encode(trailing, BodyHandStateSnapshot.rejected(new BodyHandStateQuery(0, 4), BodyHandStateSnapshot.Reason.REPLAY));
        trailing.writeByte(0);
        assertThrows(IllegalArgumentException.class, () -> BodyHandStateSnapshot.STREAM_CODEC.decode(trailing));
    }

    @Test void rejectsGridChildAliasingAnyHandOrEquipmentChild() {
        var bag = new BodyHandStateSnapshot.Hand("right", "bag", ModItems.BAG.getId().toString(), 1);
        var hand = new BodyHandStateSnapshot.Hand("left", "hand", "minecraft:stone", 1);
        var equipment = new BodyHandStateSnapshot.EquipmentSlot("belt", "belt", ModItems.BELT.getId().toString(),
                1, "worn-child", "minecraft:diamond", 1);
        for (String collision : List.of("hand", "worn-child")) {
            var host = new BodyHandStateSnapshot.GridHost("bag", "bag", 7, 4,
                    List.of(new BodyHandStateSnapshot.GridEntry(collision, "minecraft:diamond_pickaxe", 1, 0, 0, 0)));
            assertThrows(IllegalArgumentException.class, () -> new BodyHandStateSnapshot(0, 1,
                    BodyHandStateSnapshot.Reason.OK, UUID.randomUUID(), UUID.randomUUID(), 7, 0,
                    "right", List.of(bag, hand), List.of(equipment), List.of(host)));
        }
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
