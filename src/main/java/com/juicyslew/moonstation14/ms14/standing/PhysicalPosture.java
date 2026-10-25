package com.juicyslew.moonstation14.ms14.standing;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;

/** Bridges MoonStation posture to vanilla's stable physical pose. */
public final class PhysicalPosture {
    private PhysicalPosture() { }
    public static void apply(LivingEntity body) {
        if (body == null) return;
        Pose desired = PostureSystem.isDown(body) ? Pose.SWIMMING : Pose.STANDING;
        if (body.getPose() != desired) body.setPose(desired);
    }
}

