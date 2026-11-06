package com.juicyslew.moonstation14.ms14.mobs;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.character.components.MobStateComponent;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Passive, exact-body server trace used only to observe medical transitions. */
public final class MedicalMovementTrace {
    private static final int WINDOW_TICKS = 160;
    private static final int MAX_WINDOWS = 4;
    private static final Map<Key, Window> WINDOWS = new LinkedHashMap<>();
    private MedicalMovementTrace() { }

    public static void transition(LivingEntity body, MobStateComponent.State previous,
                                  MobStateComponent.State next) {
        if (!(body instanceof PlayerCharacterHarnessEntity) || body.level().isClientSide()) return;
        TransitionAction action = transitionAction(previous, next);
        if (action == TransitionAction.NONE) return;
        try {
            ServerLevel level = (ServerLevel) body.level();
            Key key = new Key(level, body.getUUID());
            if (action == TransitionAction.CLOSE) {
                close(key, next == MobStateComponent.State.ALIVE ? "healed" : "medical-state-exit");
                return;
            }
            close(key, "rearmed");
            if (WINDOWS.size() >= MAX_WINDOWS) close(WINDOWS.keySet().iterator().next(), "window-budget");
            long tick = level.getGameTime();
            Window window = new Window(level, body, tick);
            WINDOWS.put(key, window);
            Vec3 p = body.position(), v = body.getDeltaMovement();
            TrackingFrame tracking = trackingFrame(body);
            MoonStation14.LOGGER.info("[medical movement trace] side=server edge={}->{} body={} level={} xyz={} delta={} serverTick={} worldGameTime={} onGround={} ownedMarker={} nativeTrackingFrameUUID={} lastTrackingId={}",
                    previous, next, body.getUUID(), level.dimension().location(), point(p), point(v),
                    level.getServer().getTickCount(), tick, body.onGround(), ownedMarker(body),
                    tracking.frameUuid(), tracking.lastTrackingId());
        } catch (RuntimeException failure) {
            MoonStation14.LOGGER.warn("[medical movement trace] transition observer failed: {}", failure.toString());
        }
    }

    enum TransitionAction { NONE, OPEN, CLOSE }

    static TransitionAction transitionAction(MobStateComponent.State previous, MobStateComponent.State next) {
        if (previous == next) return TransitionAction.NONE;
        boolean previousDown = previous == MobStateComponent.State.CRITICAL || previous == MobStateComponent.State.DEAD;
        boolean nextDown = next == MobStateComponent.State.CRITICAL || next == MobStateComponent.State.DEAD;
        if (nextDown) return TransitionAction.OPEN;
        return previousDown ? TransitionAction.CLOSE : TransitionAction.NONE;
    }

    public static void onPostTick(ServerTickEvent.Post event) {
        if (WINDOWS.isEmpty()) return;
        List<Key> keys = new ArrayList<>(WINDOWS.keySet());
        for (Key key : keys) {
            Window window = WINDOWS.get(key);
            if (window == null || window.level.getServer() != event.getServer()) continue;
            try {
                LivingEntity body = window.body;
                if (window.level != key.level || body.level() != key.level) { close(key, "level-change"); continue; }
                if (body.isRemoved() || key.level.getEntities().get(key.uuid) != body) { close(key, "removed-or-replaced"); continue; }
                long worldTick = key.level.getGameTime();
                long serverTick = event.getServer().getTickCount();
                TrackingFrame tracking = trackingFrame(body);
                window.trace.append(serverTick, worldTick, point(body.position()), point(body.getDeltaMovement()),
                        "state=" + state(body) + ",onGround=" + body.onGround() + ",ownedMarker=" + ownedMarker(body)
                                + ",nativeTrackingFrameUUID=" + tracking.frameUuid()
                                + ",lastTrackingId=" + tracking.lastTrackingId()
                                + ",frameAnchorPending=" + (body instanceof com.juicyslew.moonstation14.ms14.shuttle.sable.FrameAnchorPending pending
                                ? pending.sable$frameAnchorInitializationPending() : "unavailable"));
                if (serverTick - window.startedServerTick >= WINDOW_TICKS || window.trace.size() == MedicalMovementTraceWindow.MAX_ROWS)
                    close(key, window.trace.size() == MedicalMovementTraceWindow.MAX_ROWS ? "row-cap" : "expired");
            } catch (RuntimeException failure) {
                Window removed = WINDOWS.remove(key);
                if (removed != null) dump(key, removed, "observer-error:" + failure.getClass().getSimpleName());
            }
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        for (Key key : new ArrayList<>(WINDOWS.keySet())) close(key, "server-unload");
    }

    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof LivingEntity body)) return;
        Key key = new Key(level, body.getUUID());
        Window window = WINDOWS.get(key);
        if (window != null && window.body == body) close(key, "level-unload-or-removal");
    }

    private static String state(LivingEntity body) {
        return MobStateSystem.isDead(body) ? "DEAD" : MobStateSystem.isCritical(body) ? "CRITICAL" : "other";
    }

    private static MedicalMovementTraceWindow.Point point(Vec3 v) {
        return new MedicalMovementTraceWindow.Point(v.x, v.y, v.z);
    }

    private static String ownedMarker(LivingEntity body) {
        if (body instanceof com.juicyslew.moonstation14.ms14.player_body_control.character.MindControlledMob owned) {
            return Boolean.toString(owned.moonstation14$isMovementOwned());
        }
        return "unavailable";
    }

    private static TrackingFrame trackingFrame(LivingEntity body) {
        if (body instanceof dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension movement) {
            var sublevel = movement.sable$getTrackingSubLevel();
            String frameUuid = sublevel == null ? "null" : sublevel.getUniqueId().toString();
            return new TrackingFrame(frameUuid, String.valueOf(movement.sable$getLastTrackingSubLevelID()));
        }
        return new TrackingFrame("unavailable", "unavailable");
    }

    private static void close(Key key, String reason) {
        Window window = WINDOWS.remove(key);
        if (window != null) dump(key, window, reason);
    }

    private static void dump(Key key, Window window, String reason) {
        var snapshot = window.trace.close(reason);
        MoonStation14.LOGGER.info("[medical movement trace] dump side=server body={} reason={} rows={} worldTicks={}..{} truncated={} coverage=one sample per observed server post tick; nonfinite fields explicitly flagged",
                key.uuid, reason, snapshot.rowCount(), snapshot.firstWorldTick(), snapshot.lastWorldTick(), snapshot.truncated());
        for (var row : snapshot.rows()) MoonStation14.LOGGER.info("[medical movement trace] serverTick={} worldGameTime={} xyz={} velocity={} finite={} {}",
                row.serverTick(), row.worldTick(), row.position() == null ? "null" : row.position().text(),
                row.velocity() == null ? "null" : row.velocity().text(), row.finite(), row.details());
    }

    private record Key(ServerLevel level, UUID uuid) { }
    private record TrackingFrame(String frameUuid, String lastTrackingId) { }
    private static final class Window {
        final ServerLevel level;
        final LivingEntity body;
        final long startedServerTick;
        final MedicalMovementTraceWindow trace;
        Window(ServerLevel level, LivingEntity body, long startedServerTick) {
            this.level = level; this.body = body; this.startedServerTick = startedServerTick;
            this.trace = new MedicalMovementTraceWindow(level, body);
        }
    }
}
