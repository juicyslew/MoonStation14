package com.juicyslew.moonstation14.gametest.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Validates a requested fixture filter from bytecode without loading Minecraft or fixture classes. */
public final class GameTestTagPreflight {
    private static final String GAME_TEST = "Lnet/minecraft/gametest/framework/GameTest;";
    private static final String TAGS = "Lcom/juicyslew/moonstation14/gametest/support/GameTestTags;";

    private GameTestTagPreflight() { }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) throw new IllegalArgumentException("Expected compiled fixture directory and filter");
        Catalogue catalogue = validate(Path.of(args[0]), args[1]);
        System.out.printf("GameTest tag preflight: %d/%d fixtures selected; available tags=%s%n", catalogue.selected(), catalogue.tests(), catalogue.availableTags());
    }

    public static Catalogue validate(Path root, String filter) throws IOException {
        Set<String> requested = GameTestTagSelector.parse(filter);
        Set<String> available = new HashSet<>();
        int[] counts = {0, 0};
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".class")).toList())
                scan(file, available, counts, requested);
        }
        if (counts[0] == 0) throw new IllegalStateException("No compiled GameTest fixtures found at " + root);
        Set<String> unknown = GameTestTagSelector.unknownTags(requested, available);
        if (!unknown.isEmpty()) throw new IllegalStateException("Unknown GameTest tags " + unknown + "; available tags: " + available);
        int selected = requested.isEmpty() ? counts[0] : counts[1];
        if (!requested.isEmpty() && selected == 0) throw new IllegalStateException("GameTest tag filter selected zero tests: " + requested);
        return new Catalogue(Set.copyOf(available), counts[0], selected);
    }

    public record Catalogue(Set<String> availableTags, int tests, int selected) { }

    private static void scan(Path file, Set<String> available, int[] counts, Set<String> requested) throws IOException {
        ClassReader reader = new ClassReader(Files.readAllBytes(file));
        if (!reader.getClassName().startsWith("com/juicyslew/moonstation14/")) return;
        Set<String> classTags = new HashSet<>();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                return descriptor.equals(TAGS) ? readTags(classTags) : null;
            }
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                Set<String> methodTags = new HashSet<>();
                return new MethodVisitor(Opcodes.ASM9) {
                    boolean test;
                    @Override public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                        if (annotation.equals(TAGS)) return readTags(methodTags);
                        if (annotation.equals(GAME_TEST)) { test = true; return null; }
                        return null;
                    }
                    @Override public void visitEnd() {
                        if (!test) return;
                        counts[0]++;
                        available.addAll(classTags);
                        available.addAll(methodTags);
                        if (classTags.stream().anyMatch(requested::contains) || methodTags.stream().anyMatch(requested::contains)) counts[1]++;
                    }
                };
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }

    private static AnnotationVisitor readTags(Set<String> into) {
        return new AnnotationVisitor(Opcodes.ASM9) {
            @Override public AnnotationVisitor visitArray(String name) {
                return new AnnotationVisitor(Opcodes.ASM9) {
                    @Override public void visit(String ignored, Object value) {
                        String tag = value.toString().toLowerCase(java.util.Locale.ROOT);
                        if (!tag.matches("[a-z][a-z0-9_-]*")) throw new IllegalArgumentException("Malformed GameTest tag: " + tag);
                        into.add(tag);
                    }
                };
            }
        };
    }

}
