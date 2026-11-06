package com.juicyslew.moonstation14.mixinvalidation;

import com.juicyslew.moonstation14.ms14.shuttle.sable.NativeSaveFailureDiagnostic;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SableSerializationFailureDiagnosticTest {
    @Test
    void actualWrapperCallsOriginalOnceWithIdenticalArgumentsWhenEntryMetadataFails() throws Exception {
        List<UUID> dependencies = List.of(UUID.randomUUID());
        int[] calls = {0};
        Object result = NativeSaveFailureDiagnostic.invoke(null, dependencies, (frame, passedDependencies) -> {
            calls[0]++;
            assertNull(frame);
            assertSame(dependencies, passedDependencies);
            return null;
        }, (entry, failure) -> fail("success must not be observed"));
        assertNull(result);
        assertEquals(1, calls[0]);
    }

    @Test
    void actualWrapperRethrowsOriginalFailureIdentityWhenMetadataFails() throws Exception {
        List<UUID> dependencies = List.of(UUID.randomUUID());
        RuntimeException expected = new IllegalStateException("original SDK fault");
        RuntimeException actual = assertThrows(RuntimeException.class,
                () -> NativeSaveFailureDiagnostic.invoke(null, dependencies, (frame, passedDependencies) -> {
                    assertNull(frame);
                    assertSame(dependencies, passedDependencies);
                    throw expected;
                }, (entry, failure) -> { throw new AssertionError("observer failure"); }));
        assertSame(expected, actual);
    }

    @Test
    void observationSeamPreservesSingleCallArgumentsResultAndExactFailure() {
        Object owner = new Object();
        List<UUID> dependencies = List.of(UUID.randomUUID());
        Object result = new Object();
        int[] calls = {0};
        assertSame(result, NativeSaveFailureDiagnostic.invoke(() -> {
            calls[0]++;
            assertNotNull(owner);
            assertEquals(1, dependencies.size());
            return result;
        }, failure -> fail("success must not be observed")));
        assertEquals(1, calls[0]);

        RuntimeException expected = new IllegalStateException("native failure");
        RuntimeException actual = assertThrows(RuntimeException.class,
                () -> NativeSaveFailureDiagnostic.invoke(() -> {
                    calls[0]++;
                    throw expected;
                }, failure -> { throw new AssertionError("observer failed"); }));
        assertSame(expected, actual);
        assertEquals(2, calls[0]);
        assertEquals(0, expected.getSuppressed().length);
    }

    @Test
    void mixinUsesRequiredNonRemappedWrapperAndIsRegistered() throws Exception {
        ClassNode node = new ClassNode();
        try (var stream = getClass().getClassLoader().getResourceAsStream(
                "com/juicyslew/moonstation14/mixin/compat/SableSerializationFailureDiagnosticMixin.class")) {
            assertNotNull(stream);
            new ClassReader(stream).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        AnnotationNode mixin = annotation(node, "Lorg/spongepowered/asm/mixin/Mixin;");
        assertEquals(Boolean.FALSE, value(mixin, "remap"));
        MethodNode wrapper = node.methods.stream().filter(method -> method.name.equals(
                "moonstation14$diagnoseSerializationFailure")).findFirst().orElseThrow();
        AnnotationNode wrap = annotation(wrapper.visibleAnnotations, wrapper.invisibleAnnotations,
                "Lcom/llamalad7/mixinextras/injector/wrapmethod/WrapMethod;");
        assertEquals(List.of("toData"), value(wrap, "method"));
        assertEquals(Boolean.FALSE, value(wrap, "remap"));
        try (var config = getClass().getClassLoader().getResourceAsStream("moonstation14.mixins.json")) {
            assertNotNull(config);
            assertTrue(new String(config.readAllBytes(), StandardCharsets.UTF_8)
                    .contains("compat.SableSerializationFailureDiagnosticMixin"));
        }
    }

    private static AnnotationNode annotation(ClassNode node, String descriptor) {
        return annotation(node.visibleAnnotations, node.invisibleAnnotations, descriptor);
    }

    private static AnnotationNode annotation(List<AnnotationNode> visible, List<AnnotationNode> invisible,
                                             String descriptor) {
        if (visible != null) for (AnnotationNode annotation : visible)
            if (annotation.desc.equals(descriptor)) return annotation;
        if (invisible != null) for (AnnotationNode annotation : invisible)
            if (annotation.desc.equals(descriptor)) return annotation;
        throw new AssertionError("Missing annotation " + descriptor);
    }

    private static Object value(AnnotationNode annotation, String key) {
        for (int i = 0; i < annotation.values.size(); i += 2)
            if (annotation.values.get(i).equals(key)) return annotation.values.get(i + 1);
        return null;
    }

}
