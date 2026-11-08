package com.juicyslew.moonstation14.ms14.hands.network;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Locks the production endpoint ordering: status admission must precede side effects. */
class StunActionAdmissionBytecodeTest {
    @Test void bodyRequestEntryGatesResolvedBodyBeforeItsOperationDispatch() throws IOException {
        var calls = calls(BodyHandRequestService.class, "apply");
        int gate = calls.indexOf("com/juicyslew/moonstation14/ms14/character/CharacterControlSystem.canAct");
        assertTrue(gate >= 0, "apply must gate the resolved authority body");
        for (String effect : List.of("BodyTimedIngestionService.startPuddle", "BodyHandItemTransfer.pickup",
                "BodyEquipmentTransfer.equip", "BodyEquippedStorageTransfer.store", "BodyLooseThrow.throwHeld",
                "BodyHandItemTransfer.drop")) {
            int sideEffect = find(calls, effect);
            if (sideEffect >= 0) assertTrue(gate < sideEffect, effect + " must follow body action admission");
        }
    }

    @Test void blockUseAdmissionGatesBeforeTargetProbe() throws IOException {
        var calls = calls(BodyHandRequestService.class, "inspectBlockUse");
        assertBefore(calls, "CharacterControlSystem.canAct", "BodyBlockUseTargetProbe.probe");
        assertEquals(1, count(calls(BodyHandRequestService.class, "admitBlockUse"),
                "BodyHandRequestService.inspectBlockUse"), "admission wrapper delegates to canonical inspection");
    }

    @Test void wireToolAdmissionGatesBeforeElectrifiedContact() throws IOException {
        var calls = calls(com.juicyslew.moonstation14.ms14.doors.ui.AirlockWireMenuService.class,
                "dispatchToolMenu");
        assertBefore(calls, "CharacterControlSystem.canAct", "AirlockBlockEntity.electrifiedContact");
    }

    @Test void wireCommandsGateOnceAtStartButQueriesAndSessionValidationRemainReadable() throws IOException {
        Class<?> service = com.juicyslew.moonstation14.ms14.doors.ui.AirlockWireMenuService.class;
        var apply = calls(service, "apply");
        int gate = find(apply, "CharacterControlSystem.canAct");
        int wire = find(apply, "AirlockBlockEntity.applyWireActor");
        assertTrue(gate >= 0 && wire > gate, "wire mutation follows command-start admission");
        assertEquals(1, count(apply, "CharacterControlSystem.canAct"),
                "do not recheck after an accepted wire action shocks its own actor");
        assertEquals(-1, find(calls(service, "valid"), "CharacterControlSystem.canAct"),
                "stun must not invalidate read-only wire session/query authority");
    }

    private static void assertBefore(List<String> calls, String first, String second) {
        int a = find(calls, first), b = find(calls, second);
        assertTrue(a >= 0 && b > a, first + " must precede " + second + ": " + calls);
    }

    private static int find(List<String> calls, String fragment) {
        for (int i = 0; i < calls.size(); i++) if (calls.get(i).contains(fragment)) return i;
        return -1;
    }

    private static long count(List<String> calls, String fragment) {
        return calls.stream().filter(call -> call.contains(fragment)).count();
    }

    private static List<String> calls(Class<?> owner, String methodName) throws IOException {
        String resource = "/" + owner.getName().replace('.', '/') + ".class";
        try (var input = owner.getResourceAsStream(resource)) {
            assertNotNull(input, "compiled class resource " + resource);
            List<String> result = new ArrayList<>();
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                            String signature, String[] exceptions) {
                    if (!name.equals(methodName)) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int opcode, String calledOwner, String calledName,
                                                              String calledDescriptor, boolean isInterface) {
                            result.add(calledOwner + "." + calledName);
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return result;
        }
    }
}
