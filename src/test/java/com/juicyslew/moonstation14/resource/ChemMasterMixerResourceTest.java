package com.juicyslew.moonstation14.resource;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChemMasterMixerResourceTest {
    private static final String ROOT = "/assets/moonstation14/";

    @Test
    void blockstateCoversPowerAndCommittedSlotForEveryFacing() {
        JsonObject variants = read("blockstates/chem_master.json").getAsJsonObject("variants");
        assertEquals(16, variants.size());
        Set<String> models = Set.of("moonstation14:block/machines/mixer/mixer_empty",
                "moonstation14:block/machines/mixer/mixer_screens",
                "moonstation14:block/machines/mixer/mixer_loaded",
                "moonstation14:block/machines/mixer/mixer_loaded_screens");
        for (String facing : Set.of("north", "east", "south", "west")) {
            for (boolean powered : new boolean[]{false, true}) for (boolean loaded : new boolean[]{false, true}) {
                JsonObject variant = variants.get("facing=" + facing + ",powered=" + powered + ",loaded=" + loaded).getAsJsonObject();
                String model = variant.get("model").getAsString();
                assertTrue(models.contains(model));
                assertEquals(facing.equals("north") ? 0 : facing.equals("east") ? 90 : facing.equals("south") ? 180 : 270,
                        variant.has("y") ? variant.get("y").getAsInt() : 0);
                String path = "models/" + model.substring(model.indexOf(':') + 1) + ".json";
                assertNotNull(resource(path), "missing model " + path);
            }
        }
        assertEquals("moonstation14:block/machines/mixer/mixer_empty",
                variants.get("facing=north,powered=false,loaded=false").getAsJsonObject().get("model").getAsString());
        assertEquals("moonstation14:block/machines/mixer/mixer_screens",
                variants.get("facing=north,powered=true,loaded=false").getAsJsonObject().get("model").getAsString());
        assertEquals("moonstation14:block/machines/mixer/mixer_loaded",
                variants.get("facing=north,powered=false,loaded=true").getAsJsonObject().get("model").getAsString());
        assertEquals("moonstation14:block/machines/mixer/mixer_loaded_screens",
                variants.get("facing=north,powered=true,loaded=true").getAsJsonObject().get("model").getAsString());
    }

    @Test
    void importedMixerAnimationMetadataAndModelTextureDependenciesExist() {
        JsonObject animation = read("textures/block/machines/mixer/mixer_screens.png.mcmeta").getAsJsonObject("animation");
        assertEquals(128, animation.get("width").getAsInt());
        assertEquals(128, animation.get("height").getAsInt());
        assertTrue(animation.getAsJsonArray("frames").size() > 1);
        for (String model : Set.of("mixer_empty", "mixer_screens", "mixer_loaded", "mixer_loaded_screens")) {
            JsonObject textures = read("models/block/machines/mixer/" + model + ".json").getAsJsonObject("textures");
            for (var texture : textures.entrySet()) {
                if (texture.getValue().getAsString().startsWith("moonstation14:")) {
                    String atlas = texture.getValue().getAsString().substring("moonstation14:".length());
                    assertNotNull(resource("textures/" + atlas + ".png"));
                }
            }
        }
        JsonObject loadedCombined = read("models/block/machines/mixer/mixer_loaded_screens.json");
        assertEquals("moonstation14:block/machines/mixer/mixer_loaded",
                loadedCombined.getAsJsonObject("textures").get("atlas").getAsString());
        assertEquals("moonstation14:block/machines/mixer/mixer_screens",
                loadedCombined.getAsJsonObject("textures").get("screens").getAsString());
        var elements = loadedCombined.getAsJsonArray("elements");
        var display = elements.asList().stream().map(e -> e.getAsJsonObject())
                .filter(e -> e.get("name").getAsString().equals("mix_display_inset")).findFirst().orElseThrow();
        assertEquals("#screens", display.getAsJsonObject("faces").getAsJsonObject("north").get("texture").getAsString());
        assertEquals("#atlas", elements.get(0).getAsJsonObject().getAsJsonObject("faces").getAsJsonObject("north").get("texture").getAsString());
        assertEquals("mixer_body", elements.get(0).getAsJsonObject().get("name").getAsString());
        assertEquals("#atlas", display.getAsJsonObject("faces").getAsJsonObject("east").get("texture").getAsString());
        int screenFaces = 0;
        for (var element : elements) for (var face : element.getAsJsonObject().getAsJsonObject("faces").entrySet())
            if (face.getValue().getAsJsonObject().get("texture").getAsString().equals("#screens")) screenFaces++;
        assertEquals(1, screenFaces, "only the authored front display is replaced by the animated screen atlas");
        JsonObject item = read("models/item/chem_master.json");
        assertEquals("moonstation14:block/machines/mixer/default", item.get("parent").getAsString());
    }

    private static JsonObject read(String path) {
        try (var stream = resource(path)) {
            if (stream == null) throw new AssertionError("Missing resource " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception exception) { throw new AssertionError("Unable to read " + path, exception); }
    }

    private static java.io.InputStream resource(String path) {
        return ChemMasterMixerResourceTest.class.getResourceAsStream(ROOT + path);
    }
}
