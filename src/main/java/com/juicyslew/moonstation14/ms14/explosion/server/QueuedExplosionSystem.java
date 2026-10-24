package com.juicyslew.moonstation14.ms14.explosion.server;

import com.juicyslew.moonstation14.ms14.explosion.core.BlastPlanner;
import com.juicyslew.moonstation14.ms14.explosion.core.PendingExplosionQueue;
import com.juicyslew.moonstation14.ms14.explosion.prototype.ExplosionData;
import com.juicyslew.moonstation14.ms14.explosion.prototype.ExplosiveSourceData;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.IdentityHashMap;
import java.util.Map;

/** Trusted server admission and deferred execution owner. Position matching is full 3D for MC's grid. */
public final class QueuedExplosionSystem {
    public enum Outcome { QUEUED, MERGED, REJECTED }
    private static final Map<ServerLevel, PendingExplosionQueue> QUEUES = new IdentityHashMap<>();
    private QueuedExplosionSystem() { }

    public static Outcome enqueue(ServerLevel level, BlockPos origin, ExplosiveSourceData source) {
        if (level == null || origin == null || source == null || level.isClientSide() || level.getServer() == null
                || !level.getServer().isSameThread() || origin.getY() < level.getMinBuildHeight() || origin.getY() >= level.getMaxBuildHeight()) return Outcome.REJECTED;
        origin = ExplosionSourceAdmission.resolveOrigin(level, origin);
        if (origin == null) return Outcome.REJECTED;
        ExplosionData data = PrototypeRuntime.serverExplosions().get(source.explosionType());
        if (data == null) return Outcome.REJECTED;
        try {
            var request = new PendingExplosionQueue.Request(level.dimension().location(),
                    new BlastPlanner.Pos(origin.getX(), origin.getY(), origin.getZ()), source, data.maxCombineDistance());
            var queue = QUEUES.computeIfAbsent(level, ignored -> new PendingExplosionQueue());
            var result = queue.add(request);
            return switch (result.status()) {
                case ACCEPTED -> Outcome.QUEUED;
                case MERGED -> Outcome.MERGED;
                case REJECTED -> Outcome.REJECTED;
            };
        } catch (RuntimeException invalid) { return Outcome.REJECTED; }
    }
    public static void tick(ServerLevel level) {
        PendingExplosionQueue queue = QUEUES.get(level);
        if (queue == null) return;
        var request = queue.poll();
        if (request == null) { QUEUES.remove(level); return; }
        BlockPos origin = new BlockPos(request.origin().x(), request.origin().y(), request.origin().z());
        var source = request.source();
        var result = ServerExplosionService.execute(level, origin, source.explosionType(), source.proposal());
        if (result.status() == ServerExplosionService.Status.APPLIED || result.status() == ServerExplosionService.Status.PARTIAL) {
            ExplosionData resolved = PrototypeRuntime.serverExplosions().get(source.explosionType());
            if (resolved != null) ExplosionFeedbackService.emitQueuedCues(level, origin, result.plannedIntensityIterationCount(), resolved.smallSoundIterationThreshold());
        }
        if (queue.size() == 0) QUEUES.remove(level);
    }
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) QUEUES.remove(level);
    }
    public static void onServerStopped(ServerStoppedEvent event) { QUEUES.clear(); }
}
