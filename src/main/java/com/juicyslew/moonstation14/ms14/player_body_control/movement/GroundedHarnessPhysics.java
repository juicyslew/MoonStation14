package com.juicyslew.moonstation14.ms14.player_body_control.movement;

/** Minecraft adapter constants shared by authoritative and predicted grounded harness steps. */
public final class GroundedHarnessPhysics {
    public static final double TICK_SECONDS = .05d;
    public static final double GRAVITY_PER_SECOND_SQUARED = 32d;
    public static final double JUMP_VELOCITY_PER_SECOND = 8.4d;
    public static final double VERTICAL_DRAG = .98d;

    private GroundedHarnessPhysics() { }
}
