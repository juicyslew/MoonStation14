package com.juicyslew.moonstation14.ms14.explosion.server;

import com.juicyslew.moonstation14.ms14.explosion.grenade.network.GrenadeSoundNetworking;
import com.juicyslew.moonstation14.ms14.explosion.grenade.network.GrenadeSoundPayload;
import com.juicyslew.moonstation14.ms14.explosion.grenade.network.GrenadeSoundPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.util.FakePlayer;

/** Shared best-effort explosion cues. Small near/far sounds deliberately overlap by distance. */
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
            if (smallNearEligible(distance)) sendBestEffort(() -> GrenadeSoundNetworking.send(player, GrenadeSoundPayload.smallNear(level.dimension().location(), x, y, z)));
            if (smallFarEligible(distance, iterations))
                sendBestEffort(() -> GrenadeSoundNetworking.send(player, GrenadeSoundPayload.smallFar(level.dimension().location())));
        }
    }
    static boolean smallNearEligible(double distanceSquared) { return distanceSquared <= 50 * 50; }
    static boolean smallFarEligible(double distanceSquared, int iterations) {
        // SS14 small-far starts outside the near audio range (min(iterations * 2, 30));
        // the Minecraft near cue remains the existing PVS-adapted 50-block radius.
        double farInnerRange = Math.min(iterations * 2.0, 30.0);
        return distanceSquared > farInnerRange * farInnerRange
                && distanceSquared <= Math.pow(iterations * 5.0, 2);
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
        sendVisual(level, ParticleTypes.EXPLOSION, x, y, z, 1, 0, 0, 0, 0);
        sendVisual(level, ParticleTypes.FLAME, x, y, z, 16, .45, .35, .45, .025);
        sendVisual(level, ParticleTypes.LARGE_SMOKE, x, y, z, 12, .4, .3, .4, .015);
    }
    private static void sendVisual(ServerLevel level, net.minecraft.core.particles.SimpleParticleType particle,
                                   double x, double y, double z, int count, double xDist, double yDist,
                                   double zDist, double speed) {
        if (level.getServer() == null) return;
        var players = level.getServer().getPlayerList();
        for (ServerPlayer player : players.getPlayers()) {
            if (player instanceof FakePlayer || player.isRemoved() || players.getPlayer(player.getUUID()) != player || player.level() != level) continue;
            Entity camera = player.getCamera();
            if (camera == null || camera.isRemoved() || camera.level() != level
                    || camera.distanceToSqr(x, y, z) > 32 * 32) continue;
            sendBestEffort(() -> player.connection.send(new ClientboundLevelParticlesPacket(
                    particle, false, x, y, z, (float) xDist, (float) yDist, (float) zDist,
                    (float) speed, count)));
        }
    }
    private static void sendBestEffort(Runnable emission) {
        try { emission.run(); } catch (RuntimeException ignored) { /* Cosmetic delivery must not abort processing. */ }
    }
}
