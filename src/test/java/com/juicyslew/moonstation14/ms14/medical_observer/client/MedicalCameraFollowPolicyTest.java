package com.juicyslew.moonstation14.ms14.medical_observer.client;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MedicalCameraFollowPolicyTest {
    @Test
    void interpolatesBodyEyeSnapshotsIncludingVerticalMotion() {
        MedicalCameraFollowPolicy samples = new MedicalCameraFollowPolicy(
                new Vec3(1, 2, 3), new Vec3(5, -2, 7));

        assertEquals(new Vec3(1, 2, 3), samples.positionAt(0.0F));
        assertEquals(new Vec3(3, 0, 5), samples.positionAt(0.5F));
        assertEquals(new Vec3(5, -2, 7), samples.positionAt(1.0F));
        assertEquals(new Vec3(2, 3, 4), new MedicalCameraFollowPolicy(
                new Vec3(2, 3, 4), new Vec3(2, 3, 4)).positionAt(0.5F));
    }
}
