package com.juicyslew.moonstation14.ms14.power.floor;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class TilePrySelectionTest {
    @Test void onlyUpWithAnInstalledFinishIsEligible() {
        for (Direction face : Direction.values()) {
            for (StationFloorBlock.TileFinish finish : StationFloorBlock.TileFinish.values()) {
                var expected = face != Direction.UP || finish == StationFloorBlock.TileFinish.NONE
                        ? TilePrySelection.Result.DENIED
                        : finish == StationFloorBlock.TileFinish.STEEL
                        ? TilePrySelection.Result.STEEL_TILE : TilePrySelection.Result.WHITE_TILE;
                assertEquals(expected, TilePrySelection.select(face, finish));
                // Repeated decisions return the same result without changing the finish or any world state.
                assertEquals(expected, TilePrySelection.select(face, finish));
            }
        }
    }

    @Test void nullFaceOrFinishIsDenied() {
        for (StationFloorBlock.TileFinish finish : StationFloorBlock.TileFinish.values()) {
            assertEquals(TilePrySelection.Result.DENIED, TilePrySelection.select(null, finish));
        }
        for (Direction face : Direction.values()) {
            assertEquals(TilePrySelection.Result.DENIED, TilePrySelection.select(face, null));
        }
        assertEquals(TilePrySelection.Result.DENIED, TilePrySelection.select(null, null));
    }
}
