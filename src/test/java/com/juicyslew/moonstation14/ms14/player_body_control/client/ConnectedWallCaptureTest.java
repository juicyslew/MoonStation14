package com.juicyslew.moonstation14.ms14.player_body_control.client;

import org.junit.jupiter.api.Test;
import org.apache.logging.log4j.message.ParameterizedMessage;
import com.juicyslew.moonstation14.ms14.player_body_control.ConnectedJumpCaptureBudget;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class ConnectedWallCaptureTest {
    @Test void cadenceClockSurvivesOffsetAndExpiresAtSixteenTicksAcrossCapturePhases() {
        var capture=new ConnectedWallCapture(); UUID body=UUID.randomUUID();
        assertTrue(capture.armJump(1,2,body,100));
        assertTrue(capture.qualify(1,2,body,101,9,true,true,true,true,body));
        // With the historical wrong world clock, the +315 offset exceeds the window.
        var oldClock=new ConnectedJumpCaptureBudget();
        assertTrue(oldClock.begin(1,body,2,101,false));
        assertFalse(oldClock.sample(1,body,2,101+315,false));
        // Production-path budget sampling uses cadence, not that offset clock.
        assertTrue(capture.record(1,2,body,101,false));
        assertEquals(ConnectedJumpCaptureBudget.WINDOW_TICKS,capture.remaining());
        for (long tick=102;tick<=116;tick++) {
            assertTrue(capture.record(1,2,body,tick,false), "MOVE/SNAPSHOT/REPLAY at cadence "+tick);
            assertTrue(capture.record(1,2,body,tick,false), "same-tick nonrender accepted");
            boolean renderAccepted=capture.record(1,2,body,tick,true);
            if (tick<=113) assertTrue(renderAccepted, "within 12-render cap");
            else assertFalse(renderAccepted, "render cap does not stop window");
        }
        assertEquals(1,capture.remaining());
        assertFalse(capture.record(1,2,body,117,false));
        assertFalse(capture.jumpActive());
    }

    @Test void snapshotHookUsesCadenceAndLogsServerTickSeparately() throws Exception {
        // Source-wiring assertion: the snapshot hook must pass local cadence, while its
        // actual server tick remains separately logged (not used as the budget clock).
        Path sourcePath=Path.of(System.getProperty("user.dir"));
        while(sourcePath!=null && !Files.exists(sourcePath.resolve("src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/client/GhostControlClient.java"))) sourcePath=sourcePath.getParent();
        assertNotNull(sourcePath,"repository source root");
        String source=Files.readString(sourcePath.resolve("src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/client/GhostControlClient.java"),StandardCharsets.UTF_8);
        int hook=source.indexOf("if (wallCapture.jumpActive())",source.indexOf("snapshot.frameState().isPresent()"));
        String snapshotHook=source.substring(hook,source.indexOf("// Preserve the local view",hook));
        assertTrue(snapshotHook.contains("entity.getUUID(), cadenceTick, false"));
        assertTrue(snapshotHook.contains("snapshot.serverGameTick()"));
        assertFalse(snapshotHook.contains("entity.level().getGameTime(), false"));
    }

    @Test void connectedWallFormatRetainsAndLabelsEveryObservation() {
        Object[] values = new Object[20];
        for (int i = 0; i < values.length; i++) values[i] = "sentinel" + i;
        String formatted = new ParameterizedMessage(GhostControlClient.CONNECTED_WALL_FORMAT, values).getFormattedMessage();

        assertTrue(formatted.contains("historyAck=sentinel9 historyRecordedSeq=sentinel10 historyServerTick=sentinel11"));
        assertTrue(formatted.contains("lastReconcileSummary=sentinel12"));
        assertTrue(formatted.endsWith("camera=sentinel19"));
    }

    @Test void repeatedRenderSamplesShareOneTickAndWindowExpiresAtCap() {
        var capture = new ConnectedWallCapture();
        UUID body = UUID.randomUUID();
        assertTrue(capture.arm(4, 8, body));
        assertTrue(capture.sample(4, 8, body, 1));
        assertFalse(capture.sample(4, 8, body, 1));
        for (int i = 2; i <= ConnectedWallCapture.MAX_TICKS; i++) assertTrue(capture.sample(4, 8, body, i));
        assertEquals(0, capture.remaining());
        assertFalse(capture.sample(4, 8, body, 101));
    }

    @Test void observationReferenceIsBoundForWindowThenClearedWithoutChangingJumpSemantics(){
        var capture=new ConnectedWallCapture();UUID body=UUID.randomUUID(),ship=UUID.randomUUID();
        assertTrue(capture.arm(4,8,body,ship));assertEquals(ship,capture.observationReferenceFrame());
        assertTrue(capture.sample(4,8,body,1)); // reference persists through later airborne samples with no live tracker
        assertEquals(ship,capture.observationReferenceFrame());
        assertTrue(capture.armJump(4,8,body,2));assertNull(capture.observationReferenceFrame());
        assertTrue(capture.arm(4,8,body,ship));capture.reset();assertNull(capture.observationReferenceFrame());
        assertNull(ConnectedPredictionFixture.Render.selectObservationFrame(null,null));
        assertEquals(ship,ConnectedPredictionFixture.Render.selectObservationFrame(null,ship));
        UUID other=UUID.randomUUID();assertEquals(other,ConnectedPredictionFixture.Render.selectObservationFrame(other,ship));
    }

    @Test void epochMismatchAndResetStopCapture() {
        var capture = new ConnectedWallCapture();
        UUID body = UUID.randomUUID();
        assertTrue(capture.arm(4, 8, body));
        assertFalse(capture.sample(5, 8, body, 1));
        capture.reset();
        assertEquals(0, capture.remaining());
    }

    @Test void geometryObservationFollowsOnlyExactArmedWallOrQualifiedJumpIdentity() {
        var capture = new ConnectedWallCapture();
        UUID body = UUID.randomUUID();
        assertFalse(capture.observesGeometry(1, 2, body));
        assertTrue(capture.arm(4, 8, body));
        assertTrue(capture.observesGeometry(4, 8, body));
        assertFalse(capture.observesGeometry(5, 8, body));
        assertFalse(capture.observesGeometry(4, 9, body));
        assertFalse(capture.observesGeometry(4, 8, UUID.randomUUID()));
        assertTrue(capture.armJump(4, 8, body, 10));
        assertFalse(capture.observesGeometry(4, 8, body), "WAITING_JUMP preserves its no-capture phase");
        assertTrue(capture.qualify(4, 8, body, 11, 1, true, true, true, true, UUID.randomUUID()));
        assertTrue(capture.observesGeometry(4, 8, body), "qualified bounded JUMP window observes geometry");
        capture.stop();
        assertFalse(capture.observesGeometry(4, 8, body));
    }

    @Test void fullRejectionTraceIsOneShotOnlyForCompleteBoundActiveCapture() {
        var capture = new ConnectedWallCapture(); UUID body = UUID.randomUUID();
        assertFalse(capture.takeFullRejectionTrace(1, 2, body, 0, 0, true, 1));
        assertTrue(capture.arm(4, 8, body));
        assertFalse(capture.takeFullRejectionTrace(4, 8, body, 1, 10, false, 3));
        assertFalse(capture.takeFullRejectionTrace(4, 8, UUID.randomUUID(), 1, 10, true, 3));
        assertFalse(capture.takeFullRejectionTrace(4, 8, body, 1, 10, true, 0));
        assertTrue(capture.takeFullRejectionTrace(4, 8, body, 1, 10, true, 3));
        assertFalse(capture.takeFullRejectionTrace(4, 8, body, 1, 11, true, 3));
        assertTrue(capture.armJump(4, 8, body, 10));
        assertFalse(capture.takeFullRejectionTrace(4, 8, body, 10, 10, true, 1), "WAITING_JUMP is not eligible");
        assertTrue(capture.qualify(4, 8, body, 11, 2, true, true, true, true, body));
        assertFalse(capture.takeFullRejectionTrace(4, 8, body, 27, 10, true, 1), "expired jump window does not log");
        assertTrue(capture.takeFullRejectionTrace(4, 8, body, 12, 10, true, 1), "valid jump window");
        capture.stop();
        assertFalse(capture.takeFullRejectionTrace(4, 8, body, 12, 10, true, 1));
    }

    @Test void expiredWallWindowDeniesRejectionBeforeNextRenderSample() {
        var capture = new ConnectedWallCapture(); UUID body = UUID.randomUUID();
        assertTrue(capture.arm(4, 8, body));
        assertTrue(capture.sample(4, 8, body, 1, 10));
        assertFalse(capture.takeFullRejectionTrace(4, 8, body, 2, 60_000_000_011L, true, 2));
        assertEquals(ConnectedWallCapture.Mode.OFF, capture.mode());
    }

    @Test void postMoveRejectionCallsiteUsesCaptureBudgetAndFormatterOverload() throws Exception {
        Path root = Path.of(System.getProperty("user.dir"));
        while (root != null && !Files.exists(root.resolve("src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/client/GhostControlClient.java"))) root = root.getParent();
        assertNotNull(root);
        String source = Files.readString(root.resolve("src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/client/GhostControlClient.java"), StandardCharsets.UTF_8);
        int rejection = source.indexOf("if (result == null)");
        int budget = source.indexOf("wallCapture.takeFullRejectionTrace", rejection);
        int format = source.indexOf("describe(body, rejectedMove, fullRejectedTrace)", budget);
        assertTrue(rejection >= 0 && budget > rejection && format > budget);
        assertTrue(source.substring(rejection, format).contains("nativeCollisionTraceComplete()"));
    }

    @Test void moveOptionsReceivesOnlyTheExistingExactCaptureGate() throws Exception {
        Path root = Path.of(System.getProperty("user.dir"));
        while (root != null && !Files.exists(root.resolve("src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/client/GhostControlClient.java"))) root = root.getParent();
        assertNotNull(root);
        String source = Files.readString(root.resolve("src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/client/GhostControlClient.java"), StandardCharsets.UTF_8);
        int gate = source.indexOf("boolean observeNativeGeometry = wallCapture.observesGeometry(activeEpoch, body.getId(), body.getUUID())");
        assertTrue(gate >= 0);
        String movementCall = source.substring(gate, source.indexOf("if (moved.collisionInfoFresh())", gate));
        assertTrue(movementCall.contains("bodyPose, observeNativeGeometry, true"));
        assertFalse(movementCall.contains("DEBUG"));
    }

    @Test void sameEpochBodyRebindStopsCaptureAndPreservesBudget() {
        var capture = new ConnectedWallCapture();
        UUID body = UUID.randomUUID();
        assertTrue(capture.arm(4, 8, body));
        assertTrue(capture.sample(4, 8, body, 1));
        assertFalse(capture.sample(4, 9, UUID.randomUUID(), 2));
        assertEquals(0, capture.remaining());
    }

    @Test void missingBodyStopCannotResumeWhenBodyReturns() {
        var capture = new ConnectedWallCapture();
        UUID body = UUID.randomUUID();
        assertTrue(capture.arm(4, 8, body));
        assertTrue(capture.sample(4, 8, body, 1));
        capture.stop(); // render hook's exact-body lookup returned absent
        assertEquals(0, capture.remaining());
        assertFalse(capture.sample(4, 8, body, 2));
    }

    @Test void jumpWaitDoesNotSpendBudgetAndReplayCannotQualify() {
        var capture=new ConnectedWallCapture(); UUID body=UUID.randomUUID();
        assertTrue(capture.armJump(4,8,body,100));
        assertFalse(capture.qualify(4,8,body,101,7,false,true,true,true,UUID.randomUUID()));
        assertFalse(capture.qualify(4,8,body,101,7,true,true,false,true,null));
        assertEquals(0,capture.rows());
        assertFalse(capture.jumpActive());
        assertTrue(capture.qualify(4,8,body,102,8,true,true,true,true,UUID.randomUUID()));
        assertEquals(ConnectedJumpCaptureBudget.WINDOW_TICKS,capture.remaining());
        assertTrue(capture.record(4,8,body,103,false));
        assertTrue(capture.jumpActive());
    }

    @Test void waitingRingHasTwoImmutableRowsAndWindowExpiresOnElapsedGap() {
        var capture=new ConnectedWallCapture(); UUID body=UUID.randomUUID();
        assertTrue(capture.armJump(2,3,body,5));
        assertTrue(capture.waitingRender(2,3,body,5,"a"));
        assertTrue(capture.waitingRender(2,3,body,6,"b"));
        assertTrue(capture.waitingRender(2,3,body,7,"c"));
        assertTrue(capture.qualify(2,3,body,8,11,true,true,true,true,UUID.randomUUID()));
        var rows=capture.takePrelaunchRows();
        assertEquals(2,rows.size()); assertTrue(rows.get(0).row().contains("b"));
        assertFalse(capture.record(2,3,body,24,false));
        assertFalse(capture.jumpActive());
    }

    @Test void waitingExpiresAndIdentityLossStopsIt() {
        var capture=new ConnectedWallCapture(); UUID body=UUID.randomUUID();
        assertTrue(capture.armJump(2,3,body,10));
        assertFalse(capture.waiting(2,3,body,210));
        assertEquals(ConnectedWallCapture.Mode.OFF,capture.mode());
        assertTrue(capture.armJump(2,3,body,10));
        assertFalse(capture.qualify(3,3,body,11,1,true,true,true,true,UUID.randomUUID()));
        assertEquals(ConnectedWallCapture.Mode.OFF,capture.mode());
    }

}
