package com.juicyslew.moonstation14.ms14.hands.client;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;

/** Shared visual treatment and foreground priority for body targets. */
final class OutlinePriority {
    static final float RED = .82f, GREEN = .98f, BLUE = 1.0f, ALPHA = .95f;

    private OutlinePriority() { }

    static boolean itemPrecedesBlock(AABB itemBounds, Vec3 start, Vec3 end, Vec3 blockHit) {
        if (itemBounds == null || start == null || end == null || blockHit == null) return false;
        Vec3 expandedEntry = LooseItemRayGeometry.entry(itemBounds, start, end);
        if (expandedEntry == null) return false;
        double blockDistance = blockHit.distanceToSqr(start);
        return expandedEntry.distanceToSqr(start) < blockDistance
                && LooseItemRayGeometry.physicalNearFace(itemBounds, start, end.subtract(start).normalize()) < Math.sqrt(blockDistance);
    }

    static boolean showItem(AABB itemBounds, Vec3 start, Vec3 end, HitResult blockHit) {
        return blockHit == null || blockHit.getType() != HitResult.Type.BLOCK
                || itemPrecedesBlock(itemBounds, start, end, blockHit.getLocation());
    }
}

