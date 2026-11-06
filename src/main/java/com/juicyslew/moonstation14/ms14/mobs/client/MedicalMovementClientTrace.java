package com.juicyslew.moonstation14.ms14.mobs.client;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.character.components.MobStateComponent;
import com.juicyslew.moonstation14.ms14.medical_observer.ObserverPayloads;
import com.juicyslew.moonstation14.ms14.mobs.MedicalMovementTraceWindow;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import java.util.UUID;

/** One passive client-local observation window for the medical perspective's exact body. */
@EventBusSubscriber(modid = MoonStation14.MOD_ID, value = Dist.CLIENT)
public final class MedicalMovementClientTrace {
    private static final int MAX_EDGE_ROWS = 6;
    private static Window active;
    private static LivingEntity lastStateBody;
    private static MobStateComponent.State lastState;
    private static ObserverPayloads.Mode lastMode;
    private static long lastEpoch = -1;
    private static LivingEntity lastObserverBody;
    private static long lastObserverEpoch = -1;
    private static Entity lastObserverGhost;
    private static Vec3 followPrevious, followCurrent, anchorPosition;
    private static int edgeRows;
    private MedicalMovementClientTrace() { }

    /** Called by the existing medical perspective state consumer; state is read-only projection. */
    public static void observeMedicalState(LivingEntity body, MobStateComponent.State state, long epoch) {
        if (body == null || state == null) return;
        if (body != lastStateBody) { lastStateBody = body; lastState = null; }
        if (state == lastState) return;
        MobStateComponent.State previous = lastState;
        lastState = state;
        if (state != MobStateComponent.State.CRITICAL && state != MobStateComponent.State.DEAD) {
            if (active != null && active.body == body) close("medical-state-exit:" + state);
            return;
        }
        if (previous == MobStateComponent.State.CRITICAL && state == MobStateComponent.State.DEAD)
            arm(body, epoch, "medical-state-edge:CRITICAL->DEAD");
        else if (active == null || active.body != body)
            arm(body, epoch, "medical-state-edge:" + (previous == null ? "unknown" : previous) + "->" + state);
        else if (previous != state) edge("medical-state-edge:" + previous + "->" + state, body, null);
    }

    /** Called for fresh observer snapshots and from corpse-mode client ticks for late body resolution. */
    public static void observeSnapshot(ObserverPayloads.Mode mode, long epoch, LivingEntity corpse, Entity ghost) {
        if (mode != ObserverPayloads.Mode.CORPSE && mode != ObserverPayloads.Mode.GHOST) return;
        if (corpse == null) return;
        boolean newIdentity = corpse != lastObserverBody || epoch != lastObserverEpoch;
        boolean changed = newIdentity || mode != lastMode || ghost != lastObserverGhost;
        Minecraft mc = Minecraft.getInstance();
        LifecycleAction action = active == null
                ? (changed ? LifecycleAction.ARM : LifecycleAction.NONE)
                : observerAction(active.level, active.connection, active.body, active.entityId, active.uuid,
                        active.snapshotMode, active.epoch, mc.level, mc.getConnection(), corpse,
                        corpse.getId(), corpse.getUUID(), mode, epoch);
        if (action == LifecycleAction.REBIND) close("observation-rebound");
        if (action == LifecycleAction.ARM || action == LifecycleAction.REBIND) {
            arm(corpse, epoch, "observer-" + mode);
        }
        if (active != null && active.body == corpse) {
            active.snapshotMode = mode;
            active.mode = mode.name();
            active.epoch = epoch;
        }
        if (changed) edge("observer-" + mode + " epoch=" + epoch + " ghost=" + entityPoint(ghost), corpse, ghost);
        lastMode = mode; lastEpoch = epoch; lastObserverBody = corpse; lastObserverEpoch = epoch;
        lastObserverGhost = ghost;
    }

    enum LifecycleAction { NONE, ARM, CONTINUE, REBIND }

    static LifecycleAction observerAction(Object activeLevel, Object activeConnection, Object activeBody,
                                          int activeEntityId, UUID activeUuid, ObserverPayloads.Mode activeMode,
                                          long activeEpoch, Object nextLevel, Object nextConnection,
                                          Object nextBody, int nextEntityId, UUID nextUuid,
                                          ObserverPayloads.Mode nextMode, long nextEpoch) {
        if (activeBody == null) return LifecycleAction.ARM;
        if (activeLevel != nextLevel || activeConnection != nextConnection || activeBody != nextBody
                || activeEntityId != nextEntityId || !java.util.Objects.equals(activeUuid, nextUuid))
            return LifecycleAction.REBIND;
        if (activeMode == ObserverPayloads.Mode.CORPSE && nextMode == ObserverPayloads.Mode.GHOST)
            return LifecycleAction.CONTINUE;
        return activeMode == nextMode && activeEpoch == nextEpoch
                ? LifecycleAction.CONTINUE : LifecycleAction.REBIND;
    }

