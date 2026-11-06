package com.juicyslew.moonstation14.ms14.shuttle.sable;

import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class YawPerturbationDiagnosticPlanTest {
    @Test void readinessRequiresFortyConsecutiveActuallyStableSamplesAndResetsOnOutlier() {
        int consecutive = 0;
        for (int i = 0; i < YawPerturbationDiagnosticPlan.STEADY_TICKS_REQUIRED - 1; i++) {
            consecutive = YawPerturbationDiagnosticPlan.nextSteadyCount(consecutive,
                    YawPerturbationDiagnosticPlan.stableYaw(.001, 4.005, -.001));
        }
        assertEquals(39, consecutive);
        consecutive = YawPerturbationDiagnosticPlan.nextSteadyCount(consecutive,
                YawPerturbationDiagnosticPlan.stableYaw(.02, 4, 0));
        assertEquals(0, consecutive);
        for (int i = 0; i < 40; i++) consecutive = YawPerturbationDiagnosticPlan.nextSteadyCount(consecutive,
                YawPerturbationDiagnosticPlan.stableYaw(0, 4, 0));
        assertEquals(40, consecutive);
        assertFalse(YawPerturbationDiagnosticPlan.stableYaw(Double.NaN, 4, 0));
    }

    @Test void nativeStepClockRequiresOneWorldTickAndSixAppliedSubsteps() {
        assertTrue(YawPerturbationDiagnosticPlan.consecutiveWorldStep(100, 101, 600, 606));
        assertFalse(YawPerturbationDiagnosticPlan.consecutiveWorldStep(100, 102, 600, 606));
        assertFalse(YawPerturbationDiagnosticPlan.consecutiveWorldStep(100, 101, 600, 612));
    }

    @Test void physicalLocalRollStimulusTransformsAndVerifiesTheExactObservedVelocityDelta() {
        var q = new Quaterniond().rotationY(Math.PI / 2);
        var expected = YawPerturbationDiagnosticPlan.worldRollDelta(q);
        assertEquals(YawPerturbationDiagnosticPlan.LOCAL_ROLL_DELTA, expected.length(), 1e-12);
        assertEquals(YawPerturbationDiagnosticPlan.LOCAL_ROLL_DELTA, expected.x, 1e-12);
        var before = new Vector3d(0, 4, 0);
        var after = new Vector3d(before).add(expected);
        assertTrue(YawPerturbationDiagnosticPlan.achievedDelta(before, after, expected));
        assertFalse(YawPerturbationDiagnosticPlan.achievedDelta(before, after, new Vector3d(0, 0, .014)));
        var scaled = new Quaterniond(q).mul(5);
        var negativeScaled = new Quaterniond(-scaled.x, -scaled.y, -scaled.z, -scaled.w);
        assertTrue(expected.distance(YawPerturbationDiagnosticPlan.worldRollDelta(scaled)) < 1e-12);
        assertTrue(expected.distance(YawPerturbationDiagnosticPlan.worldRollDelta(negativeScaled)) < 1e-12);
    }

    @Test void qSignAndTiltMetricsAreNormalizedAndUnsafeEventIsFirstOnly() {
        var q = new Quaterniond().rotationZ(.4);
        assertEquals(Math.toDegrees(.4), YawPerturbationDiagnosticPlan.upTiltDegrees(q), 1e-10);
        assertEquals(YawPerturbationDiagnosticPlan.upTiltDegrees(q),
                YawPerturbationDiagnosticPlan.upTiltDegrees(new Quaterniond(-q.x, -q.y, -q.z, -q.w)), 1e-10);
        assertEquals(YawPerturbationDiagnosticPlan.upTiltDegrees(q),
                YawPerturbationDiagnosticPlan.upTiltDegrees(new Quaterniond(q).mul(17)), 1e-10);
        assertTrue(YawPerturbationDiagnosticPlan.finiteQuaternion(q));
        assertFalse(YawPerturbationDiagnosticPlan.finiteQuaternion(new Quaterniond(0, 0, 0, 0)));
        assertFalse(YawPerturbationDiagnosticPlan.finiteQuaternion(new Quaterniond(0, 0, 0, 1e-14)));
        assertFalse(YawPerturbationDiagnosticPlan.finiteQuaternion(new Quaterniond(Double.NaN, 0, 0, 1)));
        assertFalse(YawPerturbationDiagnosticPlan.finiteQuaternion(new Quaterniond(Double.MAX_VALUE, 0, 0, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> YawPerturbationDiagnosticPlan.upTiltDegrees(new Quaterniond(0, 0, 0, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> YawPerturbationDiagnosticPlan.worldRollDelta(new Quaterniond(0, 0, 0, 1e-14)));
        assertThrows(IllegalArgumentException.class,
                () -> YawPerturbationDiagnosticPlan.upTiltDegrees(new Quaterniond(Double.MAX_VALUE, 0, 0, 0)));
        var unsafe = new YawPerturbationDiagnosticPlan.FirstUnsafe();
        assertTrue(unsafe.first()); assertFalse(unsafe.first());
        assertEquals(2400, YawPerturbationDiagnosticPlan.REQUEST_DURATION);
        assertTrue(YawPerturbationDiagnosticPlan.STEADY_WAIT_MAX_TICKS + YawPerturbationDiagnosticPlan.HOLD_TICKS
                < YawPerturbationDiagnosticPlan.REQUEST_DURATION);
        assertEquals(3400, YawPerturbationDiagnosticPlan.TEST_TIMEOUT_TICKS);
    }

    @Test void taggedEntryDelegatesToTheSingleDriverAndDoesNotReissueYawRequest() throws Exception {
        Path root = Path.of(System.getProperty("user.dir"));
        while (root != null && !Files.exists(root.resolve("src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/SableOwnedBodyCarryGameTests.java"))) root = root.getParent();
        assertNotNull(root);
        String source = Files.readString(root.resolve("src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/SableOwnedBodyCarryGameTests.java"));
        int start = source.indexOf("private static void beginYawPerturbationStudy");
        int wait = source.indexOf("private static void awaitSteadyYaw", start);
        String requestStart = source.substring(start, wait);
        assertEquals(1, occurrences(requestStart, "ShuttleFixtureMotionService.start("));
        assertTrue(source.contains("awaitRotationLiftHeight(helper, fixture, handle, false, 0,"));
        assertTrue(source.contains("YawPerturbationDiagnosticPlan.worldRollDelta(pose.orientation())"));
        assertTrue(source.contains("YawPerturbationDiagnosticPlan.HOLD_TICKS"));
        assertTrue(occurrences(source, "YawPerturbationDiagnosticPlan.consecutiveWorldStep(") >= 2,
                "both settle and hold must consume the shared actual tick/substep premise");
        assertTrue(source.contains("snapshot.sdkSubsteps() != 6"));
        assertTrue(source.contains("run.unsafe.first()"));
        assertTrue(source.contains("phase=spinup-unsafe-capsize"));
        assertTrue(source.contains("phase=unsafe-capsize"));
        assertTrue(source.contains("phase=first-growth-spinup"));
        assertTrue(source.contains("inertiaComponents(mass.getInertiaTensor())"));
        assertTrue(source.contains("run.closed.get()"));
        assertTrue(source.contains("new YawPerturbationObservationWindow(fixture.plot().engineId())"));
        assertTrue(source.contains("NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, run.observationListener)"));
        assertTrue(source.contains("Consumer<dev.ryanhcode.sable.neoforge.event.ForgeSablePostPhysicsTickEvent> observationListener"));
        assertFalse(source.contains("ForgeSablePrePhysicsTickEvent> observationListener"));
        assertTrue(source.contains("event.getPhysicsSystem().getLevel() != run.fixture.level()"));
        assertTrue(source.contains("latestAngularControlObservation(fixture.level(), frame)"));
        assertTrue(source.contains("run.observations.accept(observation)"));
        assertTrue(source.contains("dumpYawObservations(run, \"unsafe-capsize\")"));
        assertTrue(source.contains("for (int i = 0; i < dump.rows().size(); i++)"));
        assertTrue(source.contains("record={}/{} {}"));
        int dumpMethod = source.indexOf("private static void dumpYawObservations");
        int dumpEnd = source.indexOf("private static String yawObservationStatus", dumpMethod);
        String dumpSource = source.substring(dumpMethod, dumpEnd);
        assertTrue(dumpSource.contains("if (run.observations.dumped()) return"));
        assertTrue(dumpSource.contains("latestAngularControlObservation(fixture.level(), frame)"));
        assertTrue(dumpSource.contains("run.observations.accept(latest)"));
        assertTrue(dumpSource.contains("snapshot.appliedSubsteps()"));
        assertTrue(dumpSource.contains("markExpectedOrdinal"));
        String entry = Files.readString(root.resolve("src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/ShuttleYawPerturbationCauseGameTests.java"));
        assertTrue(entry.contains("SableOwnedBodyCarryGameTests.nativeYawFeedbackAfterRollPerturbation(helper)"));
    }

    @Test void actualFailureAssertionHeaderIsBoundedWhileFullUnsafeWitnessStaysInLogger() throws Exception {
        var frame = java.util.UUID.randomUUID();
        String header = YawPerturbationDiagnosticPlan.failureSummary("unsafe-capsize", frame, 937, 1587,
                "tilt=46.514 limit=45; full prior/current witness logged " + "native-witness".repeat(100));
        assertTrue(header.length() <= 384);
        assertTrue(("nativeYawFeedbackAfterRollPerturbation: " + header).length() < 512,
                "GameTest method prefix must still fit the writable-book failure payload");
        assertTrue(header.contains("phase=unsafe-capsize frame=" + frame + " sample=937 worldTick=1587"));
        assertTrue(header.contains("see [yaw-roll-cause] phase=unsafe-capsize"));
        assertFalse(header.contains("native-witness".repeat(20)));

        Path root = Path.of(System.getProperty("user.dir"));
        while (root != null && !Files.exists(root.resolve("src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/SableOwnedBodyCarryGameTests.java"))) root = root.getParent();
        assertNotNull(root);
        String source = Files.readString(root.resolve("src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/SableOwnedBodyCarryGameTests.java"));
        assertTrue(source.contains("failureSummary(\"unsafe-capsize\""));
        assertTrue(source.contains("previousNativeWitness, currentWitness, yawObservationStatus(run)"), "full witness retained in one-time logger");
        assertFalse(source.contains("new GameTestAssertException(\"stock yaw feedback exceeded gross native up-tilt cap"));
        assertFalse(source.contains("+\" current=\"+currentWitness"));
    }

    private static int occurrences(String text, String needle) {
        int count = 0, index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) { count++; index += needle.length(); }
        return count;
    }
}
