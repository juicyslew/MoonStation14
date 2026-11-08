package com.juicyslew.moonstation14.ms14.audio;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

/** Sends a sound attached to its authoritative entity, while retaining body-view audience routing. */
public final class EntityAttachedSoundRouter {
    private EntityAttachedSoundRouter() { }

    public static void play(ServerLevel level, Entity source, ResourceLocation sound, SoundSource category,
                            float volume, float pitch) {
        if (level == null || source == null || sound == null || source.isRemoved() || source.level() != level
                || level.getServer() == null) return;
        var event = BuiltInRegistries.SOUND_EVENT.getOptional(sound).orElse(null);
        if (event == null) return;
        var packet = new ClientboundSoundEntityPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(event), category,
                source, volume, pitch, level.getRandom().nextLong());
        double range = event.getRange(volume);
        for (var player : level.getServer().getPlayerList().getPlayers())
            if (BodyViewAudioAudience.canHear(level, player, source.getX(), source.getY(), source.getZ(), range))
                player.connection.send(packet);
    }
}
