package com.juicyslew.moonstation14.ms14.singularity.client;

import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class SingularityWorldLensUniformsTest {
    @AfterEach void clear() { SingularityWorldLensUniforms.deactivate(); }

    @Test void framePacksProjectionAndResolvedSourceFieldsWithoutNativeCalls() {
        Matrix4f projection = new Matrix4f().perspective(1.1f, 1.7f, .1f, 100f);
        var source = new SingularityWorldLensUniforms.Source(1, 2, -3, 4, 2.5f, 640, 1.25f, .75f);
        SingularityWorldLensUniforms.activate(projection, List.of(source));

        assertEquals(1, SingularityWorldLensUniforms.activeSourceCount());
        float[] packed = SingularityWorldLensUniforms.packedProjection();
        assertEquals(16, packed.length);
        assertEquals(projection.m00(), packed[0], 1e-6f);
        assertEquals(16, SingularityWorldLensUniforms.packedInverseProjection().length);
        packed[0] = -10;
        assertEquals(projection.m00(), SingularityWorldLensUniforms.packedProjection()[0], 1e-6f);
    }

    @Test void noFrameAndImmediateSuspensionAreIdentityState() {
        assertEquals(0, SingularityWorldLensUniforms.activeSourceCount());
        try (var suspension = SingularityWorldLensUniforms.suspend()) {
            assertTrue(SingularityWorldLensUniforms.isSuspended());
        }
        assertFalse(SingularityWorldLensUniforms.isSuspended());
        SingularityWorldLensUniforms.activate(new Matrix4f(), List.of());
        assertEquals(0, SingularityWorldLensUniforms.activeSourceCount());
    }

    @Test void sourceLimitIsFive() {
        var source = new SingularityWorldLensUniforms.Source(0, 0, 0, 1, 2, 640, 1, 1);
        assertDoesNotThrow(() -> SingularityWorldLensUniforms.activate(new Matrix4f(), List.of(source, source, source, source, source)));
        assertThrows(IllegalArgumentException.class, () -> SingularityWorldLensUniforms.activate(
                new Matrix4f(), List.of(source, source, source, source, source, source)));
    }

    @Test void occlusionGraphInvalidatesOnlyOnActiveOrWorldTransitions() {
        Object world = new Object();
        assertTrue(SingularityWorldLensClient.shouldInvalidateForFrameTransition(false, true, world, world));
        assertTrue(SingularityWorldLensClient.shouldInvalidateForFrameTransition(true, false, world, world));
        assertTrue(SingularityWorldLensClient.shouldInvalidateForFrameTransition(true, true, world, new Object()));
        assertFalse(SingularityWorldLensClient.shouldInvalidateForFrameTransition(true, true, world, world));
    }

    @Test void clientHookMixinsAreRegisteredAgainstNativeEntryPoints() throws Exception {
        String config;
        try (var input = getClass().getResourceAsStream("/moonstation14.mixins.json")) {
            assertNotNull(input);
            config = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(config.contains("client.ProgramSingularityLensMixin"));
        assertTrue(config.contains("client.ShaderInstanceSingularityLensMixin"));
        assertTrue(config.contains("client.LevelRendererSingularityLensMixin"));
        assertTrue(config.contains("client.FrustumSingularityLensMixin"));
        var compile = com.mojang.blaze3d.shaders.Program.class.getDeclaredMethod("compileShaderInternal",
                com.mojang.blaze3d.shaders.Program.Type.class, String.class, InputStream.class, String.class,
                com.mojang.blaze3d.preprocessor.GlslPreprocessor.class);
        assertEquals(int.class, compile.getReturnType());
        var renderLevel = net.minecraft.client.renderer.LevelRenderer.class.getDeclaredMethod("renderLevel",
                net.minecraft.client.DeltaTracker.class, boolean.class, net.minecraft.client.Camera.class,
                net.minecraft.client.renderer.GameRenderer.class, net.minecraft.client.renderer.LightTexture.class,
                org.joml.Matrix4f.class, org.joml.Matrix4f.class);
        assertEquals(void.class, renderLevel.getReturnType());
        assertEquals(boolean.class, net.minecraft.client.renderer.culling.Frustum.class
                .getDeclaredMethod("isVisible", net.minecraft.world.phys.AABB.class).getReturnType());
        assertEquals(void.class, net.minecraft.client.renderer.LevelRenderer.class.getDeclaredMethod("setupRender",
                net.minecraft.client.Camera.class, net.minecraft.client.renderer.culling.Frustum.class,
                boolean.class, boolean.class).getReturnType());
        assertEquals(void.class, net.minecraft.client.renderer.SectionOcclusionGraph.class.getDeclaredMethod("update",
                boolean.class, net.minecraft.client.Camera.class, net.minecraft.client.renderer.culling.Frustum.class,
                java.util.List.class).getReturnType());
    }
}
