package com.juicyslew.moonstation14.mixinvalidation;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Source-bytecode wiring regression for tracker packet ownership; not a Minecraft client fixture. */
class GhostOwnedEntityTrackerMixinTest {
    private static final String MIXIN = "com/juicyslew/moonstation14/mixin/client/GhostOwnedEntityTrackerMixin";
    private static final String CONTROL = "com/juicyslew/moonstation14/ms14/player_body_control/client/GhostControlClient";

    @Test
    void motionGuardRunsAfterMainThreadCheckAndCancelsOnlyOwnedCharacterPackets() throws Exception {
        ClassNode node = read("/" + MIXIN + ".class");
        MethodNode method = node.methods.stream().filter(m -> m.name.equals("moonstation14$keepOwnedGhostPrediction"))
                .findFirst().orElseThrow();
        MethodNode motion = node.methods.stream().filter(m -> annotation(m, "Lorg/spongepowered/asm/mixin/injection/Inject;") != null)
                .filter(m -> injectionTargets(m).contains("handleSetEntityMotion(Lnet/minecraft/network/protocol/game/ClientboundSetEntityMotionPacket;)V"))
                .findFirst().orElseThrow();
        AnnotationNode inject = annotation(motion, "Lorg/spongepowered/asm/mixin/injection/Inject;");
        assertNotNull(inject, "velocity handling must be intercepted");
        assertEquals(Boolean.TRUE, value(inject, "cancellable"));
        assertEquals(1, value(inject, "require"));
        @SuppressWarnings("unchecked") List<Object> atValues = (List<Object>) value(inject, "at");
        AnnotationNode at = (AnnotationNode) atValues.get(0);
        assertEquals("INVOKE", value(at, "value"));
        assertTrue(((String) value(at, "target")).contains("ensureRunningOnSameThread"));
        assertTrue(Arrays.equals(new String[]{"Lorg/spongepowered/asm/mixin/injection/At$Shift;", "AFTER"},
                (String[]) value(at, "shift")));

        List<String> calls = invokes(motion);
        assertTrue(calls.contains("getEntity"), calls.toString());
        assertTrue(calls.stream().anyMatch(s -> s.equals("ownsSableRemoteFrameInterpolation")), calls.toString());
        assertTrue(calls.stream().anyMatch(s -> s.equals("cancel")), calls.toString());
        assertTrue(motion.instructions.iterator().hasNext());
        assertTrue(method.name.equals("moonstation14$keepOwnedGhostPrediction"));
        assertTrue(node.methods.stream().anyMatch(m -> m.name.equals("moonstation14$barrierOwnedGhostPrediction")), "teleport barrier retained");
        assertTrue(node.methods.stream().anyMatch(m -> m.name.equals("handleMoveEntity")) == false, "fixture is mixin only");
    }

    @Test
    void absoluteTeleportOnlyUpdatesCodecBaseForExactOwnedCharacter() throws Exception {
        ClassNode node = read("/" + MIXIN + ".class");
        MethodNode teleport = node.methods.stream().filter(m -> annotation(m, "Lorg/spongepowered/asm/mixin/injection/Inject;") != null)
                .filter(m -> injectionTargets(m).contains("handleTeleportEntity(Lnet/minecraft/network/protocol/game/ClientboundTeleportEntityPacket;)V"))
                .filter(m -> Boolean.TRUE.equals(value(annotation(m, "Lorg/spongepowered/asm/mixin/injection/Inject;"), "cancellable")))
                .findFirst().orElseThrow();
        AnnotationNode inject = annotation(teleport, "Lorg/spongepowered/asm/mixin/injection/Inject;");
        assertNotNull(inject);
        assertEquals(Boolean.TRUE, value(inject, "cancellable"));
        assertEquals(1, value(inject, "require"));
        @SuppressWarnings("unchecked") List<Object> atValues = (List<Object>) value(inject, "at");
        AnnotationNode at = (AnnotationNode) atValues.get(0);
        assertEquals("INVOKE", value(at, "value"));
        assertTrue(((String) value(at, "target")).contains("ensureRunningOnSameThread"));
        assertTrue(Arrays.equals(new String[]{"Lorg/spongepowered/asm/mixin/injection/At$Shift;", "AFTER"},
                (String[]) value(at, "shift")));
        List<String> calls = invokes(teleport);
        assertTrue(calls.contains("ownsSableRemoteFrameInterpolation"), calls.toString());
        assertTrue(calls.contains("getId") && calls.contains("getEntity"), calls.toString());
        assertTrue(Arrays.stream(teleport.instructions.toArray()).filter(i -> i instanceof TypeInsnNode)
                .map(i -> (TypeInsnNode) i).anyMatch(i -> i.getOpcode() == Opcodes.INSTANCEOF
                        && i.desc.equals("com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/character/PlayerCharacterHarnessEntity")));
        assertTrue(calls.contains("getPositionCodec") && calls.contains("setBase"), calls.toString());
        assertTrue(calls.contains("cancel"), calls.toString());
        assertFalse(calls.contains("setPos") || calls.contains("lerpTo") || calls.contains("setOnGround"), calls.toString());
        assertTrue(node.methods.stream().anyMatch(m -> m.name.equals("moonstation14$barrierOwnedGhostPrediction")),
                "ghost fallback teleport barrier retained");
        ClassNode control = read("/" + CONTROL + ".class");
        MethodNode tail = control.methods.stream().filter(m -> m.name.equals("onOwnedGhostTeleport"))
                .findFirst().orElseThrow();
        AbstractInsnNode[] instructions = tail.instructions.toArray();
        int ghostKind = -1, barrier = -1, earlyReturn = -1;
        for (int i = 0; i < instructions.length; i++) {
            AbstractInsnNode insn = instructions[i];
            if (insn instanceof FieldInsnNode field && field.name.equals("GHOST")) ghostKind = i;
            if (insn instanceof MethodInsnNode call && call.name.equals("onAuthorityBarrier")) barrier = i;
            if (insn.getOpcode() == Opcodes.RETURN && earlyReturn < 0) earlyReturn = i;
        }
        assertTrue(ghostKind >= 0, "ghost-only guard must compare against MobHarnessKind.GHOST");
        assertTrue(earlyReturn > ghostKind && barrier > earlyReturn,
                "non-GHOST kinds must return before the authority barrier (ghost=" + ghostKind + ", return=" + earlyReturn + ", barrier=" + barrier + ")");
    }

