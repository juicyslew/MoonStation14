package com.juicyslew.moonstation14.ms14.hands.live;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BodyGrenadeExpiryTest {
    @Test
    void preservesBeforeDetonationAtOriginalPosition() {
        BlockPos cachedOrigin = new BlockPos(2, 3, 4);
        List<String> order = new ArrayList<>();
        List<BlockPos> blast = new ArrayList<>();
        BodyGrenadeHeldFuseService.expireAtOrigin(cachedOrigin,
                () -> order.add("preserve"), blast::add);
        assertEquals(List.of("preserve"), order);
        assertEquals(List.of(cachedOrigin), blast);
    }
}
