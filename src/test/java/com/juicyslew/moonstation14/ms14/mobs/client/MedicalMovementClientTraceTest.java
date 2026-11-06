package com.juicyslew.moonstation14.ms14.mobs.client;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MedicalMovementClientTraceTest {
    private static String source(String path) throws Exception {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(Path.of("src", "main", "java")).resolve(path.replace('/', java.io.File.separatorChar));
            if (Files.isRegularFile(candidate)) return Files.readString(candidate);
            current = current.getParent();
        }
        throw new java.io.FileNotFoundException(path);
    }

    @Test void observerLifecycleKeepsOnlySameCorpseCorpseToGhostContinuity() {
        Object level = new Object(), connection = new Object(), body = new Object();
        var id = java.util.UUID.randomUUID();
        assertEquals(MedicalMovementClientTrace.LifecycleAction.CONTINUE,
                MedicalMovementClientTrace.observerAction(level, connection, body, 5, id,
                        com.juicyslew.moonstation14.ms14.medical_observer.ObserverPayloads.Mode.CORPSE, 3,
                        level, connection, body, 5, id,
                        com.juicyslew.moonstation14.ms14.medical_observer.ObserverPayloads.Mode.GHOST, 4));
        assertEquals(MedicalMovementClientTrace.LifecycleAction.REBIND,
                action(level, connection, body, 5, id, level, connection, new Object(), 6, java.util.UUID.randomUUID(),
                        com.juicyslew.moonstation14.ms14.medical_observer.ObserverPayloads.Mode.GHOST, 4));
        assertEquals(MedicalMovementClientTrace.LifecycleAction.REBIND,
                action(level, connection, body, 5, id, level, connection, body, 5, id,
                        com.juicyslew.moonstation14.ms14.medical_observer.ObserverPayloads.Mode.CORPSE, 4));
        assertEquals(MedicalMovementClientTrace.LifecycleAction.REBIND,
                action(level, connection, body, 5, id, new Object(), connection, body, 5, id,
                        com.juicyslew.moonstation14.ms14.medical_observer.ObserverPayloads.Mode.CORPSE, 3));
        assertEquals(MedicalMovementClientTrace.LifecycleAction.REBIND,
                action(level, connection, body, 5, id, level, new Object(), body, 5, id,
                        com.juicyslew.moonstation14.ms14.medical_observer.ObserverPayloads.Mode.CORPSE, 3));
    }

    @Test void observerRebindDecisionIsAppliedBeforeEdgeLoggingAndWindowMetadataUpdates() throws Exception {
        String trace = source("com/juicyslew/moonstation14/ms14/mobs/client/MedicalMovementClientTrace.java");
        assertTrue(trace.contains("if (action == LifecycleAction.REBIND) close(\"observation-rebound\")"));
        assertTrue(trace.contains("if (action == LifecycleAction.ARM || action == LifecycleAction.REBIND)"));
        assertTrue(trace.indexOf("active.snapshotMode = mode") < trace.indexOf("if (changed) edge("));
        String server = source("com/juicyslew/moonstation14/ms14/mobs/MedicalMovementTrace.java");
        assertTrue(server.contains("if (action == TransitionAction.CLOSE)"));
        assertTrue(server.contains("close(key, next == MobStateComponent.State.ALIVE ? \"healed\""));
    }

    private static MedicalMovementClientTrace.LifecycleAction action(Object activeLevel, Object activeConnection,
            Object activeBody, int activeId, java.util.UUID activeUuid, Object nextLevel, Object nextConnection,
            Object nextBody, int nextId, java.util.UUID nextUuid,
            com.juicyslew.moonstation14.ms14.medical_observer.ObserverPayloads.Mode nextMode, long nextEpoch) {
        return MedicalMovementClientTrace.observerAction(activeLevel, activeConnection, activeBody, activeId,
                activeUuid, com.juicyslew.moonstation14.ms14.medical_observer.ObserverPayloads.Mode.CORPSE, 3,
                nextLevel, nextConnection, nextBody, nextId, nextUuid, nextMode, nextEpoch);
    }

    @Test void automaticMedicalAndObserverEdgesAreWiredToCurrentConsumers() throws Exception {
        String perspective = source("com/juicyslew/moonstation14/ms14/mobs/client/MedicalPerspectiveClient.java");
        String observer = source("com/juicyslew/moonstation14/ms14/medical_observer/client/ObserverClient.java");
        String trace = source("com/juicyslew/moonstation14/ms14/mobs/client/MedicalMovementClientTrace.java");
        assertTrue(perspective.contains("MedicalMovementClientTrace.observeMedicalState(body, state"));
        assertTrue(observer.contains("MedicalMovementClientTrace.observeSnapshot("));
        assertTrue(trace.contains("@SubscribeEvent public static void tick(ClientTickEvent.Post event)"));
        assertTrue(trace.contains("new MedicalMovementTraceWindow(level, body)"));
        assertTrue(trace.contains("mc.level.getEntity(window.entityId) != body"));
        assertTrue(trace.contains("window.trace.append(-1, mc.level.getGameTime()"));
        assertFalse(trace.contains("sable$getPlotPosition"));
        assertFalse(trace.contains("body.setPos("));
        assertFalse(trace.contains("body.setDeltaMovement("));
    }

    @Test void cameraTapUsesAlreadyComputedFollowEndpoints() throws Exception {
        String anchor = source("com/juicyslew/moonstation14/ms14/medical_observer/client/MedicalCameraAnchorEntity.java");
        int previous = anchor.indexOf("Vec3 previous = samples.previous();");
        int current = anchor.indexOf("Vec3 current = samples.current();");
        int tap = anchor.indexOf("MedicalMovementClientTrace.cameraFollow(body,");
        assertTrue(previous >= 0 && current > previous && tap > current);
        assertTrue(anchor.substring(tap).contains("previous, current, this"));
        assertEquals(2, count(anchor, "body.getEyePosition("));
        assertTrue(anchor.indexOf("setPos(current.x, current.y, current.z)") < tap);
    }

    @Test void tracesReadCachedTrackingRawPositionsAndViewportCameraWithoutMutation() throws Exception {
        String server = source("com/juicyslew/moonstation14/ms14/mobs/MedicalMovementTrace.java");
        assertTrue(server.contains("ownedMarker={} nativeTrackingFrameUUID={} lastTrackingId={}"));
        assertTrue(server.contains("movement.sable$getTrackingSubLevel()"));
        assertTrue(server.contains("movement.sable$getLastTrackingSubLevelID()"));
        assertFalse(server.contains("nativeTracking={}"));

        String client = source("com/juicyslew/moonstation14/ms14/mobs/client/MedicalMovementClientTrace.java");
        assertTrue(client.contains("extension.sable$getTrackingSubLevel()"));
        assertTrue(client.contains("extension.sable$getLastTrackingSubLevelID()"));
        assertTrue(client.contains("body.lerpTargetX(), body.lerpTargetY(), body.lerpTargetZ()"));
        assertTrue(client.contains("body.getPositionCodec().decode(0L, 0L, 0L)"));
        assertTrue(client.contains("camera.getPosition()"));
        assertTrue(client.contains("camera.getPartialTickTime()"));
        assertTrue(client.contains("renderCameraCachedAtClientPost"));
        assertFalse(client.contains("sable$getPlotPosition"));
        assertFalse(client.contains("getEyePosition("));
        assertFalse(client.contains("setBase("));
        assertFalse(client.contains("camera.setup("));
    }

    private static int count(String text, String needle) {
        int count = 0, at = 0;
        while ((at = text.indexOf(needle, at)) >= 0) { count++; at += needle.length(); }
        return count;
    }
}
