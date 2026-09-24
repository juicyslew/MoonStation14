package com.juicyslew.moonstation14.ms14.effect;

import com.juicyslew.moonstation14.component.codec.json.EmoteRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

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
        delivery.sound(level, target, plan.sound());
        if (plan.chat() != null) delivery.chat(level, target, plan.chat());
        return EffectResult.APPLIED;
    }

    public static Plan plan(String id, boolean showInChat, Entity target) {
        if (!(target instanceof LivingEntity)) return null;
        return EmoteRegistry.find(id)
                .map(spec -> new Plan(spec.sound(), showInChat
                        ? Component.translatable("moonstation14.emote." + id, target.getDisplayName())
                        : null))
                .orElse(null);
    }

    public record Plan(String sound, Component chat) { }

    public interface EmoteDelivery {
        void sound(ServerLevel level, Entity source, String soundId);
        void chat(ServerLevel level, Entity source, Component line);
    }

    static final EmoteDelivery SERVER = new EmoteDelivery() {
        @Override
        public void sound(ServerLevel level, Entity source, String id) {
            EmoteRegistry.find(id).ifPresent(spec -> level.playSound(null, source,
                    com.juicyslew.moonstation14.sounds.ModSounds.emote(spec.sound()).get(),
                    SoundSource.VOICE, 1f, 1f));
        }

        @Override
        public void chat(ServerLevel level, Entity source, Component line) {
            level.getChunkSource().broadcastAndSend(source,
                    new ClientboundSystemChatPacket(line, false));
        }
    };
}
