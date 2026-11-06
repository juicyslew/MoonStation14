package com.juicyslew.moonstation14.mixinvalidation;

import com.juicyslew.moonstation14.mixin.client.SableRemotePlotProjectionPlugin;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Resource-only structural checks; mixin definitions are never loaded as JVM classes. */
class SableOwnedRemoteFrameMixinTest {
    private static final String SDK_CONTAINING = "Ldev/ryanhcode/sable/ActiveSableCompanion;getContaining"
            + "(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/Position;)"
            + "Ldev/ryanhcode/sable/sublevel/SubLevel;";
    private static final String ENTITY_MIXIN =
            "com/juicyslew/moonstation14/mixin/client/SableOwnedRemoteFrameEntityMixin";
    private static final String LIVING_MIXIN =
            "com/juicyslew/moonstation14/mixin/client/SableOwnedRemoteFrameLivingMixin";
    private static final String GHOST_CONTROL =
            "com/juicyslew/moonstation14/ms14/player_body_control/client/GhostControlClient";
    private static final String SDK_LIVING_RESOURCE =
            "/dev/ryanhcode/sable/mixin/entity/entities_stick_sublevels/LivingEntityMixin.class";
    private static final String UNIQUE = "Lorg/spongepowered/asm/mixin/Unique;";
    private static final String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";
    private static final String AT = "Lorg/spongepowered/asm/mixin/injection/At;";
    private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final String SHADOW = "Lorg/spongepowered/asm/mixin/Shadow;";

    @Test
    void pluginGuardsExactlyThePostMergedSdkHandlerWithoutTouchingOtherMethods() throws Exception {
        ClassNode pinnedSdk = readClass("/dev/ryanhcode/sable/mixin/entity/entities_stick_sublevels/EntityMixin.class");
        MethodNode pinnedHandler = pinnedSdk.methods.stream()
                .filter(method -> method.name.equals("sable$updateSubLevelPosition"))
                .findFirst().orElseThrow(() -> new AssertionError("pinned SDK handler bytecode missing"));
        assertEquals(1, countInvoke(pinnedHandler, "getContaining", SDK_CONTAINING.substring(1)));
        assertEquals(2, countInvoke(pinnedHandler, "sable$setTrackingSubLevel"));
        assertEquals(1, countInvoke(pinnedHandler, "setPos"));

        ClassNode merged = new ClassNode(Opcodes.ASM9);
        merged.name = "net/minecraft/world/entity/Entity";
        String generatedName = "handler$test$sable$updateSubLevelPosition";
        MethodNode sdkHandler = new MethodNode(Opcodes.ASM9, pinnedHandler.access, generatedName, pinnedHandler.desc,
                pinnedHandler.signature, pinnedHandler.exceptions == null ? null : pinnedHandler.exceptions.toArray(String[]::new));
        pinnedHandler.accept(sdkHandler);
        sdkHandler.name = generatedName;
        sdkHandler.visibleAnnotations = new ArrayList<>();
        AnnotationNode mergedFrom = new AnnotationNode("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;");
        mergedFrom.values = new ArrayList<>(List.of("mixin", SableRemotePlotProjectionPlugin.SDK_ENTITY_MIXIN,
                "priority", 1000));
        sdkHandler.visibleAnnotations.add(mergedFrom);
        AbstractInsnNode[] original = sdkHandler.instructions.toArray();
        MethodNode unrelated = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PRIVATE,
                "handler$abc$sable$updateSubLevelPosition", "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V",
                null, null);
        unrelated.instructions.add(new InsnNode(Opcodes.NOP));
        unrelated.instructions.add(new InsnNode(Opcodes.RETURN));
        merged.methods.add(sdkHandler);
        merged.methods.add(unrelated);

