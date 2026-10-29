package com.juicyslew.moonstation14.ms14.effect;

import com.juicyslew.moonstation14.component.codec.json.EmoteRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import com.juicyslew.moonstation14.ms14.character.components.VocalComponent;
import com.juicyslew.moonstation14.ms14.character.CharacterPublicName;

import java.util.Objects;

/** Plans and delivers only closed-registry character emotes. */
public final class EmoteEffect {
    private EmoteEffect() {
    }

    public static EffectResult apply(String id, boolean showInChat, Entity target, ServerLevel level,
                                     EmoteDelivery delivery) {
        Objects.requireNonNull(delivery, "delivery");
        Plan plan = plan(id, showInChat, target);
        if (plan == null) return EffectResult.SKIPPED_UNSUPPORTED;
        delivery.sound(level, target, plan.sound(), plan.pitchVariation());
        if (plan.chat() != null) delivery.chat(level, target, plan.chat());
        return EffectResult.APPLIED;
    }

    public static Plan plan(String id, boolean showInChat, Entity target) {
        if (!(target instanceof LivingEntity)) return null;
        return EmoteRegistry.find(id)
                .map(spec -> new Plan(spec.sound(), spec.pitchVariation(), showInChat
                        ? Component.translatable("moonstation14.emote." + id, CharacterPublicName.component(target))
                        : null))
                .orElse(null);
    }

    public static EffectResult applyVocalScream(VocalComponent vocal, Entity target, ServerLevel level,
                                                 EmoteDelivery delivery, double selectionRandom) {
        Objects.requireNonNull(delivery, "delivery");
        if (vocal == null) return EffectResult.SKIPPED_UNSUPPORTED;
        var event = VocalScreamDelivery.resolve(vocal, selectionRandom);
        return applyVocalScream(vocal, event, target, level, delivery);
    }

    public static EffectResult applyVocalScream(VocalComponent vocal, net.minecraft.sounds.SoundEvent event,
                                                 Entity target, ServerLevel level, EmoteDelivery delivery) {
        Objects.requireNonNull(delivery, "delivery");
        if (vocal == null || event == null) return EffectResult.SKIPPED_UNSUPPORTED;
        EmoteDelivery vocalDelivery = new EmoteDelivery() {
            @Override public void sound(ServerLevel l, Entity source, String id) { sound(l, source, id, vocal.screamPitchVariation()); }
            @Override public void sound(ServerLevel l, Entity source, String id, float variation) {
                float bounded = Math.max(0f, Math.min(1f, variation));
                float pitch = 1f + (l.getRandom().nextFloat() * 2f - 1f) * bounded;
                com.juicyslew.moonstation14.ms14.explosion.grenade.network.ControlledSoundDispatch.emit(
                        l, net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getKey(event),
                        source.getX(), source.getY(), source.getZ(), 1f, pitch, SoundSource.VOICE);
            }
            @Override public void chat(ServerLevel l, Entity source, Component line) { delivery.chat(l, source, line); }
        };
        return apply(vocal.screamEmote(), true, target, level, vocalDelivery);
    }

    public record Plan(String sound, float pitchVariation, Component chat) { }

    public interface EmoteDelivery {
        void sound(ServerLevel level, Entity source, String soundId);
        default void sound(ServerLevel level, Entity source, String soundId, float pitchVariation) {
            sound(level, source, soundId);
        }
        void chat(ServerLevel level, Entity source, Component line);
    }

    public static final EmoteDelivery SERVER = new EmoteDelivery() {
        @Override
        public void sound(ServerLevel level, Entity source, String id) {
            sound(level, source, id, 0f);
        }

        @Override
        public void sound(ServerLevel level, Entity source, String id, float pitchVariation) {
            EmoteRegistry.ids().stream().map(EmoteRegistry::find).flatMap(java.util.Optional::stream)
                    .filter(spec -> spec.sound().equals(id)).findFirst().ifPresent(spec -> {
                float variation = Math.max(0f, Math.min(1f, pitchVariation));
                float pitch = 1f + (level.getRandom().nextFloat() * 2f - 1f) * variation;
                level.playSound(null, source,
                        com.juicyslew.moonstation14.sounds.ModSounds.emote(spec.sound()).get(),
                        SoundSource.VOICE, 1f, pitch);
            });
        }

        @Override
        public void chat(ServerLevel level, Entity source, Component line) {
            level.getChunkSource().broadcastAndSend(source,
                    new ClientboundSystemChatPacket(line, false));
        }
    };
}
