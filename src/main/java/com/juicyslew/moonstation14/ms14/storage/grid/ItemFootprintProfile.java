package com.juicyslew.moonstation14.ms14.storage.grid;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;

/** Prototype footprint in storage-grid cells. Coordinates are normalized at construction. */
public record ItemFootprintProfile(List<GridGeometry.Cell> cells) {
    public static final Codec<GridGeometry.Cell> CELL_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("x").forGetter(GridGeometry.Cell::x),
            Codec.INT.fieldOf("y").forGetter(GridGeometry.Cell::y)
    ).apply(i, GridGeometry.Cell::new));
    public static final Codec<ItemFootprintProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            CELL_CODEC.listOf().fieldOf("cells").forGetter(ItemFootprintProfile::cells)
    ).apply(i, ItemFootprintProfile::new));

    public ItemFootprintProfile {
        if (cells == null || cells.isEmpty() || cells.size() > 64) throw new IllegalArgumentException("invalid item footprint");
        int minX = cells.stream().mapToInt(GridGeometry.Cell::x).min().orElseThrow();
        int minY = cells.stream().mapToInt(GridGeometry.Cell::y).min().orElseThrow();
        cells = cells.stream().map(c -> new GridGeometry.Cell(c.x() - minX, c.y() - minY)).distinct().toList();
        if (cells.size() > 64) throw new IllegalArgumentException("invalid item footprint");
    }
    public GridGeometry.Footprint footprint() { return GridGeometry.Footprint.of(cells); }
}
