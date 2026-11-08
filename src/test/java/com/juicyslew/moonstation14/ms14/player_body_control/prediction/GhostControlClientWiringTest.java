package com.juicyslew.moonstation14.ms14.player_body_control.prediction;

import com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Handle;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Compiled-consumer checks: these assert the production client actually uses the tested owners. */
class GhostControlClientWiringTest {
    @Test void packetCallbackOnlyQueuesAndPostTickDispatchesThroughSchedule() throws Exception {
        List<String> network = calls("reconcileSnapshot");
        assertTrue(network.contains("com.juicyslew.moonstation14.ms14.player_body_control.prediction.OwnedPredictionApplicationSchedule.queue"), network.toString());
        assertFalse(network.contains("com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.reconcileSnapshotNow"), network.toString());
        assertFalse(network.contains("net.minecraft.world.entity.Entity.setPos"), network.toString());
        assertFalse(network.contains("net.minecraft.world.entity.Entity.lerpTo"), network.toString());

        List<String> postTick = calls("reconcileSnapshotOnClientTick");
        assertTrue(postTick.contains("com.juicyslew.moonstation14.ms14.player_body_control.prediction.OwnedPredictionApplicationSchedule.dispatchQueued"), postTick.toString());
        assertTrue(postTick.stream().anyMatch(call -> call.contains("dispatchQueued")), postTick.toString());
        assertTrue(callsContaining("lambda$reconcileSnapshotOnClientTick").stream()
                .anyMatch(call -> call.contains("GhostControlClient.reconcileSnapshotNow")));
        List<String> tick = calls("clientTick");
        assertTrue(tick.stream().anyMatch(call -> call.contains("GhostControlClient.applyClientTickPrediction")), tick.toString());

        assertTrue(calls("advancePredictionFromCurrent").contains(
                "com.juicyslew.moonstation14.ms14.player_body_control.prediction.OwnedPredictionApplicationSchedule.dispatchTicks"));
        assertTrue(calls("rebuildPrediction").contains(
                "com.juicyslew.moonstation14.ms14.player_body_control.prediction.OwnedPredictionApplicationSchedule.dispatchTicks"));
        assertFalse(calls("advancePredictionFromCurrent").contains(
                "com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.restorePredictionBase"));
        List<String> origins = fields("setOwnedBodyRenderFrom");
        assertTrue(origins.contains("xo") && origins.contains("yo") && origins.contains("zo"), origins.toString());
        assertTrue(origins.contains("xOld") && origins.contains("yOld") && origins.contains("zOld"), origins.toString());
    }

    @Test void animationIsOwnedDisplacementOnlyAndNeverRebuildWorldDelta() throws Exception {
        List<String> replay = calls("rebuildPrediction");
        assertFalse(replay.stream().anyMatch(call -> call.contains("OwnedHarnessWalkAnimation.update")), replay.toString());
        List<String> acceptedStep = calls("predictCharacterInTemporalScope");
        assertTrue(acceptedStep.stream().anyMatch(call -> call.contains("OwnedHarnessWalkAnimation.update")), acceptedStep.toString());
    }

    private static List<String> calls(String methodName) throws IOException {
        return callsMatching(methodName, false);
    }

    private static List<String> callsContaining(String methodName) throws IOException {
        return callsMatching(methodName, true);
    }

    private static List<String> callsMatching(String methodName, boolean contains) throws IOException {
        String resource = "/" + GhostControlClient.class.getName().replace('.', '/') + ".class";
        try (var input = GhostControlClient.class.getResourceAsStream(resource)) {
            assertNotNull(input, "compiled class resource " + resource);
            List<String> result = new ArrayList<>();
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                            String signature, String[] exceptions) {
                    if (contains ? !name.contains(methodName) : !name.equals(methodName)) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int opcode, String owner, String calledName,
                                                              String calledDescriptor, boolean isInterface) {
                            result.add(owner.replace('/', '.') + "." + calledName);
                        }
                        @Override public void visitInvokeDynamicInsn(String name, String descriptor,
                                                                     Handle bootstrapMethodHandle,
                                                                     Object... bootstrapMethodArguments) {
                            for (Object argument : bootstrapMethodArguments)
                                if (argument instanceof Handle handle)
                                    result.add(handle.getOwner().replace('/', '.') + "." + handle.getName());
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return result;
        }
    }

    private static List<String> fields(String methodName) throws IOException {
        String resource = "/" + GhostControlClient.class.getName().replace('.', '/') + ".class";
        try (var input = GhostControlClient.class.getResourceAsStream(resource)) {
            assertNotNull(input);
            List<String> result = new ArrayList<>();
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                            String signature, String[] exceptions) {
                    if (!name.equals(methodName)) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitFieldInsn(int opcode, String owner, String field, String descriptor) {
                            if (opcode == Opcodes.PUTFIELD) result.add(field);
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return result;
        }
    }
}
