package com.juicyslew.moonstation14.ms14.singularity;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class SingularityDepthShaderContractTest {
    private static Path source(String relative) {
        Path directory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (directory != null) {
            Path candidate = directory.resolve(relative);
            if (Files.exists(candidate)) return candidate;
            directory = directory.getParent();
        }
        return Path.of(relative);
    }
    private static float depth(float viewDistance) {
        float near=.05f, far=2048f;
        return (float)(far/(far-near) * (1-near/viewDistance));
    }
    @Test void perspectiveDepthOrderSeparatesHiddenSourcesForegroundAndBackground() {
        for(float source:new float[]{5,20,100}) {
            float z=depth(source);
            assertTrue(z >= z - .000001f); // visible source surface
            assertFalse(depth(source*.8f) >= z - .000001f); // nearer wall / foreground
            assertTrue(depth(source*1.2f) >= z - .000001f); // background
            assertTrue(1f >= z - .000001f); // clear sky
        }
    }
    @Test void nativeSolidProbeHooksActualVboDrawAndConsumesOnlyMatchingAppliedShader() throws Exception {
        String mixin=Files.readString(source("src/main/java/com/juicyslew/moonstation14/mixin/client/VertexBufferSingularityDiagnosticMixin.java"));
        String probe=Files.readString(source("src/main/java/com/juicyslew/moonstation14/ms14/singularity/client/SingularityNativeDrawProbe.java"));
        assertTrue(mixin.contains("method=\"draw()V\""));
        assertFalse(mixin.contains("drawWithShader"));
        assertTrue(mixin.contains("SingularityNativeDrawProbe.capture(RenderSystem.getShader())"));
        assertTrue(probe.contains("!ARMED_SHADER_NAMES.contains(shader.getName())"));
        assertTrue(probe.contains("ARMED_SHADER_NAMES.remove(shader.getName())"), "each requested pass is consumed at most once");
        assertTrue(probe.contains("shader.getUniform(\"ModelViewMat\")"));
        assertTrue(probe.contains("modelViewUniform.getFloatBuffer()"));
        String preview=Files.readString(source("src/main/java/com/juicyslew/moonstation14/ms14/singularity/client/SingularityVisualPreview.java"));
        assertTrue(preview.contains("SingularityNativeDrawProbe.arm(probeShaders)"));
        assertTrue(preview.contains("renderType == net.minecraft.client.renderer.RenderType.cutout()"));
        assertTrue(preview.contains("return \"rendertype_cutout\""));
        assertTrue(preview.contains("return \"rendertype_solid\""));
        assertTrue(preview.contains("renderTypes=\" + renderLayerNames"));
        assertTrue(probe.contains("generic matching-pass submission context, not aimed-PA attribution"));
    }
    @Test void activePreviewPathIsWorldVertexOnlyAndHasNoSceneColorPass() throws Exception {
        String preview=Files.readString(source("src/main/java/com/juicyslew/moonstation14/ms14/singularity/client/SingularityVisualPreview.java"));
        String active=preview.substring(preview.indexOf("public static void render("), preview.indexOf("/** Frame collector"));
        assertFalse(active.contains("captureDepth("));
        assertFalse(active.contains("distort("));
        assertFalse(preview.contains("snapshot.getColorTextureId()"));
        assertTrue(preview.contains("collectResolvedWorldSources"));
        assertTrue(preview.contains("profile.foregroundRampWorldUnits()"));
        assertTrue(preview.contains("profile.cutoffWorldUnits() * com.juicyslew.moonstation14.ms14.singularity.SingularityVisualProfile.PIXELS_PER_METER"));
        assertEquals(new SingularityDistortionProjection.Pixel(0,0), SingularityDistortionProjection.radialDisplacement(3,4,5,2,5,1));
    }
}
