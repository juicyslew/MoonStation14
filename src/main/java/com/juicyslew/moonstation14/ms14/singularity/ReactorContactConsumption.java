package com.juicyslew.moonstation14.ms14.singularity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Shared native-death admission for entities consumed by singularity-like contact sources. */
public final class ReactorContactConsumption {
    private ReactorContactConsumption() { }

    public static boolean eligible(LivingEntity body) {
        return body != null && body.isAlive() && !body.isSpectator() && !body.noPhysics && body.isAddedToLevel()
                && !(body instanceof net.minecraft.server.level.ServerPlayer player
                    && com.juicyslew.moonstation14.ms14.player_body_control.server.ActiveCharacterPolicy.isCarrier(player));
    }

    /** Zero health and dispatch native death; true only when the native death-state owner admitted it. */
    public static boolean kill(ServerLevel server, LivingEntity body) {
        if (server == null || !eligible(body)) return false;
        body.setHealth(0);
        body.die(body.damageSources().genericKill());
        return body instanceof com.juicyslew.moonstation14.mixin.LivingEntityDeathStateAccessor state && state.ms14$isDead();
    }

    public static boolean touches(Vec3 center, double radius, LivingEntity body) {
        return eligible(body) && SingularityHorizonGeometry.touches(center, radius, body.getBoundingBox());
    }
}
