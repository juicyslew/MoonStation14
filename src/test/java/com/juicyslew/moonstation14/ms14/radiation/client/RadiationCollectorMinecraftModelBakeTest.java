package com.juicyslew.moonstation14.ms14.radiation.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.juicyslew.moonstation14.ms14.radiation.ModRadiationCollectors;
import com.juicyslew.moonstation14.ms14.radiation.RadiationCollectorBlockEntity;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceMetadata;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import org.joml.Vector3f;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises generated adapter JSON through Minecraft's own deserializer and FaceBakery. */
class RadiationCollectorMinecraftModelBakeTest {
    private static final String ROOT = "/assets/moonstation14/models/block/reactor_bindings/collector/";

    @Test void allShellPanelAndPressureAdapterModelsDeserializeAndBake() throws Exception {
        var registered = RadiationCollectorBlockEntityRenderer.additionalModels();
        assertEquals(14, registered.size());
        assertTrue(registered.stream().noneMatch(model -> model.toString().contains("motion_")),
                "whole-pose OBJ alternatives are not layered with shell/panel geometry");
        try (var contents = contents()) {
            TextureAtlasSprite sprite = new TextureAtlasSprite(id("test:atlas"), contents, 128, 128, 0, 0) {};
            FaceBakery bakery = new FaceBakery();
            var paths = new ArrayList<String>();
            for (int tank = 0; tank <= 1; tank++) for (int band = 0; band <= 3; band++)
                paths.add("shell_tank" + tank + "_pressure" + band);
            paths.add("panel_active"); paths.add("panel_inactive");
            for (int band = 0; band <= 3; band++) paths.add("pressure_overlay_p" + band);
            for (String path : paths) {
                BlockModel model = read(path + ".json");
                assertFalse(model.getElements().isEmpty(), path);
                int faces = 0;
                for (var element : model.getElements()) for (var face : element.faces.entrySet()) {
                    var quad = bakery.bakeQuad(element.from, element.to, face.getValue(), sprite, face.getKey(),
                            BlockModelRotation.X0_Y0, element.rotation, element.shade);
                    assertEquals(32, quad.getVertices().length, path + " baked quad");
                    faces++;
                }
                assertTrue(faces > 0, path + " authored faces bake");
            }
        }
    }

    @Test void rawDefaultPanelIsFlatThenGlassAndLidFollowTheSingleHostClipWithinDeclaredBounds() throws Exception {
        BlockModel panel = read("panel_inactive.json");
        assertEquals(3, panel.getElements().size());
        var lid = panel.getElements().get(0);
        var glass = panel.getElements().get(1);
        assertVector(glass.from, 1.7f, 10, 4.5f);
        assertVector(glass.to, 14.3f, 10.16f, 11.5f);
        assertVector(lid.from, 1, 8, 4);
        assertVector(lid.to, 15, 10, 12);

        var snapshot = new RadiationCollectorBlockEntity.CollectorVisualSnapshot(true, false, 0,
                ModRadiationCollectors.EMPTY_ID, true, true, 100);
        float angle0 = RadiationCollectorRenderPlan.from(snapshot, 100).panelAngleDegrees();
        float angle90 = RadiationCollectorRenderPlan.from(snapshot, 108).panelAngleDegrees();
        assertEquals(0, angle0);
        assertEquals(90, angle90);
        assertBounds(glass, angle0, 10f / 16, 10.16f / 16, 4.5f / 16, 11.5f / 16);
        assertBounds(lid, angle0, 8f / 16, 10f / 16, 4f / 16, 12f / 16);
        assertBounds(glass, angle90, 10f / 16, 17f / 16, 11.5f / 16, 11.66f / 16);
        assertBounds(lid, angle90, 9.5f / 16, 17.5f / 16, 9.5f / 16, 11.5f / 16);

        for (float angle : new float[]{angle0, 22.5f, 45, 67.5f, angle90}) {
            BlockModel shell = read("shell_tank0_pressure0.json");
            BlockModel pressure = read("pressure_overlay_p0.json");
            for (var element : shell.getElements()) assertWithinRendererBounds(element, 0);
            for (var element : pressure.getElements()) assertWithinRendererBounds(element, 0);
            for (var element : panel.getElements()) assertWithinRendererBounds(element, angle);
        }
    }

    private static void assertWithinRendererBounds(net.minecraft.client.renderer.block.model.BlockElement element,
                                                   float angleDegrees) {
        for (int mask = 0; mask < 8; mask++) {
            float x = ((mask & 1) == 0 ? element.from.x() : element.to.x()) / 16;
            float y = ((mask & 2) == 0 ? element.from.y() : element.to.y()) / 16;
            float z = ((mask & 4) == 0 ? element.from.z() : element.to.z()) / 16;
            Vector3f point = transform(x, y, z, angleDegrees);
            assertTrue(point.x >= 1f / 16 - 1e-5 && point.x <= 15f / 16 + 1e-5, "renderer x bounds");
            assertTrue(point.y >= -1e-5 && point.y <= 20f / 16 + 1e-5, "renderer y bounds");
            assertTrue(point.z >= 2.8f / 16 - 1e-5 && point.z <= 21f / 16 + 1e-5, "renderer z bounds");
        }
    }

    private static void assertBounds(net.minecraft.client.renderer.block.model.BlockElement element, float angle,
                                     float minY, float maxY, float minZ, float maxZ) {
        float loY = Float.POSITIVE_INFINITY, hiY = Float.NEGATIVE_INFINITY;
        float loZ = Float.POSITIVE_INFINITY, hiZ = Float.NEGATIVE_INFINITY;
        for (int mask = 0; mask < 8; mask++) {
            Vector3f point = transform(((mask & 1) == 0 ? element.from.x() : element.to.x()) / 16,
                    ((mask & 2) == 0 ? element.from.y() : element.to.y()) / 16,
                    ((mask & 4) == 0 ? element.from.z() : element.to.z()) / 16, angle);
            loY = Math.min(loY, point.y); hiY = Math.max(hiY, point.y);
            loZ = Math.min(loZ, point.z); hiZ = Math.max(hiZ, point.z);
        }
        assertEquals(minY, loY, 1e-5); assertEquals(maxY, hiY, 1e-5);
        assertEquals(minZ, loZ, 1e-5); assertEquals(maxZ, hiZ, 1e-5);
    }

    private static Vector3f transform(float x, float y, float z, float angle) {
        PoseStack pose = new PoseStack();
        pose.translate(.5f, 10f / 16, 11.5f / 16);
        pose.mulPose(Axis.XP.rotationDegrees(angle));
        pose.translate(-.5f, -10f / 16, -11.5f / 16);
        return pose.last().pose().transformPosition(new Vector3f(x, y, z));
    }

    private static void assertVector(Vector3f actual, float x, float y, float z) {
        assertEquals(x, actual.x, 1e-5); assertEquals(y, actual.y, 1e-5); assertEquals(z, actual.z, 1e-5);
    }

    private static BlockModel read(String path) throws Exception {
        try (var stream = RadiationCollectorMinecraftModelBakeTest.class.getResourceAsStream(ROOT + path)) {
            assertNotNull(stream, path);
            return BlockModel.fromStream(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
    }
    private static SpriteContents contents() {
        return new SpriteContents(id("test:atlas"), new FrameSize(128, 128), new NativeImage(128, 128, false), ResourceMetadata.EMPTY);
    }
    private static ResourceLocation id(String value) { return ResourceLocation.parse(value); }
}
