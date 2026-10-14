package com.juicyslew.moonstation14.ms14.medical_observer;

import net.minecraft.world.entity.LivingEntity;

/** Applies observer yaw/pitch to every native rotation consumed by living-entity camera rendering. */
public final class ObserverGhostLook {
    private ObserverGhostLook() { }

    public static void apply(LivingEntity entity, float yaw, float pitch) {
        entity.setYRot(yaw);
        entity.setXRot(pitch);
        entity.yRotO = yaw;
        entity.xRotO = pitch;
        entity.yHeadRot = yaw;
        entity.yHeadRotO = yaw;
        entity.yBodyRot = yaw;
        entity.yBodyRotO = yaw;
    }
}

