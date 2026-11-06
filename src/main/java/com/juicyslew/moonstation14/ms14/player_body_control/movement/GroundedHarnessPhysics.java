package com.juicyslew.moonstation14.ms14.player_body_control.movement;

/** Minecraft adapter constants shared by authoritative and predicted grounded harness steps. */
public final class GroundedHarnessPhysics {
    public static final double TICK_SECONDS = .05d;
    public static final double GRAVITY_PER_SECOND_SQUARED = 32d;
    public static final double JUMP_VELOCITY_PER_SECOND = 8.4d;
    public static final double VERTICAL_DRAG = .98d;
    /** Minecraft player air retention, promoted from the installed vanilla float factor. */
    public static final double AIR_HORIZONTAL_RETENTION = .91F;
    /** Adapter velocity is blocks/second; vanilla's 0.003-block displacement/tick equivalent. */
    public static final double VELOCITY_SNAP_THRESHOLD_BLOCKS_PER_SECOND = .003d / TICK_SECONDS;

    private GroundedHarnessPhysics() { }
}
