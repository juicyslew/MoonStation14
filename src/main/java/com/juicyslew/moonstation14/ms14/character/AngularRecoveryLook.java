package com.juicyslew.moonstation14.ms14.character;

/** Converts raw Minecraft look angles through the inertial body basis for world-space aim/movement. */
public final class AngularRecoveryLook {
    private AngularRecoveryLook() { }

    public static AngularRecoveryModel.Vec3 worldLook(AngularRecoveryModel.State state, float rawYawDegrees,
                                                       float rawPitchDegrees) {
        double yaw = Math.toRadians(rawYawDegrees), pitch = Math.toRadians(rawPitchDegrees);
        var raw = new AngularRecoveryModel.Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch),
                Math.cos(yaw) * Math.cos(pitch));
        return state == null ? raw : state.orientation().rotate(raw);
    }

    /** World yaw convention consumed by GroundedHarnessMotor; raw yaw is a stable near-vertical fallback. */
    public static float worldPlanarYaw(AngularRecoveryModel.State state, float rawYawDegrees) {
        if (state == null) return rawYawDegrees;
        var f = worldLook(state, rawYawDegrees, 0f);
        if (f.x() * f.x() + f.z() * f.z() < 1e-12) return rawYawDegrees;
        return (float) Math.toDegrees(Math.atan2(-f.x(), f.z()));
    }
}
