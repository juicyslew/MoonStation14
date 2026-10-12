package com.juicyslew.moonstation14.resource;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.LightTexture;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApcScreenGlowResourceTest {
    private static final String ROOT = "/assets/moonstation14/";
    private static final Map<String, String> DISPLAYS = Map.of(
            "full,on,fixed_on", "apc_screen_glow",
            "charging", "apc_charging_screen_glow",
            "lack,off,unpowered,dead", "apc_lack_screen_glow");

    @Test
    void nativeFaceBakeryBakesFullBrightUv2OnEveryFullAndChargingVertex() {
        for (String modelId : new String[]{"apc_screen_glow", "apc_charging_screen_glow"}) {
            BlockModel model;
            try (var stream = ApcScreenGlowResourceTest.class.getResourceAsStream(
                    ROOT + "models/block/" + modelId + ".json")) {
                if (stream == null) throw new AssertionError("Missing native model " + modelId);
                model = BlockModel.fromStream(new InputStreamReader(stream, StandardCharsets.UTF_8));
            } catch (Exception exception) {
                throw new AssertionError("Unable to deserialize native model " + modelId, exception);
            }
            BlockElement element = model.getElements().getFirst();
            BlockElementFace face = element.faces.get(Direction.NORTH);
            var image = new NativeImage(16, 16, false);
            try (var contents = new SpriteContents(id("block/" + modelId),
                    new FrameSize(16, 16), image, ResourceMetadata.EMPTY)) {
                var sprite = new TestSprite(contents);
                BakedQuad quad = new FaceBakery().bakeQuad(element.from, element.to, face, sprite,
                        Direction.NORTH, BlockModelRotation.X0_Y0, element.rotation, element.shade);
                assertEquals(Direction.NORTH, quad.getDirection());
                assertTrue(!quad.isShade(), modelId + " should disable baked face shading");
                assertTrue(!quad.hasAmbientOcclusion(), modelId + " should disable per-face AO");
                int[] vertices = quad.getVertices();
                assertEquals(FaceBakery.VERTEX_COUNT * FaceBakery.VERTEX_INT_SIZE, vertices.length);
                for (int vertex = 0; vertex < FaceBakery.VERTEX_COUNT; vertex++) {
                    int lightmap = vertices[vertex * FaceBakery.VERTEX_INT_SIZE + 6];
                    assertEquals(LightTexture.FULL_BRIGHT, lightmap,
                            modelId + " baked UV2 at vertex " + vertex);
                }
            }
        }
    }

    @Test
    void glowOverlayConditionsCoverEachDisplayFamilyAndFacing() {
        var multipart = read("blockstates/apc.json").getAsJsonArray("multipart");
        for (var display : DISPLAYS.entrySet()) {
            for (String facing : new String[]{"north", "east", "south", "west"}) {
                String model = null;
                Integer expectedRotation = null;
                for (var entry : multipart) {
                    JsonObject condition = entry.getAsJsonObject().getAsJsonObject("when");
                    if ((!condition.has("glow") || "true".equals(condition.get("glow").getAsString()))
                            && display.getKey().equals(condition.get("display").getAsString())
                            && facing.equals(condition.get("facing").getAsString())) {
                        JsonObject apply = entry.getAsJsonObject().getAsJsonObject("apply");
                        model = apply.get("model").getAsString();
                        expectedRotation = apply.has("y") ? apply.get("y").getAsInt() : 0;
                        break;
                    }
                }
                assertEquals("moonstation14:block/" + display.getValue(), model,
                        "missing correct glow model for " + display.getKey() + " facing " + facing);
                assertEquals(switch (facing) {
                    case "north" -> 0;
                    case "east" -> 90;
                    case "south" -> 180;
                    default -> 270;
                }, expectedRotation);
                if (display.getValue().equals("apc_screen_glow") || display.getValue().equals("apc_charging_screen_glow")) {
                    assertTrue(multipart.asList().stream().noneMatch(entry -> {
                        JsonObject condition = entry.getAsJsonObject().getAsJsonObject("when");
                        return display.getKey().equals(condition.get("display").getAsString()) && condition.has("glow");
                    }), "Charging/Full overlay must not depend on GLOW");
                }
            }
        }
    }

    @Test
    void glowModelsMatchScreenGeometryAndCarryFullBrightFaceData() {
        JsonObject baseScreen = read("models/block/apc.json").getAsJsonArray("elements").get(3)
                .getAsJsonObject();
        for (String modelId : DISPLAYS.values()) {
            JsonObject model = read("models/block/" + modelId + ".json");
            JsonObject overlay = model.getAsJsonArray("elements").get(0).getAsJsonObject();
            var screenFrom = baseScreen.getAsJsonArray("from");
            var glowFrom = overlay.getAsJsonArray("from");
            var screenTo = baseScreen.getAsJsonArray("to");
            var glowTo = overlay.getAsJsonArray("to");
            assertEquals(screenFrom.get(0), glowFrom.get(0));
            assertEquals(screenFrom.get(1), glowFrom.get(1));
            assertEquals(screenTo.get(0), glowTo.get(0));
            assertEquals(screenTo.get(1), glowTo.get(1));
            assertEquals(12.75, screenFrom.get(2).getAsDouble());
            assertEquals(12.748, glowFrom.get(2).getAsDouble());
            assertTrue(glowTo.get(2).getAsDouble() < screenTo.get(2).getAsDouble(),
                    "overlay must be a front-facing thin screen plane, not the full bezel depth");
            assertEquals(baseScreen.get("shade"), overlay.get("shade"));
            JsonObject face = overlay.getAsJsonObject("faces").getAsJsonObject("north");
            JsonObject baseFace = baseScreen.getAsJsonObject("faces").getAsJsonObject("north");
            assertEquals(baseFace.getAsJsonArray("uv"), face.getAsJsonArray("uv"));
            JsonObject light = face.getAsJsonObject("neoforge_data");
            assertEquals(15, light.get("block_light").getAsInt());
            assertEquals(15, light.get("sky_light").getAsInt());
            assertTrue(light.has("ambient_occlusion") && !light.get("ambient_occlusion").getAsBoolean());
        }
    }

    private static JsonObject read(String path) {
        try (var stream = ApcScreenGlowResourceTest.class.getResourceAsStream(ROOT + path)) {
            if (stream == null) throw new AssertionError("Missing resource " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception exception) {
            throw new AssertionError("Unable to read " + path, exception);
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("moonstation14", path);
    }

    private static final class TestSprite extends TextureAtlasSprite {
        private TestSprite(SpriteContents contents) {
            super(id("textures/atlas/blocks.png"), contents, 16, 16, 0, 0);
        }
    }
}
