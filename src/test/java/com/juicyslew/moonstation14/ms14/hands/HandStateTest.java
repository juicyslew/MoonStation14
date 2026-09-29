package com.juicyslew.moonstation14.ms14.hands;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class HandStateTest {
    private static final List<String> TWO_HANDS = List.of("left", "right");

    @Test
    void createsValidOrderedTwoHandStateWithFirstHandActive() {
        HandState state = HandState.create(TWO_HANDS);

        assertEquals(TWO_HANDS, state.handIds());
        assertEquals("left", state.activeHand());
        assertEquals(Optional.empty(), state.occupant("left"));
        assertEquals(Optional.empty(), state.occupant("right"));
    }

    @Test
    void selectsActiveHandWithoutChangingPriorSnapshot() {
        HandState original = HandState.create(TWO_HANDS);

        HandOperation result = original.selectActive("right");

        assertTrue(result.succeeded());
        assertEquals("right", result.state().activeHand());
        assertEquals("left", original.activeHand());
    }

    @Test
    void rejectsSelectingAlreadyActiveHandWithoutChangingState() {
        HandState state = HandState.create(TWO_HANDS).place("left", new ItemToken("item-1")).state();

        HandOperation result = state.selectActive("left");

        assertEquals(HandOperation.Rejection.ALREADY_ACTIVE, result.rejection());
        assertSame(state, result.state());
        assertEquals("left", state.activeHand());
        assertEquals(Optional.of(new ItemToken("item-1")), state.occupant("left"));
        assertTrue(state.occupant("right").isEmpty());
    }

    @Test
    void rejectsMissingDuplicateEmptyAndUnboundedHandDefinitions() {
        assertThrows(IllegalArgumentException.class, () -> HandState.create(null));
        assertThrows(IllegalArgumentException.class, () -> HandState.create(List.of()));
        assertThrows(IllegalArgumentException.class, () -> HandState.create(java.util.Arrays.asList("left", null)));
        assertThrows(IllegalArgumentException.class, () -> HandState.create(List.of("left", " ")));
        assertThrows(IllegalArgumentException.class, () -> HandState.create(List.of("left", "left")));
        assertThrows(IllegalArgumentException.class, () -> HandState.create(
                java.util.stream.IntStream.range(0, HandState.MAX_HANDS + 1).mapToObj(i -> "hand-" + i).toList()));
        assertThrows(IllegalArgumentException.class, () -> HandState.create(TWO_HANDS, "unknown"));
        assertThrows(IllegalArgumentException.class,
                () -> HandState.create(List.of("h".repeat(HandState.MAX_ID_LENGTH + 1))));

        HandState state = HandState.create(TWO_HANDS);
        HandOperation missing = state.selectActive("missing");
        assertEquals(HandOperation.Rejection.UNKNOWN_HAND, missing.rejection());
        assertSame(state, missing.state());
    }

    @Test
    void rejectsDuplicateItemTokenAcrossHands() {
        ItemToken token = new ItemToken("item-1");
        HandState state = HandState.create(TWO_HANDS).place("left", token).state();

        HandOperation result = state.place("right", token);

        assertEquals(HandOperation.Rejection.DUPLICATE_ITEM, result.rejection());
        assertSame(state, result.state());
        assertEquals(Optional.of(token), state.occupant("left"));
        assertTrue(state.occupant("right").isEmpty());
    }

    @Test
    void rejectsPlacementIntoOccupiedHandWithoutMutation() {
        HandState state = HandState.create(TWO_HANDS).place("left", new ItemToken("item-1")).state();

        HandOperation result = state.place("left", new ItemToken("item-2"));

        assertEquals(HandOperation.Rejection.HAND_NOT_EMPTY, result.rejection());
        assertSame(state, result.state());
        assertEquals(Optional.of(new ItemToken("item-1")), state.occupant("left"));
    }

    @Test
    void rejectsUnknownHandsForPlaceRemoveMoveAndSwapWithoutMutation() {
        ItemToken left = new ItemToken("item-1");
        ItemToken right = new ItemToken("item-2");
        HandState state = HandState.create(TWO_HANDS)
                .place("left", left).state()
                .place("right", right).state();

        HandOperation unknownPlace = state.place("missing", new ItemToken("item-3"));
        assertEquals(HandOperation.Rejection.UNKNOWN_HAND, unknownPlace.rejection());
        assertSame(state, unknownPlace.state());

        HandOperation unknownRemove = state.remove("missing");
        assertEquals(HandOperation.Rejection.UNKNOWN_HAND, unknownRemove.rejection());
        assertSame(state, unknownRemove.state());

        HandOperation unknownMoveSource = state.move("missing", "left");
        assertEquals(HandOperation.Rejection.UNKNOWN_HAND, unknownMoveSource.rejection());
        assertSame(state, unknownMoveSource.state());

        HandOperation unknownMoveTarget = state.move("left", "missing");
        assertEquals(HandOperation.Rejection.UNKNOWN_HAND, unknownMoveTarget.rejection());
        assertSame(state, unknownMoveTarget.state());

        HandOperation unknownSwapFirst = state.swap("missing", "right");
        assertEquals(HandOperation.Rejection.UNKNOWN_HAND, unknownSwapFirst.rejection());
        assertSame(state, unknownSwapFirst.state());

        HandOperation unknownSwapSecond = state.swap("left", "missing");
        assertEquals(HandOperation.Rejection.UNKNOWN_HAND, unknownSwapSecond.rejection());
        assertSame(state, unknownSwapSecond.state());

        assertEquals(Optional.of(left), state.occupant("left"));
        assertEquals(Optional.of(right), state.occupant("right"));
    }

    @Test
    void rejectsInvalidItemTokensWithoutChangingState() {
        HandState state = HandState.create(TWO_HANDS);

        assertThrows(IllegalArgumentException.class, () -> new ItemToken(null));
        assertThrows(IllegalArgumentException.class, () -> new ItemToken(" "));
        assertThrows(IllegalArgumentException.class, () -> new ItemToken("t".repeat(ItemToken.MAX_LENGTH + 1)));
        assertTrue(state.occupant("left").isEmpty());
        assertTrue(state.occupant("right").isEmpty());
    }

    @Test
    void acceptsDomainValuesAtPersistenceLengthLimitsAndPreservesTwoHandState() {
        String maximumHandId = "h".repeat(HandState.MAX_ID_LENGTH);
        String maximumToken = "t".repeat(ItemToken.MAX_LENGTH);
        HandState bounded = HandState.create(List.of(maximumHandId))
                .place(maximumHandId, new ItemToken(maximumToken)).state();

        assertEquals(HandComponent.MAX_ID_LENGTH, HandState.MAX_ID_LENGTH);
        assertEquals(HandComponent.MAX_TOKEN_LENGTH, ItemToken.MAX_LENGTH);
        assertEquals(List.of(maximumHandId), bounded.handIds());
        assertEquals(Optional.of(new ItemToken(maximumToken)), bounded.occupant(maximumHandId));
        assertEquals(TWO_HANDS, HandState.create(TWO_HANDS).handIds());
    }

    @Test
    void removesOccupantAndReturnsItsToken() {
        ItemToken token = new ItemToken("item-1");
        HandState state = HandState.create(TWO_HANDS).place("left", token).state();

        HandOperation result = state.remove("left");

        assertTrue(result.succeeded());
        assertEquals(Optional.of(token), result.removedItem());
        assertTrue(result.state().occupant("left").isEmpty());
        assertEquals(Optional.of(token), state.occupant("left"));
        assertEquals(HandOperation.Rejection.HAND_EMPTY, result.state().remove("left").rejection());
    }

    @Test
    void rejectsSameHandMoveAndOccupiedDestinationAtomically() {
        HandState state = HandState.create(TWO_HANDS).place("left", new ItemToken("item-1")).state();

        HandOperation sameHand = state.move("left", "left");
        assertEquals(HandOperation.Rejection.SAME_HAND, sameHand.rejection());
        assertSame(state, sameHand.state());

        HandState bothOccupied = state.place("right", new ItemToken("item-2")).state();
        HandOperation occupiedTarget = bothOccupied.move("left", "right");
        assertEquals(HandOperation.Rejection.TARGET_NOT_EMPTY, occupiedTarget.rejection());
        assertSame(bothOccupied, occupiedTarget.state());
    }

    @Test
    void rejectsSwappingEmptyOrPartiallyOccupiedHandsWithoutMutation() {
        HandState empty = HandState.create(TWO_HANDS);
        HandOperation bothEmpty = empty.swap("left", "right");
        assertEquals(HandOperation.Rejection.BOTH_HANDS_EMPTY, bothEmpty.rejection());
        assertSame(empty, bothEmpty.state());
        assertTrue(empty.occupant("left").isEmpty());
        assertTrue(empty.occupant("right").isEmpty());

        ItemToken token = new ItemToken("item-1");
        HandState leftOccupied = empty.place("left", token).state();
        HandOperation leftOnly = leftOccupied.swap("left", "right");
        assertEquals(HandOperation.Rejection.TARGET_NOT_EMPTY, leftOnly.rejection());
        assertSame(leftOccupied, leftOnly.state());
        assertEquals(Optional.of(token), leftOccupied.occupant("left"));
        assertTrue(leftOccupied.occupant("right").isEmpty());

        HandState rightOccupied = empty.place("right", token).state();
        HandOperation rightOnly = rightOccupied.swap("left", "right");
        assertEquals(HandOperation.Rejection.TARGET_NOT_EMPTY, rightOnly.rejection());
        assertSame(rightOccupied, rightOnly.state());
        assertTrue(rightOccupied.occupant("left").isEmpty());
        assertEquals(Optional.of(token), rightOccupied.occupant("right"));
    }

    @Test
    void handToHandMoveAndSwapAreAtomicAndKeepConfiguredOrder() {
        ItemToken first = new ItemToken("item-1");
        ItemToken second = new ItemToken("item-2");
        HandState state = HandState.create(TWO_HANDS).place("left", first).state();

        HandState moved = state.move("left", "right").state();
        assertTrue(moved.occupant("left").isEmpty());
        assertEquals(Optional.of(first), moved.occupant("right"));
        assertEquals(TWO_HANDS, moved.handIds());

        HandState bothOccupied = moved.place("left", second).state();
        HandState swapped = bothOccupied.swap("left", "right").state();
        assertEquals(Optional.of(first), swapped.occupant("left"));
        assertEquals(Optional.of(second), swapped.occupant("right"));
        assertEquals(TWO_HANDS, swapped.handIds());
    }

    @Test
    void snapshotIsImmutableAndDoesNotExposeMutableBackingState() {
        HandState state = HandState.create(TWO_HANDS);
        List<String> source = new ArrayList<>(TWO_HANDS);
        HandState fromSource = HandState.create(source);
        source.clear();

        assertThrows(UnsupportedOperationException.class,
                () -> state.snapshot().put("third", Optional.empty()));
        assertThrows(UnsupportedOperationException.class, () -> state.handIds().add("third"));
        assertEquals(TWO_HANDS, state.handIds());
        assertEquals(TWO_HANDS, fromSource.handIds());
    }
}
