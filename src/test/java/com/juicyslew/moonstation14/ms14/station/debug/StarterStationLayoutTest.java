package com.juicyslew.moonstation14.ms14.station.debug;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class StarterStationLayoutTest {
    private final StarterStationLayout layout = StarterStationLayout.standard();

    @Test void hasBoundedFootprintFourRoomsAndFourClearLevels() {
        assertEquals(0, layout.minX());
        assertEquals(0, layout.minZ());
        assertEquals(19, layout.maxX());
        assertEquals(19, layout.maxZ());
        assertEquals(4, layout.clearHeight());
        assertEquals(5, layout.roofY());
        assertEquals(StarterStationLayout.Classification.INTERIOR, layout.classify(1, 1));
        assertEquals(StarterStationLayout.Classification.INTERIOR, layout.classify(18, 18));
        assertEquals(StarterStationLayout.Classification.INTERIOR, layout.classify(0, 3));
        assertEquals(StarterStationLayout.Classification.INTERIOR, layout.classify(0, 4));
        assertEquals(StarterStationLayout.Classification.WALL, layout.classify(0, 10));
        assertEquals(StarterStationLayout.Classification.OUTSIDE, layout.classify(-1, 0));
        assertEquals(StarterStationLayout.Classification.OUTSIDE, layout.classify(20, 4));

        for (int z : new int[]{1, 13}) {
            for (int x : new int[]{1, 13}) {
                for (int dz = 0; dz < 6; dz++) {
                    for (int dx = 0; dx < 6; dx++) {
                        assertEquals(StarterStationLayout.Classification.INTERIOR, layout.classify(x + dx, z + dz));
                    }
                }
            }
        }
    }

    @Test void allInteriorCellsAreFlooredClearedAndRoofedAndWallsAreSteel() {
        for (int z = 0; z <= layout.maxZ(); z++) {
            for (int x = 0; x <= layout.maxX(); x++) {
                StarterStationLayout.Classification classification = layout.classify(x, z);
                assertTrue(hasCell(x, 0, z, classification == StarterStationLayout.Classification.INTERIOR
                        && isHallFloor(x, z) ? StarterStationLayout.Material.WHITE_TILE : StarterStationLayout.Material.STEEL));
                if (classification == StarterStationLayout.Classification.OUTSIDE) continue;
                for (int y = 1; y <= layout.clearHeight(); y++) {
                    StarterStationLayout.Material expected = classification == StarterStationLayout.Classification.INTERIOR
                            ? StarterStationLayout.Material.AIR : StarterStationLayout.Material.STEEL;
                    assertTrue(hasCell(x, y, z, expected), "unexpected cell at " + x + "," + y + "," + z);
                }
                assertTrue(hasCell(x, layout.roofY(), z, StarterStationLayout.Material.STEEL));
            }
        }
        assertFalse(layout.plan().stream().anyMatch(cell -> cell.x() < 0 || cell.x() > layout.maxX()
                || cell.z() < 0 || cell.z() > layout.maxZ()));
    }

    @Test void everyRoomAndHallwayIsInOneContiguousWalkableRegion() {
        Set<Long> visited = new HashSet<>();
        ArrayDeque<int[]> pending = new ArrayDeque<>();
        pending.add(new int[]{0, 3});
        while (!pending.isEmpty()) {
            int[] point = pending.removeFirst();
            long key = key(point[0], point[1]);
            if (!visited.add(key) || layout.classify(point[0], point[1]) != StarterStationLayout.Classification.INTERIOR) continue;
            pending.add(new int[]{point[0] + 1, point[1]});
            pending.add(new int[]{point[0] - 1, point[1]});
            pending.add(new int[]{point[0], point[1] + 1});
            pending.add(new int[]{point[0], point[1] - 1});
        }
        for (int z : new int[]{2, 14}) {
            for (int x : new int[]{2, 14}) assertTrue(visited.contains(key(x, z)), "room unreachable");
        }
        assertTrue(visited.contains(key(9, 3)), "upper hallway unreachable");
        assertTrue(visited.contains(key(10, 15)), "lower hallway unreachable");
        assertTrue(visited.contains(key(3, 9)), "left hallway unreachable");
        assertTrue(visited.contains(key(15, 10)), "right hallway unreachable");
        for (int x = 0; x <= 7; x++) {
            assertTrue(visited.contains(key(x, 3)), "west entrance corridor unreachable at x=" + x);
            assertTrue(visited.contains(key(x, 4)), "west entrance corridor unreachable at x=" + x);
            assertTrue(hasCell(x, 0, 3, StarterStationLayout.Material.WHITE_TILE), "untiled west corridor at x=" + x);
            assertTrue(hasCell(x, 0, 4, StarterStationLayout.Material.WHITE_TILE), "untiled west corridor at x=" + x);
        }
    }

    @Test void planIsDeterministicAndImmutable() {
        assertSame(layout, StarterStationLayout.standard());
        assertEquals(layout.plan(), StarterStationLayout.standard().plan());
        assertThrows(UnsupportedOperationException.class, () -> layout.plan().clear());
    }

    private boolean hasCell(int x, int y, int z, StarterStationLayout.Material material) {
        return layout.plan().contains(new StarterStationLayout.Cell(x, y, z, material));
    }

    private static boolean isHallFloor(int x, int z) {
        return (x == 7 || x == 12) && (z == 3 || z == 4 || z == 15 || z == 16)
                || (z == 7 || z == 12) && (x == 3 || x == 4 || x == 15 || x == 16)
                || x >= 0 && x <= 7 && (z == 3 || z == 4)
                || (z == 3 || z == 4 || z == 15 || z == 16) && x >= 8 && x <= 11
                || (x == 3 || x == 4 || x == 15 || x == 16) && z >= 8 && z <= 11;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }
}
