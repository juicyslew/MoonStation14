package com.juicyslew.moonstation14.ms14.player_body_control.ghost.client;

/** Maps Minecraft yaw relative to the view direction to RobustToolbox RSI cardinal order. */
final class GhostSpriteDirection {
    private static final int[] RSI_DIRECTION_BY_QUARTER_TURN = {0, 3, 1, 2};

    private GhostSpriteDirection() {}

    static int fromRelativeYaw(float relativeYaw) {
        int quarterTurn = (int) Math.floor((relativeYaw + 45.0F) / 90.0F) & 3;
        return RSI_DIRECTION_BY_QUARTER_TURN[quarterTurn];
    }
}

