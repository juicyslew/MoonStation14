package com.juicyslew.moonstation14.ms14.atmos.core;

/** Explicit bridge between SS14 reference-tile quantities and Minecraft's one-cubic-meter cell. */
public final class AtmosphereUnits {
    public static final double UPSTREAM_TILE_VOLUME_LITERS = 2500.0;
    public static final double MINECRAFT_CELL_VOLUME_LITERS = 1000.0;
    public static final double REFERENCE_INVENTORY_SCALE = MINECRAFT_CELL_VOLUME_LITERS / UPSTREAM_TILE_VOLUME_LITERS;
    public static final double JOULES_PER_UPSTREAM_REFERENCE_TILE_TO_CELL = REFERENCE_INVENTORY_SCALE;
    public static final double UPSTREAM_HEAT_SCALE_DEFAULT = 8.0;

    private AtmosphereUnits() { }

    /** Converts an absolute inventory/energy threshold defined for one upstream tile. */
    public static double referenceTileQuantity(double upstreamQuantity) {
        if (!Double.isFinite(upstreamQuantity)) throw new IllegalArgumentException("quantity must be finite");
        return upstreamQuantity * REFERENCE_INVENTORY_SCALE;
    }

    /** Converts a local-cell quantity back to its upstream reference-tile equivalent. */
    public static double localCellQuantityToReferenceTile(double localQuantity) {
        if (!Double.isFinite(localQuantity)) throw new IllegalArgumentException("quantity must be finite");
        return localQuantity / REFERENCE_INVENTORY_SCALE;
    }

    /** Pressure, temperature, ratios, per-mole values, and computational tolerances are not scaled. */
    public static double referenceTileVolumeCubicMeters() { return UPSTREAM_TILE_VOLUME_LITERS / 1000.0; }
    public static double minecraftCellVolumeCubicMeters() { return MINECRAFT_CELL_VOLUME_LITERS / 1000.0; }
}