        new SableRemotePlotProjectionPlugin().postApply("net.minecraft.world.entity.Entity", merged,
                SableRemotePlotProjectionPlugin.OWNED_ENTITY_MIXIN, null);
        assertEquals(Opcodes.ALOAD, sdkHandler.instructions.getFirst().getOpcode());
        assertTrue(hasInvoke(sdkHandler, "ownsSableRemoteFrameInterpolation"));
        assertTrue(hasInvoke(sdkHandler, "sable$setPlotPosition"));
        AbstractInsnNode[] guarded = sdkHandler.instructions.toArray();
        int originalStart = -1;
        for (int i = 0; i < guarded.length; i++) if (guarded[i] == original[0]) { originalStart = i; break; }
        assertTrue(originalStart > 0, "real SDK handler body remains after the new guard");
        int guardReturn = -1;
        int branchToOriginal = -1;
        for (int i = 0; i < originalStart; i++) {
            if (guarded[i].getOpcode() == Opcodes.RETURN) guardReturn = i;
            if (guarded[i] instanceof JumpInsnNode jump && jump.getOpcode() == Opcodes.IFEQ)
                branchToOriginal = sdkHandler.instructions.indexOf(jump.label);
            if (guarded[i] instanceof MethodInsnNode call)
                assertFalse(call.name.equals("setPos") || call.name.equals("sable$setTrackingSubLevel"),
                        "guard branch reaches neither SDK cached position nor tracking writes");
        }
        assertTrue(guardReturn >= 0 && branchToOriginal > guardReturn && branchToOriginal <= originalStart,
                "owned return precedes continuation into SDK cache branches");
        assertEquals(original.length, guarded.length - originalStart, "original SDK instruction count is preserved");
        for (int i = 0; i < original.length; i++)
            assertSame(original[i], guarded[originalStart + i], "original SDK instructions retain identity and order");
        assertEquals(2, unrelated.instructions.size(), "unrelated methods remain untouched");

