package com.juicyslew.moonstation14.ms14.hands.live;

import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LiveHandsTest {
    private static final ItemToken TOKEN = new ItemToken("live-token");

    private static LiveHands parse(String json) {
        return LiveHands.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    @Test
    void emptyLayoutHasIndependentOrderedIdentityAndRevision() {
        LiveHands state = parse("{" +
                "\"hands\":[{\"id\":\"left\"},{\"id\":\"right\"}]," +
                "\"active\":\"right\",\"revision\":12}");
        assertEquals(java.util.List.of("left", "right"), state.handIds());
        assertEquals("right", state.activeHand());
        assertEquals(12, state.revision());
        assertTrue(state.stackCopy("left").isEmpty());
        assertTrue(state.move(12, "left", "right").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> state.stackCopy("missing"));
        assertEquals(state.handIds(), LiveHands.CODEC.parse(JsonOps.INSTANCE,
                LiveHands.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow()).getOrThrow().handIds());
    }

    @Test
    void invalidLayoutsFailAtCodecBoundary() {
        for (String hands : new String[]{"[]", "[{\"id\":\"left\"},{\"id\":\"left\"}]",
                "[{\"id\":\"\"}]", "[{\"id\":\"left\",\"occupant\":{\"token\":\"x\"}}]"}) {
            assertTrue(LiveHands.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                    "{\"hands\":" + hands + ",\"active\":\"left\",\"revision\":0}")).error().isPresent());
        }
        assertTrue(LiveHands.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"hands\":[{\"id\":\"left\"}],\"active\":\"other\",\"revision\":0}"))
                .error().isPresent());
        assertTrue(LiveHands.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"hands\":[{\"id\":\"left\"}],\"active\":\"left\",\"revision\":-1}"))
                .error().isPresent());
    }

    @Test
    void wholeTransitionsCopyBothWaysAndKeepActiveHandIndependent() {
        LiveHands empty = parse("{\"hands\":[{\"id\":\"left\"},{\"id\":\"right\"}],\"active\":\"right\",\"revision\":3}");
        ItemStack source = new ItemStack(Items.DIAMOND, 7);
        source.set(DataComponents.CUSTOM_NAME, Component.literal("original"));
        LiveHands.WholeResult placed = empty.putWhole(3, "left", TOKEN, source);
        assertTrue(placed.succeeded());
        LiveHands occupied = placed.state();
        source.setCount(1);
        source.set(DataComponents.CUSTOM_NAME, Component.literal("changed"));
        assertEquals(7, occupied.stackCopy("left").orElseThrow().getCount());
        assertEquals("original", occupied.stackCopy("left").orElseThrow().getHoverName().getString());
        assertEquals(Optional.of(TOKEN.value()), occupied.token("left"));
        assertEquals(4, occupied.revision());
        assertEquals("right", occupied.activeHand());
        assertEquals("right", empty.activeHand());
        assertTrue(empty.stackCopy("left").isEmpty());

        LiveHands.WholeResult taken = occupied.takeWhole(4, "left", TOKEN);
        assertTrue(taken.succeeded());
        ItemStack firstRead = taken.takenStack().orElseThrow();
        assertEquals(7, firstRead.getCount());
        assertEquals("original", firstRead.getHoverName().getString());
        firstRead.setCount(2);
        firstRead.set(DataComponents.CUSTOM_NAME, Component.literal("mutated return"));
        assertEquals(7, taken.takenStack().orElseThrow().getCount());
        assertEquals("original", taken.takenStack().orElseThrow().getHoverName().getString());
        assertTrue(taken.state().stackCopy("left").isEmpty());
        assertEquals(5, taken.state().revision());
        assertEquals("right", taken.state().activeHand());
        assertEquals(7, occupied.stackCopy("left").orElseThrow().getCount());
    }

    @Test
    void wholeTransitionsRejectWithoutChangingSnapshots() {
        LiveHands empty = parse("{\"hands\":[{\"id\":\"left\"},{\"id\":\"right\"}],\"active\":\"left\",\"revision\":0}");
        ItemStack source = new ItemStack(Items.DIAMOND, 4);
        assertRejected(empty.putWhole(1, "left", TOKEN, source), empty, LiveHands.Rejection.STALE_REVISION);
        assertRejected(empty.putWhole(0, "missing", TOKEN, source), empty, LiveHands.Rejection.UNKNOWN_HAND);
        assertRejected(empty.takeWhole(0, "left", TOKEN), empty, LiveHands.Rejection.HAND_EMPTY);
        assertRejected(empty.putWhole(0, "left", TOKEN, ItemStack.EMPTY), empty, LiveHands.Rejection.INVALID_STACK);
        assertRejected(empty.putWhole(0, "left", TOKEN, new ItemStack(Items.DIAMOND, 65)),
                empty, LiveHands.Rejection.INVALID_STACK);
        LiveHands occupied = empty.putWhole(0, "left", TOKEN, source).state();
        assertRejected(occupied.putWhole(1, "left", new ItemToken("other"), source),
                occupied, LiveHands.Rejection.HAND_OCCUPIED);
        assertRejected(occupied.putWhole(1, "right", TOKEN, source), occupied, LiveHands.Rejection.DUPLICATE_TOKEN);
        assertRejected(occupied.takeWhole(0, "left", TOKEN), occupied, LiveHands.Rejection.STALE_REVISION);
        assertRejected(occupied.takeWhole(1, "missing", TOKEN), occupied, LiveHands.Rejection.UNKNOWN_HAND);
        assertRejected(occupied.takeWhole(1, "left", new ItemToken("other")),
                occupied, LiveHands.Rejection.TOKEN_MISMATCH);
        assertRejected(occupied.takeWhole(1, "right", TOKEN), occupied, LiveHands.Rejection.HAND_EMPTY);
        assertEquals(4, occupied.stackCopy("left").orElseThrow().getCount());
        assertEquals(1, occupied.revision());
        assertEquals(4, source.getCount());

        LiveHands max = parse("{\"hands\":[{\"id\":\"left\"},{\"id\":\"right\"}],\"active\":\"left\",\"revision\":9223372036854775807}");
        assertRejected(max.putWhole(Long.MAX_VALUE, "left", TOKEN, source), max,
                LiveHands.Rejection.REVISION_OVERFLOW);
        assertRejected(max.takeWhole(Long.MAX_VALUE, "left", TOKEN), max,
                LiveHands.Rejection.REVISION_OVERFLOW);
    }

    private static void assertRejected(LiveHands.WholeResult result, LiveHands state, LiveHands.Rejection reason) {
        assertFalse(result.succeeded());
        assertSame(state, result.state());
        assertEquals(Optional.of(reason), result.rejection());
        assertTrue(result.takenStack().isEmpty());
    }
}
