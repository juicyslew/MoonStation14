package com.juicyslew.moonstation14.ms14.examine;

import net.minecraft.world.phys.AABB;
import org.joml.Matrix4dc;

/** Pure eight-corner AABB transform with no Sable pose type in its API. */
public final class FrameBoundsTransform {
    private FrameBoundsTransform() { }

    public static AABB transformedBounds(AABB local, Matrix4dc matrix) {
        if (local == null || matrix == null) return null;
        AABB result = null;
        for (int mask = 0; mask < 8; mask++) {
            var point = matrix.transformPosition(new org.joml.Vector3d(
                    (mask & 1) == 0 ? local.minX : local.maxX,
                    (mask & 2) == 0 ? local.minY : local.maxY,
                    (mask & 4) == 0 ? local.minZ : local.maxZ));
            AABB corner = new AABB(point.x, point.y, point.z, point.x, point.y, point.z);
            result = result == null ? corner : result.minmax(corner);
        }
        return result;
    }
}
