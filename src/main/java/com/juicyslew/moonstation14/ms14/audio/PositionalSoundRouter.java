package com.juicyslew.moonstation14.ms14.audio;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;

/** Shared body-view-routed positional sound packet owner. */
public final class PositionalSoundRouter {
    private PositionalSoundRouter() { }
    public static void play(ServerLevel level, ResourceLocation sound, SoundSource source,
                            double x, double y, double z, float volume, float pitch) {
        play(level, sound, source, x, y, z, volume, pitch, null);
    }
    public static void play(ServerLevel level, ResourceLocation sound, SoundSource source,
                            double x, double y, double z, float volume, float pitch, java.util.UUID excludedPlayer) {
        var event = BuiltInRegistries.SOUND_EVENT.getOptional(sound).orElse(null);
        if (event == null) return;
        var packet = new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(event), source,
                x, y, z, volume, pitch, level.getRandom().nextLong());
        double range = event.getRange(volume);
        for (var player : level.getServer().getPlayerList().getPlayers())
            if ((excludedPlayer == null || !excludedPlayer.equals(player.getUUID()))
                    && BodyViewAudioAudience.canHear(level, player, x, y, z, range)) player.connection.send(packet);
    }
}
