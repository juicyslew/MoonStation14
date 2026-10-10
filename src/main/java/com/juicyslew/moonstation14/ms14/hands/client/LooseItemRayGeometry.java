package com.juicyslew.moonstation14.ms14.hands.client;

import com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Shared loose-item nomination ray geometry; presentation retains physical entity bounds. */
final class LooseItemRayGeometry {
    private LooseItemRayGeometry() { }

    static AABB expandedBounds(AABB physicalBounds) {
        return physicalBounds.inflate(BodyItemTargetProbe.TARGET_MARGIN);
    }

    static Vec3 entry(AABB physicalBounds, Vec3 start, Vec3 end) {
        AABB expanded = expandedBounds(physicalBounds);
        return expanded.contains(start) ? start : expanded.clip(start, end).orElse(null);
    }

    static double physicalNearFace(AABB box, Vec3 start, Vec3 look) {
        return (look.x >= 0 ? box.minX : box.maxX) * look.x
                + (look.y >= 0 ? box.minY : box.maxY) * look.y
                + (look.z >= 0 ? box.minZ : box.maxZ) * look.z - start.dot(look);
    }
}
