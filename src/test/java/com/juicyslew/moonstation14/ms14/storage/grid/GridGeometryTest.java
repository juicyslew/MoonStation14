package com.juicyslew.moonstation14.ms14.storage.grid;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry.*;
import static org.junit.jupiter.api.Assertions.*;

class GridGeometryTest {
    @Test
    void oneByOneFitsAndAllRotationsRemainAvailable() {
        Footprint one = Footprint.of(List.of(new Cell(4, -7)));
        assertEquals(List.of(new Cell(0, 0)), one.cells());
        for (Rotation rotation : Rotation.values())
            assertEquals(List.of(new Cell(0, 0)), one.cells(rotation));

        Snapshot<String> empty = Snapshot.empty(StorageMask.rectangle(1, 1));
        Mutation<String> placed = empty.place("one", one, new Cell(0, 0), Rotation.DEG_0);
        assertTrue(placed.accepted());
        assertFalse(empty.isOccupied(new Cell(0, 0)));
        assertTrue(placed.snapshot().isOccupied(new Cell(0, 0)));
    }

    @Test
    void rectangleRotatesWithSwappedDimensions() {
        Footprint rectangle = Footprint.of(List.of(new Cell(0, 0), new Cell(1, 0), new Cell(2, 0),
                new Cell(0, 1), new Cell(1, 1), new Cell(2, 1)));
        assertEquals(6, rectangle.cells(Rotation.DEG_0).size());
        assertEquals(Set.of(new Cell(0, 0), new Cell(1, 0), new Cell(0, 1), new Cell(1, 1),
                        new Cell(0, 2), new Cell(1, 2)), Set.copyOf(rectangle.cells(Rotation.DEG_90)));
    }

    @Test
    void asymmetricIrregularShapeHasDistinctHalfAndQuarterTurns() {
        Footprint lShape = Footprint.of(List.of(new Cell(10, 20), new Cell(11, 20), new Cell(10, 21)));
        assertNotEquals(lShape.cells(Rotation.DEG_0), lShape.cells(Rotation.DEG_180));
        assertNotEquals(lShape.cells(Rotation.DEG_90), lShape.cells(Rotation.DEG_270));
        assertEquals(4, lShape.rotations().size());
    }

    @Test
    void asymmetricFootprintRotationsAreExactAfterNegativeCoordinatesNormalize() {
        Footprint shape = Footprint.of(List.of(
                new Cell(-3, -2), new Cell(-2, -2), new Cell(-3, -1), new Cell(-1, -1)));
        assertEquals(List.of(new Cell(0, 0), new Cell(1, 0), new Cell(0, 1), new Cell(2, 1)), shape.cells(Rotation.DEG_0));
        assertEquals(List.of(new Cell(0, 0), new Cell(1, 0), new Cell(1, 1), new Cell(0, 2)), shape.cells(Rotation.DEG_90));
        assertEquals(List.of(new Cell(0, 0), new Cell(2, 0), new Cell(1, 1), new Cell(2, 1)), shape.cells(Rotation.DEG_180));
        assertEquals(List.of(new Cell(1, 0), new Cell(0, 1), new Cell(0, 2), new Cell(1, 2)), shape.cells(Rotation.DEG_270));
    }

    @Test
    void concaveStorageMaskPreservesHoleAndRejectsFootprintAcrossIt() {
        StorageMask concave = StorageMask.of(3, 3, List.of(
                new Cell(0, 0), new Cell(1, 0), new Cell(2, 0),
                new Cell(0, 1), new Cell(2, 1),
                new Cell(0, 2), new Cell(1, 2), new Cell(2, 2)));
        Snapshot<String> base = Snapshot.empty(concave);
        Footprint domino = Footprint.of(List.of(new Cell(0, 0), new Cell(1, 0)));
        Mutation<String> acrossHole = base.place("x", domino, new Cell(0, 1), Rotation.DEG_0);
        assertFalse(acrossHole.accepted());
        assertSame(base, acrossHole.snapshot());
    }

    @Test
    void overlapAndDuplicateTokenRejectWithoutChangingSnapshot() {
        Snapshot<String> base = Snapshot.empty(StorageMask.rectangle(2, 1));
        Footprint one = Footprint.of(List.of(new Cell(0, 0)));
        Snapshot<String> occupied = base.place("a", one, new Cell(0, 0), Rotation.DEG_0).snapshot();
        Mutation<String> overlap = occupied.place("b", one, new Cell(0, 0), Rotation.DEG_0);
        assertFalse(overlap.accepted());
        assertSame(occupied, overlap.snapshot());
        Mutation<String> duplicateToken = occupied.place("a", one, new Cell(1, 0), Rotation.DEG_0);
        assertEquals(Rejection.TOKEN_ALREADY_PRESENT, duplicateToken.rejection());
        assertSame(occupied, duplicateToken.snapshot());
    }

