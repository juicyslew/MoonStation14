package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.ms14.player_body_control.server.ActiveCharacterPolicy;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Shared damageable-target filter for client nomination and server admission. */
public final class CommonMeleeTargetEligibility {
    private CommonMeleeTargetEligibility() { }

    public static boolean damageable(Entity entity) {
        boolean player = entity instanceof Player;
        boolean living = entity instanceof LivingEntity;
        boolean looseItem = entity instanceof LooseItemEntity;
        boolean itemDamage = looseItem && ((LooseItemEntity) entity).profile() != null
                && ((LooseItemEntity) entity).profile().itemDamage() != null;
        return eligible(living, player && ((Player) entity).isSpectator(), player && ActiveCharacterPolicy.isCarrier(entity), itemDamage);
    }

    static boolean eligible(boolean living, boolean spectator, boolean carrier, boolean looseItemDamageable) {
        if (spectator || carrier) return false;
        return living || looseItemDamageable;
    }
}
