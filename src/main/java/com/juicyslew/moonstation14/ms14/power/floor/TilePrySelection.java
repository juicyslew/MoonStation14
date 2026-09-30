package com.juicyslew.moonstation14.ms14.power.floor;

import net.minecraft.core.Direction;

/** Pure face/finish eligibility and tile-drop choice; no world or inventory access. */
public final class TilePrySelection {
    private TilePrySelection() { }

    public enum Result { DENIED, STEEL_TILE, WHITE_TILE }

    public static Result select(Direction face, StationFloorBlock.TileFinish finish) {
        if (face == null || finish == null) return Result.DENIED;
        if (face != Direction.UP || finish == StationFloorBlock.TileFinish.NONE) return Result.DENIED;
        return finish == StationFloorBlock.TileFinish.STEEL ? Result.STEEL_TILE : Result.WHITE_TILE;
    }
}
