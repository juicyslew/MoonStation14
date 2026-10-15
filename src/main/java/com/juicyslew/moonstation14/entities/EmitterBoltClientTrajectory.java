package com.juicyslew.moonstation14.entities;

import net.minecraft.world.phys.Vec3;

/** Small client-only motion policy: tracker positions rebase prediction; no second physics simulation. */
public final class EmitterBoltClientTrajectory {
    private EmitterBoltClientTrajectory() { }

    public static Vec3 advance(Vec3 position, Vec3 velocity, boolean removed) {
        return removed ? position : position.add(velocity);
    }

    public static Vec3 partial(Vec3 oldPosition, Vec3 currentPosition, float partialTick) {
        return oldPosition.lerp(currentPosition, Math.max(0, Math.min(1, partialTick)));
    }
}
