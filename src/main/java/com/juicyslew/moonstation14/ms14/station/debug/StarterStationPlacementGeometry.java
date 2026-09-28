package com.juicyslew.moonstation14.ms14.station.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Pure placement rules shared with focused tests. Footprint edges are inclusive. */
public final class StarterStationPlacementGeometry {
    private StarterStationPlacementGeometry() { }

    public static boolean containsSpawn(int originX, int originZ, int spawnX, int spawnZ,
                                         StarterStationLayout layout) {
        return spawnX >= originX + layout.minX() && spawnX <= originX + layout.maxX()
                && spawnZ >= originZ + layout.minZ() && spawnZ <= originZ + layout.maxZ();
    }

    /** Number of steel-wall support blocks between local terrain's air surface and the floor. */
    public static int supportBlockCount(int floorY, int terrainSurfaceY) {
        return Math.max(0, floorY - terrainSurfaceY);
    }

    /** Lowest terrain block inspected: at most maxVariation support positions plus the ground cell. */
    public static int terrainScanMinY(int floorY, int maxVariation) {
        return floorY - maxVariation - 1;
    }

    /** One column's terrain surface and any exact intended supports already written during an earlier attempt. */
    public record SupportColumn(int x, int z, int terrainSurfaceY, Set<Integer> installedSupportY) {
        public SupportColumn {
            installedSupportY = Set.copyOf(installedSupportY);
        }
    }

    /** A frozen support write. Existing intended supports are retained in the plan but need no second write. */
    public record SupportBlock(int x, int y, int z, boolean alreadyInstalled) { }

    /**
     * Plans supports from terrain below the selected floor, independent of current heightmaps. Every column
     * must be within the variation bound, and pre-existing support blocks must be an exact subset of the plan.
     */
    public static Optional<List<SupportBlock>> planSupports(int floorY, int maxVariation, List<SupportColumn> columns) {
        List<SupportBlock> plan = new ArrayList<>();
        for (SupportColumn column : columns) {
            int count = supportBlockCount(floorY, column.terrainSurfaceY());
            if (column.terrainSurfaceY() > floorY || count > maxVariation) return Optional.empty();
            for (int installedY : column.installedSupportY()) {
                if (installedY < column.terrainSurfaceY() || installedY >= floorY) return Optional.empty();
            }
            for (int y = column.terrainSurfaceY(); y < floorY; y++) {
                plan.add(new SupportBlock(column.x(), y, column.z(), column.installedSupportY().contains(y)));
            }
        }
        return Optional.of(List.copyOf(plan));
    }
}
