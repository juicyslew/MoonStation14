package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** Canonical passive-presentation participant lookup for controlled bodies. */
public final class CombatPresentationAudience {
    private CombatPresentationAudience() { }
    public static boolean controlsParticipant(ServerPlayer player, LivingEntity first, LivingEntity second) {
        if (player == null) return false;
        var body = com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.LifecycleCharacterSessionControl.activeCharacterBody(player)
                .<LivingEntity>map(com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.LifecycleCharacterSessionControl.ActiveCharacterBody::body)
                .or(() -> com.juicyslew.moonstation14.ms14.player_body_control.server.GhostMobHarnessControl.activeHarness(player)
                        .<LivingEntity>map(com.juicyslew.moonstation14.ms14.player_body_control.server.GhostMobHarnessControl.ActiveHarness::entity));
        return body.filter(controlled -> first != null && controlled.getUUID().equals(first.getUUID())
                || second != null && controlled.getUUID().equals(second.getUUID())).isPresent();
    }
}
