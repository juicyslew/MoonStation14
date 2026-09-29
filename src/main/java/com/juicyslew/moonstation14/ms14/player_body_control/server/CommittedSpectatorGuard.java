package com.juicyslew.moonstation14.ms14.player_body_control.server;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.LifecycleCharacterSessionControl;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.LifecycleGhostSessionControl;
import java.util.WeakHashMap;
import net.minecraft.network.protocol.game.ServerboundTeleportToEntityPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;


/** Prevents vanilla spectator actions from escaping an exact committed control session. */
public final class CommittedSpectatorGuard {
    private static final int LOG_INTERVAL_TICKS = 100;
    // Server thread only; weak keys cannot retain disconnected player instances.
    private static final WeakHashMap<ServerPlayer, Long> LAST_LOG_TICK = new WeakHashMap<>();

    private CommittedSpectatorGuard() { }

    /** A committed session can exist even when its owned body cannot be safely resolved. */
    public record Ownership(boolean committed, Entity owned) {
        public static Ownership absent() { return new Ownership(false, null); }
        public static Ownership unavailable() { return new Ownership(true, null); }
        public static Ownership valid(Entity owned) { return new Ownership(true, java.util.Objects.requireNonNull(owned)); }
    }

    private static Ownership[] owners(ServerPlayer player) {
        return new Ownership[] {
                LifecycleCharacterSessionControl.committedControlSnapshot(player),
                LifecycleGhostSessionControl.committedControlSnapshot(player),
                GhostMobHarnessControl.committedControlSnapshot(player)
        };
    }

    /** A teleport packet is never an ownership handoff, even when it targets the owned body. */
    public static boolean blockTeleport(ServerPlayer player) {
        return blockTeleport(player, null);
    }

    public static boolean blockTeleport(ServerPlayer player, ServerboundTeleportToEntityPacket packet) {
        if (player != null && player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return false;
        Ownership[] snapshots = owners(player);
        int count = committedCount(snapshots);
        if (count == 0) return false;
        if (shouldLog(player)) {
            // The packet's lookup is indexed by UUID in this level, not an entity scan.
            Entity target = packet == null ? null : packet.getEntity(player.serverLevel());
            logSuppression(player, "teleport-to-entity-packet",
                    target == null ? "packet-target-unresolved" : "packet-target-resolved",
                    target, snapshots, count);
        }
        return true;
    }

    public static boolean rejectTeleport(boolean exactCommittedOwner) {
        return exactCommittedOwner;
    }

    public static boolean blockCamera(ServerPlayer player, Entity target) {
        if (player != null && player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return false;
        Ownership[] snapshots = owners(player);
        if (!rejectCamera(snapshots, target)) return false;
        if (shouldLog(player)) {
            int count = committedCount(snapshots);
            logSuppression(player, "set-camera",
                    count > 1 ? "multiple-committed-owners"
                            : singleOwned(snapshots, count) == null ? "owned-body-unavailable" : "foreign-camera-target",
                    target, snapshots, count);
        }
        return true;
    }

    private static int committedCount(Ownership[] snapshots) {
        int count = 0;
        for (Ownership snapshot : snapshots) if (snapshot.committed()) count++;
        return count;
    }

    private static Entity singleOwned(Ownership[] snapshots, int count) {
        if (count != 1) return null;
        for (Ownership snapshot : snapshots) if (snapshot.committed()) return snapshot.owned();
        return null;
    }

    static boolean logDue(long now, Long previous) {
        return previous == null || now < previous || now - previous >= LOG_INTERVAL_TICKS;
    }

    private static boolean shouldLog(ServerPlayer player) {
        if (player == null || player.server == null || !player.server.isSameThread()
                || player.server.getPlayerList().getPlayer(player.getUUID()) != player) return false;
        long now = player.server.getTickCount();
        if (!logDue(now, LAST_LOG_TICK.get(player))) return false;
        LAST_LOG_TICK.put(player, now);
        return true;
    }

    private static void logSuppression(ServerPlayer player, String route, String reason, Entity target,
                                       Ownership[] snapshots, int count) {
        Entity camera = player.getCamera();
        Entity owned = singleOwned(snapshots, count);
        MoonStation14.LOGGER.info("Committed spectator guard suppressed route={} reason={} cameraId={} attemptedId={} ownedId={} committedOwners={} gameMode={}",
                route, reason, camera == null ? -1 : camera.getId(), target == null ? -1 : target.getId(),
                owned == null ? -1 : owned.getId(), count, player.gameMode.getGameModeForPlayer());
    }

    static boolean rejectCamera(Ownership[] owners, Entity target) {
        int count = 0;
        Entity owned = null;
        for (Ownership owner : owners) {
            if (!owner.committed()) continue;
            count++;
            owned = owner.owned();
        }
        return count > 1 || count == 1 && (owned == null || owned != target);
    }

    public static boolean blockForeignCamera(Entity owned, Entity target) {
        return rejectForeignCamera(owned != null, owned == target);
    }

    /** Pure policy: no committed owner means vanilla spectator camera behavior is unchanged. */
    public static boolean rejectForeignCamera(boolean exactCommittedOwner, boolean targetIsOwnedEntity) {
        return exactCommittedOwner && !targetIsOwnedEntity;
    }
}
