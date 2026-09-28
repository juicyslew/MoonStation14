package com.juicyslew.moonstation14.ms14.station.debug;

/** Explicit mapping from the pure layout intent to the runtime block roles. */
public final class StarterStationBlockMapping {
    public enum BlockRole { AIR, STEEL_WALL, STEEL_FLOOR, WHITE_FLOOR }

    private StarterStationBlockMapping() { }

    public static BlockRole role(StarterStationLayout.Cell cell) {
        return role(cell.y(), cell.material());
    }

    public static BlockRole role(int y, StarterStationLayout.Material material) {
        if (material == StarterStationLayout.Material.AIR) return BlockRole.AIR;
        if (y == StarterStationLayout.FLOOR_Y)
            return material == StarterStationLayout.Material.WHITE_TILE ? BlockRole.WHITE_FLOOR : BlockRole.STEEL_FLOOR;
        return BlockRole.STEEL_WALL;
    }

    /** Supports under a raised tiled floor use the same robust steel wall block as the structure. */
    public static BlockRole supportRole() {
        return BlockRole.STEEL_WALL;
    }
}
