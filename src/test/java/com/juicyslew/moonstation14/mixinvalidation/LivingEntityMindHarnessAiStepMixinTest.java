package com.juicyslew.moonstation14.mixinvalidation;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Pins native aiStep velocity-write sites and the narrowly scoped ownership redirects. */
class LivingEntityMindHarnessAiStepMixinTest {
    private static final String MIXIN = "/com/juicyslew/moonstation14/mixin/LivingEntityMindHarnessTravelMixin.class";
    private static final String REDIRECT = "Lorg/spongepowered/asm/mixin/injection/Redirect;";

    @Test
    void redirectsOnlyVecAndDddAiStepVelocityNormalizationWrites() throws Exception {
        ClassNode mixin = read(MIXIN);
        List<MethodNode> redirects = mixin.methods.stream()
                .filter(method -> annotation(method, REDIRECT) != null).toList();
        assertEquals(2, redirects.size());
        assertRedirect(redirects, "setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V");
        assertRedirect(redirects, "setDeltaMovement(DDD)V");
        for (MethodNode redirect : redirects) {
            List<String> calls = invokes(redirect);
            assertTrue(calls.stream().anyMatch(call -> call.endsWith("hasCustomPhysicsOwner")), calls.toString());
            assertTrue(calls.contains("setDeltaMovement"), calls.toString());
            assertFalse(calls.contains("isClientSide"), "ownership predicate must remain client/server symmetric");
        }
        MethodNode ownership = mixin.methods.stream().filter(method -> method.name.equals("moonstation14$hasCustomPhysicsOwner"))
                .findFirst().orElseThrow();
        assertTrue(invokes(ownership).contains("eligible"));
        assertTrue(invokes(ownership).stream().anyMatch(call -> call.contains("MovementOwned")));
        assertTrue(invokes(ownership).stream().anyMatch(call -> call.contains("CustomPhysicalOwner")));
    }

    private static void assertRedirect(List<MethodNode> redirects, String descriptorSuffix) {
        MethodNode method = redirects.stream().filter(candidate -> {
            AnnotationNode at = (AnnotationNode) value(annotation(candidate, REDIRECT), "at");
            return ((String) value(at, "target")).endsWith(descriptorSuffix);
        }).findFirst().orElseThrow(() -> new AssertionError("missing redirect for " + descriptorSuffix));
        AnnotationNode redirect = annotation(method, REDIRECT);
        assertEquals(1, value(redirect, "require"));
        AnnotationNode at = (AnnotationNode) value(redirect, "at");
        assertEquals("INVOKE", value(at, "value"));
        assertEquals(0, value(at, "ordinal"));
        assertTrue(((String) value(at, "target")).startsWith("Lnet/minecraft/world/entity/LivingEntity;"));
        Object targetMethod = value(redirect, "method");
        assertTrue(targetMethod.equals("aiStep") || targetMethod.equals(List.of("aiStep")), String.valueOf(targetMethod));
    }

    private static ClassNode read(String path) throws Exception {
        try (InputStream input = LivingEntityMindHarnessAiStepMixinTest.class.getResourceAsStream(path)) {
            assertNotNull(input, path);
            ClassNode node = new ClassNode(Opcodes.ASM9);
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }
    private static AnnotationNode annotation(MethodNode method, String descriptor) {
        if (method.visibleAnnotations != null) for (AnnotationNode node : method.visibleAnnotations) if (node.desc.equals(descriptor)) return node;
        if (method.invisibleAnnotations != null) for (AnnotationNode node : method.invisibleAnnotations) if (node.desc.equals(descriptor)) return node;
        return null;
    }
    private static Object value(AnnotationNode annotation, String key) {
        for (int i = 0; i < annotation.values.size(); i += 2) if (annotation.values.get(i).equals(key)) return annotation.values.get(i + 1);
        return null;
    }
    private static List<String> invokes(MethodNode method) {
        return java.util.Arrays.stream(method.instructions.toArray()).filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast).map(call -> call.name).toList();
    }
}
