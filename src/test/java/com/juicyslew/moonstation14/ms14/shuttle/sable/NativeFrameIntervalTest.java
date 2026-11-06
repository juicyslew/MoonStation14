package com.juicyslew.moonstation14.ms14.shuttle.sable;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NativeFrameIntervalTest {
    private static final String CARRY_OWNER = "com/juicyslew/moonstation14/ms14/shuttle/sable/SableOwnedBodyCarryGameTests";
    private static final String MICRO_DIAGNOSTIC = CARRY_OWNER + "$MicroNativeDiagnostic";

    @Test void microNativeDiagnosticValidatesStoredHandleWithoutRewrappingIt() throws Exception {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        try (var in = NativeFrameIntervalTest.class.getClassLoader().getResourceAsStream(MICRO_DIAGNOSTIC + ".class")) {
            assertNotNull(in); new ClassReader(in).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        var listener = node.methods.stream().filter(method -> method.instructions != null
                && method.instructions.iterator().hasNext() && calls(method, "getPhysicsSystem")).toList();
        assertFalse(listener.isEmpty(), "compiled diagnostic listener checks its event physics system");
        assertTrue(listener.stream().anyMatch(method -> calls(method, "isValid")),
                "listener validates its stored native handle");
        assertTrue(listener.stream().noneMatch(method -> calls(method, "of")),
                "listener must not compare a freshly wrapped RigidBodyHandle by identity");
    }

    private static boolean calls(MethodNode method, String name) {
        for (var insn = method.instructions.getFirst(); insn != null; insn = insn.getNext())
            if (insn instanceof MethodInsnNode call && call.name.equals(name)) return true;
        return false;
    }

    @Test void ownedCarryClearsSupportInTheSceneAwareFixtureCallbackBeforeAwaitingBody() throws Exception {
        ClassNode node = carryFixtureClass();
        var callbacks = node.methods.stream().filter(m -> m.name.startsWith(
                "lambda$ownedCharacterRetainsFreshSubCutoffNativeFrameCarryOnce$") && argumentCount(m.desc) == 5).toList();
        assertEquals(1, callbacks.size(), "unique callback with helper plus four scene-aware arguments");
        MethodNode callback = callbacks.getFirst();
        int clear = callIndex(callback, "SableFixtureScene", "clearStoneSupportPad");
        int await = callIndex(callback, CARRY_OWNER, "awaitMicroCarryBody");
        assertTrue(clear >= 0 && await > clear, "support-pad ownership is cleared before the body wait in that callback");
        for (var insn = callback.instructions.getFirst(); insn != null && callback.instructions.indexOf(insn) < clear; insn = insn.getNext())
            if (insn instanceof MethodInsnNode call)
                assertFalse(call.name.equals("runAfterDelay") || call.name.equals("safeDelay"),
                        "no delayed callback before support clearing");
    }

    @Test void microCarryProbeHasOneImpulseThenOneIntervalWaitAndNoDirectMotionWrites() throws Exception {
        MethodNode probe = carryFixtureClass().methods.stream().filter(m -> m.name.equals("probeMicroCarry")
                && (m.access & Opcodes.ACC_PRIVATE) != 0).findFirst().orElseThrow();
        int impulses = 0, impulseIndex = -1, delayIndex = -1;
        for (var insn = probe.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (!(insn instanceof MethodInsnNode call)) continue;
            if (call.name.equals("applyLinearAndAngularImpulse") && call.owner.endsWith("RigidBodyHandle")) {
                impulses++; impulseIndex = probe.instructions.indexOf(insn);
            }
            if (call.name.equals("safeDelay") && call.owner.endsWith("BoundedGameTestWait")) delayIndex = probe.instructions.indexOf(insn);
            assertFalse(java.util.Set.of("teleport", "setPos", "setDeltaMovement", "setLinearVelocity", "setAngularVelocity").contains(call.name),
                    "probe must not directly write motion: " + call.name);
        }
        assertEquals(1, impulses, "probe applies exactly one native micro-impulse");
        assertTrue(delayIndex > impulseIndex, "probe waits one interval after impulse before sampling");
    }

    @Test void microYawProducerTargetsARealOffCenterPointWithoutLinearImpulse() {
        var lever = new org.joml.Vector3d(-.5, .5, -.5);
        var orientation = new org.joml.Quaterniond();
        var inertia = new org.joml.Matrix3d().identity().scale(20);
        var impulse = SableOwnedBodyCarryGameTests.microYawImpulse(lever, orientation, inertia, .001);
        assertNotNull(impulse);
        var angularMomentum = inertia.invert(new org.joml.Matrix3d()).transform(impulse, new org.joml.Vector3d());
        var worldOmega = orientation.transform(angularMomentum, new org.joml.Vector3d());
        var pointVelocity = worldOmega.cross(lever, new org.joml.Vector3d());
        assertEquals(.001, pointVelocity.length(), 1e-15);
        assertTrue(pointVelocity.x > 0, "dominant carrier point component is positive");
        var rotated = new org.joml.Quaterniond().rotationY(worldOmega.y * .05)
                .transform(new org.joml.Vector3d(lever), new org.joml.Vector3d());
        var delta = rotated.sub(lever, new org.joml.Vector3d());
        assertTrue(delta.length() > 16 * Math.ulp(35.0));
        assertTrue(delta.length() < 1e-4);
        assertTrue(delta.x >= 1.25e-5);
        assertEquals(lever.y, rotated.y, 0);
        assertNull(SableOwnedBodyCarryGameTests.microYawImpulse(new org.joml.Vector3d(.01, 0, 0),
                orientation, inertia, .001), "near-zero horizontal lever arm is rejected");
        assertNull(SableOwnedBodyCarryGameTests.microYawImpulse(lever, orientation,
                new org.joml.Matrix3d().zero(), .001), "zero inertia cannot produce an impulse");
    }

    private static ClassNode carryFixtureClass() throws Exception {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        try (var in = NativeFrameIntervalTest.class.getClassLoader().getResourceAsStream(CARRY_OWNER + ".class")) {
            assertNotNull(in); new ClassReader(in).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        return node;
    }
    private static int callIndex(MethodNode method, String ownerSuffix, String name) {
        for (var i = method.instructions.getFirst(); i != null; i = i.getNext())
            if (i instanceof MethodInsnNode call && call.owner.endsWith(ownerSuffix) && call.name.equals(name))
                return method.instructions.indexOf(i);
        return -1;
    }
    private static int argumentCount(String descriptor) { return org.objectweb.asm.Type.getArgumentTypes(descriptor).length; }

    @Test void microCarryFixtureBudgetsCoverControllerSettleAndRealSetupPhases() {
        int scene = SableOwnedBodyCarryGameTests.MICRO_SCENE_TICKS;
        int mass = SableOwnedBodyCarryGameTests.MICRO_MASS_TICKS;
        int initial = SableOwnedBodyCarryGameTests.MICRO_INITIAL_HOVER_TICKS;
        int support = SableOwnedBodyCarryGameTests.MICRO_SUPPORT_TICKS;
        int post = SableOwnedBodyCarryGameTests.MICRO_POST_SUPPORT_HOVER_TICKS;
        int sample = SableOwnedBodyCarryGameTests.MICRO_SAMPLE_TICKS;
        int dispatch = 4, margin = 29;
        assertTrue(scene + mass + initial + support + post + sample + dispatch + margin
                <= SableOwnedBodyCarryGameTests.MICRO_TEST_DEADLINE_TICKS);
        assertTrue(initial + support + post + sample + dispatch
                < SableOwnedBodyCarryGameTests.MICRO_SERVICE_TICKS);
        try {
            var method = SableOwnedBodyCarryGameTests.class.getDeclaredMethod(
                    "ownedCharacterRetainsFreshSubCutoffNativeFrameCarryOnce",
                    net.minecraft.gametest.framework.GameTestHelper.class);
            assertEquals(SableOwnedBodyCarryGameTests.MICRO_TEST_DEADLINE_TICKS,
                    method.getAnnotation(net.minecraft.gametest.framework.GameTest.class).timeoutTicks());
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        // Ideal feedback-controller settling bounds only; this is not evidence about native SDK quantization.
        int settleAtOne = (int) Math.ceil(Math.log(.55 / .0005) / 2 / .05)
                + SableOwnedBodyCarryGameTests.MICRO_STABLE_SAMPLES;
        int settleAtCap = (int) Math.ceil((2.0 - 1.0) / (2.0 * .05)
                + Math.log(1.0 / .0005) / 2 / .05) + SableOwnedBodyCarryGameTests.MICRO_STABLE_SAMPLES;
        assertTrue(settleAtOne < SableOwnedBodyCarryGameTests.MICRO_INITIAL_HOVER_TICKS);
        assertTrue(settleAtCap < SableOwnedBodyCarryGameTests.MICRO_INITIAL_HOVER_TICKS);
        assertTrue((int) Math.ceil(Math.log(.0917 / .0005) / 2 / .05)
                + SableOwnedBodyCarryGameTests.MICRO_STABLE_SAMPLES
                < SableOwnedBodyCarryGameTests.MICRO_POST_SUPPORT_HOVER_TICKS);
    }

    @Test void rotatingJumpLandingWitnessAdmitsHorizontalTravelAndRejectsInvalidEvidence() {
        UUID frame = UUID.randomUUID();
        var pos = new net.minecraft.core.BlockPos(4, 0, 5);
        var state = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
        var request = new com.juicyslew.moonstation14.ms14.movement.MovementVector(-.007478732832744823,
                -.4448, .15029594046889044);
        var measured = new com.juicyslew.moonstation14.ms14.movement.MovementVector(.01, -.02, .03);
        var row = new CurrentFrameCollisionScope.NativeCollisionTrace(frame, 1, 1, 1, pos, state,
                "stone", "stone", 0, 1, 0, 0, -.4, 0, .01, .38, .03, 0, .38, 0, null, true);
        var rows = java.util.List.of(row);
        java.util.function.Predicate<CurrentFrameCollisionScope.NativeCollisionTrace> known = r ->
                r.selectedPos().getX() == 4 && r.selectedPos().getY() == 0 && r.selectedPos().getZ() == 5
                        && r.selectedState().equals(state);
        assertTrue(NativeJumpFlightPhase.landingWitnessAdmissible(true, true, true, true, true, true,
                request, measured, .01, -.02, .03, rows, frame, known));
        assertFalse(SableOwnedBodyMotion.nativeFloorTraceAdmissible(request, .01, -.02, .03,
                rows, frame, known), "idle floor correction certificate must reject ballistic horizontal request");
        assertFalse(NativeJumpFlightPhase.landingWitnessAdmissible(false, true, true, true, true, true,
                request, measured, .01, -.02, .03, rows, frame, known), "stale sample");
        assertFalse(NativeJumpFlightPhase.landingWitnessAdmissible(true, true, false, true, true, true,
                request, measured, .01, -.02, .03, rows, frame, known), "wrong tracked frame");
        assertFalse(NativeJumpFlightPhase.landingWitnessAdmissible(true, true, true, true, true, false,
                request, measured, .01, -.02, .03, rows, frame, known), "non-upward contact");
        assertFalse(NativeJumpFlightPhase.landingWitnessAdmissible(true, true, true, true, true, true,
                request, measured, .02, -.02, .03, rows, frame, known), "native-motion mismatch");
        assertFalse(NativeJumpFlightPhase.landingWitnessAdmissible(true, false, true, true, true, true,
                request, measured, .01, -.02, .03, rows, frame, known), "incomplete trace");
        assertFalse(NativeJumpFlightPhase.landingWitnessAdmissible(true, true, true, true, true, true,
                request, measured, .01, -.02, .03, java.util.List.of(), frame, known), "empty trace");
        assertFalse(NativeJumpFlightPhase.landingWitnessAdmissible(true, true, true, true, true, true,
                request, measured, .01, -.02, .03, rows, UUID.randomUUID(), known), "foreign trace frame");
        assertFalse(NativeJumpFlightPhase.landingWitnessAdmissible(true, true, true, true, true, true,
                request, measured, .01, -.02, .03, rows, frame, r -> false), "unknown/out-of-deck state");
        var head = new CurrentFrameCollisionScope.NativeCollisionTrace(frame, 1, 1, 1, pos, state,
                "stone", "stone", 0, -1, 0, 0, -.4, 0, .01, -.6, .03, 0, -.6, 0, null, true);
        assertFalse(NativeJumpFlightPhase.landingWitnessAdmissible(true, true, true, true, true, true,
                request, measured, .01, -.02, .03, java.util.List.of(head), frame, known), "negative/head response");
    }

    @Test void rotatingJumpPhaseRequiresFreshDepartureAirProofKnownLandingAndTwelveObservations() {
        UUID frame = UUID.randomUUID();
        var phase = new NativeJumpFlightPhase(frame);
        assertFalse(phase.observeDeparture(false, true, frame, null), "stale collision cannot authorize impulse");
        assertFalse(phase.observeDeparture(true, false, frame, null), "grounded observation cannot authorize impulse");
        assertFalse(phase.observeDeparture(true, true, UUID.randomUUID(), null), "wrong frame cannot authorize impulse");
        assertFalse(phase.observeDeparture(true, true, frame, UUID.randomUUID()), "unrelated post-frame cannot authorize impulse");
        assertTrue(phase.observeDeparture(true, true, frame, null));
        assertFalse(phase.observeDeparture(true, true, frame, null), "departure impulse is one-shot");
        phase.observeAir(true, true, true, 1, 0.8, false);
        phase.observeAir(true, true, true, 0.8, 0.4, false);
        phase.observeAir(true, true, true, -0.1, -0.4, true);
        phase.observeAir(true, true, true, -0.4, -0.7, false);
        assertTrue(phase.airProofComplete());
        phase.observeLanding(true, true);
        for (int i = 0; i < 7; i++) phase.observeGrounded(true, true);
        assertEquals(12, phase.observations());
        assertThrows(IllegalStateException.class, () -> phase.observeGrounded(true, true));
    }

    @Test void rotatingJumpPhaseRejectsLandingWithoutAirProofAndInvalidSamples() {
        UUID frame = UUID.randomUUID();
        var phase = new NativeJumpFlightPhase(frame);
        assertThrows(IllegalStateException.class, () -> phase.observeAir(true, true, true, 1, 1, false));
        assertTrue(phase.observeDeparture(true, true, frame, null));
        assertThrows(IllegalStateException.class, () -> phase.observeAir(true, true, false, 1, 1, false),
                "contact samples do not count as flight proof");
        assertThrows(IllegalStateException.class, () -> phase.observeLanding(true, true));
    }

    @Test void rotatingJumpPhaseAllowsTwelveAirObservationsButNoThirteenthLanding() {
        UUID frame = UUID.randomUUID();
        var phase = new NativeJumpFlightPhase(frame);
        assertTrue(phase.observeDeparture(true, true, frame, null));
        for (int i = 0; i < 12; i++)
            phase.observeAir(true, true, true, i < 6 ? 1 : -1, i < 6 ? 1 : -1, true);
        assertEquals(12, phase.observations());
        assertThrows(IllegalStateException.class, () -> phase.observeLanding(true, true));
        assertEquals(12, phase.observations());
    }

    @Test void corpseDepartureOracleSeparatesSingleInheritanceFromOrdinaryAirDrag() {
        double retention = .91F;
        double preX = 1.792223577852381, preZ = 1.7922325930505423;
        double postX = 1.6309235028486004, postZ = 1.6309317066791638;
        double residual = Math.hypot(postX - preX * retention, postZ - preZ * retention);
        assertTrue(residual < 1e-12, "captured airborne sample matches ordinary single-tick air drag");
        assertTrue(Math.hypot(postX - preX, postZ - preZ) > .1,
                "raw pre/post delta is expected drag, not replayed inheritance");
        double duplicateCarrierResidual = Math.hypot(postX - (preX * retention + 1.792223577852381),
                postZ - (preZ * retention + 1.7922325930505423));
        assertTrue(duplicateCarrierResidual > .1, "second old-carrier impulse is rejected by the existing residual bound");
        double doubleDragResidual = Math.hypot(postX - preX * retention * retention,
                postZ - preZ * retention * retention);
        assertTrue(doubleDragResidual > .1, "an unintended second motor application is rejected");
        double initialOwnX = 0, carrierX = 1.792223577852381;
        assertEquals(carrierX, initialOwnX + carrierX, 1e-12, "departure still checks one full carrier inheritance");
    }

    @Test void immutablePoseFingerprintsAndIntervalsAreBodyEpochScoped() {
        double[] source = new double[16]; source[0] = source[5] = source[10] = source[15] = 1; source[12] = 1;
        var value = new NativeFrameInterval.PoseValue(source);
        var pair = new NativeFrameInterval.PosePair(value, value);
        var pairCopy = new NativeFrameInterval.PosePair(new NativeFrameInterval.PoseValue(value.matrix()), new NativeFrameInterval.PoseValue(value.matrix()));
        assertTrue(value.toString().contains("[1.0, 0.0, 0.0, 0.0"));
        assertFalse(value.toString().contains("[D@"));
        source[12] = 99;
        assertEquals(pair, pairCopy);
        assertEquals(value, new NativeFrameInterval.PoseValue(value.matrix()), "diagnostic value equality stays exact");
        var policy = new NativeFrameInterval();
        UUID frame = UUID.randomUUID();
        assertTrue(policy.consumeIfChanged(1, frame, pair));
        assertFalse(policy.consumeIfChanged(1, frame, pairCopy));
        source[12] = 99;
        var changedValue = new NativeFrameInterval.PoseValue(source);
        var changed = new NativeFrameInterval.PosePair(changedValue, changedValue);
        assertTrue(policy.consumeIfChanged(1, frame, changed));
        assertFalse(policy.consumeIfChanged(1, frame, changed));
        assertTrue(policy.consumeIfChanged(1, UUID.randomUUID(), changed));
        assertTrue(policy.consumeIfChanged(2, frame, changed));
    }
}
