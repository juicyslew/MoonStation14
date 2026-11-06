package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Guards against a policy-only implementation disconnected from the live CHARACTER lifecycle path. */
class LifecycleCharacterInputWiringTest {
    @Test void worldCadenceFixtureWaitsForGlobalFrameRetirementBeforeStartingActor() throws IOException, NoSuchMethodException {
        var fixture = com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.CharacterInputCadenceGameTests.class;
        var entry = fixture.getDeclaredMethod("inputBurstIsHeldByWorldCadence", net.minecraft.gametest.framework.GameTestHelper.class);
        var metadata = entry.getAnnotation(net.minecraft.gametest.framework.GameTest.class);
        assertEquals("character_input_cadence_world_only", metadata.batch());
        assertTrue(!metadata.batch().isBlank() && !metadata.batch().equals("default"));
        assertEquals(400, metadata.timeoutTicks(), "80 ticks are reserved for bounded world-isolation setup");

        ClassNode node = new ClassNode();
        try (var stream = fixture.getResourceAsStream(
                "/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/server/CharacterInputCadenceGameTests.class")) {
            if (stream == null) throw new IOException("missing compiled cadence GameTest fixture");
            new ClassReader(stream).accept(node, 0);
        }
        var entryCode = node.methods.stream().filter(method -> method.name.equals(entry.getName())).findFirst().orElseThrow();
        assertTrue(calls(entryCode).contains("BoundedGameTestWait.await"), "bounded isolation gate runs before fixture startup");
        var start = node.methods.stream().filter(method -> method.name.equals("startCadenceFixture")).findFirst().orElseThrow();
        assertTrue(calls(start).contains("LifecycleHandActorFixture.start"), "actor starts only in the await completion callback");
        assertTrue(node.methods.stream().flatMap(method -> java.util.stream.StreamSupport.stream(method.instructions.spliterator(), false))
                        .filter(instruction -> instruction instanceof MethodInsnNode call && call.name.equals("getAllSubLevels"))
                        .count() >= 2,
                "the gate and retained strict WORLD invariant both inspect the global sublevel container");
    }

