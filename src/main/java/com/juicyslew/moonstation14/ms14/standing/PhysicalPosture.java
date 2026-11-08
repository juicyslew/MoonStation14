package com.juicyslew.moonstation14.ms14.standing;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;

/** Bridges MoonStation posture to vanilla's stable physical pose. */
public final class PhysicalPosture {
    private PhysicalPosture() { }
    /** Physical dimensions are shared by the vanilla pose and the aimed-prone dimension mixin. */
    public record Variant(boolean down, boolean supportedGunAim) { }
    public static Variant variant(PostureData data) {
        return new Variant(data != null && data.down(), data != null && data.down() && data.supportedGunAim());
    }
    public static void apply(LivingEntity body) {
        if (body == null) return;
        Pose desired = PostureSystem.isDown(body) ? Pose.SWIMMING : Pose.STANDING;
        if (body.getPose() != desired) body.setPose(desired);
    }
}
