package com.juicyslew.moonstation14.ms14.mobs;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MedicalMovementTraceWindowTest {
    @Test void explicitReviveCallsTraceWithCommittedDeadToNextState() throws Exception {
        Path root = Path.of("").toAbsolutePath().normalize();
        Path source = null;
        while (root != null) {
            Path candidate = root.resolve(Path.of("src", "main", "java",
                    "com", "juicyslew", "moonstation14", "ms14", "mobs", "MobStateSystem.java"));
            if (Files.isRegularFile(candidate)) { source = candidate; break; }
            root = root.getParent();
        }
        assertNotNull(source, "MobStateSystem source must be available for caller-wiring regression");
        String code = Files.readString(source);
        int begin = code.indexOf("public static boolean explicitRevive(LivingEntity body)");
        int end = code.indexOf("private static void logTransition", begin);
        assertTrue(begin >= 0 && end > begin);
        String caller = code.substring(begin, end);
        int committedState = caller.indexOf("body.setData(ModDataAttachments.MOB_STATE.get()");
        String traceCall = "MedicalMovementTrace.transition(body, MobStateComponent.State.DEAD, transition.state().value());";
        int trace = caller.indexOf(traceCall);
        int event = caller.indexOf("new MobStateChangedEvent", trace);
        assertTrue(committedState >= 0 && trace > committedState && event > trace,
                "explicit revival must notify the observer after the real state update");
        assertEquals(trace, caller.lastIndexOf(traceCall), "one observer transition per successful revive");
    }

    @Test void transitionLifecycleOnlyOpensOnRealDownEdgesAndClosesOnRecovery() {
        assertEquals(MedicalMovementTrace.TransitionAction.OPEN,
                MedicalMovementTrace.transitionAction(com.juicyslew.moonstation14.ms14.character.components.MobStateComponent.State.ALIVE,
                        com.juicyslew.moonstation14.ms14.character.components.MobStateComponent.State.CRITICAL));
        assertEquals(MedicalMovementTrace.TransitionAction.OPEN,
                MedicalMovementTrace.transitionAction(com.juicyslew.moonstation14.ms14.character.components.MobStateComponent.State.CRITICAL,
                        com.juicyslew.moonstation14.ms14.character.components.MobStateComponent.State.DEAD));
        assertEquals(MedicalMovementTrace.TransitionAction.CLOSE,
                MedicalMovementTrace.transitionAction(com.juicyslew.moonstation14.ms14.character.components.MobStateComponent.State.CRITICAL,
                        com.juicyslew.moonstation14.ms14.character.components.MobStateComponent.State.ALIVE));
        assertEquals(MedicalMovementTrace.TransitionAction.CLOSE,
                MedicalMovementTrace.transitionAction(com.juicyslew.moonstation14.ms14.character.components.MobStateComponent.State.DEAD,
                        com.juicyslew.moonstation14.ms14.character.components.MobStateComponent.State.ALIVE));
        assertEquals(MedicalMovementTrace.TransitionAction.NONE,
                MedicalMovementTrace.transitionAction(com.juicyslew.moonstation14.ms14.character.components.MobStateComponent.State.CRITICAL,
                        com.juicyslew.moonstation14.ms14.character.components.MobStateComponent.State.CRITICAL));
    }

    @Test void exactLevelAndBodyIdentityAndImmutableSnapshot() {
        Object level = new Object(), body = new Object();
        var window = new MedicalMovementTraceWindow(level, body);
        assertTrue(window.matches(level, body));
        assertFalse(window.matches(level, new Object()));
        assertFalse(window.matches(new Object(), body));
        assertTrue(window.append(11, 7, new MedicalMovementTraceWindow.Point(1, 2, 3),
                new MedicalMovementTraceWindow.Point(0.12345678901234566, 0, 0), "edge"));
        assertTrue(window.append(12, 8, new MedicalMovementTraceWindow.Point(1, 2, 3),
                new MedicalMovementTraceWindow.Point(0, 0, 0), "sample"));
        var snapshot = window.close("test");
        assertEquals("test", snapshot.stopReason());
        assertEquals(2, snapshot.rowCount());
        assertEquals(7L, snapshot.firstWorldTick());
        assertEquals(8L, snapshot.lastWorldTick());
        assertEquals(1, snapshot.rows().get(1).serverTick() - snapshot.rows().get(0).serverTick());
        assertEquals("0.12345678901234566,0.0,0.0", snapshot.rows().getFirst().velocity().text());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.rows().clear());
        assertFalse(window.append(13, 9, null, null, "late"));
        assertEquals(2, window.close("again").rowCount());
    }

    @Test void capsAt160ThenCallerCanStopWithoutDroppedRow() {
        var window = new MedicalMovementTraceWindow(new Object(), new Object());
        var point = new MedicalMovementTraceWindow.Point(1, 2, 3);
        for (int i = 0; i < 160; i++) assertTrue(window.append(i, i + 20, point, point, "sample"));
        assertEquals(160, window.size());
        assertEquals(160L, window.close("row-cap").rowCount());
    }

    @Test void invalidOrMissingNumericDataIsPreservedAndFlagged() {
        var window = new MedicalMovementTraceWindow(new Object(), new Object());
        assertTrue(window.append(1, 1, new MedicalMovementTraceWindow.Point(Double.NaN, 2, 3),
                new MedicalMovementTraceWindow.Point(0, 0, 0), "invalidPosition"));
        assertTrue(window.append(2, 2, null, null, "missing"));
        var snapshot = window.close("test");
        assertFalse(snapshot.rows().get(0).finite());
        assertFalse(snapshot.rows().get(1).finite());
        assertTrue(Double.isNaN(snapshot.rows().get(0).position().x()));
        assertNull(snapshot.rows().get(1).position());
    }

    @Test void attemptedOverflowIsExplicit() {
        var window = new MedicalMovementTraceWindow(new Object(), new Object());
        var point = new MedicalMovementTraceWindow.Point(1, 2, 3);
        for (int i = 0; i < MedicalMovementTraceWindow.MAX_ROWS; i++)
            assertTrue(window.append(i, i, point, point, "sample"));
        assertFalse(window.append(161, 161, point, point, "overflow"));
        var snapshot = window.close("overflow");
        assertEquals(160, snapshot.rowCount());
        assertTrue(snapshot.truncated());
    }
}
