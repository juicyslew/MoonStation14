package com.juicyslew.moonstation14.ms14.explosion.grenade.server;

import com.juicyslew.moonstation14.ms14.explosion.prototype.ModExplosiveSources;
import com.juicyslew.moonstation14.ms14.explosion.prototype.ExplosiveSourceData;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.explosion.core.BlastPlanner;
import com.juicyslew.moonstation14.ms14.explosion.server.QueuedExplosionSystem;
import com.juicyslew.moonstation14.ms14.explosion.server.ExplosionSourceAdmission;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Trusted server bridge for the fixed grenade blast; callers own terminal/idempotent fuse handling. */
public final class GrenadeDetonationService {

    private GrenadeDetonationService() { }

    static ExplosiveSourceData source(PrototypeCatalog<ExplosiveSourceData> catalog) {
        try { return ModExplosiveSources.require(catalog, ModExplosiveSources.EX_GRENADE_ID); }
        catch (RuntimeException missing) { return null; }
    }

    public enum Outcome { QUEUED, REJECTED }

    /** Executes only fixed, server-owned grenade settings. Partial application is not rolled back. */
    public static Outcome detonate(ServerLevel level, BlockPos grenadePos) {
        if (level == null || level.isClientSide()) return Outcome.REJECTED;
        ExplosiveSourceData source = source(PrototypeRuntime.serverExplosiveSources());
        if (!isResolved(source, PrototypeRuntime.serverExplosions())) return Outcome.REJECTED;
        BlockPos origin = ExplosionSourceAdmission.resolveOrigin(level, grenadePos);
        if (origin == null) return Outcome.REJECTED;

        QueuedExplosionSystem.Outcome queued = QueuedExplosionSystem.enqueue(level, origin, source);
        return queued == QueuedExplosionSystem.Outcome.REJECTED ? Outcome.REJECTED : Outcome.QUEUED;
    }

    static boolean isResolved(ExplosiveSourceData source, com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog<com.juicyslew.moonstation14.ms14.explosion.prototype.ExplosionData> explosions) {
        return source != null && explosions != null && explosions.get(source.explosionType()) != null;
    }

    public static void spawnVisuals(ServerLevel level, BlockPos origin) { com.juicyslew.moonstation14.ms14.explosion.server.ExplosionFeedbackService.emitLegacyGrenadeCues(level, origin); }
    static void sendBestEffort(Runnable emission) { try { emission.run(); } catch (RuntimeException ignored) { } }
    static BlockPos resolveOrigin(ServerLevel level, BlockPos pos) { return ExplosionSourceAdmission.resolveOrigin(level, pos); }
}

