package com.juicyslew.moonstation14.ms14.shuttle.sable;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SableOwnedBodyMotionCompositionWiringTest {
    @Test void productionMoveUsesNormalActorPhaseGuardAndCallsObserverAfterNativeMovement() throws Exception {
        String name = SableOwnedBodyMotion.class.getName().replace('.', '/') + ".class";
        try (InputStream in = SableOwnedBodyMotion.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(in);
            ClassNode node = new ClassNode(Opcodes.ASM9);
            new ClassReader(in).accept(node, 0);
            MethodNode move = movementImplementation(node);
            List<MethodInsnNode> calls = new ArrayList<>();
            for (var instruction : move.instructions) if (instruction instanceof MethodInsnNode call) calls.add(call);
            int guard = index(calls, "mayCommit");
            int observe = index(calls, "observePhysicalMove");
            int afterPoint = lastIndex(calls, "currentActorPoint");
            assertTrue(guard >= 0, "production move must use the tested phase guard");
            assertTrue(observe > guard, "observer must be behind the phase guard");
            assertTrue(afterPoint >= 0 && observe > afterPoint, "observer must run after the final physical-point sample");
            assertTrue(calls.stream().anyMatch(c -> c.name.equals("sable$frameAnchorInitializationPending")));

            MethodInsnNode helperCall = calls.get(guard);
            AbstractInsnNode helperBranch = nextOpcode(helperCall);
            assertInstanceOf(JumpInsnNode.class, helperBranch, "phase result must control observer admission");
            JumpInsnNode jump = (JumpInsnNode) helperBranch;
            assertEquals(Opcodes.IFEQ, jump.getOpcode(),
                    "mayCommit(false) for initialization-pending actors must branch around the observer");
            int observerInstruction = instructionIndex(move, calls.get(observe));
            assertTrue(instructionIndex(move, jump.label) > observerInstruction,
                    "false phase result must jump past observePhysicalMove");
            assertTrue(observerInstruction < opcodeIndex(move, Opcodes.ARETURN),
                    "observer call must complete before move returns");
        }
    }

    @Test void postRestoreTraceIsGatedByNormalActorPhaseAndRunsAfterMoveResultConstruction() throws Exception {
        ClassNode node = readClass(SableOwnedBodyMotion.class);
        MethodNode move = movementImplementation(node);
        List<MethodInsnNode> calls = methodCalls(move);
        List<MethodInsnNode> traces = calls.stream().filter(c -> c.name.equals("tracePostRestoreMotion")).toList();
        assertEquals(1, traces.size(), "exactly one bounded trace seam belongs to the completed move");
        MethodInsnNode trace = traces.getFirst();
        int traceAt = instructionIndex(move, trace);
        MethodInsnNode phaseGuard = calls.stream().filter(c -> c.name.equals("mayCommit")
                && instructionIndex(move, c) < traceAt).reduce((a, b) -> b).orElseThrow();
        JumpInsnNode branch = assertInstanceOf(JumpInsnNode.class, nextOpcode(phaseGuard));
        assertEquals(Opcodes.IFEQ, branch.getOpcode(), "pending initialization (false commit phase) must skip tracing");
        assertTrue(instructionIndex(move, branch.label) > traceAt, "false phase result jumps beyond trace call");
        assertTrue(calls.stream().anyMatch(c -> c.name.equals("sable$frameAnchorInitializationPending")
                && instructionIndex(move, c) < instructionIndex(move, phaseGuard)));
        assertTrue(traceAt > instructionIndex(move, calls.stream().filter(c -> c.name.equals("observePhysicalMove")).findFirst().orElseThrow()));
        assertTrue(traceAt > instructionIndex(move, calls.stream().filter(c -> c.name.equals("currentActorPoint")).reduce((a,b)->b).orElseThrow()));
        assertTrue(traceAt > instructionIndex(move, calls.stream().filter(c -> c.name.equals("<init>")
                && c.owner.endsWith("SableOwnedBodyMotion$MoveResult")).findFirst().orElseThrow()));
        assertTrue(traceAt < opcodeIndex(move, Opcodes.ARETURN));
        int observerAt = instructionIndex(move, calls.stream().filter(c -> c.name.equals("observePhysicalMove")).findFirst().orElseThrow());
        int returnAt = opcodeIndex(move, Opcodes.ARETURN);
        assertFalse(calls.stream().anyMatch(c -> c.name.equals("currentActorPoint")
                        && instructionIndex(move, c) > observerAt && instructionIndex(move, c) < returnAt),
                "post-observer diagnostic tail must not add an actor-point query");
    }

    private static int index(List<MethodInsnNode> calls, String name) {
        for (int i = 0; i < calls.size(); i++) if (calls.get(i).name.equals(name)) return i;
        return -1;
    }
    private static MethodNode movementImplementation(ClassNode node) {
        MethodNode implementation = node.methods.stream().filter(m -> m.name.equals("move")
                && Type.getArgumentTypes(m.desc).length == 5
                && Type.getArgumentTypes(m.desc)[3].equals(Type.BOOLEAN_TYPE)
                && Type.getArgumentTypes(m.desc)[4].equals(Type.BOOLEAN_TYPE))
                .findFirst().orElseThrow();
        MethodNode forwarder = node.methods.stream().filter(m -> m.name.equals("move")
                && Type.getArgumentTypes(m.desc).length == 4
                && Type.getArgumentTypes(m.desc)[3].equals(Type.BOOLEAN_TYPE))
                .findFirst().orElseThrow();
        assertTrue(methodCalls(forwarder).stream().anyMatch(call -> call.owner.equals(node.name)
                        && call.name.equals("move") && call.desc.equals(implementation.desc)),
                "four-argument public move must forward to the five-argument implementation");
        return implementation;
    }
    private static ClassNode readClass(Class<?> type) throws Exception {
        String name = type.getName().replace('.', '/') + ".class";
        try (InputStream in = type.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(in);
            ClassNode node = new ClassNode(Opcodes.ASM9); new ClassReader(in).accept(node, 0); return node;
        }
    }
    private static List<MethodInsnNode> methodCalls(MethodNode method) {
        List<MethodInsnNode> result = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions) if (instruction instanceof MethodInsnNode call) result.add(call);
        return result;
    }
    private static int lastIndex(List<MethodInsnNode> calls, String name) {
        for (int i = calls.size() - 1; i >= 0; i--) if (calls.get(i).name.equals(name)) return i;
        return -1;
    }
    private static AbstractInsnNode nextOpcode(AbstractInsnNode instruction) {
        for (AbstractInsnNode next = instruction.getNext(); next != null; next = next.getNext())
            if (next.getOpcode() >= 0) return next;
        return null;
    }
    private static int instructionIndex(MethodNode method, AbstractInsnNode target) {
        int index = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction == target) return index;
            index++;
        }
        return -1;
    }
    private static int opcodeIndex(MethodNode method, int opcode) {
        for (AbstractInsnNode instruction : method.instructions)
            if (instruction.getOpcode() == opcode) return instructionIndex(method, instruction);
        return -1;
    }
}