    @Test
    void ownershipPredicateRetainsEpochEntityCameraAndAuthoritativeSnapshotVelocityPath() throws Exception {
        ClassNode node = read("/com/juicyslew/moonstation14/ms14/player_body_control/client/GhostControlClient.class");
        MethodNode ownership = node.methods.stream().filter(m -> m.name.equals("ownsSableRemoteFrameInterpolation"))
                .findFirst().orElseThrow();
        List<String> calls = invokes(ownership);
        assertTrue(calls.contains("ownedHarnessForTracker"), calls.toString());
        assertTrue(Arrays.stream(ownership.instructions.toArray()).filter(i -> i instanceof TypeInsnNode)
                .map(i -> (TypeInsnNode) i)
                .anyMatch(i -> i.getOpcode() == Opcodes.INSTANCEOF
                        && i.desc.equals("com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/character/PlayerCharacterHarnessEntity")));
        MethodNode exactOwner = node.methods.stream().filter(m -> m.name.equals("ownedHarnessForTracker")).findFirst().orElseThrow();
        String ownerSource = exactOwner.instructions.toString();
        List<String> ownerCalls = invokes(exactOwner);
        assertTrue(ownerCalls.contains("getId"), ownerCalls.toString());
        assertTrue(ownerCalls.stream().anyMatch(s -> s.toLowerCase().contains("camera")), ownerCalls.toString());
        assertTrue(ownerCalls.stream().anyMatch(s -> s.toLowerCase().contains("medical")), ownerCalls.toString());
        String fields = Arrays.stream(exactOwner.instructions.toArray()).filter(i -> i instanceof FieldInsnNode)
                .map(i -> ((FieldInsnNode)i).name).toList().toString();
        assertTrue(fields.contains("activeEpoch") && fields.contains("pendingEpoch"), fields);
        MethodNode snapshot = node.methods.stream().filter(m -> m.name.toLowerCase().contains("snapshot"))
                .filter(m -> invokes(m).stream().anyMatch(s -> s.contains("setDeltaMovement")))
                .findFirst().orElseThrow(() -> new AssertionError("authoritative snapshot must still apply velocity"));
        assertTrue(invokes(snapshot).stream().anyMatch(s -> s.contains("setDeltaMovement")));
    }

    private static ClassNode read(String path) throws Exception {
        try (InputStream in = GhostOwnedEntityTrackerMixinTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path);
            ClassNode node = new ClassNode(Opcodes.ASM9);
            new ClassReader(in).accept(node, 0);
            return node;
        }
    }
    private static AnnotationNode annotation(MethodNode m, String desc) {
        List<AnnotationNode> all = new java.util.ArrayList<>();
        if (m.visibleAnnotations != null) all.addAll(m.visibleAnnotations);
        if (m.invisibleAnnotations != null) all.addAll(m.invisibleAnnotations);
        return all.stream().filter(a -> a.desc.equals(desc)).findFirst().orElse(null);
    }
    private static Object value(AnnotationNode a, String key) {
        for (int i=0; i<a.values.size(); i+=2) if (a.values.get(i).equals(key)) return a.values.get(i+1);
        return null;
    }
    private static List<String> injectionTargets(MethodNode method) {
        Object targets = value(annotation(method, "Lorg/spongepowered/asm/mixin/injection/Inject;"), "method");
        return targets instanceof List<?> values ? values.stream().map(String.class::cast).toList() : List.of((String) targets);
    }
    private static List<String> invokes(MethodNode m) {
        return Arrays.stream(m.instructions.toArray()).filter(i -> i instanceof MethodInsnNode)
                .map(i -> ((MethodInsnNode)i).name).toList();
    }
}