    public static void observationEnded() { close("observer-END"); lastMode = null; lastEpoch = -1; }

    /** Stores the two eye samples already computed by MedicalCameraAnchorEntity.follow. */
    public static void cameraFollow(Entity body, Vec3 previous, Vec3 current, Entity anchor,
                                    ObserverPayloads.Mode mode, long epoch) {
        if (active == null || active.body != body) return;
        followPrevious = previous;
        followCurrent = current;
        anchorPosition = anchor == null ? null : anchor.position();
        if (mode != null) {
            active.mode = mode.name();
            active.snapshotMode = mode;
            active.epoch = epoch;
        }
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Window window = active;
        if (window == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.getConnection() == null || window.level != mc.level
                || window.connection != mc.getConnection()) { close("level-or-connection-change"); return; }
        LivingEntity body = window.body;
        if (body.isRemoved() || mc.level.getEntity(window.entityId) != body
                || !body.getUUID().equals(window.uuid)) { close("removed-or-replaced"); return; }
        long clientTick = ++window.clientTick;
        String state = medicalState(body);
        if ("CRITICAL".equals(state) || "DEAD".equals(state)) window.sawMedicalDown = true;
        if ("ALIVE".equals(state) && window.sawMedicalDown) { close("medical-state-exit:ALIVE"); return; }
        if (window.startedTick < 0) window.startedTick = clientTick;
        if (window.startedTick + MedicalMovementTraceWindow.MAX_ROWS <= clientTick) {
            close("expired-160-client-ticks"); return;
        }
        String detail = fields(mc, body, window);
        window.trace.append(-1, mc.level.getGameTime(), point(body.position()), point(body.getDeltaMovement()), detail);
        if (window.trace.size() == MedicalMovementTraceWindow.MAX_ROWS) close("row-cap");
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { close("disconnect"); resetEdges(); }
    @SubscribeEvent public static void levelEntering(ClientPlayerNetworkEvent.LoggingIn event) { close("connection-replaced"); resetEdges(); }

    private static void arm(LivingEntity body, long epoch, String cause) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.getConnection() == null || body.level() != mc.level) return;
        if (active != null) close("rearmed:" + cause);
        Window window = new Window(mc.level, mc.getConnection(), body, epoch);
        active = window; edgeRows = 0;
        logEdge(window, cause, body, null);
    }

    private static void edge(String cause, LivingEntity body, Entity ghost) {
        Window window = active;
        if (window == null || window.body != body || edgeRows >= MAX_EDGE_ROWS) return;
        edgeRows++;
        logEdge(window, cause, body, ghost);
    }

    private static void logEdge(Window window, String cause, LivingEntity body, Entity ghost) {
        MoonStation14.LOGGER.info("[medical movement trace] side=client-local edge={} body={} uuid={} epoch={} world={} xyz={} delta={} ghost={} camera={}",
                cause, body.getId(), window.uuid, window.epoch, window.level.dimension().location(),
                point(body.position()).text(), point(body.getDeltaMovement()).text(), entityPoint(ghost),
                cameraMode() + ",renderCameraCachedAtClientPost=" + cachedRenderCamera(Minecraft.getInstance()));
    }

    private static String fields(Minecraft mc, LivingEntity body, Window window) {
        Entity camera = mc.getCameraEntity();
        String tracking = "unavailable";
        String lastTrackingId = "unavailable";
        if (body instanceof dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension extension) {
            var sublevel = extension.sable$getTrackingSubLevel();
            tracking = sublevel == null ? "null" : sublevel.getUniqueId().toString();
            lastTrackingId = String.valueOf(extension.sable$getLastTrackingSubLevelID());
        }
        MedicalMovementTraceWindow.Point rawTarget = point(body.lerpTargetX(), body.lerpTargetY(), body.lerpTargetZ());
        Vec3 rawCodecBase = body.getPositionCodec().decode(0L, 0L, 0L);
        return "clientLocalTick=" + window.clientTick + ",localWorldTime=" + mc.level.getGameTime()
                + ",state=" + medicalState(body) + ",onGround=" + body.onGround() + ",pose=" + body.getPose()
                + ",ownedMarker=" + (body instanceof com.juicyslew.moonstation14.ms14.player_body_control.character.MindControlledMob owned
                ? owned.moonstation14$isMovementOwned() : "unavailable")
                + ",trackingFrameUUID=" + tracking + ",lastTrackingId=" + lastTrackingId
                + ",xOld=" + vec(body.xOld, body.yOld, body.zOld) + ",xo=" + vec(body.xo, body.yo, body.zo)
                + ",cameraEntity=" + entityPoint(camera) + ",cameraMode=" + cameraMode()
                + ",renderCameraCachedAtClientPost=" + cachedRenderCamera(mc)
                + ",cameraAnchor=" + pointText(anchorPosition) + ",followEyePrevious=" + pointText(followPrevious)
                + ",followEyeCurrent=" + pointText(followCurrent) + ",epoch=" + window.epoch
                + ",ghostMode=" + window.mode + ",nanoTime=" + System.nanoTime()
                + ",lerpTargetRAW=" + formatted(rawTarget)
                + ",positionCodecRAWBase=" + pointText(rawCodecBase) + "(basis=unknown)";
    }

    private static String formatted(MedicalMovementTraceWindow.Point value) {
        return value == null ? "missing" : value.finite() ? value.text() : "invalid:" + value.text();
    }

    private static String medicalState(LivingEntity body) {
        var attachment = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.MOB_STATE.get());
        return attachment == null ? "unavailable" : attachment.data().state().name();
    }
    private static String cameraMode() {
        Minecraft mc = Minecraft.getInstance();
        return mc.options == null ? "unavailable" : mc.options.getCameraType().name();
    }
    private static String cachedRenderCamera(Minecraft mc) {
        if (mc.gameRenderer == null) return "unavailable:gameRenderer";
        var camera = mc.gameRenderer.getMainCamera();
        if (camera == null || !camera.isInitialized()) return "unavailable:camera-not-initialized";
        Vec3 position = camera.getPosition();
        float partialTick = camera.getPartialTickTime();
        if (position == null || !Double.isFinite(position.x) || !Double.isFinite(position.y)
                || !Double.isFinite(position.z) || !Float.isFinite(partialTick)) return "invalid-numeric";
        return "xyz=" + pointText(position) + ",partialTickTime=" + Float.toString(partialTick)
                + ",basis=last-rendered-camera";
    }
    private static String entityPoint(Entity entity) {
        return entity == null ? "null" : entity.getUUID() + "/" + entity.getId() + "@" + pointText(entity.position());
    }
    private static String pointText(Vec3 value) {
        return value == null ? "null" : point(value.x, value.y, value.z).text();
    }
    private static MedicalMovementTraceWindow.Point point(Vec3 value) {
        return value == null ? null : point(value.x, value.y, value.z);
    }
    private static MedicalMovementTraceWindow.Point point(double x, double y, double z) {
        return new MedicalMovementTraceWindow.Point(x, y, z);
    }
    private static String vec(double x, double y, double z) { return point(x, y, z).text(); }

    private static void close(String reason) {
        Window window = active;
        if (window == null) return;
        active = null;
        var snapshot = window.trace.close(reason);
        MoonStation14.LOGGER.info("[medical movement trace] dump side=client-local body={} uuid={} reason={} rows={} localWorldTimes={}..{} truncated={} coverage=one sample per client post tick; client tick counter is in each row, server tick unavailable",
                window.entityId, window.uuid, reason, snapshot.rowCount(), snapshot.firstWorldTick(),
                snapshot.lastWorldTick(), snapshot.truncated());
        for (var row : snapshot.rows()) MoonStation14.LOGGER.info("[medical movement trace] serverTick=unavailable localWorldTime={} xyz={} velocity={} finite={} {}",
                row.worldTick(), row.position() == null ? "null" : row.position().text(),
                row.velocity() == null ? "null" : row.velocity().text(), row.finite(), row.details());
    }
    private static void resetEdges() {
        lastStateBody = null; lastState = null; lastMode = null; lastEpoch = -1;
        lastObserverBody = null; lastObserverEpoch = -1; lastObserverGhost = null;
        followPrevious = followCurrent = anchorPosition = null; edgeRows = 0;
    }

    private static final class Window {
        final net.minecraft.client.multiplayer.ClientLevel level;
        final net.minecraft.client.multiplayer.ClientPacketListener connection;
        final LivingEntity body;
        final int entityId;
        final UUID uuid;
        final MedicalMovementTraceWindow trace;
        long epoch, clientTick, startedTick = -1;
        String mode = "unavailable";
        ObserverPayloads.Mode snapshotMode;
        boolean sawMedicalDown;
        Window(net.minecraft.client.multiplayer.ClientLevel level,
               net.minecraft.client.multiplayer.ClientPacketListener connection, LivingEntity body, long epoch) {
            this.level = level; this.connection = connection; this.body = body;
            this.entityId = body.getId(); this.uuid = body.getUUID(); this.epoch = epoch;
            String state = medicalState(body);
            this.sawMedicalDown = "CRITICAL".equals(state) || "DEAD".equals(state);
            this.trace = new MedicalMovementTraceWindow(level, body);
        }
    }
}