    @Test void soleSnapshotProducerStampsCompletedSableStepAndBodyReference() throws IOException {
        ClassNode node = new ClassNode();
        try (var stream = LifecycleCharacterSessionControl.class.getResourceAsStream(
                "/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/server/LifecycleCharacterSessionControl.class")) {
            if (stream == null) throw new IOException("missing compiled lifecycle controller");
            new ClassReader(stream).accept(node, 0);
        }
        var send = node.methods.stream().filter(method -> method.name.equals("sendCharacterSnapshot")).findFirst().orElseThrow();
        Set<String> calls = calls(send);
        assertTrue(calls.stream().anyMatch(call -> call.endsWith(".withServerTemporalMetadata")));
        assertTrue(calls.stream().anyMatch(call -> call.endsWith(".airMotionReference")));
        assertTrue(calls.stream().anyMatch(call -> call.endsWith(".completedSableStep")));
        var clock = node.methods.stream().filter(method -> method.name.equals("completedSableStep")).findFirst().orElseThrow();
        assertTrue(calls(clock).stream().anyMatch(call -> call.endsWith(".getInterpolationTick")));
        assertTrue(calls(clock).stream().anyMatch(call -> call.endsWith(".getContainer")));
        assertTrue(!calls.contains("ServerLevel.getGameTime"), "SDK key is not inferred from Minecraft game time");
    }
    @Test
    void receiveQueuesThenTickDequeuesBeforeApplyingHeldStateAndBarriersDiscardQueue() throws IOException {
        ClassNode node = new ClassNode();
        try (var stream = LifecycleCharacterSessionControl.class.getResourceAsStream(
                "/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/server/LifecycleCharacterSessionControl.class")) {
            if (stream == null) throw new IOException("missing compiled lifecycle controller");
            new ClassReader(stream).accept(node, 0);
        }

        var receive = node.methods.stream().filter(method -> method.name.equals("onPayload")).findFirst().orElseThrow();
        var tick = node.methods.stream().filter(method -> method.name.equals("playerTick")).findFirst().orElseThrow();
        Set<String> receiveCalls = calls(receive);
        Set<String> tickCalls = calls(tick);
        assertTrue(receiveCalls.contains("GhostIntentGate.offerQueued"));
        boolean usesSharedCapacityValue = false;
        for (var instruction = receive.instructions.getFirst(); instruction != null; instruction = instruction.getNext())
            if (instruction instanceof IntInsnNode integer && integer.operand == 64) usesSharedCapacityValue = true;
        assertTrue(usesSharedCapacityValue && com.juicyslew.moonstation14.ms14.player_body_control.prediction
                        .GhostPredictionHistory.CAPACITY == 64,
                "runtime FIFO bound matches client prediction-history bound");
        assertTrue(!receiveCalls.contains("CharacterMovementInputState.accept"),
                "future queued state must not mutate the currently held movement state");
        assertTrue(tickCalls.contains("CharacterMovementInputState.acceptQueued"));
        ClassNode reducerNode = new ClassNode();
        try (var stream = CharacterMovementInputState.class.getResourceAsStream(
                "/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/server/CharacterMovementInputState.class")) {
            if (stream == null) throw new IOException("missing compiled CHARACTER input reducer");
            new ClassReader(stream).accept(reducerNode, 0);
        }
        var reducer = reducerNode.methods.stream().filter(method -> method.name.equals("acceptQueued")).findFirst().orElseThrow();
        assertTrue(calls(reducer).contains("GhostIntentGate.takeQueued"), "live reducer drains the bounded FIFO");
        assertTrue(calls(reducer).contains("CharacterMovementInputState.accept"));
        assertTrue(tickCalls.contains("CharacterMovementInputState.afterStep"),
                "the accepted batch is reduced before the single physical step consumes its jump pulse");
        assertTrue(tickCalls.contains("CharacterMovementInputState.held"));
        assertTrue(tickCalls.contains("CharacterMovementInputState.jumpForStep"));
        boolean dispatchTickRead = false, dispatchTickWrite = false;
        for (var instruction = tick.instructions.getFirst(); instruction != null; instruction = instruction.getNext())
            if (instruction instanceof FieldInsnNode field && field.name.equals("lastInputDispatchTick")) {
                dispatchTickRead |= field.getOpcode() == org.objectweb.asm.Opcodes.GETFIELD;
                dispatchTickWrite |= field.getOpcode() == org.objectweb.asm.Opcodes.PUTFIELD;
            }
        assertTrue(dispatchTickRead && dispatchTickWrite,
                "session callback dispatch is guarded once per server tick independently of physical-owner consumption");
        assertTrue(tickCalls.contains("LifecycleCharacterSessionControl.clearQueuedMovement"),
                "hold/resume barriers discard queued commands and clear held controls");
        var barrier = node.methods.stream().filter(method -> method.name.equals("clearQueuedMovement")).findFirst().orElseThrow();
        assertTrue(calls(barrier).contains("GhostIntentGate.discardQueuedThroughSequence"));
        assertTrue(calls(barrier).contains("CharacterMovementInputState.clear"));
        assertTrue(calls(barrier).contains("Math.max"), "barrier ACK advances only through a real discarded sequence");
        boolean readsAndWritesAck = false, writesAck = false;
        for (var instruction = barrier.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
            if (instruction instanceof FieldInsnNode field && field.name.equals("lastAppliedSequence")) {
                readsAndWritesAck |= field.getOpcode() == org.objectweb.asm.Opcodes.GETFIELD;
                writesAck |= field.getOpcode() == org.objectweb.asm.Opcodes.PUTFIELD;
            }
        }
        assertTrue(readsAndWritesAck && writesAck, "discard barrier settles the highest real sequence, never a synthetic one");
        assertTrue(tickCalls.contains("LifecycleCharacterSessionControl$WorldStep.step"),
                "per-tick held input reaches the existing world-step port");
    }

    @Test void productionDefaultNativeStepIsSingleCallWithReferenceForwardedAndNoServerCaptureWriter() throws IOException {
        ClassNode node = new ClassNode();
        try (var stream = LifecycleCharacterSessionControl.class.getResourceAsStream(
                "/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/server/LifecycleCharacterSessionControl$Session.class")) {
            if (stream == null) throw new IOException("missing compiled lifecycle controller");
            new ClassReader(stream).accept(node, 0);
        }
        var method = node.methods.stream().filter(candidate -> candidate.name.startsWith("lambda$new$"))
                .filter(candidate -> calls(candidate).contains("GroundedHarnessWorldStep.step")).findFirst().orElseThrow();
        long nativeCalls = java.util.stream.StreamSupport.stream(method.instructions.spliterator(), false)
                .filter(instruction -> instruction instanceof MethodInsnNode call
                        && call.owner.endsWith("GroundedHarnessWorldStep") && call.name.equals("step")).count();
        assertEquals(1, nativeCalls, "default delegates once to the native step");
        assertTrue(method.desc.contains("Optional"), "supported movement reference remains in helper signature");
        assertTrue(node.methods.stream().noneMatch(candidate -> candidate.name.equals("defaultWorldStep")));
        assertTrue(node.methods.stream().flatMap(candidate -> java.util.stream.StreamSupport.stream(candidate.instructions.spliterator(), false))
                        .noneMatch(instruction -> instruction instanceof org.objectweb.asm.tree.LdcInsnNode constant
                                && constant.cst instanceof String text && text.contains("[CONNECTED_JUMP] side=SERVER")),
                "server lifecycle has no automatic connected-jump trace writer");
    }

    private static Set<String> calls(org.objectweb.asm.tree.MethodNode method) {
        Set<String> result = new java.util.HashSet<>();
        for (var instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext())
            if (instruction instanceof MethodInsnNode call)
                result.add(call.owner.substring(call.owner.lastIndexOf('/') + 1) + "." + call.name);
        return result;
    }
}
