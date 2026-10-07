package com.juicyslew.moonstation14.ms14.medical_observer;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.ChunkPos;
import com.juicyslew.moonstation14.mixin.ChunkMapObserverAccess;

import java.util.Map;
import java.util.WeakHashMap;

/** Supplies the observer camera's position to server entity tracking without moving its body. */
public final class ObserverTracking {
    private static final Map<ServerPlayer, ChunkPos> CENTERS = new WeakHashMap<>();
    private ObserverTracking() { }

    public static Vec3 viewPosition(ServerPlayer player) {
        ObserverSession session = ObserverSystem.query(player);
        if (session == null || session.mode() != ObserverSession.Mode.GHOST
                || !session.ownerId().equals(player.getUUID())
                || !session.corpseBodyId().equals(player.getUUID())) return player.position();
        var level = player.serverLevel();
        var ghost = level.getEntity(session.ghostId());
        if (ghost == null || ghost.isRemoved() || !ghost.getUUID().equals(session.ghostId())) return player.position();
        return ghost.position();
    }

    /** Recenter chunk tracking at the observer harness without relocating the native player. */
    public static void updateChunkCenter(ServerPlayer player) {
        ObserverSession session = ObserverSystem.query(player);
        if (session == null) {
            ChunkPos old = CENTERS.remove(player);
            if (old != null) ((ChunkMapObserverAccess) (Object) player.serverLevel().getChunkSource().chunkMap)
                    .moonstation14$applyChunkTrackingView(player, player.getChunkTrackingView());
            return;
        }
        Vec3 position = viewPosition(player);
        if (position == player.position()) return;
        ChunkPos center = new ChunkPos(net.minecraft.core.BlockPos.containing(position));
        ChunkPos old = CENTERS.put(player, center);
        if (center.equals(old)) return;
        int radius = Math.min(player.serverLevel().getServer().getPlayerList().getViewDistance(),
                player.getChunkTrackingView() instanceof ChunkTrackingView.Positioned view ? view.viewDistance()
                        : player.serverLevel().getServer().getPlayerList().getViewDistance());
        ((ChunkMapObserverAccess) (Object) player.serverLevel().getChunkSource().chunkMap)
                .moonstation14$applyChunkTrackingView(player, ChunkTrackingView.of(center, radius));
        player.serverLevel().getChunkSource().chunkMap.move(player);
    }
}
