package com.juicyslew.moonstation14.ms14.medical;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** Small production policies for physical-root validation and paused window readings. */
final class HealthAnalyzerLifecycle {
    private HealthAnalyzerLifecycle() { }

    static Entity loadedRoot(ServerLevel level, UUID exactId) {
        Entity entity = exactId == null ? null : level.getEntity(exactId);
        return entity != null && exactRoot(exactId, entity.getUUID()) && !entity.isRemoved() && entity.isAddedToLevel()
                && entity.level() == level && level.getEntity(exactId) == entity ? entity : null;
    }

    static boolean exactRoot(UUID expected, UUID resolved) {
        return expected != null && expected.equals(resolved);
    }

    record RootSelection(UUID entity, boolean worldItem) { }

    static RootSelection transferRoot(UUID controlledBodyId, UUID worldItemId, boolean drop, boolean validWorldItem) {
        if (drop) return validWorldItem && worldItemId != null ? new RootSelection(worldItemId, true) : null;
        return controlledBodyId == null ? null : new RootSelection(controlledBodyId, false);
    }

    static boolean inUiRange(Vec3 controlledBody, Vec3 root, double resolvedRange) {
        return inResolvedRange(controlledBody, root, resolvedRange);
    }

    static boolean inResolvedRange(Vec3 origin, Vec3 target, double resolvedRange) {
        return Double.isFinite(resolvedRange) && resolvedRange > 0
                && origin.distanceToSqr(target) <= resolvedRange * resolvedRange;
    }

    static HealthAnalyzerSnapshot paused(HealthAnalyzerSnapshot last, long frame, boolean active, boolean inRange) {
        if (last == null) return null;
        return new HealthAnalyzerSnapshot(last.scannerId(), last.epoch(), last.actorId(), last.targetEntityId(),
                last.targetId(), frame, last.name(), last.species(), last.state(), active, inRange,
                last.temperatureKelvin(), last.usableBloodFraction(), last.bleeding(), last.unrevivable(),
                last.totalDamage(), last.groups(), last.damage());
    }
}
