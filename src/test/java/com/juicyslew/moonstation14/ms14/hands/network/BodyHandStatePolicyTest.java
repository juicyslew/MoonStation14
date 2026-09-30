package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.ms14.hands.HandState;
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
        duplicate.writeUtf("same");
        duplicate.writeBoolean(false);
        assertThrows(IllegalArgumentException.class, () -> BodyHandStateSnapshot.STREAM_CODEC.decode(duplicate));
        var invalidCount = header(1);
        invalidCount.writeUtf("right");
        invalidCount.writeBoolean(true);
        invalidCount.writeUtf("token");
        invalidCount.writeUtf("minecraft:stone");
        invalidCount.writeVarInt(0);
        assertThrows(IllegalArgumentException.class, () -> BodyHandStateSnapshot.STREAM_CODEC.decode(invalidCount));
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
