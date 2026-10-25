package com.juicyslew.moonstation14.ms14.character;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Renderer-only policy; logical pose and movement remain owned by their existing systems. */
public final class KnockdownRenderPolicy {
    private KnockdownRenderPolicy() { }

    /** Production renderer adapter, intentionally querying the synchronized authoritative status. */
    public static boolean shouldRenderProne(LivingEntity entity) {
        return shouldRenderProne(entity, CharacterControlSystem.isKnockedDown(entity));
    }

    public static boolean shouldRenderProne(LivingEntity entity, boolean knockedDown) {
        if (!(entity instanceof Player player)) return false;
        if (isUpsideDownName(player)) return false;
        return shouldRenderProne(knockedDown, player.isDeadOrDying(), player.isSleeping(), player.isPassenger(),
                player.isSpectator(), player.getAbilities().flying || player.isFallFlying(),
                player.isSwimming() || player.isInWater(), player.onClimbable());
    }

    private static boolean isUpsideDownName(Player player) {
        return isUpsideDownName(player.hasCustomName() ? player.getCustomName().getString() : null);
    }

    static boolean isUpsideDownName(String name) {
        if (name == null) return false;
        return name.equalsIgnoreCase("Dinnerbone") || name.equalsIgnoreCase("Grumm");
    }

    static boolean shouldRenderProne(boolean knockedDown, boolean dead, boolean sleeping, boolean passenger,
                                    boolean spectator, boolean flying, boolean swimming, boolean climbing) {
        return knockedDown && !dead && !sleeping && !passenger && !spectator && !flying && !swimming && !climbing;
    }
}

