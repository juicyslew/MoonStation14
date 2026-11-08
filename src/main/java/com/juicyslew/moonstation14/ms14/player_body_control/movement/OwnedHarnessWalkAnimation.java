package com.juicyslew.moonstation14.ms14.player_body_control.movement;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Updates vanilla walk animation from displacement accepted by an owned harness movement step. */
public final class OwnedHarnessWalkAnimation {
    private OwnedHarnessWalkAnimation() { }

    public static void update(LivingEntity body, Vec3 acceptedDisplacement) {
        body.walkAnimation.update(vanillaWalkSpeed(acceptedDisplacement.horizontalDistance()), .4f);
    }

    /** Native limb animation is distance-driven: accepted walk/run displacement advances it generically. */
    public static float vanillaWalkSpeed(double acceptedHorizontalDistance) {
        if (!Double.isFinite(acceptedHorizontalDistance) || acceptedHorizontalDistance <= 0) return 0f;
        return (float) Math.min(acceptedHorizontalDistance * 4d, 1d);
    }

    /** Cadence ratio for authored movement clips, from measured horizontal displacement per tick. */
    public static float cadenceScale(double dx, double dz, double resolvedWalkSpeedPerSecond) {
        if (!Double.isFinite(resolvedWalkSpeedPerSecond) || resolvedWalkSpeedPerSecond <= 0) return 1f;
        double observed = Math.hypot(dx, dz);
        double walkBlocksPerTick = resolvedWalkSpeedPerSecond / 20d;
        if (!Double.isFinite(observed) || observed <= 0 || !Double.isFinite(walkBlocksPerTick)) return 1f;
        return (float) Math.max(1d, observed / walkBlocksPerTick);
    }
}

