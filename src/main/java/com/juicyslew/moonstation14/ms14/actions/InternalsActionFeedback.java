package com.juicyslew.moonstation14.ms14.actions;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;

/** Rare threshold-edge feedback for the currently controlled body; never runs per inhale absent an edge. */
public final class InternalsActionFeedback {
    private InternalsActionFeedback() { }
    public static void notifyController(LivingEntity body, String translationKey) {
        if (body == null || translationKey == null || !(body.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
        for (var player : level.players()) {
            var authority = com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionAuthority.resolve(player).orElse(null);
            if (authority != null && authority.body() == body) {
                player.sendSystemMessage(Component.translatable(translationKey));
                return;
            }
        }
    }
}
