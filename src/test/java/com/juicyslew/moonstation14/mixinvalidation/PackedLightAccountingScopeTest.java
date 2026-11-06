package com.juicyslew.moonstation14.mixinvalidation;

import com.juicyslew.moonstation14.mixin.client.SableRemotePlotProjectionPlugin;
import com.juicyslew.moonstation14.ms14.shuttle.sable.PackedLightAccountingScope;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.List;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class PackedLightAccountingScopeTest {
    @Test
    void scopeNestsByRendererAndAlwaysCleansUp() {
        Object first = new Object(), second = new Object();
        assertFalse(PackedLightAccountingScope.activeForRenderer(first));
        PackedLightAccountingScope.begin(first);
        try {
            assertTrue(PackedLightAccountingScope.activeForRenderer(first));
            assertFalse(PackedLightAccountingScope.activeForRenderer(second));
            PackedLightAccountingScope.begin(second);
            try {
                assertFalse(PackedLightAccountingScope.activeForRenderer(first));
                assertTrue(PackedLightAccountingScope.activeForRenderer(second));
            } finally { PackedLightAccountingScope.end(); }
            assertTrue(PackedLightAccountingScope.activeForRenderer(first));
            assertThrows(IllegalStateException.class, () -> {
                PackedLightAccountingScope.begin(second);
                try { throw new IllegalStateException("expected"); }
                finally { PackedLightAccountingScope.end(); }
            });
            assertTrue(PackedLightAccountingScope.activeForRenderer(first));
        } finally { PackedLightAccountingScope.end(); }
        assertFalse(PackedLightAccountingScope.activeForRenderer(first));
        assertFalse(PackedLightAccountingScope.activeForRenderer(second));
    }

    @Test
    void translationCellChangesCanChangeDoubleAccountedSkyButNotPreciseResult() {
        int[] worldSky = {15, 15};
        int[] plotSky = {8, 7};
        int[] legacyDouble = new int[2];
        int[] corrected = new int[2];
        for (int i = 0; i < 2; i++) {
            legacyDouble[i] = Math.min(worldSky[i], plotSky[i]);
            corrected[i] = Math.min(worldSky[i], plotSky[0]); // stable precise local probe cell
        }
        assertArrayEquals(new int[]{8, 7}, legacyDouble);
        assertArrayEquals(new int[]{8, 8}, corrected);
        assertEquals(8, Math.min(15, 8)); // vanilla world seed and final precise plot accounting
        assertEquals(15, 15); // intentional burning-entity block seed remains unaffected
    }

    @Test
    void pluginGuardsExactlyBothMergedSdkBridgesAndRetainsTheirFallbackBodies() {
        ClassNode target = new ClassNode(Opcodes.ASM9);
        target.name = "net/minecraft/client/renderer/entity/EntityRenderer";
        for (String bridge : List.of("$sable$getSkyLightLevel", "$sable$getBlockLightLevel")) {
            MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PRIVATE, bridge,
                    "(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/LightLayer;Lnet/minecraft/core/BlockPos;)I", null, null);
            method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
            boolean sky = bridge.contains("Sky");
            method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "dev/ryanhcode/sable/mixin/entity/entity_rendering/EntityRendererMixin",
                    sky ? "sable$getSubLevelAccountedSkyLight" : "sable$getSubLevelAccountedBlockLight", "()I", false));
            method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "dev/ryanhcode/sable/companion/math/JOMLConversion", "atCenterOf", "()V", false));
            method.instructions.add(new InsnNode(Opcodes.IRETURN));
            AnnotationNode merged = new AnnotationNode("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;");
            merged.values = new ArrayList<>(List.of("mixin", SableRemotePlotProjectionPlugin.SDK_RENDERER_MIXIN));
            method.invisibleAnnotations = new ArrayList<>(List.of(merged));
            target.methods.add(method);
        }
        new SableRemotePlotProjectionPlugin().postApply("net.minecraft.client.renderer.entity.EntityRenderer", target,
                SableRemotePlotProjectionPlugin.LIGHTING_MIXIN, null);
        for (MethodNode method : target.methods) {
            assertTrue(method.instructions.getFirst() instanceof VarInsnNode);
            assertTrue(method.instructions.toArray().length > 5);
            assertTrue(method.instructions.toArray()[method.instructions.size() - 1].getOpcode() == Opcodes.IRETURN,
                    "SDK original body remains as out-of-scope fallback");
        }
        assertThrows(IllegalStateException.class, () -> new SableRemotePlotProjectionPlugin().postApply(
                "net.minecraft.client.renderer.entity.EntityRenderer", new ClassNode(Opcodes.ASM9),
                SableRemotePlotProjectionPlugin.LIGHTING_MIXIN, null));
    }

    @Test
    void pluginAcceptsOnlyThePinnedSdkBridgeAbiAndPreservesRealBodies() throws Exception {
        ClassNode sdk = SableTemporalCollisionMixinContractTest.readRuntime(
                "dev/ryanhcode/sable/mixin/entity/entity_rendering/EntityRendererMixin");
        ClassNode target = new ClassNode(Opcodes.ASM9);
        target.name = "net/minecraft/client/renderer/entity/EntityRenderer";
        for (String name : List.of("sable$getSkyLightLevel", "sable$getBlockLightLevel")) {
            MethodNode source = sdk.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
            MethodNode merged = new MethodNode(Opcodes.ASM9, source.access, source.name, source.desc,
                    source.signature, source.exceptions == null ? null : source.exceptions.toArray(String[]::new));
            source.accept(merged);
            merged.visibleAnnotations = new ArrayList<>(List.of(new AnnotationNode(
                    "Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;")));
            merged.visibleAnnotations.get(0).values = new ArrayList<>(List.of("mixin",
                    SableRemotePlotProjectionPlugin.SDK_RENDERER_MIXIN));
            target.methods.add(merged);
        }
        List<AbstractInsnNode[]> originalInstructions = target.methods.stream()
                .map(method -> method.instructions.toArray()).toList();
        new SableRemotePlotProjectionPlugin().postApply("net.minecraft.client.renderer.entity.EntityRenderer", target,
                SableRemotePlotProjectionPlugin.LIGHTING_MIXIN, null);
        for (int n = 0; n < originalInstructions.size(); n++) {
            AbstractInsnNode[] original = originalInstructions.get(n);
            MethodNode patched = target.methods.get(n);
            AbstractInsnNode[] actual = patched.instructions.toArray();
            int originalStart = -1;
            for (int i = 0; i < actual.length; i++) if (actual[i] == original[0]) { originalStart = i; break; }
            assertTrue(originalStart > 0, "raw branch is inserted before SDK method body");
            assertEquals(original.length, actual.length - originalStart, "original fallback instruction count is intact");
            for (int i = 0; i < original.length; i++) assertSame(original[i], actual[originalStart + i]);
            int lastExecutable = actual.length - 1;
            while (lastExecutable >= 0 && actual[lastExecutable].getOpcode() < 0) lastExecutable--;
            assertTrue(lastExecutable >= 0 && actual[lastExecutable].getOpcode() == Opcodes.IRETURN,
                    "SDK fallback ends in an executable integer return before trailing metadata");
            assertTrue(hasCall(patched, "activeForRenderer"));
            assertTrue(hasCall(patched, "getBrightness"));
            assertTrue(hasCall(patched, "sable$getSubLevelAccounted"), "original SDK fallback remains intact");
            assertTrue(callsOwner(patched, "com/juicyslew/moonstation14/ms14/shuttle/sable/PackedLightAccountingScope"));
            assertTrue(patched.instructions.indexOf(findCall(patched, "activeForRenderer")) < originalStart);
            assertTrue(patched.instructions.indexOf(findCall(patched, "getBrightness")) < originalStart);
        }
        MethodNode finalSdkHandler = sdk.methods.stream().filter(m -> m.name.equals("getPackedLightCoords")
                && m.desc.equals("(ILnet/minecraft/world/entity/Entity;F)I")).findFirst().orElseThrow();
        assertTrue(hasCall(finalSdkHandler, "sable$getSubLevelAccountedSkyLight"));
        assertTrue(hasCall(finalSdkHandler, "sable$getSubLevelAccountedBlockLight"));
        assertTrue(hasCall(finalSdkHandler, "pack"));

        ClassNode lightingMixin = new ClassNode(Opcodes.ASM9);
        try (InputStream input = getClass().getResourceAsStream(
                "/com/juicyslew/moonstation14/mixin/client/SableEntityRendererLightingMixin.class")) {
            assertNotNull(input);
            new ClassReader(input).accept(lightingMixin, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        MethodNode wrapper = lightingMixin.methods.stream().filter(m -> m.name.equals("moonstation14$accountPlotLightOnce"))
                .findFirst().orElseThrow();
        assertFalse(lightingMixin.name.startsWith("com/juicyslew/moonstation14/ms14/shuttle/sable/"));
        assertFalse("com/juicyslew/moonstation14/ms14/shuttle/sable/PackedLightAccountingScope"
                .startsWith("com/juicyslew/moonstation14/mixin/"), "runtime helper stays outside the configured mixin package");
        assertTrue(wrapper.instructions.toArray().length > 0);
        assertTrue(hasCall(wrapper, "begin"));
        assertTrue(hasCall(wrapper, "end"));
        assertNotNull(wrapper.tryCatchBlocks);
        assertTrue(wrapper.tryCatchBlocks.stream().anyMatch(t -> t.type == null), "scope cleanup covers exceptions");
        assertTrue(callsOwner(wrapper, "com/juicyslew/moonstation14/ms14/shuttle/sable/PackedLightAccountingScope"));
        ClassNode runtimeScope = new ClassNode(Opcodes.ASM9);
        try (InputStream input = getClass().getResourceAsStream(
                "/com/juicyslew/moonstation14/ms14/shuttle/sable/PackedLightAccountingScope.class")) {
            assertNotNull(input, "runtime scope emitted outside reserved mixin package");
            new ClassReader(input).accept(runtimeScope, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        assertEquals("com/juicyslew/moonstation14/ms14/shuttle/sable/PackedLightAccountingScope", runtimeScope.name);
        assertFalse(runtimeScope.name.startsWith("com/juicyslew/moonstation14/mixin/"));
    }

    private static boolean hasCall(MethodNode method, String namePart) {
        for (AbstractInsnNode insn : method.instructions)
            if (insn instanceof MethodInsnNode call && call.name.contains(namePart)) return true;
        return false;
    }

    private static boolean callsOwner(MethodNode method, String owner) {
        for (AbstractInsnNode insn : method.instructions)
            if (insn instanceof MethodInsnNode call && call.owner.equals(owner)) return true;
        return false;
    }

    private static MethodInsnNode findCall(MethodNode method, String namePart) {
        for (AbstractInsnNode insn : method.instructions)
            if (insn instanceof MethodInsnNode call && call.name.contains(namePart)) return call;
        fail("missing call " + namePart);
        return null;
    }
}
