package com.juicyslew.moonstation14.ms14.explosion.server;

import com.juicyslew.moonstation14.ms14.explosion.grenade.network.GrenadeSoundNetworking;
import com.juicyslew.moonstation14.ms14.explosion.grenade.network.GrenadeSoundPayload;
import com.juicyslew.moonstation14.ms14.explosion.grenade.network.GrenadeSoundPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.util.FakePlayer;

/** Shared best-effort legacy explosion cues; currently preserves grenade particle and audio policy. */
public final class ExplosionFeedbackService {
    private ExplosionFeedbackService() { }

    public static void emitLegacyGrenadeCues(ServerLevel level, BlockPos origin) {
        spawnVisuals(level, origin);
        sendSounds(level, origin);
    }
    public static void emitQueuedCues(ServerLevel level, BlockPos origin, int iterations, int smallThreshold) {
        if (iterations >= smallThreshold) { emitLegacyGrenadeCues(level, origin); return; }
        spawnVisuals(level, origin);
        sendSmallSounds(level, origin, iterations);
    }
    private static void sendSmallSounds(ServerLevel level, BlockPos origin, int iterations) {
        if (level.getServer() == null) return;
        double x = origin.getX() + .5, y = origin.getY() + .5, z = origin.getZ() + .5;
        var players = level.getServer().getPlayerList();
        for (ServerPlayer player : players.getPlayers()) {
            if (player instanceof FakePlayer || player.isRemoved() || players.getPlayer(player.getUUID()) != player || player.level() != level) continue;
            Entity camera = player.getCamera();
            if (camera == null || camera.isRemoved() || camera.level() != level) continue;
            double distance = camera.distanceToSqr(x, y, z);
            if (distance <= 50 * 50) sendBestEffort(() -> GrenadeSoundNetworking.send(player, GrenadeSoundPayload.smallNear(level.dimension().location(), x, y, z)));
            if (distance > 30 * 30 && distance <= Math.pow(iterations * 5.0, 2)) sendBestEffort(() -> GrenadeSoundNetworking.send(player, GrenadeSoundPayload.smallFar(level.dimension().location())));
        }
    }
    private static void sendSounds(ServerLevel level, BlockPos origin) {
        if (level.getServer() == null) return;
        double x = origin.getX() + .5, y = origin.getY() + .5, z = origin.getZ() + .5;
        var players = level.getServer().getPlayerList();
        for (ServerPlayer player : players.getPlayers()) {
            if (player instanceof FakePlayer || player.isRemoved() || players.getPlayer(player.getUUID()) != player || player.level() != level) continue;
            Entity camera = player.getCamera();
            if (camera == null || camera.isRemoved() || camera.level() != level) continue;
            var decision = GrenadeSoundPolicy.classify(level.dimension().location(), camera.level().dimension().location(),
                    x, y, z, camera.getX(), camera.getY(), camera.getZ());
            if (decision.mode() == GrenadeSoundPolicy.Mode.NONE) continue;
            sendBestEffort(() -> GrenadeSoundNetworking.send(player, decision.mode() == GrenadeSoundPolicy.Mode.NEAR
                    ? GrenadeSoundPayload.near(level.dimension().location(), x, y, z)
                    : GrenadeSoundPayload.far(level.dimension().location(), decision.gain())));
        }
    }
    private static void spawnVisuals(ServerLevel level, BlockPos origin) {
        double x = origin.getX() + .5, y = origin.getY() + .5, z = origin.getZ() + .5;
        sendBestEffort(() -> level.sendParticles(ParticleTypes.EXPLOSION, x, y, z, 1, 0, 0, 0, 0));
        sendBestEffort(() -> level.sendParticles(ParticleTypes.FLAME, x, y, z, 16, .45, .35, .45, .025));
        sendBestEffort(() -> level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 12, .4, .3, .4, .015));
    }
    private static void sendBestEffort(Runnable emission) {
        try { emission.run(); } catch (RuntimeException ignored) { /* Cosmetic delivery must not abort processing. */ }
    }
}
