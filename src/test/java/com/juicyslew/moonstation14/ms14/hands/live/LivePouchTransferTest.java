package com.juicyslew.moonstation14.ms14.hands.live;

import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.storage.pouch.PouchContents;
import com.mojang.serialization.JsonOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LivePouchTransferTest {
    private static LiveHands empty(long revision) {
        return LiveHands.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"hands\":[{\"id\":\"left\"},{\"id\":\"right\"}],\"active\":\"right\",\"revision\":"
                        + revision + "}")).getOrThrow();
    }

    @Test
    void rejectsStaleEmptyWrongHandAndOverflowWithoutMutation() {
        LiveHands state = empty(7);
        assertTrue(state.insertPouch(7, "left", "p", "right", "c").isEmpty());
        assertTrue(state.extractPouch(7, "left", "p", "right", "c").isEmpty());
        assertTrue(state.insertPouch(6, "left", "p", "right", "c").isEmpty());
        assertTrue(state.extractPouch(7, "left", "p", "left", "c").isEmpty());
        assertTrue(state.insertPouch(7, "left", "p", "absent", "c").isEmpty());
        assertEquals(7, state.revision());
        assertTrue(state.stackCopy("left").isEmpty());
        assertTrue(empty(Long.MAX_VALUE).insertPouch(Long.MAX_VALUE, "left", "p", "right", "c").isEmpty());
    }

    @Test
    void rejectsDuplicateTokensAndOccupiedDestination() {
        LiveHands state = empty(0).putWhole(0, "left", new com.juicyslew.moonstation14.ms14.hands.ItemToken("p"),
                new ItemStack(Items.APPLE)).state();
        assertTrue(state.insertPouch(1, "left", "p", "right", "p").isEmpty());
        assertTrue(state.extractPouch(1, "left", "p", "right", "p").isEmpty());
        assertTrue(PouchContents.put(new ItemStack(Items.APPLE), "c", new ItemStack(Items.DIAMOND)).isEmpty());
    }
}
