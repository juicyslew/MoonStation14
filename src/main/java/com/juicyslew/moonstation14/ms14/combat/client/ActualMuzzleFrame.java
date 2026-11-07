package com.juicyslew.moonstation14.ms14.combat.client;

import net.minecraft.world.phys.Vec3;

/** A render-frame-only world-space gun frame captured from the native item display transform. */
public record ActualMuzzleFrame(Vec3 muzzle, Vec3 bore, Vec3 up, long frame) {
    public static ActualMuzzleFrame capture(org.joml.Matrix4f cameraRelativeModel,
            GunPresentationBindings.Binding binding, Vec3 cameraWorld, long frame) {
        var p = cameraRelativeModel.transformPosition(new org.joml.Vector3f((float)binding.muzzle().x(),(float)binding.muzzle().y(),(float)binding.muzzle().z()));
        var b = cameraRelativeModel.transformDirection(new org.joml.Vector3f((float)binding.bore().x(),(float)binding.bore().y(),(float)binding.bore().z())).normalize();
        var u = cameraRelativeModel.transformDirection(new org.joml.Vector3f((float)binding.up().x(),(float)binding.up().y(),(float)binding.up().z())).normalize();
        return new ActualMuzzleFrame(cameraWorld.add(p.x,p.y,p.z),new Vec3(b.x,b.y,b.z),new Vec3(u.x,u.y,u.z),frame);
    }
}
