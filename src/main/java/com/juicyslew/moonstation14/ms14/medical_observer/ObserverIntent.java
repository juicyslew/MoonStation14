package com.juicyslew.moonstation14.ms14.medical_observer;

import net.minecraft.util.Mth;

/** Bounded semantic wishes only; clients never submit a destination or position. */
public record ObserverIntent(int strafe, int forward, int vertical, boolean sprint, float yaw) {
    public ObserverIntent {
        if (strafe < -1000 || strafe > 1000 || forward < -1000 || forward > 1000
                || vertical < -1 || vertical > 1 || !Float.isFinite(yaw))
            throw new IllegalArgumentException("invalid observer intent");
        yaw = Mth.wrapDegrees(yaw);
    }
}

