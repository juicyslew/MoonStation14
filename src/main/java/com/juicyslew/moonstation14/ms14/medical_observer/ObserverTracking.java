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
        if (session != null && session.mode() == ObserverSession.Mode.GHOST
                && session.ownerId().equals(player.getUUID())
                && session.corpseBodyId().equals(player.getUUID())) {
            var level = player.serverLevel();
            var ghost = level.getEntity(session.ghostId());
            if (ghost != null && !ghost.isRemoved() && ghost.getUUID().equals(session.ghostId())) return ghost.position();
        }
        // The native player is a carrier during an authenticated controlled-body session.
        // Resolve via the same lifecycle/harness authority used by body hand actions, but do
        // not revalidate an action or apply its target/range admission here.
        var controlled = com.juicyslew.moonstation14.ms14.hands.HandActorAuthority.resolve(player).orElse(null);
        if (controlled != null && controlled.actor() == player && controlled.body().level() == player.level()
                && !controlled.body().isRemoved() && player.serverLevel().getEntity(controlled.body().getUUID()) == controlled.body())
            return activeBodyOrCarrier(player.position(), controlled.body().position());
        return activeBodyOrCarrier(player.position(), null);
    }

    /** A missing/stale controlled-body context deliberately retains the physical carrier position. */
    public static Vec3 activeBodyOrCarrier(Vec3 carrier, Vec3 validatedBody) {
        return validatedBody == null ? carrier : validatedBody;
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

