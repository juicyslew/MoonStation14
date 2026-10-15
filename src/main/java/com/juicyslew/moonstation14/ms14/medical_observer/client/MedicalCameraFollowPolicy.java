package com.juicyslew.moonstation14.ms14.medical_observer.client;

import net.minecraft.world.phys.Vec3;

/** Position samples for native camera interpolation between body snapshots. */
record MedicalCameraFollowPolicy(Vec3 previous, Vec3 current) {
    Vec3 positionAt(float partialTick) {
        return previous.lerp(current, partialTick);
    }
}

