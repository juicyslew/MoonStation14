package com.juicyslew.moonstation14.ms14.loose_objects.pulling;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Shared server/client tether integration. Return value is delta-velocity in blocks/second. */
public final class TetherMotion {
    public static final double SECONDS_PER_TICK = .05;
    private TetherMotion() { }

    public static Vec3 deltaVelocityPerSecond(Entity entity) {
        return deltaVelocityPerSecond(entity, entity.position(), entity.getDeltaMovement().scale(20));
    }

    /** Evaluate a client-owned endpoint from the exact motor state being integrated this frame. */
    public static Vec3 deltaVelocityPerSecond(Entity entity, Vec3 predictedPosition, Vec3 predictedVelocityPerSecond) {
        RopeConstraintAttachment attachment = entity.getExistingDataOrNull(ModDataAttachments.ROPE_CONSTRAINT.get());
        if (attachment == null || entity.noPhysics || !finite(predictedPosition) || !finite(predictedVelocityPerSecond)) return Vec3.ZERO;
        Vec3 result = Vec3.ZERO;
        Vec3 selfPosition = predictedPosition;
        Vec3 selfVelocityPerSecond = predictedVelocityPerSecond;
        for (var tether : attachment.constraints()) {
            if (!entity.level().isClientSide) {
                selfPosition = tether.self().position();
                selfVelocityPerSecond = tether.self().velocityPerSecond();
            }
            Vec3 acceleration = TetherSpring.acceleration(selfPosition, selfVelocityPerSecond,
                    tether.otherSnapshot().position(), tether.otherSnapshot().velocityPerSecond(), tether.slack(),
                    tether.tuning().stiffness(), tether.tuning().progressiveStiffness(),
                    tether.tuning().damping(), tether.tuning().maximumAcceleration());
            result = result.add(acceleration.scale(SECONDS_PER_TICK));
        }
        return finite(result) ? result : Vec3.ZERO;
    }

    private static boolean finite(Vec3 v) { return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z); }
}
