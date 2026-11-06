package com.juicyslew.moonstation14.ms14.player_body_control.movement;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Shared finite/bounded coordinates admission for lifecycle and debug Character snapshots. */
public final class CharacterMovementSafePose {
    private static final double WORLD_BOUND = 30_000_000.0;
    private CharacterMovementSafePose() { }

    public static boolean finiteBounded(Vec3 point) {
        return point != null && Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z)
                && Math.abs(point.x) <= WORLD_BOUND && Math.abs(point.y) <= WORLD_BOUND
                && Math.abs(point.z) <= WORLD_BOUND;
    }

    public static boolean valid(Level level, Vec3 worldPoint) {
        return level != null && finiteBounded(worldPoint)
                && level.getWorldBorder().isWithinBounds(worldPoint)
                && worldPoint.y >= level.getMinBuildHeight() && worldPoint.y < level.getMaxBuildHeight();
    }

    public static boolean snapshotAdmissible(Level level, Vec3 worldPoint, Vec3 storagePoint,
                                             boolean knownCurrentActorPoint) {
        return valid(level, worldPoint) && snapshotCoordinatesAdmissible(worldPoint, storagePoint,
                knownCurrentActorPoint);
    }

    public static boolean snapshotCoordinatesAdmissible(Vec3 worldPoint, Vec3 storagePoint,
                                                        boolean knownCurrentActorPoint) {
        return knownCurrentActorPoint && finiteBounded(worldPoint)
                && (storagePoint == null || finiteBounded(storagePoint));
    }
}
