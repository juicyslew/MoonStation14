package com.juicyslew.moonstation14.resource;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisposalUnitModelResourceTest {
    private static final String ROOT = "/assets/moonstation14/";
    private static final Map<String, String> STATES = Map.of(
            "disposal_unit", "disposal/disposal-controls",
            "disposal_unit_unpowered", "disposal/condisposal-controls",
            "disposal_unit_ready", "disposal/disposal-controls",
            "disposal_unit_charging", "disposal/disposal-charging-controls",
            "disposal_unit_flushed", "disposal/disposal-flush-controls");

    @Test
    void allUnitModelsResolveSharedGeometryAndExistingTextures() {
        JsonObject base = read("models/block/disposal_unit.json");
        assertEquals("moonstation14:block/disposal/disposal_unit_metal",base.getAsJsonObject("textures").get("body").getAsString());
        assertEquals("moonstation14:block/disposal/disposal_unit_rim",base.getAsJsonObject("textures").get("rim").getAsString());
        assertEquals("moonstation14:block/disposal/disposal_unit_intake",base.getAsJsonObject("textures").get("dark").getAsString());
        JsonArray elements = base.getAsJsonArray("elements");
        assertTrue(elements.size() > 1, "cabinet must have separate modeled parts");
        int panelFaces = 0;
        for (var element : elements) {
            JsonObject part = element.getAsJsonObject();
            for (String bound : new String[]{"from", "to"}) {
                for (var coordinate : part.getAsJsonArray(bound)) {
                    assertTrue(coordinate.getAsDouble() >= 0 && coordinate.getAsDouble() <= 16,
                            "model exceeds unit bounds");
                }
            }
            JsonObject faces = part.getAsJsonObject("faces");
            for (var entry : faces.entrySet()) {
                if (entry.getValue().getAsJsonObject().get("texture").getAsString().equals("#panel")) panelFaces++;
            }
        }
        assertEquals(1, panelFaces, "native RSI artwork should be limited to the front inset panel");
        var panelUv=elements.get(4).getAsJsonObject().getAsJsonObject("faces").getAsJsonObject("north").getAsJsonArray("uv");
        assertEquals(0.0,panelUv.get(0).getAsDouble());
        assertEquals(0.0,panelUv.get(1).getAsDouble());
        assertEquals(16.0,panelUv.get(2).getAsDouble());
        assertEquals(16.0,panelUv.get(3).getAsDouble());
        assertResource(ROOT+"textures/block/disposal/disposal_unit_metal.png");
        assertResource(ROOT+"textures/block/disposal/disposal_unit_rim.png");
        assertResource(ROOT+"textures/block/disposal/disposal_unit_intake.png");
        assertTextureSize(ROOT+"textures/block/disposal/disposal-controls.png",12,12);
        assertTextureSize(ROOT+"textures/block/disposal/condisposal-controls.png",12,12);
        assertTextureSize(ROOT+"textures/block/disposal/disposal-charging-controls.png",12,12);
        assertTextureSize(ROOT+"textures/block/disposal/disposal-flush-controls.png",12,144);

        Map<String, String> statePanels = new HashMap<>();
        for (var state : STATES.entrySet()) {
            JsonObject model = read("models/block/" + state.getKey() + ".json");
            JsonObject resolved = resolveModel(state.getKey(), model);
            assertEquals(elements, resolved.getAsJsonArray("elements"),
                    "state model must inherit the shared cabinet geometry");
            assertEquals("moonstation14:block/" + state.getValue(),
                    resolved.getAsJsonObject("textures").get("panel").getAsString());
            statePanels.put(state.getKey(), state.getValue());
            assertTrue(resolved.getAsJsonArray("elements").size() > 1);
            for (var texture : resolved.getAsJsonObject("textures").entrySet()) {
                String id = texture.getValue().getAsString();
                String path = id.startsWith("minecraft:") ? "/assets/minecraft/" : ROOT;
                String resource = id.substring(id.indexOf(':') + 1).replace(':', '/') + ".png";
                assertResource(path + "textures/" + resource);
            }
        }
        assertEquals(5, statePanels.size());
        assertTrue(statePanels.get("disposal_unit_charging").contains("charging"));
        assertTrue(statePanels.get("disposal_unit_flushed").contains("flush"));
        assertResource(ROOT + "textures/block/disposal/disposal-flush.png.mcmeta");
    }

    private static JsonObject resolveModel(String modelName, JsonObject model) {
        JsonObject resolved = new JsonObject();
        JsonObject textures = new JsonObject();
        JsonArray elements;
        if (model.has("parent")) {
            assertEquals("moonstation14:block/disposal_unit", model.get("parent").getAsString());
            JsonObject parent = read("models/block/disposal_unit.json");
            JsonObject inherited = resolveModel("disposal_unit", parent);
            textures = inherited.getAsJsonObject("textures").deepCopy();
            elements = inherited.getAsJsonArray("elements").deepCopy();
        } else {
            assertEquals("disposal_unit", modelName, "only the shared base may omit a parent");
            elements = model.getAsJsonArray("elements").deepCopy();
        }
        if (model.has("textures")) {
            for (var texture : model.getAsJsonObject("textures").entrySet()) {
                textures.add(texture.getKey(), texture.getValue().deepCopy());
            }
        }
        resolved.add("textures", textures);
        resolved.add("elements", elements);
        return resolved;
    }

    private static JsonObject read(String resource) {
        try (var stream = DisposalUnitModelResourceTest.class.getResourceAsStream(ROOT + resource)) {
            if (stream == null) throw new AssertionError("Missing resource " + resource);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception exception) {
            throw new AssertionError("Unable to read " + resource, exception);
        }
    }

    private static void assertResource(String resource) {
        try (var stream = DisposalUnitModelResourceTest.class.getResourceAsStream(resource)) {
            assertTrue(stream != null, "Missing texture " + resource);
        } catch (Exception exception) {
            throw new AssertionError("Unable to read " + resource, exception);
        }
    }
    private static void assertTextureSize(String resource,int width,int height) {
        try (var stream=DisposalUnitModelResourceTest.class.getResourceAsStream(resource)) {
            assertTrue(stream!=null,"Missing texture "+resource);
            var image=javax.imageio.ImageIO.read(stream);assertEquals(width,image.getWidth());assertEquals(height,image.getHeight());
        } catch(Exception exception) { throw new AssertionError("Unable to inspect texture "+resource,exception); }
    }
}
