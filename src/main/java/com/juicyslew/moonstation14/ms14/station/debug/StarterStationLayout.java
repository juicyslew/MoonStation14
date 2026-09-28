package com.juicyslew.moonstation14.ms14.station.debug;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pure, bounded block plan for a small four-room debug station. Coordinates are local and inclusive. */
public final class StarterStationLayout {
    public static final int MIN_X = 0;
    public static final int MIN_Z = 0;
    public static final int MAX_X = 19;
    public static final int MAX_Z = 19;
    public static final int FLOOR_Y = 0;
    public static final int CLEAR_HEIGHT = 4;
    public static final int ROOF_Y = CLEAR_HEIGHT + 1;

    private static final StarterStationLayout DEFAULT = new StarterStationLayout();

    public enum Classification { INTERIOR, WALL, OUTSIDE }

    /** Block intent only; consumers decide how (or whether) to apply it to a world. */
    public enum Material { AIR, STEEL, WHITE_TILE }

    public record Cell(int x, int y, int z, Material material) {}

    private final List<Cell> plan;

    private StarterStationLayout() {
        List<Cell> cells = new ArrayList<>();
        for (int z = MIN_Z; z <= MAX_Z; z++) {
            for (int x = MIN_X; x <= MAX_X; x++) {
                cells.add(new Cell(x, FLOOR_Y, z, isHallFloor(x, z) ? Material.WHITE_TILE : Material.STEEL));
                Classification classification = classify(x, z);
                if (classification == Classification.INTERIOR) {
                    for (int y = 1; y <= CLEAR_HEIGHT; y++) {
                        cells.add(new Cell(x, y, z, Material.AIR));
                    }
                    cells.add(new Cell(x, ROOF_Y, z, Material.STEEL));
                } else if (classification == Classification.WALL) {
                    for (int y = 1; y <= CLEAR_HEIGHT; y++) {
                        cells.add(new Cell(x, y, z, Material.STEEL));
                    }
                    cells.add(new Cell(x, ROOF_Y, z, Material.STEEL));
                }
            }
        }
        plan = Collections.unmodifiableList(cells);
    }

    public static StarterStationLayout standard() {
        return DEFAULT;
    }

    public int minX() { return MIN_X; }
    public int minZ() { return MIN_Z; }
    public int maxX() { return MAX_X; }
    public int maxZ() { return MAX_Z; }
    public int clearHeight() { return CLEAR_HEIGHT; }
    public int roofY() { return ROOF_Y; }

    /** Classifies the bounded x/z footprint. No coordinate outside it is part of the plan. */
    public Classification classify(int x, int z) {
        if (x < MIN_X || x > MAX_X || z < MIN_Z || z > MAX_Z) return Classification.OUTSIDE;
        return isInterior(x, z) ? Classification.INTERIOR : Classification.WALL;
    }

    /** O(1) lookup for the explicit plan cell at a coordinate, or null when the plan has no cell. */
    public Material materialAt(int x, int y, int z) {
        if (x < MIN_X || x > MAX_X || z < MIN_Z || z > MAX_Z
                || y < FLOOR_Y || y > ROOF_Y) return null;
        if (y == FLOOR_Y) return isHallFloor(x, z) ? Material.WHITE_TILE : Material.STEEL;
        if (y == ROOF_Y) return Material.STEEL;
        return classify(x, z) == Classification.INTERIOR ? Material.AIR : Material.STEEL;
    }

    /** Stable z-major, then x-major iteration; contains only explicit writes within the footprint. */
    public List<Cell> plan() {
        return plan;
    }

    private static boolean isDoorway(int x, int z) {
        return (x == 0 && (z == 3 || z == 4))
                || (x == 7 || x == 12) && (z == 3 || z == 4 || z == 15 || z == 16)
                || (z == 7 || z == 12) && (x == 3 || x == 4 || x == 15 || x == 16);
    }

    private static boolean isHallFloor(int x, int z) {
        return isDoorway(x, z)
                || x >= 0 && x <= 7 && (z == 3 || z == 4)
                || (z == 3 || z == 4 || z == 15 || z == 16) && x >= 8 && x <= 11
                || (x == 3 || x == 4 || x == 15 || x == 16) && z >= 8 && z <= 11;
    }

    private static boolean isInterior(int x, int z) {
        boolean room = (x >= 1 && x <= 6 || x >= 13 && x <= 18)
                && (z >= 1 && z <= 6 || z >= 13 && z <= 18);
        boolean corridor = (x >= 8 && x <= 11 && (z == 3 || z == 4 || z == 15 || z == 16))
                || ((x == 3 || x == 4 || x == 15 || x == 16) && z >= 8 && z <= 11)
                || (x >= 8 && x <= 11 && z >= 8 && z <= 11);
        return room || corridor || isDoorway(x, z);
    }
}