        ClassNode missing = new ClassNode(Opcodes.ASM9);
        missing.name = "net/minecraft/world/entity/Entity";
        assertThrows(IllegalStateException.class, () -> new SableRemotePlotProjectionPlugin().postApply(
                "net.minecraft.world.entity.Entity", missing,
                SableRemotePlotProjectionPlugin.OWNED_ENTITY_MIXIN, null));
        ClassNode duplicate = new ClassNode(Opcodes.ASM9);
        duplicate.name = "net/minecraft/world/entity/Entity";
        duplicate.methods.add(sdkHandler);
        duplicate.methods.add(sdkHandler);
        assertThrows(IllegalStateException.class, () -> new SableRemotePlotProjectionPlugin().postApply(
                "net.minecraft.world.entity.Entity", duplicate,
                SableRemotePlotProjectionPlugin.OWNED_ENTITY_MIXIN, null));
    }

    @Test
    void clientMixinTargetsAndLerpGuardsAreVerifiedFromResources() throws Exception {
        ClassNode living = readClass("/" + LIVING_MIXIN + ".class");
        AnnotationNode livingMixin = annotation(living.visibleAnnotations, living.invisibleAnnotations, MIXIN);
        assertEquals(900, annotationValue(livingMixin, "priority"));
        assertMixinTargets(livingMixin, "net/minecraft/world/entity/LivingEntity");
        FieldNode lerpSteps = living.fields.stream().filter(field -> field.name.equals("lerpSteps")).findFirst()
                .orElseThrow(() -> new AssertionError("missing lerpSteps shadow field"));
        assertNotNull(annotation(lerpSteps.visibleAnnotations, lerpSteps.invisibleAnnotations, SHADOW));

        MethodNode setup = method(living, "moonstation14$disableOwnedRemoteLerpSetup");
        AnnotationNode setupInject = annotation(setup.visibleAnnotations, setup.invisibleAnnotations, INJECT);
        assertEquals(List.of("sable$setupLerp()V"), annotationValue(setupInject, "method"));
        assertTrue(Boolean.TRUE.equals(annotationValue(setupInject, "cancellable")));
        assertEquals(1, annotationValue(setupInject, "require"));
        assertEquals(Boolean.FALSE, annotationValue(setupInject, "remap"));
        assertEquals("HEAD", nestedAnnotation(annotationValue(setupInject, "at"), "value"));
        assertFieldWriteZero(setup, LIVING_MIXIN, "lerpSteps");
        assertTrue(invokes(setup, GHOST_CONTROL, "ownsSableRemoteFrameInterpolation"));
        assertFalse(living.methods.stream().anyMatch(candidate ->
                candidate.name.equals("moonstation14$disableOwnedRemoteLerpApplication")),
                "owned apply hook must not cancel Sable's stock null-cache cleanup");

        ClassNode sdkLiving = readClass(SDK_LIVING_RESOURCE);
        assertUniqueIntegerField(sdkLiving, "sable$sableLerpSteps");
        assertUniqueIntegerField(sdkLiving, "sable$sableRotLerpSteps");
        assertNullCacheBranchClearsLerpsWithoutPoseWrites(method(sdkLiving, "sable$applyLerp"));
        MethodNode updatePosition = method(sdkLiving, "sable$updatePlotPosition");
        AnnotationNode updateInject = annotation(updatePosition.visibleAnnotations,
                updatePosition.invisibleAnnotations, INJECT);
        assertEquals(List.of("aiStep"), annotationValue(updateInject, "method"));
        assertEquals("INVOKE", nestedAnnotation(annotationValue(updateInject, "at"), "value"));
        assertEquals("Lnet/minecraft/world/entity/LivingEntity;setDeltaMovement(DDD)V",
                nestedAnnotation(annotationValue(updateInject, "at"), "target"));
        assertTrue(invokes(updatePosition,
                        "dev/ryanhcode/sable/mixin/entity/entities_stick_sublevels/LivingEntityMixin", "sable$applyLerp"),
                "pinned Sable plot-position update reaches the stock null-cache reset path");
        MethodNode updateRotation = method(sdkLiving, "sable$updateRotLerp");
        AnnotationNode rotationInject = annotation(updateRotation.visibleAnnotations,
                updateRotation.invisibleAnnotations, INJECT);
        assertEquals(List.of("aiStep"), annotationValue(rotationInject, "method"));
        assertEquals("HEAD", nestedAnnotation(annotationValue(rotationInject, "at"), "value"));
        assertTrue(invokes(updateRotation,
                        "dev/ryanhcode/sable/mixin/entity/entities_stick_sublevels/LivingEntityMixin", "sable$setupLerp"),
                "pinned Sable setup path runs each aiStep before apply cleanup");

        ClassNode entity = readClass("/" + ENTITY_MIXIN + ".class");
        AnnotationNode entityMixin = annotation(entity.visibleAnnotations, entity.invisibleAnnotations, MIXIN);
        assertEquals(900, annotationValue(entityMixin, "priority"));
        assertMixinTargets(entityMixin, "net/minecraft/world/entity/Entity");
        MethodNode plot = method(entity, "moonstation14$hideOwnedRemotePlotPosition");
        AnnotationNode plotInject = annotation(plot.visibleAnnotations, plot.invisibleAnnotations, INJECT);
        assertEquals(List.of("sable$getPlotPosition()Lnet/minecraft/world/phys/Vec3;"),
                annotationValue(plotInject, "method"));
        assertTrue(Boolean.TRUE.equals(annotationValue(plotInject, "cancellable")));
        assertEquals(1, annotationValue(plotInject, "require"));
        assertEquals(Boolean.FALSE, annotationValue(plotInject, "remap"));
        assertEquals("HEAD", nestedAnnotation(annotationValue(plotInject, "at"), "value"));
        assertTrue(invokes(plot, GHOST_CONTROL, "ownsSableRemoteFrameInterpolation"));
        assertTrue(invokes(plot, "org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable", "setReturnValue"));
        assertCallReceivesNull(plot, "setReturnValue");
    }

    @Test
    void establishedExactOwnerPredicateIsReferencedByBytecode() throws Exception {
        ClassNode ghostControl = readClass("/" + GHOST_CONTROL + ".class");
        MethodNode ownership = method(ghostControl, "ownsSableRemoteFrameInterpolation");
        assertTrue(invokes(ownership, GHOST_CONTROL, "ownedHarnessForTracker"),
                "remote suppression delegates to the established exact tracker-owner predicate");
    }

    private static ClassNode readClass(String resource) throws Exception {
        String classPath = resource.startsWith("/") ? resource.substring(1) : resource;
        InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(classPath);
        if (stream == null) stream = ClassLoader.getSystemResourceAsStream(classPath);
        if (stream == null) stream = SableOwnedRemoteFrameMixinTest.class.getClassLoader().getResourceAsStream(classPath);
        try (InputStream input = stream) {
            assertNotNull(input, "class bytecode resource exists: " + resource);
            ClassNode node = new ClassNode(Opcodes.ASM9);
            new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }

    private static MethodNode method(ClassNode owner, String name) {
        return owner.methods.stream().filter(candidate -> candidate.name.equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("missing bytecode method " + owner.name + "." + name));
    }

    private static AnnotationNode annotation(List<AnnotationNode> visible, List<AnnotationNode> invisible,
                                             String descriptor) {
        if (visible != null)
            for (AnnotationNode value : visible) if (value.desc.equals(descriptor)) return value;
        if (invisible != null)
            for (AnnotationNode value : invisible) if (value.desc.equals(descriptor)) return value;
        throw new AssertionError("missing annotation " + descriptor);
    }

    private static Object annotationValue(AnnotationNode annotation, String key) {
        assertNotNull(annotation);
        if (annotation.values != null)
            for (int i = 0; i + 1 < annotation.values.size(); i += 2)
                if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        return null;
    }

    private static void assertMixinTargets(AnnotationNode mixin, String targetInternalName) {
        Object targets = annotationValue(mixin, "value");
        assertTrue(targets instanceof List<?> values && values.stream().anyMatch(value -> value instanceof Type type
                && type.getInternalName().equals(targetInternalName)), "expected @Mixin target " + targetInternalName);
    }

    private static String nestedAnnotation(Object annotationValue, String key) {
        if (annotationValue instanceof List<?> values) {
            assertFalse(values.isEmpty());
            annotationValue = values.get(0);
        }
        assertTrue(annotationValue instanceof AnnotationNode);
        AnnotationNode nested = (AnnotationNode) annotationValue;
        assertEquals(AT, nested.desc, "expected nested @At annotation");
        return (String) annotationValue(nested, key);
    }

    private static void assertFieldWriteZero(MethodNode method, String owner, String field) {
        boolean found = false;
        for (AbstractInsnNode instruction : method.instructions) {
            if (!(instruction instanceof FieldInsnNode write) || write.getOpcode() != Opcodes.PUTFIELD
                    || !write.owner.equals(owner) || !write.name.equals(field)) continue;
            AbstractInsnNode previous = instruction.getPrevious();
            while (previous != null && previous.getOpcode() < 0) previous = previous.getPrevious();
            assertNotNull(previous);
            assertEquals(Opcodes.ICONST_0, previous.getOpcode(), "lerpSteps is explicitly cleared");
            found = true;
        }
        assertTrue(found, "setup hook writes lerpSteps=0");
    }

    private static void assertUniqueIntegerField(ClassNode owner, String name) {
        FieldNode field = owner.fields.stream().filter(candidate -> candidate.name.equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("pinned Sable unique field missing: " + name));
        assertEquals("I", field.desc);
        assertTrue((field.access & Opcodes.ACC_PRIVATE) != 0);
        assertNotNull(annotation(field.visibleAnnotations, field.invisibleAnnotations, UNIQUE),
                "SDK lerp counter is @Unique: " + name);
    }

    /** Proves the actual Sable null-cache path clears both unique counters and returns before any transform write. */
    private static void assertNullCacheBranchClearsLerpsWithoutPoseWrites(MethodNode apply) {
        MethodInsnNode cachedPlotRead = null;
        for (AbstractInsnNode instruction : apply.instructions)
            if (instruction instanceof MethodInsnNode call && call.name.equals("sable$getPlotPosition")) {
                cachedPlotRead = call;
                break;
            }
        assertNotNull(cachedPlotRead, "Sable apply reads the guarded cached-plot getter");
        assertEquals("()Lnet/minecraft/world/phys/Vec3;", cachedPlotRead.desc);
        JumpInsnNode cachePresent = null;
        for (AbstractInsnNode instruction : apply.instructions)
            if (instruction instanceof JumpInsnNode jump && jump.getOpcode() == Opcodes.IFNONNULL) {
                cachePresent = jump;
                break;
            }
        assertNotNull(cachePresent, "null cache branches around stock interpolation");
        boolean moveStepsCleared = false;
        boolean rotationStepsCleared = false;
        AbstractInsnNode instruction = cachePresent.getNext();
        int returnIndex = -1;
        while (instruction != null) {
            if (instruction instanceof FieldInsnNode write && write.getOpcode() == Opcodes.PUTFIELD) {
                assertEquals("dev/ryanhcode/sable/mixin/entity/entities_stick_sublevels/LivingEntityMixin", write.owner);
                AbstractInsnNode previous = instruction.getPrevious();
                while (previous != null && previous.getOpcode() < 0) previous = previous.getPrevious();
                assertNotNull(previous);
                assertEquals(Opcodes.ICONST_0, previous.getOpcode(), "null branch explicitly zeros Sable counters");
                if (write.name.equals("sable$sableLerpSteps")) moveStepsCleared = true;
                if (write.name.equals("sable$sableRotLerpSteps")) rotationStepsCleared = true;
            }
            if (instruction instanceof MethodInsnNode call)
                assertFalse(call.name.equals("setPos") || call.name.equals("sable$setPlotPosition")
                                || call.name.equals("setYRot") || call.name.equals("setXRot") || call.name.equals("setRot"),
                        "null-cache branch cannot change position or rotation");
            if (instruction.getOpcode() == Opcodes.RETURN) {
                returnIndex = apply.instructions.indexOf(instruction);
                break;
            }
            instruction = instruction.getNext();
        }
        assertTrue(moveStepsCleared && rotationStepsCleared,
                "null-cache path resets both private Sable interpolation counters");
        assertTrue(returnIndex >= 0 && returnIndex < apply.instructions.indexOf(cachePresent.label),
                "null-cache branch returns before the non-null interpolation path");
    }

    private static boolean invokes(MethodNode method, String owner, String name) {
        for (AbstractInsnNode instruction : method.instructions)
            if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) return true;
        return false;
    }

    private static void assertCallReceivesNull(MethodNode method, String name) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (!(instruction instanceof MethodInsnNode call) || !call.name.equals(name)) continue;
            AbstractInsnNode previous = instruction.getPrevious();
            while (previous != null && previous.getOpcode() < 0) previous = previous.getPrevious();
            assertNotNull(previous);
            assertEquals(Opcodes.ACONST_NULL, previous.getOpcode(), name + " receives null to hide stale cached plot position");
            return;
        }
        fail("missing call " + name);
    }

    private static int countInvoke(MethodNode method, String name, String bytecodeOwnerAndDescriptor) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions)
            if (instruction instanceof MethodInsnNode call
                    && (call.owner + ";" + call.name + call.desc).equals(bytecodeOwnerAndDescriptor)) count++;
        return count;
    }

    private static int countInvoke(MethodNode method, String name) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions)
            if (instruction instanceof MethodInsnNode call && call.name.equals(name)) count++;
        return count;
    }

    private static boolean hasInvoke(MethodNode method, String name) {
        return countInvoke(method, name) > 0;
    }
}
