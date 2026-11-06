package com.juicyslew.moonstation14.ms14.shuttle.fixture;

import org.junit.jupiter.api.Test;
import org.joml.Quaterniond;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ShuttleFixtureMotionAngularObservationTest {
    @Test void telemetryEntryDefaultsOffAndObservationContainsOnlyCopiedValueRecords() throws Exception {
        Class<?> entry = java.util.Arrays.stream(ShuttleFixtureMotionService.class.getDeclaredClasses())
                .filter(c -> c.getSimpleName().equals("Entry")).findFirst().orElseThrow();
        var constructor = entry.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object value = constructor.newInstance((Object) null);
        var enabled = entry.getDeclaredField("angularObservationEnabled"); enabled.setAccessible(true);
        var latest = entry.getDeclaredField("latestAngularObservation"); latest.setAccessible(true);
        var priorOmega = entry.getDeclaredField("lastPostActuationWorldAngularVelocity"); priorOmega.setAccessible(true);
        var priorOrientation = entry.getDeclaredField("lastPostActuationNativeOrientation"); priorOrientation.setAccessible(true);
        assertFalse(enabled.getBoolean(value));
        assertNull(latest.get(value));
        assertNull(priorOmega.get(value)); assertNull(priorOrientation.get(value));

        for (var component : ShuttleFixtureMotionService.AngularControlObservation.class.getRecordComponents()) {
            Class<?> type = component.getType();
            assertTrue(type.isPrimitive() || type == UUID.class || type.isRecord(), component.getName()+" must be copied/immutable: "+type);
            assertFalse(org.joml.Vector3dc.class.isAssignableFrom(type));
            assertFalse(org.joml.Quaterniondc.class.isAssignableFrom(type));
            assertFalse(org.joml.Matrix3dc.class.isAssignableFrom(type));
        }
        assertTrue(Modifier.isFinal(ShuttleFixtureMotionService.AngularControlObservation.class.getModifiers()));
        var q = new Quaterniond().rotationZ(.25);
        var observation = new ShuttleFixtureMotionService.AngularControlObservation(UUID.randomUUID(), 10, 4, 1d/120,
                new ShuttleFixtureMotionService.QuaternionValue(q.x, q.y, q.z, q.w),
                new ShuttleMotionMath.Vector(1,2,3), new ShuttleMotionMath.Vector(4,5,6),
                new ShuttleMotionMath.Vector(7,8,9), null, null,
                new ShuttleMotionMath.Vector(19,20,21), new ShuttleMotionMath.Vector(22,23,24),
                new ShuttleMotionMath.Vector(0,-9.8,0), new ShuttleMotionMath.Vector(13,14,15),
                new ShuttleMotionMath.Vector(16,17,18), new ShuttleFixtureMotionService.InertiaValue(1,0,0,0,2,0,0,0,3),
                42, .2, new ShuttleMotionRequest(0,0,0,4,2400));
        assertEquals(4, observation.controllerSubstepOrdinal());
        assertEquals(10, observation.sourceWorldGameTime());
        assertNull(observation.previousPostWorldAngularVelocity());
        assertEquals(19, observation.measuredUncontrolledAlpha().x());
        assertEquals(22, observation.uncontrolledAlphaInput().x());
    }

    @Test void actualPostPhysicsTapCopiesSourceInputsWithoutDuplicateMathImpulsesOrNativeQueries() throws Exception {
        String source = serviceSource();
        int method = source.indexOf("public static void onPostPhysics");
        int active = source.indexOf("if (level.getGameTime() >= e.deadline)", method);
        int catchAt = source.indexOf("} catch (RuntimeException failure)", active);
        String activeStep = source.substring(active, catchAt);
        assertEquals(1, occurrences(activeStep, "ShuttleMotionMath.computeControlled("),
                "legacy and freight modes share exactly one impulse calculator with mode-specific limits");
        assertEquals(0, occurrences(activeStep, "ShuttleMotionMath.compute("),
                "do not permit a second legacy math path in the native producer");
        assertEquals(1, occurrences(activeStep, "applyLinearAndAngularImpulse("));
        assertEquals(2, occurrences(activeStep, "h.getAngularVelocity(new Vector3d())"), "PRE and immediate POST are the existing source queries");
        assertEquals(1, occurrences(activeStep, "h.getLinearVelocity(new Vector3d())"));
        assertEquals(1, occurrences(activeStep, "mass.getMass()"));
        assertEquals(1, occurrences(activeStep, "mass.getInertiaTensor()"));
        assertEquals(1, occurrences(activeStep, "new AngularControlObservation("));
        int freeAlpha = activeStep.indexOf("measureUncontrolledAngularAcceleration(");
        int forecast = activeStep.indexOf("forecastNextWorldAngularAcceleration(");
        int compute = activeStep.indexOf("ShuttleMotionMath.computeControlled(");
        int impulse = activeStep.indexOf("h.applyLinearAndAngularImpulse(");
        int appliedState = activeStep.indexOf("e.lastAppliedCommand=nextCommand;");
        int postRead = activeStep.indexOf("immediatePostWorldOmega = h.getAngularVelocity");
        int telemetry = activeStep.indexOf("new AngularControlObservation(");
        assertTrue(freeAlpha < forecast && forecast < compute && compute < impulse && impulse < appliedState
                && appliedState < postRead && postRead < telemetry,
                "record command history only after applying the shared-calculator impulse and before telemetry");
        assertTrue(activeStep.contains("e.freightFlight")
                        && activeStep.contains("ShuttleFreightFlightProfile.CONTROL_LIMITS")
                        && activeStep.contains("ShuttleMotionMath.LEGACY_LIMITS"),
                "both existing motion and freight mode use the same calculator with their respective limits");
        assertTrue(activeStep.contains("measuredUncontrolledAlpha, previousPostNativeOrientation, pose.orientation()"));
        assertTrue(activeStep.contains("e.lastPostActuationNativeOrientation = immediatePostNativeOrientation"));
        for (String field : new String[] {"pose.orientation()", "sdkVelocity", "transportVelocity", "worldOmega",
                "previousPostWorldOmega", "previousPostNativeOrientation", "measuredUncontrolledAlpha", "observedFreeAlpha",
                "result.angularLocal()", "immediatePostWorldOmega",
                "inertia", "mergedMass", "e.drag", "e.request", "e.callbacks"})
            assertTrue(activeStep.contains(field), "observation must copy exact controller-local source " + field);
        assertTrue(activeStep.contains("if (e.angularObservationEnabled)"));
        assertTrue(activeStep.indexOf("if (e.angularObservationEnabled)") < activeStep.indexOf("new AngularControlObservation("),
                "opt-in false path must allocate no observation record");
        assertTrue(activeStep.contains("clearAngularObservation(e, true)"), "telemetry failure disables only observation");
    }

    @Test void scopedOptInAndRequestLifecycleResetLatestWithoutDisablingBeforeRequest() throws Exception {
        String source = serviceSource();
        String hooks = eventHooksSource();
        int start = source.indexOf("public static synchronized boolean start(");
        int transition = source.indexOf("private static void transition(", start);
        String requestStart = source.substring(start, transition);
        assertTrue(requestStart.contains("entry.latestAngularObservation = null"));
        assertFalse(requestStart.contains("entry.angularObservationEnabled = false"), "pre-enabled yaw study survives its one request start");
        assertTrue(requestStart.contains("entry.callbacks = 0"), "substep ordinal reuses request callback counter");
        assertTrue(requestStart.contains("entry.lastPostActuationWorldAngularVelocity = null"));
        assertTrue(requestStart.contains("entry.lastPostActuationNativeOrientation = null"));
        assertTrue(source.contains("public static synchronized boolean enableAngularControlObservation"));
        assertTrue(source.contains("!live(level, owner)"));
        assertTrue(source.contains("e.owner != owner"));
        assertTrue(source.contains("public static synchronized AngularControlObservation latestAngularControlObservation"));
        assertTrue(source.contains("e.request != null && e.angularObservationEnabled"));
        assertTrue(source.contains("public static synchronized boolean disableAngularControlObservation"));
        assertTrue(source.contains("public static synchronized boolean unregisterFixture"));
        assertTrue(source.contains("clearAngularObservation(e,true)"));
        assertTrue(source.contains("clearAngularObservation(e, true)"));
        assertTrue(source.contains("e.lastPostActuationNativeOrientation=null"));
        assertTrue(source.contains("e.request != null && e.angularObservationEnabled"));
        assertTrue(source.contains("if (!live(level,e.owner)) { transition(level,e,\"owner removed/replaced\"); it.remove(); continue; }"));
        assertTrue(source.contains("entry.lastPostActuationNativeOrientation = null; clearAngularObservation(entry, true)"),
                "level unload retires paired angular histories with telemetry");
        assertTrue(hooks.contains("ShuttleFixtureMotionService::onPostPhysics"), "controller tap remains on the existing normal post-physics hook");
        assertTrue(source.contains("controller's NORMAL post-physics callback before its"));
    }

    private static String serviceSource() throws Exception {
        Path root = Path.of(System.getProperty("user.dir"));
        while (root != null && !Files.exists(root.resolve("src/main/java/com/juicyslew/moonstation14/ms14/shuttle/fixture/ShuttleFixtureMotionService.java"))) root = root.getParent();
        assertNotNull(root);
        return Files.readString(root.resolve("src/main/java/com/juicyslew/moonstation14/ms14/shuttle/fixture/ShuttleFixtureMotionService.java"));
    }

    private static String eventHooksSource() throws Exception {
        Path root = Path.of(System.getProperty("user.dir"));
        while (root != null && !Files.exists(root.resolve("src/main/java/com/juicyslew/moonstation14/eventhooks/ModEventHooks.java"))) root = root.getParent();
        assertNotNull(root);
        return Files.readString(root.resolve("src/main/java/com/juicyslew/moonstation14/eventhooks/ModEventHooks.java"));
    }

    private static int occurrences(String source, String term) {
        int count = 0, offset = 0;
        while ((offset = source.indexOf(term, offset)) >= 0) { count++; offset += term.length(); }
        return count;
    }
}