    @Test
    void rejectsDuplicateInvalidOversizedAndOverflowingGeometry() {
        assertThrows(IllegalArgumentException.class, () -> Footprint.of(List.of(new Cell(0, 0), new Cell(0, 0))));
        assertThrows(IllegalArgumentException.class, () -> Footprint.of(List.of(new Cell(Integer.MIN_VALUE, 0), new Cell(Integer.MAX_VALUE, 0))));
        assertThrows(IllegalArgumentException.class, () -> StorageMask.of(2, 2, List.of(new Cell(2, 0))));
        assertThrows(IllegalArgumentException.class, () -> StorageMask.of(2, 2, List.of(new Cell(0, 0), new Cell(0, 0))));

        Snapshot<String> base = Snapshot.empty(StorageMask.rectangle(2, 2));
        Footprint one = Footprint.of(List.of(new Cell(0, 0)));
        for (Cell anchor : List.of(new Cell(Integer.MAX_VALUE, 0), new Cell(Integer.MIN_VALUE, 0), new Cell(99, 99))) {
            Mutation<String> rejected = base.place("far", one, anchor, Rotation.DEG_270);
            assertFalse(rejected.accepted());
            assertSame(base, rejected.snapshot());
        }
    }

    @Test
    void firstFitIsDeterministicByRowMajorAnchorThenRotation() {
        Snapshot<String> base = Snapshot.empty(StorageMask.rectangle(3, 2));
        Footprint domino = Footprint.of(List.of(new Cell(0, 0), new Cell(1, 0)));
        Mutation<String> first = base.firstFit("a", domino);
        assertTrue(first.accepted());
        assertEquals(new Cell(0, 0), first.placement().anchor());
        assertEquals(Rotation.DEG_0, first.placement().rotation());
        Mutation<String> second = first.snapshot().firstFit("b", domino);
        assertEquals(new Cell(2, 0), second.placement().anchor());
        assertEquals(Rotation.DEG_90, second.placement().rotation());
    }

    @Test
    void firstFitCanAnchorAtOriginMissingFromStorageMask() {
        StorageMask mask = StorageMask.of(2, 2, List.of(
                new Cell(1, 0), new Cell(0, 1), new Cell(1, 1)));
        Footprint lShape = Footprint.of(List.of(new Cell(1, 0), new Cell(0, 1), new Cell(1, 1)));

        Mutation<String> result = Snapshot.<String>empty(mask).firstFit("l", lShape);

        assertTrue(result.accepted());
        assertEquals(new Cell(0, 0), result.placement().anchor());
        assertEquals(Rotation.DEG_0, result.placement().rotation());
    }

    @Test
    void occupiedIndexAndRejectedOverlapPreserveOldImmutableSnapshots() {
        Snapshot<String> empty = Snapshot.empty(StorageMask.rectangle(2, 1));
        Footprint one = Footprint.of(List.of(new Cell(0, 0)));
        Snapshot<String> occupied = empty.place("a", one, new Cell(0, 0), Rotation.DEG_0).snapshot();

        Mutation<String> overlap = occupied.place("b", one, new Cell(0, 0), Rotation.DEG_0);
        assertFalse(overlap.accepted());
        assertSame(occupied, overlap.snapshot());
        assertFalse(empty.isOccupied(new Cell(0, 0)));
        assertTrue(occupied.isOccupied(new Cell(0, 0)));
        assertFalse(occupied.isOccupied(new Cell(1, 0)));
        assertEquals(1, occupied.placements().size());
    }

    @Test
    void snapshotsAndCollectionsAreImmutableAndRemoveIsPure() {
        Snapshot<String> base = Snapshot.empty(StorageMask.rectangle(2, 1));
        Footprint one = Footprint.of(List.of(new Cell(0, 0)));
        Snapshot<String> placed = base.place("a", one, new Cell(0, 0), Rotation.DEG_0).snapshot();
        assertThrows(UnsupportedOperationException.class, () -> placed.placements().clear());
        assertThrows(UnsupportedOperationException.class, () -> one.cells().clear());
        Snapshot<String> removed = placed.remove("a").snapshot();
        assertTrue(removed.placements().isEmpty());
        assertEquals(1, placed.placements().size());
        Mutation<String> missing = placed.remove("missing");
        assertFalse(missing.accepted());
        assertSame(placed, missing.snapshot());
    }
}
