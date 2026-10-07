package com.juicyslew.moonstation14.ms14.standing;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Entity-root-centred geometry shared by prone rendering, culling and interaction targeting. */
public final class PronePoseGeometry {
    public static final double NATIVE_HALF_WIDTH = 6.0 / 16.0;
    public static final double NATIVE_HEAD_Y = -8.0 / 16.0;
    public static final double NATIVE_FOOT_Y = 24.0 / 16.0;
    public static final double NATIVE_HALF_DEPTH = 4.0 / 16.0;
    private PronePoseGeometry() { }

    /** Native renderer scales for the project's concrete humanoid renderer paths. */
    public static double modelScale(net.minecraft.world.entity.LivingEntity entity) {
        return entity instanceof net.minecraft.world.entity.player.Player ? 0.9375 : 1.0;
    }

    public static Vec3 transform(Vec3 entityRoot, Vec3 uprightOffset, double standingHeight,
                                 float yaw, boolean faceUp) {
        // Compatibility coordinates are feet-origin offsets, not native model
        // coordinates (whose feet are at Y=1.5 and head extends above Y=0).
        double nativeCenterY = (NATIVE_HEAD_Y + NATIVE_FOOT_Y) * 0.5;
        return transform(entityRoot, new Vec3(uprightOffset.x,
                nativeCenterY + (standingHeight * 0.5 - uprightOffset.y),
                uprightOffset.z), 1.0, 1.0, yaw, faceUp);
    }

    public static Vec3 transform(Vec3 entityRoot, Vec3 nativeModelPoint, double modelScale,
                                 double entityScale, float yaw, boolean faceUp) {
        // Match setupRotations, the common root correction, and vanilla's native
        // humanoid model stack in their actual multiplication order.
        Matrix4f matrix = new Matrix4f()
                .translate((float)entityRoot.x, (float)entityRoot.y, (float)entityRoot.z)
                .scale((float)entityScale)
                .rotateY((float)Math.toRadians(180.0 - yaw))
                .translate(0, 2.0F / 16.0F * (float)modelScale, 0)
                .rotateX((float)Math.toRadians(PronePoseMath.PITCH_DEGREES))
                .rotateY(faceUp ? (float)Math.PI : 0)
                .translate(0, PronePoseMath.nativeRootCenterOffset((float)modelScale), 0)
                .scale(-1, -1, 1).scale((float)modelScale)
                .translate(0, -1.501F, 0);
        Vector3f point = matrix.transformPosition(new Vector3f((float)nativeModelPoint.x,
                (float)nativeModelPoint.y, (float)nativeModelPoint.z));
        return new Vec3(point.x, point.y, point.z);
    }

    public static Vec3 transformCorner(Vec3 entityRoot, double width, double height,
                                       float yaw, boolean faceUp, double xSign,
                                       double ySign, double zSign) {
        return transform(entityRoot, new Vec3(xSign * NATIVE_HALF_WIDTH,
                ySign < 0 ? NATIVE_HEAD_Y : NATIVE_FOOT_Y,
                zSign * NATIVE_HALF_DEPTH), 1.0, 1.0, yaw, faceUp);
    }

    public static Vec3 transformScaledCorner(Vec3 entityRoot, double modelScale, double entityScale,
                                       float yaw, boolean faceUp, double xSign,
                                       double ySign, double zSign) {
        return transform(entityRoot, new Vec3(xSign * NATIVE_HALF_WIDTH,
                ySign < 0 ? NATIVE_HEAD_Y : NATIVE_FOOT_Y,
                zSign * NATIVE_HALF_DEPTH), modelScale, entityScale, yaw, faceUp);
    }
}
