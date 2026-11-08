package com.juicyslew.moonstation14.ms14.audio;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;

/** Routes positional audio according to the committed entity the player is viewing. */
public final class BodyViewAudioAudience {
    private BodyViewAudioAudience() { }

    public static boolean canHear(ServerLevel level, ServerPlayer player,
                                  double x, double y, double z, double range) {
        if (level == null || player == null || player instanceof FakePlayer || player.isRemoved()
                || level.getServer() == null || player.level() != level
                || level.getServer().getPlayerList().getPlayer(player.getUUID()) != player
                || !Double.isFinite(range) || range < 0) return false;
        var camera = player.getCamera();
        return camera != null && !camera.isRemoved() && camera.level() == level
                && withinRange(x, y, z, camera.getX(), camera.getY(), camera.getZ(), range);
    }

    public static boolean withinRange(double x, double y, double z,
                                      double listenerX, double listenerY, double listenerZ, double range) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Double.isFinite(listenerX) || !Double.isFinite(listenerY) || !Double.isFinite(listenerZ)
                || !Double.isFinite(range) || range < 0) return false;
        double dx = x - listenerX, dy = y - listenerY, dz = z - listenerZ;
        return dx * dx + dy * dy + dz * dz <= range * range;
    }
}
