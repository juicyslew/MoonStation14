package com.juicyslew.moonstation14.ms14.eye.client;

import com.juicyslew.moonstation14.ms14.character.AngularRecoveryLook;
import com.juicyslew.moonstation14.ms14.character.AngularRecoveryModel;

import java.util.Optional;

/** Pure stable-world look bookkeeping for angular-recovery comfort presentation. */
public final class AngularRecoveryComfortCameraPolicy {
    public record Angles(float yaw, float pitch) { }

    private Angles lastRaw;
    private Angles world;

    public Optional<Angles> worldAngles() { return Optional.ofNullable(world); }
    public boolean active() { return world != null; }

    public void start(AngularRecoveryModel.State state, float rawYaw, float rawPitch) {
        Angles raw = checked(rawYaw, rawPitch);
        AngularRecoveryModel.Vec3 forward = AngularRecoveryLook.worldLook(state, raw.yaw(), raw.pitch());
        world = fromForward(forward);
        lastRaw = raw;
    }

    /** Tracks only user input delta; body rotation changes cannot drag the comfort view. */
    public void update(float rawYaw, float rawPitch) {
        if (!active()) return;
        Angles raw = checked(rawYaw, rawPitch);
        world = new Angles(wrap(world.yaw() + wrap(raw.yaw() - lastRaw.yaw())),
                clampPitch(world.pitch() + raw.pitch() - lastRaw.pitch()));
        lastRaw = raw;
    }

    /** Converts stable world look back to the current body's raw look for normal-camera handoff. */
    public Optional<Angles> finish(AngularRecoveryModel.State currentState) {
        if (!active()) return Optional.empty();
        var worldForward = AngularRecoveryLook.worldLook(null, world.yaw(), world.pitch());
        AngularRecoveryModel.Vec3 rawForward = worldForward;
        if (currentState != null) {
            var q = currentState.orientation();
            var v = new org.joml.Vector3d(worldForward.x(), worldForward.y(), worldForward.z());
            new org.joml.Quaterniond(q.x(), q.y(), q.z(), q.w()).normalize().conjugate().transform(v);
            rawForward = new AngularRecoveryModel.Vec3(v.x, v.y, v.z);
        }
        Angles result = fromForward(rawForward);
        clear();
        return Optional.of(result);
    }

    public void clear() { lastRaw = null; world = null; }

    private static Angles checked(float yaw, float pitch) {
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) throw new IllegalArgumentException("non-finite look");
        return new Angles(wrap(yaw), clampPitch(pitch));
    }

    private static Angles fromForward(AngularRecoveryModel.Vec3 f) {
        var n = f.normalized();
        double yaw = Math.toDegrees(Math.atan2(-n.x(), n.z()));
        double pitch = -Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, n.y()))));
        return new Angles(wrap((float) yaw), clampPitch((float) pitch));
    }

    private static float wrap(float degrees) {
        float value = degrees % 360f;
        if (value > 180f) value -= 360f;
        if (value < -180f) value += 360f;
        return value;
    }
    private static float clampPitch(float pitch) { return Math.max(-90f, Math.min(90f, pitch)); }
}
