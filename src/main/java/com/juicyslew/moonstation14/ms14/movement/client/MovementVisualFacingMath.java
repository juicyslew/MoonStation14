package com.juicyslew.moonstation14.ms14.movement.client;

import java.util.OptionalDouble;

/** Pure angle conversion for quantized custom movement intent. */
public final class MovementVisualFacingMath {
    private MovementVisualFacingMath() { }

    /** Minecraft yaw convention: forward (+Z) is zero and positive X faces -90 degrees. */
    public static OptionalDouble yawDegrees(short x, short z) {
        if (x == 0 && z == 0) return OptionalDouble.empty();
        return OptionalDouble.of(Math.toDegrees(Math.atan2(-x, z)));
    }
}
