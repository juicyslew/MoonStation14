package com.juicyslew.moonstation14.ms14.actions;

import com.juicyslew.moonstation14.ms14.actions.prototype.ActionHandler;
import com.juicyslew.moonstation14.ms14.actions.prototype.ActionPrototype;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.components.VocalComponent;
import com.juicyslew.moonstation14.ms14.effect.EmoteEffect;
import com.juicyslew.moonstation14.ms14.effect.EffectResult;
import com.juicyslew.moonstation14.ms14.effect.VocalScreamDelivery;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.Optional;

/** Prepares supported body action effects before the caller commits cooldown state. */
public final class BodyActionEffects {
    private BodyActionEffects() { }

    public static Optional<PreparedEffect> prepare(ServerPlayer player, LivingEntity body, ActionPrototype action) {
        if (player == null || body == null || action == null || !(body.level() instanceof ServerLevel level))
            return Optional.empty();
        return switch (action.handler()) {
            case COMBAT_TOGGLE -> CombatModeSystem.capability(body).isPresent()
                    ? Optional.of(() -> {
                        CombatModeSystem.ToggleResult result = CombatModeSystem.toggle(body);
                        if (!result.success()) return false;
                        player.sendSystemMessage(Component.translatable(result.enabled()
                                ? "moonstation14.combat.enabled" : "moonstation14.combat.disabled"));
                        return true;
                    }) : Optional.empty();
            case SCREAM -> CharacterIdentitySystem.resolveForActor(body)
                    .flatMap(data -> data.component(VocalComponent.class))
                    .filter(vocal -> EmoteEffect.plan(vocal.screamEmote(), true, body) != null)
                    .map(vocal -> new PreparedScream(vocal, VocalScreamDelivery.resolve(vocal, level.getRandom().nextDouble())))
                    .filter(prepared -> prepared.event() != null)
                    .map(prepared -> (PreparedEffect) () -> EmoteEffect.applyVocalScream(prepared.vocal(), prepared.event(),
                            body, level, EmoteEffect.SERVER) == EffectResult.APPLIED);
            case DEBUG_PULSE -> Optional.of(() -> {
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, body.getX(), body.getY() + body.getBbHeight() * 0.5,
                        body.getZ(), 6, 0.25, 0.25, 0.25, 0.0);
                player.sendSystemMessage(Component.translatable("moonstation14.action.debug_pulse"));
                return true;
            });
        };
    }

    @FunctionalInterface
    public interface PreparedEffect {
        boolean apply();
    }

    private record PreparedScream(VocalComponent vocal, net.minecraft.sounds.SoundEvent event) { }
}
