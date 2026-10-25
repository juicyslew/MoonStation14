package com.juicyslew.moonstation14.ms14.explosion.grenade.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.common.util.FakePlayer;

/** Shared bounded positional sound routing using each connected player's active camera. */
public final class ControlledSoundDispatch {
    public static final double RADIUS = 32;
    private ControlledSoundDispatch() { }
    public static void emit(ServerLevel level, ResourceLocation sound, double x, double y, double z) {
        emit(level, sound, x, y, z, 1f);
    }
    public static void emit(ServerLevel level, ResourceLocation sound, double x, double y, double z, float volume) {
        emit(level, sound, x, y, z, volume, 1f);
    }
    public static void emit(ServerLevel level, ResourceLocation sound, double x, double y, double z, float volume, float pitch) {
        emit(level, sound, x, y, z, volume, pitch, SoundSource.PLAYERS);
    }
    public static void emit(ServerLevel level, ResourceLocation sound, double x, double y, double z, float volume, float pitch, SoundSource category) {
        if (level == null || sound == null || level.getServer() == null) return;
        var payload = new ControlledSoundPayload(level.dimension().location(), sound, x, y, z, volume, pitch, category);
        var players = level.getServer().getPlayerList();
        for (ServerPlayer player : players.getPlayers()) {
            if (player instanceof FakePlayer || player.isRemoved() || players.getPlayer(player.getUUID()) != player
                    || player.level() != level) continue;
            var camera = player.getCamera();
            if (camera == null || camera.isRemoved() || camera.level() != level
                    || !withinRange(x, y, z, camera.getX(), camera.getY(), camera.getZ())) continue;
            PacketDistributor.sendToPlayer(player, payload);
        }
    }
    public static boolean withinRange(double x, double y, double z, double listenerX, double listenerY, double listenerZ) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Double.isFinite(listenerX) || !Double.isFinite(listenerY) || !Double.isFinite(listenerZ)) return false;
        double dx = x - listenerX, dy = y - listenerY, dz = z - listenerZ;
        return dx * dx + dy * dy + dz * dz <= RADIUS * RADIUS;
    }
}
