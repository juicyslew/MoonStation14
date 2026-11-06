package com.juicyslew.moonstation14.ms14.standing;

import org.joml.Quaterniond;

/** Neutral pure math for extracting world-up heading from a rotation delta. */
public final class WorldUpTwist {
    private WorldUpTwist() { }
    public static float degrees(Quaterniond change) {
        if (change == null || !Double.isFinite(change.w) || !Double.isFinite(change.y)) return 0;
        double w=change.w, y=change.y;
        if (w < 0) { w=-w; y=-y; }
        if (w == 0d && y == 0d) return 0;
        return (float)Math.toDegrees(-2d*Math.atan2(y,w));
    }
}
