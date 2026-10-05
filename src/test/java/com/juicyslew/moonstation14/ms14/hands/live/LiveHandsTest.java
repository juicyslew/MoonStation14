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
import java.util.UUID;

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

    @Test
    void componentReplacementPreservesOwnedItemAndToken() {
        LiveHands empty = parse("{\"hands\":[{\"id\":\"left\"},{\"id\":\"right\"}],\"active\":\"right\",\"revision\":4}");
        LiveHands owned = empty.putWhole(4, "left", TOKEN, new ItemStack(Items.DIAMOND, 3)).state();
        ItemStack changed = new ItemStack(Items.DIAMOND, 3);
        changed.set(DataComponents.CUSTOM_NAME, Component.literal("liquid"));
        LiveHands updated = owned.replaceOwnedComponents(5, "left", TOKEN, changed).orElseThrow();
        assertEquals(6, updated.revision());
        assertEquals(Optional.of(TOKEN.value()), updated.token("left"));
        assertEquals("liquid", updated.stackCopy("left").orElseThrow().getHoverName().getString());
        assertTrue(owned.replaceOwnedComponents(5, "left", TOKEN, new ItemStack(Items.EMERALD, 3)).isEmpty());
        assertTrue(owned.replaceOwnedComponents(5, "left", TOKEN, new ItemStack(Items.DIAMOND, 2)).isEmpty());
    }

    @Test
    void consumeOneConsumesOnlyExactActiveHandItemAndKeepsRemainingOwnershipToken() {
        LiveHands empty = parse("{\"hands\":[{\"id\":\"left\"},{\"id\":\"right\"}],\"active\":\"left\",\"revision\":0}");
        ItemStack one = new ItemStack(Items.DIAMOND);
        LiveHands single = empty.putWhole(0, "left", TOKEN, one).state();
        LiveHands removed = single.consumeOne(1, "left", TOKEN.value(), one).orElseThrow();
        assertTrue(removed.stackCopy("left").isEmpty());
        assertEquals(2, removed.revision());
        assertEquals(1, single.stackCopy("left").orElseThrow().getCount());

        ItemStack three = new ItemStack(Items.DIAMOND, 3);
        LiveHands stacked = empty.putWhole(0, "left", TOKEN, three).state();
        LiveHands remainder = stacked.consumeOne(1, "left", TOKEN.value(), three).orElseThrow();
        assertEquals(2, remainder.stackCopy("left").orElseThrow().getCount());
        assertEquals(Optional.of(TOKEN.value()), remainder.token("left"));
        assertEquals(3, stacked.stackCopy("left").orElseThrow().getCount());
        assertEquals("left", remainder.activeHand());
    }

    @Test
    void consumeOneRejectsMismatchWrongHandOverflowAndOversizedExpectedStackWithoutMutation() {
        LiveHands empty = parse("{\"hands\":[{\"id\":\"left\"},{\"id\":\"right\"}],\"active\":\"left\",\"revision\":0}");
        ItemStack stack = new ItemStack(Items.DIAMOND, 3);
        LiveHands occupied = empty.putWhole(0, "left", TOKEN, stack).state();
        assertTrue(occupied.consumeOne(1, "right", TOKEN.value(), stack).isEmpty());
        assertTrue(occupied.consumeOne(1, "left", "wrong-token", stack).isEmpty());
        assertTrue(occupied.consumeOne(1, "left", TOKEN.value(), new ItemStack(Items.EMERALD, 3)).isEmpty());
        assertTrue(occupied.consumeOne(1, "left", TOKEN.value(), new ItemStack(Items.DIAMOND, 2)).isEmpty());
        assertTrue(occupied.consumeOne(Long.MAX_VALUE, "left", TOKEN.value(), stack).isEmpty());
        assertTrue(occupied.consumeOne(1, "left", TOKEN.value(), new ItemStack(Items.DIAMOND, 65)).isEmpty());
        assertEquals(3, occupied.stackCopy("left").orElseThrow().getCount());
        assertEquals(1, occupied.revision());

        LiveHands overflow = parse("{\"hands\":[{\"id\":\"left\"}],\"active\":\"left\",\"revision\":9223372036854775807}");
        assertTrue(overflow.consumeOne(Long.MAX_VALUE, "left", TOKEN.value(), stack).isEmpty());
    }

    @Test
    void activeUseMutationPreservesOwnerAndOtherHandsAndBoundsQuantityAndType() {
        LiveHands empty = parse("{\"hands\":[{\"id\":\"left\"},{\"id\":\"right\"}],\"active\":\"left\",\"revision\":0}");
        LiveHands original = empty.putWhole(0, "left", TOKEN, new ItemStack(Items.DIAMOND, 3)).state()
                .putWhole(1, "right", new ItemToken("other"), new ItemStack(Items.EMERALD)).state();
        ItemStack before = original.stackCopy("left").orElseThrow();
        ItemStack after = before.copy();
        after.set(DataComponents.CUSTOM_NAME, Component.literal("used"));
        LiveHands updated = original.replaceActiveUseStack(2, "left", TOKEN.value(), before, after).orElseThrow();
        assertEquals(3, updated.stackCopy("left").orElseThrow().getCount());
        assertEquals("used", updated.stackCopy("left").orElseThrow().getHoverName().getString());
        assertEquals(Optional.of(TOKEN.value()), updated.token("left"));
        assertTrue(ItemStack.isSameItemSameComponents(original.stackCopy("right").orElseThrow(),
                updated.stackCopy("right").orElseThrow()));
        assertEquals(original.token("right"), updated.token("right"));
        assertEquals("left", updated.activeHand());
        assertEquals(3, updated.revision());
        ItemStack smaller = before.copy(); smaller.setCount(2);
        assertTrue(original.replaceActiveUseStack(2, "left", TOKEN.value(), before, smaller).isPresent());
        assertTrue(original.replaceActiveUseStack(2, "left", TOKEN.value(), before, ItemStack.EMPTY).isPresent());
        assertTrue(original.replaceActiveUseStack(2, "left", TOKEN.value(), before,
                new ItemStack(Items.DIAMOND, 4)).isEmpty());
        assertTrue(original.replaceActiveUseStack(2, "left", TOKEN.value(), before,
                new ItemStack(Items.EMERALD)).isEmpty());
        assertTrue(empty.replaceActiveUseStack(0, "left", "new-token", ItemStack.EMPTY,
                new ItemStack(Items.DIAMOND)).isEmpty());
        assertTrue(original.replaceActiveUseStack(1, "left", TOKEN.value(), before, after).isEmpty());
        assertTrue(original.replaceActiveUseStack(2, "right", TOKEN.value(), before, after).isEmpty());
        assertTrue(original.replaceActiveUseStack(2, "left", "wrong", before, after).isEmpty());
        assertTrue(original.replaceActiveUseStack(2, "left", TOKEN.value(), new ItemStack(Items.EMERALD, 3), after).isEmpty());
    }

    @Test void switchingAllowsIndependentPickupSlotsWithoutMovingStacks() {
        LiveHands empty = parse("{\"hands\":[{\"id\":\"left\"},{\"id\":\"right\"}],\"active\":\"left\",\"revision\":0}");
        assertTrue(empty.stackCopy(empty.activeHand()).isEmpty());
        LiveHands first = empty.putWhole(0, "left", TOKEN, new ItemStack(Items.DIAMOND, 3)).state();
        assertEquals("left", first.activeHand());
        LiveHands switched = first.selectActive(1, "right").orElseThrow();
        assertEquals(2, switched.revision());
        assertEquals("right", switched.activeHand());
        assertTrue(switched.stackCopy(switched.activeHand()).isEmpty());
        assertEquals(Optional.of(TOKEN.value()), switched.token("left"));
        LiveHands both = switched.putWhole(2, "right", new ItemToken("second"),
                new ItemStack(Items.EMERALD, 2)).state();
        LiveHands back = both.selectActive(3, "left").orElseThrow();
        assertEquals(4, back.revision());
        assertEquals(Optional.of(TOKEN.value()), back.token("left"));
        assertEquals(Optional.of("second"), back.token("right"));
        assertEquals(3, back.stackCopy("left").orElseThrow().getCount());
        assertEquals(2, back.stackCopy("right").orElseThrow().getCount());
        assertTrue(first.selectActive(0, "right").isEmpty());
        assertTrue(first.selectActive(1, "left").isEmpty());
        assertTrue(first.selectActive(1, "missing").isEmpty());
        assertTrue(parse("{\"hands\":[{\"id\":\"only\"}],\"active\":\"only\",\"revision\":0}")
                .selectActive(0, "other").isEmpty());
        assertTrue(parse("{\"hands\":[{\"id\":\"left\"},{\"id\":\"right\"}],\"active\":\"left\",\"revision\":9223372036854775807}")
                .selectActive(Long.MAX_VALUE, "right").isEmpty());
    }

    @Test
    void reservationClaimsOnlyItsEmptyHandAndIsTransientAcrossCodec() {
        LiveHands empty = parse("{\"hands\":[{\"id\":\"left\"},{\"id\":\"right\"}],\"active\":\"left\",\"revision\":0}");
        UUID target = UUID.randomUUID();
        LiveHands reserved = empty.reserveHand(0, "left", target).orElseThrow();
        assertEquals(1, reserved.revision());
        assertEquals(Optional.of(target), reserved.reservation("left"));
        assertTrue(reserved.putWhole(1, "left", TOKEN, new ItemStack(Items.DIAMOND)).state() == reserved);
        assertTrue(reserved.takeWhole(1, "left", TOKEN).state() == reserved);
        assertTrue(reserved.transferWhole(1, "left", "right", TOKEN).state() == reserved);
        assertTrue(reserved.putWhole(1, "right", TOKEN, new ItemStack(Items.DIAMOND)).succeeded());
        LiveHands unrelated = reserved.selectActive(1, "right").orElseThrow();
        assertEquals(Optional.of(target), unrelated.reservation("left"));
        assertEquals(2, unrelated.revision());
        assertTrue(unrelated.releaseHand("left", UUID.randomUUID()).isEmpty());
        LiveHands released = unrelated.releaseHand("left", target).orElseThrow();
        assertTrue(released.reservation("left").isEmpty());
        assertEquals(3, released.revision());

        var encoded = LiveHands.CODEC.encodeStart(JsonOps.INSTANCE, reserved).getOrThrow();
        LiveHands restored = LiveHands.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertTrue(restored.reservation("left").isEmpty());
        assertEquals(reserved.revision(), restored.revision());
    }
}
