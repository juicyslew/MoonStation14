package com.juicyslew.moonstation14.ms14.hands.transfer;

import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ItemLocationLedgerTest {
    private static ItemToken token(String name) { return new ItemToken(name); }
    private static final UUID BODY = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static ItemLocation.BodyHand hand(String id) { return new ItemLocation.BodyHand(BODY, id); }
    private static ItemLocation.WorldEntity world(String id) {
        return new ItemLocation.WorldEntity(WORLD, UUID.nameUUIDFromBytes(id.getBytes()));
    }
    private static ItemLocation.StorageCell cell(ItemToken owner, String id) {
        return new ItemLocation.StorageCell(owner, "main", ItemLocation.Orientation.UNSPECIFIED, id);
    }

    @Test void movesHandToWorldAndEquippedAndContainerWithExactRevision() {
        ItemLocationLedger ledger = new ItemLocationLedger();
        ItemToken item = token("item");
        assertTrue(ledger.register(item, hand("left")).accepted());
        ItemLocationSnapshot first = ledger.snapshot();
        var dropped = ledger.compareAndMove(first.revision(), item, hand("left"), world("entity"));
        assertTrue(dropped.accepted());
        var equipped = ledger.compareAndMove(dropped.snapshot().revision(), item, world("entity"),
                new ItemLocation.BodyEquipment(BODY, "back"));
        assertTrue(equipped.accepted());

        ItemToken container = token("bag");
        assertTrue(ledger.register(container, hand("right")).accepted());
        var stored = ledger.compareAndMove(ledger.snapshot().revision(), item,
                new ItemLocation.BodyEquipment(BODY, "back"), cell(container, "cell-1"));
        assertTrue(stored.accepted());
        assertEquals(cell(container, "cell-1"), stored.snapshot().locationOf(item).orElseThrow());
    }

    @Test void rejectsDuplicateStaleMissingAndInvalidMovesWithoutMutation() {
        ItemLocationLedger ledger = new ItemLocationLedger();
        ItemToken item = token("one");
        assertTrue(ledger.register(item, hand("left")).accepted());
        ItemLocationSnapshot before = ledger.snapshot();
        assertEquals(ItemLocationLedger.Rejection.DUPLICATE_ITEM,
                ledger.register(item, world("duplicate")).rejection());
        assertEquals(ItemLocationLedger.Rejection.STALE_REVISION,
                ledger.compareAndMove(0, item, hand("left"), world("stale")).rejection());
        assertEquals(ItemLocationLedger.Rejection.MISSING_ITEM,
                ledger.compareAndMove(before.revision(), token("missing"), hand("left"), world("missing")).rejection());
        assertEquals(ItemLocationLedger.Rejection.SOURCE_MISMATCH,
                ledger.compareAndMove(before.revision(), item, hand("wrong"), world("wrong")).rejection());
        assertEquals(ItemLocationLedger.Rejection.SAME_LOCATION,
                ledger.compareAndMove(before.revision(), item, hand("left"), hand("left")).rejection());
        assertSame(before, ledger.snapshot());
    }

    @Test void rejectsSelfContainmentAndContainerInContainer() {
        ItemLocationLedger ledger = new ItemLocationLedger();
        ItemToken bag = token("bag");
        ItemToken nested = token("nested");
        assertTrue(ledger.register(bag, hand("left")).accepted());
        assertEquals(ItemLocationLedger.Rejection.NESTED_STORAGE,
                ledger.compareAndMove(ledger.snapshot().revision(), bag, hand("left"), cell(bag, "self")).rejection());
        assertTrue(ledger.register(nested, cell(bag, "inner")).accepted());
        ItemToken other = token("other");
        assertEquals(ItemLocationLedger.Rejection.NESTED_STORAGE,
                ledger.register(other, cell(nested, "deeper")).rejection());
        assertEquals(ItemLocationLedger.Rejection.NESTED_STORAGE,
                ledger.compareAndMove(ledger.snapshot().revision(), nested, cell(bag, "inner"), cell(bag, "other")).rejection());
    }

    @Test void rejectsOccupiedHandsEquipmentAndExactStorageCellsWithoutMutation() {
        ItemLocationLedger ledger = new ItemLocationLedger();
        ItemToken owner = token("owner");
        ItemToken contender = token("contender");
        ItemToken bag = token("bag");
        ItemToken stored = token("stored");
        assertTrue(ledger.register(owner, hand("left")).accepted());
        assertTrue(ledger.register(bag, hand("right")).accepted());
        assertTrue(ledger.register(stored, cell(bag, "one")).accepted());

        ItemLocationSnapshot before = ledger.snapshot();
        assertOccupiedWithoutMutation(ledger, before,
                () -> ledger.register(contender, hand("left")));
        assertTrue(ledger.register(contender, world("contender")).accepted());
        ItemLocationSnapshot moveBefore = ledger.snapshot();
        assertOccupiedWithoutMutation(ledger, moveBefore,
                () -> ledger.compareAndMove(moveBefore.revision(), contender, world("contender"), hand("left")));

        ItemLocation.BodyEquipment slot = new ItemLocation.BodyEquipment(BODY, "back");
        assertTrue(ledger.register(token("equipped"), slot).accepted());
        ItemLocationSnapshot equipmentBefore = ledger.snapshot();
        assertOccupiedWithoutMutation(ledger, equipmentBefore,
                () -> ledger.register(token("other-equipped"), slot));
        ItemToken movable = token("movable");
        ItemLocation movableWorld = world("movable");
        assertTrue(ledger.register(movable, movableWorld).accepted());
        ItemLocationSnapshot equipmentMoveBefore = ledger.snapshot();
        assertOccupiedWithoutMutation(ledger, equipmentMoveBefore,
                () -> ledger.compareAndMove(equipmentMoveBefore.revision(), movable, movableWorld, slot));

        ItemLocationSnapshot cellBefore = ledger.snapshot();
        assertOccupiedWithoutMutation(ledger, cellBefore,
                () -> ledger.register(token("other-stored"), cell(bag, "one")));
        assertOccupiedWithoutMutation(ledger, cellBefore,
                () -> ledger.compareAndMove(cellBefore.revision(), contender, world("contender"), cell(bag, "one")));

        // World entities are distinct by entity UUID, even in the same world.
        assertTrue(ledger.register(token("world-one"), world("world-one")).accepted());
        assertTrue(ledger.register(token("world-two"), world("world-two")).accepted());
    }

    @Test void containedTokenContinuesToNameBagWhenBagMoves() {
        ItemLocationLedger ledger = new ItemLocationLedger();
        ItemToken bag = token("moving-bag");
        ItemToken contents = token("contents");
        ItemLocation bagHand = hand("left");
        ItemLocation bagEquipment = new ItemLocation.BodyEquipment(BODY, "back");
        ItemLocation bagWorld = world("moving-bag");
        ItemLocation contentsCell = cell(bag, "contents-cell");
        assertTrue(ledger.register(bag, bagHand).accepted());
        assertTrue(ledger.register(contents, contentsCell).accepted());

        for (ItemLocation destination : List.of(bagEquipment, bagWorld, hand("right"))) {
            ItemLocation source = ledger.snapshot().locationOf(bag).orElseThrow();
            var moved = ledger.compareAndMove(ledger.snapshot().revision(), bag, source, destination);
            assertTrue(moved.accepted());
            assertEquals(destination, moved.snapshot().locationOf(bag).orElseThrow());
            assertEquals(contentsCell, moved.snapshot().locationOf(contents).orElseThrow());
            assertEquals(bag, ((ItemLocation.StorageCell) moved.snapshot().locationOf(contents).orElseThrow()).container());
        }
    }

    private static void assertOccupiedWithoutMutation(ItemLocationLedger ledger, ItemLocationSnapshot before,
            java.util.function.Supplier<ItemLocationLedger.Result> attempt) {
        var result = attempt.get();
        assertFalse(result.accepted());
        assertEquals(ItemLocationLedger.Rejection.DESTINATION_OCCUPIED, result.rejection());
        assertSame(before, ledger.snapshot());
        assertEquals(before.revision(), ledger.snapshot().revision());
    }

    @Test void snapshotsAreImmuneToExternalMutationAndListingsAreDeterministic() {
        LinkedHashMap<ItemToken, ItemLocation> supplied = new LinkedHashMap<>();
        ItemToken one = token("one");
        supplied.put(one, hand("left"));
        ItemLocationSnapshot snapshot = new ItemLocationSnapshot(9, supplied);
        supplied.clear();
        assertEquals(List.of(one), new ArrayList<>(snapshot.locations().keySet()));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.locations().clear());
        assertThrows(IllegalArgumentException.class, () -> new ItemLocation.BodyHand(BODY, " "));
        assertThrows(IllegalArgumentException.class, () -> new ItemLocation.BodyEquipment(BODY, "x".repeat(65)));
    }

    @Test void refusesRevisionOverflowWithoutReplacingSnapshot() {
        ItemToken one = token("one");
        ItemLocationSnapshot max = new ItemLocationSnapshot(Long.MAX_VALUE, Map.of(one, hand("left")));
        ItemLocationLedger ledger = new ItemLocationLedger(max);
        assertEquals(ItemLocationLedger.Rejection.REVISION_OVERFLOW,
                ledger.compareAndMove(Long.MAX_VALUE, one, hand("left"), world("overflow")).rejection());
        assertSame(max, ledger.snapshot());
    }

    @Test void boundedTwentyPlayerScaleListingAndMoves() {
        ItemLocationLedger ledger = new ItemLocationLedger();
        for (int player = 0; player < 20; player++) {
            for (int slot = 0; slot < 10; slot++) {
                ItemToken item = token("p" + player + "-i" + slot);
                assertTrue(ledger.register(item, new ItemLocation.BodyEquipment(
                        UUID.nameUUIDFromBytes(("body" + player).getBytes()), "slot" + slot)).accepted());
            }
        }
        assertEquals(200, ledger.snapshot().size());
        assertEquals(200, ledger.snapshot().locations().size());
    }
}
