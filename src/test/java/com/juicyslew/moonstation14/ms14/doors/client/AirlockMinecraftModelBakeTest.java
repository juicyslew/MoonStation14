package com.juicyslew.moonstation14.ms14.doors.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceMetadata;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** CPU-only verification through Minecraft's own model JSON deserializer and FaceBakery. */
class AirlockMinecraftModelBakeTest {
    private static final String ROOT = "/assets/moonstation14/models/block/airlock/vertical_all/standard_basic/";
    private static final List<String> COMPONENTS = List.of("fixed", "header_lamps", "upper_leaf", "lower_leaf",
            "upper_infill", "reader_cover", "reader_housing", "maintenance_wires");
    private static final List<String> STATES = List.of("overlays/closed_unlit_00_header", "overlays/opening_unlit_00_header",
            "overlays/opening_unlit_00_reader", "overlays/bolted_unlit_00_header", "overlays/deny_unlit_00_header",
            "overlays/deny_unlit_00_reader");

    @Test
    void allRegisteredComponentsAndLiveStateModelsDeserializeAndBakeAuthoredFaces() throws Exception {
        try (var sourceContents = contents("test:source"); var targetContents = contents("test:target")) {
            TextureAtlasSprite source = new FixtureSprite(id("source"), sourceContents, 64, 64, 0, 0);
            TextureAtlasSprite target = new FixtureSprite(id("target"), targetContents, 64, 64, 32, 16);
            FaceBakery bakery = new FaceBakery();
            for (String name : COMPONENTS) {
                BlockModel model = read("components/" + name + ".json");
                assertFalse(model.getElements().isEmpty(), name);
                for (BlockElement element : model.getElements()) {
                    for (var entry : element.faces.entrySet()) {
                        BakedQuad baked = bakery.bakeQuad(element.from, element.to, entry.getValue(), source,
                                entry.getKey(), BlockModelRotation.X0_Y0, element.rotation, element.shade);
                        assertBakedQuadInDoorVolume(baked, source, target, name);
                    }
                }
            }
            for (String name : STATES) {
                BlockModel model = read(name + ".json");
                assertFalse(model.getElements().isEmpty(), name);
                for (BlockElement element : model.getElements()) {
                    for (var entry : element.faces.entrySet()) {
                        BakedQuad baked = bakery.bakeQuad(element.from, element.to, entry.getValue(), source,
                                entry.getKey(), BlockModelRotation.X0_Y0, element.rotation, element.shade);
                        assertBakedQuadInDoorVolume(baked, source, target, name);
                    }
                }
            }
        }
    }

    private static void assertBakedQuadInDoorVolume(BakedQuad quad, TextureAtlasSprite source,
                                                     TextureAtlasSprite target, String model) {
        int[] vertices = quad.getVertices();
        assertEquals(32, vertices.length);
        List<AirlockQuadClipper.Vertex> bakedVertices = new java.util.ArrayList<>(4);
        for (int index = 0; index < 4; index++) {
            int offset = index * 8;
            float x = Float.intBitsToFloat(vertices[offset]);
            float y = Float.intBitsToFloat(vertices[offset + 1]);
            float z = Float.intBitsToFloat(vertices[offset + 2]);
            assertTrue(x >= -1 - 1e-6 && x <= 2 + 1e-6, model + " raw model x " + x);
            assertTrue(y >= -1e-6 && y <= 2 + 1e-6, model + " raw model y " + y);
            assertTrue(z >= -1 - 1e-6 && z <= 2 + 1e-6, model + " raw model z " + z);
            float sourceU = Float.intBitsToFloat(vertices[offset + 4]);
            float sourceV = Float.intBitsToFloat(vertices[offset + 5]);
            float normalizedU = source.getUOffset(sourceU);
            float normalizedV = source.getVOffset(sourceV);
            bakedVertices.add(new AirlockQuadClipper.Vertex(x, y, z, normalizedU, normalizedV));
        }
        for (AirlockQuadClipper.Vertex vertex : AirlockQuadClipper.clip(bakedVertices)) {
            assertTrue(vertex.x() >= -1e-6 && vertex.x() <= 1 + 1e-6, model + " x " + vertex.x());
            assertTrue(vertex.y() >= -1e-6 && vertex.y() <= 2 + 1e-6, model + " y " + vertex.y());
            assertTrue(vertex.z() >= -1e-6 && vertex.z() <= 1 + 1e-6, model + " z " + vertex.z());
            float normalizedU = vertex.u();
            float normalizedV = vertex.v();
            assertTrue(normalizedU >= -1e-6 && normalizedU <= 1 + 1e-6, model + " u " + normalizedU);
            assertTrue(normalizedV >= -1e-6 && normalizedV <= 1 + 1e-6, model + " v " + normalizedV);
            float targetU = AirlockBlockEntityRenderer.remapAtlasCoordinate(normalizedU, target.getU0(), target.getU1());
            float targetV = AirlockBlockEntityRenderer.remapAtlasCoordinate(normalizedV, target.getV0(), target.getV1());
            assertEquals(target.getU(normalizedU), targetU, 1e-6, model + " atlas U rebase");
            assertEquals(target.getV(normalizedV), targetV, 1e-6, model + " atlas V rebase");
        }
    }

    private static BlockModel read(String path) throws Exception {
        try (var stream = AirlockMinecraftModelBakeTest.class.getResourceAsStream(ROOT + path)) {
            assertNotNull(stream, path);
            return BlockModel.fromStream(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
    }

    private static SpriteContents contents(String path) {
        NativeImage image = new NativeImage(16, 16, false);
        return new SpriteContents(id(path), new FrameSize(16, 16), image, ResourceMetadata.EMPTY);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.parse(path);
    }

    private static final class FixtureSprite extends TextureAtlasSprite {
        private FixtureSprite(ResourceLocation atlas, SpriteContents contents, int atlasWidth, int atlasHeight, int x, int y) {
            super(atlas, contents, atlasWidth, atlasHeight, x, y);
        }
    }
}
