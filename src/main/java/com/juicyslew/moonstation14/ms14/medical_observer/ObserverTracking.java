package com.juicyslew.moonstation14.ms14.medical_observer;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import com.juicyslew.moonstation14.mixin.ChunkMapObserverAccess;
import com.juicyslew.moonstation14.MoonStation14;

import java.util.Map;
import java.util.WeakHashMap;

/** Supplies the observer camera's position to server entity tracking without moving its body. */
public final class ObserverTracking {
    private record AppliedView(ChunkPos center, int radius, Object level, ControlledViewTarget.Family family,
                              long epoch, java.util.UUID target) { }
    record Reconciliation(boolean applyView, boolean resyncClientCenter) { }
    private static final Map<ServerPlayer, AppliedView> CENTERS = new WeakHashMap<>();
    private ObserverTracking() { }

    public static Vec3 viewPosition(ServerPlayer player) {
        var target = ControlledViewTarget.resolve(player);
        return resolveViewPosition(player.position(), target);
    }

    /** A missing/stale controlled-body context deliberately retains the physical carrier position. */
    public static Vec3 activeBodyOrCarrier(Vec3 carrier, Vec3 validatedBody) {
        return validatedBody == null ? carrier : validatedBody;
    }

    static Vec3 resolveViewPosition(Vec3 nativePosition, ControlledViewTarget.Target target) {
        return target == null ? nativePosition : target.entity().position();
    }

    /** Recenter chunk tracking at the observer harness without relocating the native player. */
    public static void updateChunkCenter(ServerPlayer player) {
        var target = ControlledViewTarget.resolve(player);
        Vec3 position = target == null ? player.position() : target.entity().position();
        if (target == null) {
            AppliedView old = CENTERS.remove(player);
            if (old != null) {
                var chunkMap = player.serverLevel().getChunkSource().chunkMap;
                int radius = player.getChunkTrackingView() instanceof ChunkTrackingView.Positioned view
                        ? view.viewDistance() : player.serverLevel().getServer().getPlayerList().getViewDistance();
                ((ChunkMapObserverAccess) (Object) chunkMap).moonstation14$applyChunkTrackingView(player,
                        ChunkTrackingView.of(player.chunkPosition(), radius));
                chunkMap.move(player);
            }
            return;
        }
        ChunkPos center = new ChunkPos(net.minecraft.core.BlockPos.containing(position));
        int radius = Math.min(player.serverLevel().getServer().getPlayerList().getViewDistance(),
                player.getChunkTrackingView() instanceof ChunkTrackingView.Positioned view ? view.viewDistance()
                        : player.serverLevel().getServer().getPlayerList().getViewDistance());
        AppliedView previous = CENTERS.get(player);
        boolean sameOwner = previous != null && previous.level() == player.serverLevel()
                && previous.family() == target.family() && previous.epoch() == target.epoch()
                && previous.target().equals(target.entity().getUUID());
        ChunkPos actualCenter = player.getChunkTrackingView() instanceof ChunkTrackingView.Positioned view
                ? view.center() : null;
        int actualRadius = player.getChunkTrackingView() instanceof ChunkTrackingView.Positioned view
                ? view.viewDistance() : -1;
        Reconciliation decision = reconcile(previous, sameOwner, player.serverLevel(), center, radius,
                actualCenter, actualRadius);
        if (!decision.applyView())
            return;
        CENTERS.put(player, new AppliedView(center, radius, player.serverLevel(), target.family(), target.epoch(),
                target.entity().getUUID()));
        MoonStation14.LOGGER.info("[controlled view] owner={} family={} epoch={} camera={} nativeChunk={} target={} center={}",
                player.getUUID(), target.family(), target.epoch(), player.getCamera().getUUID(), player.chunkPosition(),
                target.entity().getUUID(), center);
        // ChunkMap only emits this packet when its native view center changes. A new control owner
        // at the same center still needs a bounded client cache-center resync.
        if (decision.resyncClientCenter())
            player.connection.send(new ClientboundSetChunkCacheCenterPacket(center.x, center.z));
        ((ChunkMapObserverAccess) (Object) player.serverLevel().getChunkSource().chunkMap)
                .moonstation14$applyChunkTrackingView(player, ChunkTrackingView.of(center, radius));
        player.serverLevel().getChunkSource().chunkMap.move(player);
    }

    static Reconciliation reconcile(ChunkPos cachedCenter, int cachedRadius, Object cachedLevel,
                                    boolean sameOwner, Object level, ChunkPos desiredCenter, int desiredRadius,
                                    ChunkPos actualCenter, int actualRadius) {
        boolean apply = !(sameOwner && level == cachedLevel && desiredCenter.equals(cachedCenter)
                && desiredRadius == cachedRadius && desiredCenter.equals(actualCenter)
                && desiredRadius == actualRadius);
        return new Reconciliation(apply, apply && !sameOwner && desiredCenter.equals(actualCenter));
    }

    private static Reconciliation reconcile(AppliedView previous, boolean sameOwner, Object level,
                                            ChunkPos desiredCenter, int desiredRadius,
                                            ChunkPos actualCenter, int actualRadius) {
        return reconcile(previous == null ? null : previous.center(), previous == null ? -1 : previous.radius(),
                previous == null ? null : previous.level(), sameOwner, level, desiredCenter, desiredRadius,
                actualCenter, actualRadius);
    }
}

