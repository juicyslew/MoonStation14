package com.juicyslew.moonstation14.ms14.combat.client;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Client-owned camera recoil presentation state; it never changes body aim or movement. */
public final class GunCameraRecoilClient {
    private static GunCameraRecoilPolicy.State state = GunCameraRecoilPolicy.State.ZERO;
    private static UUID body;
    private static long epoch;
    private static ResourceLocation dimension;
    private static long lastNanos;
    private GunCameraRecoilClient() { }

    private static boolean bind(UUID id, long bodyEpoch, ResourceLocation world) {
        if (id == null || bodyEpoch <= 0 || world == null) { clear(); return false; }
        if (!id.equals(body) || epoch != bodyEpoch || !world.equals(dimension)) {
            state = GunCameraRecoilPolicy.State.ZERO;
            lastNanos = 0;
            body = id; epoch = bodyEpoch; dimension = world;
        }
        return true;
    }
    public static void admittedShot(UUID id, long bodyEpoch, ResourceLocation world, Vec3 look, float scalar) {
        if (!bind(id, bodyEpoch, world) || look == null) return;
        state = GunCameraRecoilPolicy.kick(state, 0, 0, 0, look.x, look.y, look.z, scalar, 1);
    }
    /** Called once after native camera setup; returned vector is an additive eye-position translation. */
    public static Vec3 renderOffset(UUID id, long bodyEpoch, ResourceLocation world, long nowNanos) {
        if (!bind(id, bodyEpoch, world)) return Vec3.ZERO;
        double dt = lastNanos == 0 ? 0 : Math.max(0, Math.min(.1, (nowNanos - lastNanos) / 1_000_000_000d));
        lastNanos = nowNanos;
        state = GunCameraRecoilPolicy.advance(state, dt);
        var kick = state.kick();
        return new Vec3(kick.x(), kick.y(), kick.z());
    }
    public static void clear() { state = GunCameraRecoilPolicy.State.ZERO; body = null; epoch = 0; dimension = null; lastNanos = 0; }
}
