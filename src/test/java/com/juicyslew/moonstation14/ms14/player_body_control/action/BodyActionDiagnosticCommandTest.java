package com.juicyslew.moonstation14.ms14.player_body_control.action;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BodyActionDiagnosticCommandTest {
    @Test
    void absentOrUncommittedBodyAndStaleSnapshotShareBoundedFailure() {
        var noBody = BodyActionDiagnosticCommand.Result.NO_EXACT_BODY;
        assertEquals(noBody, BodyActionDiagnosticCommand.decision(false, false, false, false));
        assertEquals(noBody, BodyActionDiagnosticCommand.decision(false, true, true, false));
        assertEquals(noBody, BodyActionDiagnosticCommand.decision(true, false, false, false));
        assertEquals(noBody, BodyActionDiagnosticCommand.decision(true, false, true, true));
    }

    @Test
    void currentExactActorWithoutLiveComponentReportsAbsenceOnly() {
        assertEquals(BodyActionDiagnosticCommand.Result.NO_COMPONENT,
                BodyActionDiagnosticCommand.decision(true, true, false, false));
    }

    @Test
    void componentObservationReportsOnlyAndDoesNotGrantOrExecuteAnAction() {
        // The decision only returns a fixed diagnostic; there is no action callback or mutation path.
        assertEquals(BodyActionDiagnosticCommand.Result.COMPONENT_ENABLED,
                BodyActionDiagnosticCommand.decision(true, true, true, false));
        assertEquals("Exact CHARACTER body has ComplexInteraction enabled; no action executed.",
                BodyActionDiagnosticCommand.decision(true, true, true, false).message);
        assertEquals(BodyActionDiagnosticCommand.Result.STUNNED,
                BodyActionDiagnosticCommand.decision(true, true, true, true));
    }

    @Test
    void messagesAreFixedAndDoNotExposeBodyOrMindIdentifiers() {
        String id = UUID.randomUUID().toString();
        for (var result : BodyActionDiagnosticCommand.Result.values()) {
            assertFalse(result.message.contains(id));
            assertFalse(result.message.contains("UUID"));
            assertFalse(result.message.contains("epoch"));
            assertFalse(result.message.matches(".*[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}.*"));
        }
    }

    @Test
    void commandAndAuthorityLoadWithoutClientClasses() throws Exception {
        assertNotNull(Class.forName(BodyActionDiagnosticCommand.class.getName(), true,
                BodyActionDiagnosticCommand.class.getClassLoader()));
        assertNotNull(Class.forName(BodyActionAuthority.class.getName(), true,
                BodyActionAuthority.class.getClassLoader()));
    }
}
