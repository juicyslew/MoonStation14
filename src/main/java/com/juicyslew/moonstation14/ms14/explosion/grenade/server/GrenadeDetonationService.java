package com.juicyslew.moonstation14.ms14.explosion.grenade.server;

import com.juicyslew.moonstation14.ms14.explosion.prototype.ModExplosiveSources;
import com.juicyslew.moonstation14.ms14.explosion.prototype.ExplosiveSourceData;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.explosion.core.BlastPlanner;
import com.juicyslew.moonstation14.ms14.explosion.server.ServerExplosionService;
import com.juicyslew.moonstation14.ms14.explosion.grenade.network.GrenadeSoundNetworking;
import com.juicyslew.moonstation14.ms14.explosion.grenade.network.GrenadeSoundPayload;
import com.juicyslew.moonstation14.ms14.explosion.grenade.network.GrenadeSoundPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.EmptyBlockGetter;
import net.neoforged.neoforge.common.util.FakePlayer;

/** Trusted server bridge for the fixed grenade blast; callers own terminal/idempotent fuse handling. */
public final class GrenadeDetonationService {
    private static final int[][] FACES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private GrenadeDetonationService() { }

    static ExplosiveSourceData source(PrototypeCatalog<ExplosiveSourceData> catalog) {
        try { return ModExplosiveSources.require(catalog, ModExplosiveSources.EX_GRENADE_ID); }
        catch (RuntimeException missing) { return null; }
    }

    public enum Outcome { APPLIED, REJECTED, PARTIAL }

    /** Executes only fixed, server-owned grenade settings. Partial application is not rolled back. */
    public static Outcome detonate(ServerLevel level, BlockPos grenadePos) {
        if (level == null || level.isClientSide()) return Outcome.REJECTED;
        ExplosiveSourceData source = source(PrototypeRuntime.serverExplosiveSources());
        if (!isResolved(source, PrototypeRuntime.serverExplosions())) return Outcome.REJECTED;
        BlockPos origin = resolveOrigin(level, grenadePos);
        if (origin == null) return Outcome.REJECTED;

        ServerExplosionService.Result result = ServerExplosionService.execute(level, origin, source.explosionType(), source.proposal());
        if (result.status() != ServerExplosionService.Status.APPLIED && result.status() != ServerExplosionService.Status.PARTIAL)
            return Outcome.REJECTED;

        spawnVisuals(level, origin);
        sendSounds(level, origin);
        return result.status() == ServerExplosionService.Status.APPLIED ? Outcome.APPLIED : Outcome.PARTIAL;
    }

    static boolean isResolved(ExplosiveSourceData source, com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog<com.juicyslew.moonstation14.ms14.explosion.prototype.ExplosionData> explosions) {
        return source != null && explosions != null && explosions.get(source.explosionType()) != null;
    }

    private static void sendSounds(ServerLevel level, BlockPos origin) {
        if (level.getServer() == null) return;
        double x = origin.getX() + 0.5, y = origin.getY() + 0.5, z = origin.getZ() + 0.5;
        var players = level.getServer().getPlayerList();
        for (ServerPlayer player : players.getPlayers()) {
            if (player instanceof FakePlayer || player.isRemoved() || players.getPlayer(player.getUUID()) != player
                    || player.level() != level) continue;
            Entity camera = player.getCamera();
            if (camera == null || camera.isRemoved() || camera.level() != level) continue;
            var decision = GrenadeSoundPolicy.classify(level.dimension().location(), camera.level().dimension().location(),
                    x, y, z, camera.getX(), camera.getY(), camera.getZ());
            if (decision.mode() == GrenadeSoundPolicy.Mode.NONE) continue;
            sendBestEffort(() -> GrenadeSoundNetworking.send(player,
                    decision.mode() == GrenadeSoundPolicy.Mode.NEAR
                            ? GrenadeSoundPayload.near(level.dimension().location(), x, y, z)
                            : GrenadeSoundPayload.far(level.dimension().location(), decision.gain())));
        }
    }

    /** Brief, bounded vanilla-particle cue; this is cosmetic and does not mutate the world. */
    private static void spawnVisuals(ServerLevel level, BlockPos origin) {
        double x = origin.getX() + 0.5;
        double y = origin.getY() + 0.5;
        double z = origin.getZ() + 0.5;
        sendBestEffort(() -> level.sendParticles(ParticleTypes.EXPLOSION, x, y, z, 1, 0, 0, 0, 0));
        sendBestEffort(() -> level.sendParticles(ParticleTypes.FLAME, x, y, z, 16, 0.45, 0.35, 0.45, 0.025));
        sendBestEffort(() -> level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 12, 0.4, 0.3, 0.4, 0.015));
    }

    static void sendBestEffort(Runnable emission) {
        try {
            emission.run();
        } catch (RuntimeException ignored) {
            // Cosmetic packet failures must not prevent remaining effects or recipient deliveries.
        }
    }

    /** Resolve a blast voxel without loading chunks; an occupied source can move only one face step. */
    static BlockPos resolveOrigin(ServerLevel level, BlockPos grenadePos) {
        if (level == null || level.isClientSide() || grenadePos == null) return null;
        if (grenadePos.getY() < level.getMinBuildHeight() || grenadePos.getY() >= level.getMaxBuildHeight()) return null;
        var state = stateIfLoaded(level, grenadePos);
        if (state == null) return null;
        if (isOpen(state)) return grenadePos.immutable();

        for (int[] face : FACES) {
            BlockPos candidate = grenadePos.offset(face[0], face[1], face[2]);
            if (candidate.getY() < level.getMinBuildHeight() || candidate.getY() >= level.getMaxBuildHeight()) continue;
            var adjacent = stateIfLoaded(level, candidate);
            if (adjacent != null && isOpen(adjacent))
                return candidate.immutable();
        }
        return null;
    }

    private static net.minecraft.world.level.block.state.BlockState stateIfLoaded(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk == null ? null : chunk.getBlockState(pos);
    }

    private static boolean isOpen(net.minecraft.world.level.block.state.BlockState state) {
        // Non-colliding ordinary blocks are destructible blast cells, not valid grenade origins.
        return state.isAir() && state.getDestroySpeed(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) >= 0;
    }
}
