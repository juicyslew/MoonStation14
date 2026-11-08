package com.juicyslew.moonstation14.ms14.ghost_roles.server;

import net.minecraft.world.phys.Vec3;

/** Deterministic observer camera placement for a selected role target. */
final class GhostRoleTeleportGeometry {
    static final double BEHIND_DISTANCE = 2.0;
    static final double ABOVE_TARGET = 1.0;

    private GhostRoleTeleportGeometry() { }

    static Look compute(double targetX, double targetY, double targetZ, float targetYaw,
                        double targetEyeY, double ghostEyeHeight) {
        double radians = Math.toRadians(targetYaw);
        Vec3 position = new Vec3(targetX + Math.sin(radians) * BEHIND_DISTANCE,
                targetY + ABOVE_TARGET, targetZ - Math.cos(radians) * BEHIND_DISTANCE);
        double dx = targetX - position.x;
        double dy = targetEyeY - (position.y + ghostEyeHeight);
        double dz = targetZ - position.z;
        double horizontal = Math.hypot(dx, dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        return new Look(position, yaw, pitch);
    }

    record Look(Vec3 position, float yaw, float pitch) { }
}
