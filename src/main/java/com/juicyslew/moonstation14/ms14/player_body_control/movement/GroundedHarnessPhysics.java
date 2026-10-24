package com.juicyslew.moonstation14.ms14.player_body_control.movement;

import com.juicyslew.moonstation14.ms14.movement.GravityAirControl;

import java.util.Optional;

/** Minecraft adapter constants shared by authoritative and predicted grounded harness steps. */
public final class GroundedHarnessPhysics {
    public static final double TICK_SECONDS = .05d;
    public static final double GRAVITY_PER_SECOND_SQUARED = 32d;
    public static final double JUMP_VELOCITY_PER_SECOND = 8.4d;
    public static final double VERTICAL_DRAG = .98d;
    private static final Optional<GravityAirControl> WALK_AIR_CONTROL = Optional.of(
            new GravityAirControl(TICK_SECONDS, .91d, 8d * VERTICAL_DRAG));
    private static final Optional<GravityAirControl> SPRINT_AIR_CONTROL = Optional.of(
            new GravityAirControl(TICK_SECONDS, .91d, 10.4d * VERTICAL_DRAG));

    /** Cached Minecraft adaptation profile; movement targets remain prototype/policy-owned. */
    public static Optional<GravityAirControl> gravityAirControl(boolean sprint) {
        return sprint ? SPRINT_AIR_CONTROL : WALK_AIR_CONTROL;
    }

    private GroundedHarnessPhysics() { }
}
