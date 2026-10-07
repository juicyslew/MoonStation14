package com.juicyslew.moonstation14.ms14.standing;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/** Shared nearest-point/reach rule for client nomination and authoritative target validation. */
public final class BodyInteractionRay {
    private BodyInteractionRay() { }

    public static Optional<Vec3> hit(AABB bounds, Vec3 start, Vec3 end, double reach, double blockerDistance) {
        if (bounds == null || start == null || end == null || reach < 0 || blockerDistance < 0) return Optional.empty();
        Optional<Vec3> point = bounds.contains(start) ? Optional.of(start) : bounds.clip(start, end);
        return point.filter(value -> start.distanceTo(value) + 1.0e-7 < reach
                && start.distanceTo(value) < blockerDistance);
    }
}
