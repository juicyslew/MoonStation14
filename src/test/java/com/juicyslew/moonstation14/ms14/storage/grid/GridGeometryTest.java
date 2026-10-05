package com.juicyslew.moonstation14.ms14.storage.grid;

import org.junit.jupiter.api.Test;
import net.minecraft.resources.ResourceLocation;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.storage.grid.ItemFootprintProfile;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GridGeometryTest {
    private static GridGeometry.Cell c(int x, int y) { return new GridGeometry.Cell(x, y); }

    @Test
    void onlyCataloguedMoonstationPickaxeGetsTFootprint() throws Exception {
        var server = PrototypeRuntime.serverManager();
        var client = PrototypeRuntime.clientManager();
        server.clearPublishedCatalogs();
        client.clearPublishedCatalogs();
        try (var reader = new java.io.InputStreamReader(getClass().getClassLoader().getResourceAsStream(
                "data/moonstation14/moonstation14/item_footprint/pickaxe.json"))) {
            var profile = ItemFootprintProfile.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader)).getOrThrow();
            server.publishDecoded(ModItemFootprints.TYPE, java.util.Map.of(
                    ResourceLocation.fromNamespaceAndPath("moonstation14", "pickaxe"), profile));
        var ss14Pickaxe = ItemFootprints.forId(ResourceLocation.fromNamespaceAndPath("moonstation14", "pickaxe"));
        assertEquals(5, ss14Pickaxe.cells().size());
        assertEquals(1, ItemFootprints.forId(ResourceLocation.fromNamespaceAndPath("minecraft", "iron_pickaxe")).cells().size());
        } finally {
            server.clearPublishedCatalogs();
            client.clearPublishedCatalogs();
        }
    }

    @Test
    void approvedDomainsAndExactRotations() {
        assertEquals(28, GridGeometry.StorageMask.rectangle(7, 4).cells().size());
        assertEquals(16, GridGeometry.StorageMask.rectangle(8, 2).cells().size());
        var t = GridGeometry.Footprint.of(List.of(c(0, 0), c(1, 0), c(2, 0), c(1, 1), c(1, 2)));
        assertEquals(List.of(c(0,0), c(1,0), c(2,0), c(1,1), c(1,2)), t.cells(GridGeometry.Rotation.DEG_0));
        assertEquals(List.of(c(2,0), c(0,1), c(1,1), c(2,1), c(2,2)), t.cells(GridGeometry.Rotation.DEG_90));
        assertEquals(List.of(c(1,0), c(1,1), c(0,2), c(1,2), c(2,2)), t.cells(GridGeometry.Rotation.DEG_180));
        assertEquals(List.of(c(0,0), c(0,1), c(1,1), c(2,1), c(0,2)), t.cells(GridGeometry.Rotation.DEG_270));
    }

    @Test
    void holesForbiddenCellsAndBoundsAreCheckedPerOccupiedCell() {
        var board = GridGeometry.StorageMask.of(3, 3, List.of(c(0,0), c(1,0), c(2,0), c(0,1), c(2,1), c(0,2), c(1,2), c(2,2)));
        var empty = GridGeometry.Snapshot.<String>empty(board);
        var square = GridGeometry.Footprint.of(List.of(c(0,0), c(1,0), c(0,1), c(1,1)));
        var hole = empty.place("hole", square, c(0,0), GridGeometry.Rotation.DEG_0);
        assertFalse(hole.accepted());
        assertSame(empty, hole.snapshot());
        assertFalse(empty.place("edge", GridGeometry.Footprint.of(List.of(c(0,0), c(1,0))), c(2,0), GridGeometry.Rotation.DEG_0).accepted());
        assertFalse(empty.place("negative", square, c(Integer.MIN_VALUE, 0), GridGeometry.Rotation.DEG_0).accepted());
    }

    @Test
    void firstFitIsDeterministicAndRotatesWhenNeeded() {
        var board = GridGeometry.StorageMask.rectangle(2, 3);
        var vertical = GridGeometry.Footprint.of(List.of(c(0,0), c(0,1), c(0,2)));
        var result = GridGeometry.Snapshot.<String>empty(board).firstFit("x", vertical);
        assertTrue(result.accepted());
        assertEquals(c(0,0), result.placement().anchor());
        assertEquals(GridGeometry.Rotation.DEG_0, result.placement().rotation());
        assertEquals(result.placement(), GridGeometry.Snapshot.<String>empty(board).firstFit("x", vertical).placement());
        var twoWide = GridGeometry.Footprint.of(List.of(c(0,0), c(1,0)));
        assertEquals(GridGeometry.Rotation.DEG_0, GridGeometry.Snapshot.<String>empty(GridGeometry.StorageMask.rectangle(2,1)).firstFit("x", twoWide).placement().rotation());
    }

    @Test
    void mutationsArePersistentUniqueAndCapped() {
        var board = GridGeometry.StorageMask.rectangle(8, 8);
        var snapshot = GridGeometry.Snapshot.<Integer>empty(board);
        for (int i = 0; i < GridGeometry.MAX_ENTRIES; i++)
            snapshot = snapshot.place(i, GridGeometry.Footprint.of(List.of(c(0,0))), c(i % 8, i / 8), GridGeometry.Rotation.DEG_0).snapshot();
        var full = snapshot;
        var rejected = full.place(99, GridGeometry.Footprint.of(List.of(c(0,0))), c(7,7), GridGeometry.Rotation.DEG_0);
        assertFalse(rejected.accepted());
        assertSame(full, rejected.snapshot());
        assertEquals(GridGeometry.Rejection.TOKEN_ALREADY_PRESENT, full.firstFit(0, GridGeometry.Footprint.of(List.of(c(0,0)))).rejection());
        assertEquals(32, full.placements().size());
        var removed = full.remove(0);
        assertTrue(removed.accepted());
        assertEquals(32, full.placements().size());
        assertEquals(31, removed.snapshot().placements().size());
    }

    @Test
    void rejectsMalformedAndOversizedInputsAndExposesOnlyImmutableCopies() {
        assertThrows(IllegalArgumentException.class, () -> GridGeometry.StorageMask.rectangle(9, 1));
        assertThrows(IllegalArgumentException.class, () -> GridGeometry.StorageMask.of(2,2,List.of(c(0,0),c(0,0))));
        assertThrows(IllegalArgumentException.class, () -> GridGeometry.Footprint.of(List.of()));
        assertThrows(IllegalArgumentException.class, () -> GridGeometry.Footprint.of(List.of(c(Integer.MIN_VALUE,0), c(Integer.MAX_VALUE,0))));
        var source = new ArrayList<>(List.of(c(0,0), c(1,0), c(1,1)));
        var shape = GridGeometry.Footprint.of(source);
        source.clear();
        assertEquals(3, shape.cells().size());
        assertThrows(UnsupportedOperationException.class, () -> shape.cells().clear());
        var domainSource = new ArrayList<>(List.of(c(0,0)));
        var mask = GridGeometry.StorageMask.of(1,1,domainSource);
        domainSource.clear();
        assertEquals(1, mask.cells().size());
        assertThrows(UnsupportedOperationException.class, () -> mask.cells().clear());
        var placements = GridGeometry.Snapshot.<String>empty(mask).place("item", shape, c(0,0), GridGeometry.Rotation.DEG_0);
        assertThrows(UnsupportedOperationException.class, () -> placements.snapshot().placements().clear());
        assertThrows(UnsupportedOperationException.class, () -> placements.placement().occupiedCells().clear());
    }
}
