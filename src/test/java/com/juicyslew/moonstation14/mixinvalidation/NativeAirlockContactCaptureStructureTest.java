package com.juicyslew.moonstation14.mixinvalidation;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies nominee storage from bytecode resources without loading any Mixin class. */
class NativeAirlockContactCaptureStructureTest {
    private static final String STORAGE = "/com/juicyslew/moonstation14/mixin/compat/"
            + "SableCollisionInfoAirlockContactsMixin.class";

    @Test
    void bypassWitnessUsesPreMoveBoundsRatherThanSolverBounds() throws Exception {
        Path sourcePath = Path.of(System.getProperty("user.dir"));
        while (sourcePath != null && !Files.exists(sourcePath.resolve("src/main/java/com/juicyslew/moonstation14/mixin/compat/"
                + "SableNativeAirlockContactCaptureMixin.java"))) sourcePath = sourcePath.getParent();
        assertNotNull(sourcePath, "project source tree is available to the source contract test");
        String source = Files.readString(sourcePath.resolve("src/main/java/com/juicyslew/moonstation14/mixin/compat/"
                + "SableNativeAirlockContactCaptureMixin.java"));
        assertTrue(source.contains("@Local(name = \"entityBounds\") net.minecraft.world.phys.AABB originalBounds"));
        assertTrue(source.contains("preMoveBody(originalBounds, entityBoundsOBB, subLevelPose)"));
        assertTrue(source.contains("preMoveDoorWitness = collisionTimeWitness(server, frame, candidatePos, candidate,\n                        preMoveBody"));
        assertFalse(source.substring(source.indexOf("private static OrientedBoundingBox3d preMoveBody"),
                source.indexOf("private static boolean collisionTimeWitness")).contains("pose.transformPosition(center)"),
                "native entity bounds are already world-coordinate bounds");
        assertTrue(source.contains("entityBoundsOBB, subLevelPose, normalizedMtv)) continue"),
                "the live solver OBB remains the genuine nominee witness");
        assertFalse(source.contains("true, false,\n                        collisionTimeWitness(server, frame, candidatePos, candidate, entityBoundsOBB"));
    }

    @Test
    void nomineeAppendHasNoCopiedSdkIterationCapAndCanRetainLaterRoots() throws Exception {
        ClassNode storage = readResource(STORAGE);
        MethodNode record = storage.methods.stream()
                .filter(method -> method.name.equals("moonstation14$recordAirlockContact"))
                .findFirst().orElseThrow(() -> new AssertionError("native nominee recorder bytecode missing"));
        boolean hasDedup = false;
        boolean hasAppend = false;
        int conditionalBranches = 0;
        for (AbstractInsnNode instruction : record.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals("java/util/List")) {
                if (call.name.equals("contains")) hasDedup = true;
                if (call.name.equals("add")) hasAppend = true;
                assertFalse(call.name.equals("size"), "nominee storage must not truncate after copied SDK iteration counts");
            }
            if (instruction instanceof JumpInsnNode jump) {
                conditionalBranches++;
                assertNotEquals(Opcodes.IF_ICMPGE, jump.getOpcode(), "no fixed nominee-count threshold");
                assertNotEquals(Opcodes.IF_ICMPGT, jump.getOpcode(), "no fixed nominee-count threshold");
            }
            assertFalse(instruction instanceof IntInsnNode integer && integer.operand == 40,
                    "SDK iteration internals must not become a flattened nominee cap");
        }
        assertTrue(hasDedup, "repeated selected-block nominations remain deduplicated");
        assertTrue(hasAppend, "later distinct roots append after earlier nominees regardless of list size");
        assertTrue(conditionalBranches <= 2, "recording branches only for list initialization and duplicate suppression");

        MethodNode claim = storage.methods.stream()
                .filter(method -> method.name.equals("moonstation14$claimAirlockSelection"))
                .findFirst().orElseThrow(() -> new AssertionError("selection memo bytecode missing"));
        boolean setDedup = false;
        for (AbstractInsnNode instruction : claim.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals("java/util/Set")) {
                if (call.name.equals("add")) setDedup = true;
                assertFalse(call.name.equals("size"), "selection memo also has no copied SDK count cap");
            }
            assertFalse(instruction instanceof IntInsnNode integer && integer.operand == 40,
                    "native SDK iteration limits are not reimplemented as selection truncation");
        }
        assertTrue(setDedup, "selection memo accepts later distinct selected blocks after deduping repeats");
    }

    private static ClassNode readResource(String path) throws Exception {
        try (InputStream input = NativeAirlockContactCaptureStructureTest.class.getResourceAsStream(path)) {
            assertNotNull(input, "mixin bytecode resource exists without loading it");
            ClassNode result = new ClassNode(Opcodes.ASM9);
            new ClassReader(input).accept(result, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return result;
        }
    }
}
