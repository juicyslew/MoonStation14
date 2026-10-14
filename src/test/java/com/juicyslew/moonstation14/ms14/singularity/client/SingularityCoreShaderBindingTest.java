package com.juicyslew.moonstation14.ms14.singularity.client;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class SingularityCoreShaderBindingTest {
    private static String source(String relative) throws Exception {
        var directory = java.nio.file.Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (directory != null) {
            var candidate = directory.resolve(relative);
            if (java.nio.file.Files.exists(candidate)) return java.nio.file.Files.readString(candidate);
            directory = directory.getParent();
        }
        throw new java.nio.file.NoSuchFileException(relative);
    }
    @Test void dedicatedShaderBindsNativeCutoutProgramsAndCompleteNativeUniformCatalog() throws Exception {
        try (var input = getClass().getResourceAsStream("/assets/moonstation14/shaders/core/singularity_core.json")) {
            assertNotNull(input);
            var json = JsonParser.parseReader(new java.io.InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("rendertype_entity_cutout_no_cull", json.get("vertex").getAsString());
            assertEquals("rendertype_entity_cutout_no_cull", json.get("fragment").getAsString());
            assertEquals(java.util.List.of("Sampler0", "Sampler1", "Sampler2"), json.getAsJsonArray("samplers").asList().stream()
                    .map(s -> s.getAsJsonObject().get("name").getAsString()).toList());
            assertEquals(9, json.getAsJsonArray("uniforms").size());
            assertEquals(java.util.List.of("ModelViewMat", "ProjMat", "ColorModulator", "Light0_Direction", "Light1_Direction",
                            "FogStart", "FogEnd", "FogColor", "FogShape"),
                    json.getAsJsonArray("uniforms").asList().stream().map(u -> u.getAsJsonObject().get("name").getAsString()).toList());
        }
        assertEquals("moonstation14:singularity_core", SingularityWorldLensUniforms.CORE_PROGRAM);
        // The actual buffered source must bind the dedicated supplier and write native opaque depth.
        var rendererSource = source("src/main/java/com/juicyslew/moonstation14/entities/client/SingularityRenderer.java");
        var clientSource = source("src/main/java/com/juicyslew/moonstation14/MoonStation14Client.java");
        assertTrue(clientSource.contains("SingularityRenderer::registerShaders"));
        assertTrue(rendererSource.contains(".setShaderState(CORE_SHADER_STATE)"));
        assertTrue(rendererSource.contains(".setWriteMaskState(net.minecraft.client.renderer.RenderStateShard.COLOR_DEPTH_WRITE)"));
        assertFalse(rendererSource.contains("RENDERTYPE_ENTITY_CUTOUT_NO_CULL_SHADER"));
        assertTrue(rendererSource.contains("loaded -> {"));
        assertTrue(rendererSource.contains("MS14 singularity world shader initialized"));
    }
}
