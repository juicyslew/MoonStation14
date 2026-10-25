package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.world.phys.Vec3;

/** Pure geometry shared by predicted and observer melee arc capture. */
public final class MeleeArcGeometry {
    public record Aim(Vec3 bodyPosition, Vec3 direction, Vec3 attackPoint) { }
    private MeleeArcGeometry() { }

    public static Aim capture(Vec3 bodyPosition, double bodyHeight, Vec3 look,
                              Vec3 nullableTargetPosition, double range) {
        Vec3 body = new Vec3(bodyPosition.x, bodyPosition.y, bodyPosition.z);
        Vec3 horizontalLook = horizontal(look);
        Vec3 direction = nullableTargetPosition == null ? horizontalLook
                : horizontal(new Vec3(nullableTargetPosition.x-body.x, nullableTargetPosition.y-body.y, nullableTargetPosition.z-body.z));
        if (!Double.isFinite(direction.lengthSqr()) || direction.lengthSqr() < 1e-8) direction = horizontalLook;
        if (!Double.isFinite(direction.lengthSqr()) || direction.lengthSqr() < 1e-8) return null;
        direction = direction.normalize();
        double max = Double.isFinite(range) ? Math.max(0, range-.2) : 0;
        double distance = max;
        if (nullableTargetPosition != null) {
            double d = body.subtract(nullableTargetPosition).horizontalDistance();
            if (Double.isFinite(d)) distance = Math.min(max, d);
        }
        return new Aim(body, direction, body.add(direction.scale(distance)).add(0, bodyHeight*.5, 0));
    }
    private static Vec3 horizontal(Vec3 v) { return new Vec3(v.x, 0, v.z); }
}
