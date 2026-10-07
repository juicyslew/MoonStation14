package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.gametest.support.GameTestTagSelector;
import com.juicyslew.moonstation14.gametest.support.GameTestTagPreflight;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import java.util.Set;
import java.util.List;
import java.nio.file.Path;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.AfterBatch;
import com.juicyslew.moonstation14.gametest.support.GameTestTagRegistrar;
import org.objectweb.asm.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GameTestTagSelectorTest {
    @GameTestTags({"Power", "station"})
    static class Fixture {
        @GameTestTags({"power", "ventilation"}) void test() {}
        void untagged() {}
    }

    static class UntaggedFixture {
        @GameTest(templateNamespace = "moonstation14", template = "empty") void test() {}
    }
    static class BatchFixture {
        @GameTest(batch = "tour") @GameTestTags("tour") void tour() {}
        @GameTest(batch = "other") @GameTestTags("other") void other() {}
        @BeforeBatch(batch = "tour") public static void setup() {}
        @AfterBatch(batch = "tour") public static void teardown() {}
    }

    @Test void absentMeansFullRunAndPresentBlankFails() {
        assertTrue(GameTestTagSelector.parse(null).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> GameTestTagSelector.parse("  "));
    }

    @Test void parsesStableTagsAndRejectsMalformedValues() {
        assertEquals(Set.of("power", "ventilation"), GameTestTagSelector.parse("Power,ventilation"));
        assertThrows(IllegalArgumentException.class, () -> GameTestTagSelector.parse("power,,air"));
        assertThrows(IllegalArgumentException.class, () -> GameTestTagSelector.parse("invalid tag"));
        assertThrows(IllegalArgumentException.class, () -> GameTestTagSelector.parse("moonstation14:power"));
    }

    @Test void unionsClassAndMethodTagsAndSupportsOrUnknownChecks() throws Exception {
        var method = Fixture.class.getDeclaredMethod("test");
        assertEquals(Set.of("power", "station", "ventilation"), GameTestTagSelector.tags(Fixture.class, method));
        assertTrue(GameTestTagSelector.unknownTags(Set.of("power", "missing"), Set.of("power")).contains("missing"));
        assertTrue(GameTestTagSelector.tags(Fixture.class, method).stream().anyMatch(Set.of("station", "air")::contains));
    }

    @Test void selectionDefaultsToAllAndFilteredOrExcludesUnmatched() throws Exception {
        var power = Fixture.class.getDeclaredMethod("test");
        var untagged = UntaggedFixture.class.getDeclaredMethod("test");
        assertEquals(Set.of(power, untagged), GameTestTagSelector.select(List.of(power, untagged), Set.of()));
        assertEquals(Set.of(power), GameTestTagSelector.select(List.of(power, untagged), Set.of("station", "air")));
        assertTrue(GameTestTagSelector.select(List.of(power, untagged), Set.of("missing")).isEmpty());
    }

    @Test void compiledFixtureCatalogueHasExplicitValidMetadata() throws Exception {
        Path classes = Path.of(System.getProperty("moonstation14.gametestClasses"));
        List<String> classFiles;
        try (var files = java.nio.file.Files.walk(classes)) {
            classFiles = files.filter(p -> p.toString().endsWith(".class")).toList().stream().map(Path::toString).toList();
        }
        int[] tests = {0};
        Set<String> catalogue = new java.util.HashSet<>();
        Set<String> supportedTemplateNamespaces = Set.of("moonstation14", "moonstation14_camera_tour");
        for (String file : classFiles) {
            ClassReader reader = new ClassReader(java.nio.file.Files.readAllBytes(Path.of(file)));
            if (!reader.getClassName().startsWith("com/juicyslew/moonstation14/")) continue;
            reader.accept(new ClassVisitor(Opcodes.ASM9) {
                boolean fixture;
                Set<String> classTags = new java.util.HashSet<>();
                @Override public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    if (descriptor.equals("Lnet/neoforged/neoforge/gametest/GameTestHolder;")) fail("GameTestHolder bypass: " + reader.getClassName());
                    if (!descriptor.equals("Lcom/juicyslew/moonstation14/gametest/support/GameTestTags;")) return null;
                    return tags(classTags);
                }
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        Set<String> methodTags = new java.util.HashSet<>();
                        boolean gameTest;
                        @Override public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                            if (!annotation.equals("Lnet/minecraft/gametest/framework/GameTest;")) {
                                if (annotation.equals("Lcom/juicyslew/moonstation14/gametest/support/GameTestTags;")) return tags(methodTags);
                                return null;
                            }
                            fixture = true;
                            gameTest = true;
                            tests[0]++;
                            boolean[] templateNamespacePresent = {false};
                            return new AnnotationVisitor(Opcodes.ASM9) {
                                @Override public void visit(String key, Object value) {
                                    if (key.equals("templateNamespace")) {
                                        templateNamespacePresent[0] = true;
                                        assertTrue(supportedTemplateNamespaces.contains(value), "unsupported template namespace " + value + " at " + reader.getClassName() + "." + name);
                                    }
                                }
                                @Override public void visitEnd() {
                                    assertTrue(templateNamespacePresent[0], "missing explicit templateNamespace: " + reader.getClassName() + "." + name);
                                }
                            };
                        }
                        @Override public void visitEnd() {
                            if (!gameTest) return;
                            if (classTags.isEmpty() && methodTags.isEmpty()) fail("Untagged GameTest: " + reader.getClassName() + "." + name);
                            catalogue.addAll(classTags); catalogue.addAll(methodTags);
                        }
                    };
                }
                AnnotationVisitor tags(Set<String> target) {
                    return new AnnotationVisitor(Opcodes.ASM9) {
                        @Override public AnnotationVisitor visitArray(String name) {
                            return new AnnotationVisitor(Opcodes.ASM9) { @Override public void visit(String n, Object value) {
                                 String tag = value.toString().toLowerCase(java.util.Locale.ROOT);
                                assertTrue(tag.matches("[a-z][a-z0-9_-]*"), "malformed tag " + tag);
                                target.add(tag);
                            }};
                        }
                    };
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        assertTrue(tests[0] > 0, "compiled fixture metadata must be present");
        assertFalse(catalogue.isEmpty());
        assertEquals(tests[0], GameTestTagPreflight.validate(classes, null).tests());
        assertThrows(IllegalStateException.class, () -> GameTestTagPreflight.validate(classes, "not-a-real-tag"));
    }

    @Test void registrationSeamPublishesFullOrTaggedSubsetAndRejectsBeforePublishing() throws Exception {
        List<java.lang.reflect.Method> methods = List.of(Fixture.class.getDeclaredMethod("test"), Fixture.class.getDeclaredMethod("untagged"), UntaggedFixture.class.getDeclaredMethod("test"));
        Set<java.lang.reflect.Method> published = new java.util.HashSet<>();
        assertEquals(3, GameTestTagRegistrar.registerMethods(methods, Set.of(), published::add).selected());
        assertEquals(Set.copyOf(methods), published);
        published.clear();
        assertEquals(1, GameTestTagRegistrar.registerMethods(methods, Set.of("ventilation"), published::add).selected());
        assertEquals(Set.of(Fixture.class.getDeclaredMethod("test")), published);
        published.clear();
        assertThrows(IllegalStateException.class, () -> GameTestTagRegistrar.registerMethods(methods, Set.of("missing"), published::add));
        assertTrue(published.isEmpty());
    }

    @Test void batchCallbacksFollowSelectedTestBatches() throws Exception {
        var tour = BatchFixture.class.getDeclaredMethod("tour");
        var other = BatchFixture.class.getDeclaredMethod("other");
        var setup = BatchFixture.class.getDeclaredMethod("setup");
        var teardown = BatchFixture.class.getDeclaredMethod("teardown");
        List<java.lang.reflect.Method> methods = List.of(tour, other);
        List<java.lang.reflect.Method> callbacks = List.of(setup, teardown);
        List<java.lang.reflect.Method> published = new java.util.ArrayList<>();
        GameTestTagRegistrar.registerMethods(methods, Set.of("tour"), callbacks, published::add);
        assertEquals(Set.of(tour, setup, teardown), Set.copyOf(published));
        published.clear();
        GameTestTagRegistrar.registerMethods(methods, Set.of(), callbacks, published::add);
        assertEquals(Set.of(tour, other, setup, teardown), Set.copyOf(published));
        published.clear();
        assertThrows(IllegalStateException.class, () -> GameTestTagRegistrar.registerMethods(methods, Set.of("missing"), callbacks, published::add));
        assertTrue(published.isEmpty());
    }
}
