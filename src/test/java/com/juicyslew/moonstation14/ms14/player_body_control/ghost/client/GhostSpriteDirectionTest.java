package com.juicyslew.moonstation14.ms14.player_body_control.ghost.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GhostSpriteDirectionTest {
    @Test
    void mapsMinecraftYawToRsiCardinalOrder() {
        assertEquals(1, GhostSpriteDirection.fromRelativeYaw(180));
        assertEquals(2, GhostSpriteDirection.fromRelativeYaw(-90));
        assertEquals(0, GhostSpriteDirection.fromRelativeYaw(0));
        assertEquals(3, GhostSpriteDirection.fromRelativeYaw(90));
    }

    @Test
    void oppositeSpriteOrientationSelectsFrontWhenGhostFacesViewer() {
        // RSI cardinal order is South, North, East, West; the ghost yaw points South at 0 degrees.
        assertEquals(0, GhostSpriteDirection.fromView(0, 180)); // front / South
        assertEquals(1, GhostSpriteDirection.fromView(0, 0)); // back / North
        assertEquals(3, GhostSpriteDirection.fromView(90, 180)); // side / East
        assertEquals(2, GhostSpriteDirection.fromView(-90, 180)); // side / West
        assertEquals(GhostSpriteDirection.fromView(0, 180), GhostSpriteDirection.fromView(360, -180));
    }
}
