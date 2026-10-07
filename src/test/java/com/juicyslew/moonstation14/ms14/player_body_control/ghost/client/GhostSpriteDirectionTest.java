package com.juicyslew.moonstation14.ms14.player_body_control.ghost.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GhostSpriteDirectionTest {
    @Test
    void mapsMinecraftYawToRsiCardinalOrder() {
        assertEquals(0, GhostSpriteDirection.fromRelativeYaw(0));
        assertEquals(3, GhostSpriteDirection.fromRelativeYaw(90));
        assertEquals(1, GhostSpriteDirection.fromRelativeYaw(180));
        assertEquals(2, GhostSpriteDirection.fromRelativeYaw(-90));
    }
}
