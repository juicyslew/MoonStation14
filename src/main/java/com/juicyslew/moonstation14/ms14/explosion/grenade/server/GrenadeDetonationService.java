package com.juicyslew.moonstation14.ms14.explosion.grenade.server;

import com.juicyslew.moonstation14.ms14.explosion.prototype.ModExplosiveSources;
import com.juicyslew.moonstation14.ms14.explosion.prototype.ExplosiveSourceData;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.explosion.core.BlastPlanner;
import com.juicyslew.moonstation14.ms14.explosion.server.ServerExplosionService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.EmptyBlockGetter;

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

        return result.status() == ServerExplosionService.Status.APPLIED ? Outcome.APPLIED : Outcome.PARTIAL;
    }

    static boolean isResolved(ExplosiveSourceData source, com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog<com.juicyslew.moonstation14.ms14.explosion.prototype.ExplosionData> explosions) {
        return source != null && explosions != null && explosions.get(source.explosionType()) != null;
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

