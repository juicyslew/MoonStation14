package com.juicyslew.moonstation14.ms14.power.floor;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StationFloorBlockTest {
    @Test
    void tileFinishIsSmallAndHasStableSerializedNames() {
        assertEquals(List.of("none", "steel", "white"),
                StationFloorBlock.TILE_FINISH.getPossibleValues().stream()
                        .map(StationFloorBlock.TileFinish::getSerializedName)
                        .toList());
    }
}
