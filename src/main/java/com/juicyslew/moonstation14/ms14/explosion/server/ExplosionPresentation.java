package com.juicyslew.moonstation14.ms14.explosion.server;

import com.juicyslew.moonstation14.ms14.explosion.grenade.network.GrenadeSoundNetworking;
import com.juicyslew.moonstation14.ms14.explosion.grenade.network.GrenadeSoundPayload;
import com.juicyslew.moonstation14.ms14.explosion.grenade.network.GrenadeSoundPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.util.FakePlayer;

/** Shared best-effort Minecraft-adapted cue for accepted explosions. */
public final class ExplosionPresentation {
    private ExplosionPresentation() { }

    public static void present(ServerLevel level, BlockPos origin) {
        if (level == null || origin == null) return;
        double x = origin.getX() + 0.5, y = origin.getY() + 0.5, z = origin.getZ() + 0.5;
        if (level.getServer() == null) return;
        var players = level.getServer().getPlayerList();
        for (ServerPlayer player : players.getPlayers()) {
            if (player instanceof FakePlayer || player.isRemoved() || players.getPlayer(player.getUUID()) != player || player.level() != level) continue;
            Entity camera = player.getCamera();
            if (camera == null || camera.isRemoved() || camera.level() != level) continue;
            var decision = GrenadeSoundPolicy.classify(level.dimension().location(), camera.level().dimension().location(),
                    x, y, z, camera.getX(), camera.getY(), camera.getZ());
            if (decision.mode() == GrenadeSoundPolicy.Mode.NONE) continue;
            if (decision.mode() == GrenadeSoundPolicy.Mode.NEAR) {
                sendParticleBestEffort(player, ParticleTypes.EXPLOSION, x, y, z, 1, 0, 0, 0, 0);
                sendParticleBestEffort(player, ParticleTypes.FLAME, x, y, z, 16, 0.45f, 0.35f, 0.45f, 0.025f);
                sendParticleBestEffort(player, ParticleTypes.LARGE_SMOKE, x, y, z, 12, 0.4f, 0.3f, 0.4f, 0.015f);
            }
            sendBestEffort(() -> GrenadeSoundNetworking.send(player, decision.mode() == GrenadeSoundPolicy.Mode.NEAR
                    ? GrenadeSoundPayload.near(level.dimension().location(), x, y, z)
                    : GrenadeSoundPayload.far(level.dimension().location(), decision.gain())));
        }
    }

    private static void sendParticleBestEffort(ServerPlayer player, net.minecraft.core.particles.ParticleOptions particle,
                                                double x, double y, double z, int count,
                                                float xSpread, float ySpread, float zSpread, float speed) {
        sendBestEffort(() -> player.connection.send(new ClientboundLevelParticlesPacket(
                particle, false, x, y, z, xSpread, ySpread, zSpread, speed, count)));
    }

    static void sendBestEffort(Runnable emission) {
        try { emission.run(); } catch (RuntimeException ignored) { /* cosmetic failures do not affect accepted gameplay */ }
    }
}
