package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Screen-plane rotation for the 3D billboard adaptation of SS14's velocity-oriented sprite. */
public final class ProjectileSpritePose {
    private ProjectileSpritePose() {}
    public static float rotation(Vec3 velocity, Quaternionf cameraOrientation, boolean noRot) {
        if (noRot || velocity == null || cameraOrientation == null || !Double.isFinite(velocity.x) || !Double.isFinite(velocity.y) || !Double.isFinite(velocity.z)) return 0f;
        Vector3f view = new Vector3f((float) velocity.x, (float) velocity.y, (float) velocity.z);
        cameraOrientation.transformInverse(view);
        float x = view.x, y = view.y;
        if (!Float.isFinite(x) || !Float.isFinite(y) || x*x + y*y < 1.0e-12f) return 0f;
        // Source bullet art points up; align that axis to projected travel direction.
        return (float) Math.atan2(y, x) - (float) (Math.PI * 0.5);
    }
}
