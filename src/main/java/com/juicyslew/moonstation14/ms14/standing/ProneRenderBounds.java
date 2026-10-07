package com.juicyslew.moonstation14.ms14.standing;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Renderer-only culling bounds from the shared lying-model basis; physical bounds are untouched. */
public final class ProneRenderBounds {
    private static final double INTERPOLATION_MARGIN = 0.5;
    private ProneRenderBounds() { }

    public static AABB calculate(AABB original, Vec3 position, double width, double height,
                                 float bodyYaw, boolean faceUp) {
        return calculate(original, position, width, height, 1, 1, bodyYaw, faceUp);
    }

    public static AABB calculate(AABB original, Vec3 position, double width, double height,
                                 double modelScale, double entityScale, float bodyYaw, boolean faceUp) {
        if (original == null || position == null || !Double.isFinite(width) || !Double.isFinite(height)
                || width <= 0 || height <= 0 || !Float.isFinite(bodyYaw)) return original;
        AABB result = original;
        double half = width * .5;
        for (double x : new double[]{-half, half})
            for (double y : new double[]{0, height})
                for (double z : new double[]{-half, half}) {
                    Vec3 point = PronePoseGeometry.transformScaledCorner(position, modelScale, entityScale,
                            bodyYaw, faceUp, x / half, y == 0 ? -1 : 1, z / half);
                    result = result.minmax(new AABB(point, point));
                }
        // Entity YRot is not necessarily the renderer's interpolated body yaw (notably while
        // the head tracks independently). Bound the full yaw sweep by the shared pose's radial
        // extent instead of guessing which yaw the renderer will use this frame.
        double nativeScale = modelScale * entityScale;
        double radius = Math.hypot(PronePoseGeometry.NATIVE_HALF_WIDTH,
                1.0 + PronePoseGeometry.NATIVE_HALF_DEPTH) * nativeScale + INTERPOLATION_MARGIN;
        double verticalRadius = (PronePoseGeometry.NATIVE_HALF_WIDTH
                + PronePoseMath.TORSO_OUTER_DEPTH_PIXELS / 16.0) * nativeScale + INTERPOLATION_MARGIN;
        double centerY = position.y + PronePoseMath.pivotOffset(width).y;
        AABB yawIndependent = new AABB(position.x - radius, centerY - verticalRadius, position.z - radius,
                position.x + radius, centerY + verticalRadius, position.z + radius);
        result = result.minmax(yawIndependent);
        return result;
    }

    static Vec3 transformCorner(Vec3 position, double width, double height, float bodyYaw,
                                boolean faceUp, double xSign, double ySign, double zSign) {
        return PronePoseGeometry.transformCorner(position, width, height, bodyYaw, faceUp,
                xSign, ySign, zSign);
    }
}
