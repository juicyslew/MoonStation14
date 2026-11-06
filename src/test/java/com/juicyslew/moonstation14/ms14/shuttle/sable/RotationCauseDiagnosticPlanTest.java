package com.juicyslew.moonstation14.ms14.shuttle.sable;

import org.junit.jupiter.api.Test;
import org.joml.Quaterniond;
import static org.junit.jupiter.api.Assertions.*;

class RotationCauseDiagnosticPlanTest {
    @Test void phasesDriveYNotZAndOnlyRetargetAtActualCommandChanges() {
        assertEquals(new RotationCauseDiagnosticPlan.Phase("spinup", 0, 4), RotationCauseDiagnosticPlan.phaseAt(0));
        assertEquals("steady", RotationCauseDiagnosticPlan.phaseAt(360).label());
        assertEquals(new RotationCauseDiagnosticPlan.Phase("cross-positive", .5, 4), RotationCauseDiagnosticPlan.phaseAt(460));
        assertEquals(new RotationCauseDiagnosticPlan.Phase("cross-neutral", 0, 4), RotationCauseDiagnosticPlan.phaseAt(560));
        assertEquals(new RotationCauseDiagnosticPlan.Phase("cross-negative", -.5, 4), RotationCauseDiagnosticPlan.phaseAt(660));
        assertEquals(new RotationCauseDiagnosticPlan.Phase("brake", 0, 0), RotationCauseDiagnosticPlan.phaseAt(760));
        assertEquals(1160, RotationCauseDiagnosticPlan.TOTAL_TICKS);
        assertEquals(2400, RotationCauseDiagnosticPlan.REQUEST_DURATION);
        assertTrue(RotationCauseDiagnosticPlan.retargetAt(0));
        for (int tick = 1; tick < 1160; tick++)
            assertEquals(tick == 460 || tick == 560 || tick == 660 || tick == 760,
                    RotationCauseDiagnosticPlan.retargetAt(tick), "driver tick " + tick);
        assertThrows(IllegalArgumentException.class, () -> RotationCauseDiagnosticPlan.phaseAt(1160));
        assertEquals(RotationCauseDiagnosticPlan.SetupStage.LIFT, RotationCauseDiagnosticPlan.setupStage(false, false));
        assertEquals(RotationCauseDiagnosticPlan.SetupStage.HOVER, RotationCauseDiagnosticPlan.setupStage(true, false),
                "height readiness releases lift request before velocity settling is required");
        assertEquals(RotationCauseDiagnosticPlan.SetupStage.READY, RotationCauseDiagnosticPlan.setupStage(true, true));
        assertTrue(RotationCauseDiagnosticPlan.consecutiveWorldClock(100, 101));
        assertFalse(RotationCauseDiagnosticPlan.consecutiveWorldClock(100, 102), "model index cannot hide a skipped world tick");
        assertTrue(RotationCauseDiagnosticPlan.sixAppliedSteps(120, 126, false));
        assertFalse(RotationCauseDiagnosticPlan.sixAppliedSteps(120, 132, false), "same-request sample must advance six native steps");
        assertTrue(RotationCauseDiagnosticPlan.sixAppliedSteps(120, 140, true), "true retarget resets applied-step baseline");
    }

    @Test void quaternionErrorNormalizesAndTreatsSignAsEquivalent() {
        var identity = new Quaterniond(0, 0, 0, 2);
        assertEquals(0, QuaternionAngularDistance.radians(identity, new Quaterniond(0, 0, 0, 7)), 1e-12);
        assertEquals(0, QuaternionAngularDistance.radians(identity, new Quaterniond(0, 0, 0, -3)), 1e-12);
        var quarterTurn = new Quaterniond().rotationY(Math.PI / 2).mul(5);
        assertEquals(Math.PI / 2, QuaternionAngularDistance.radians(identity, quarterTurn), 1e-12);
        assertThrows(IllegalArgumentException.class,
                () -> QuaternionAngularDistance.radians(new Quaterniond(0, 0, 0, 0), identity));
    }
}
