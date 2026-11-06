package com.juicyslew.moonstation14.ms14.hands.live;

import net.minecraft.world.phys.Vec3;

/** Combines frame-relative body motion with exact carrier velocity and hand-release intent. */
final class HandReleaseKinematics {
    private HandReleaseKinematics() { }

    static Vec3 compose(Vec3 bodyOwnVelocityPerTick, Vec3 sourceCarrierVelocityPerSecond,
                        Vec3 intentionalVelocityPerTick) {
        if (!finite(bodyOwnVelocityPerTick) || !finite(sourceCarrierVelocityPerSecond)
                || !finite(intentionalVelocityPerTick)) return null;
        Vec3 result = bodyOwnVelocityPerTick.add(sourceCarrierVelocityPerSecond.scale(
                com.juicyslew.moonstation14.ms14.player_body_control.movement.GroundedHarnessPhysics.TICK_SECONDS))
                .add(intentionalVelocityPerTick);
        return finite(result) ? result : null;
    }

    static Vec3 composePlacement(BodyItemTargetProbe.DropPlacement placement, Vec3 bodyOwnVelocityPerTick,
                                Vec3 sourceCarrierVelocityPerSecond) {
        if (placement == null) return null;
        return placement.surfacePlacement() ? Vec3.ZERO
                : compose(bodyOwnVelocityPerTick, sourceCarrierVelocityPerSecond, placement.velocity());
    }

    private static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
}
