package com.juicyslew.moonstation14.resource;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AirlockVerticalAssetResourceTest {
    private static final String ROOT = "/assets/moonstation14/";
    private static final String BASE = "models/block/airlock/vertical_all/standard_basic/";

    @Test
    void acceptedComponentAndOverlayResourcesResolveTheirTextureIds() {
        for (String part : List.of("fixed", "header_lamps", "upper_leaf", "lower_leaf", "upper_infill",
                "reader_cover", "reader_housing", "maintenance_wires"))
            assertModelTextures(BASE + "components/" + part + ".json");

        for (String state : List.of("closed_unlit", "opening_unlit", "bolted_unlit", "deny_unlit")) {
            assertModelTextures(BASE + "overlays/" + state + "_00_header.json");
            String reader = BASE + "overlays/" + state + "_00_reader.json";
            if (exists(reader)) assertModelTextures(reader);
        }
        assertTrue(exists("textures/block/airlock/vertical_all/standard_basic/atlas.png"));
        assertModelTextures(BASE + "poses/closed_opaque.json");
    }

    @Test
    void existingItemUsesAcceptedTwoBlockClosedPoseCandidate() {
        JsonObject item = read("models/item/airlock_vertical_standard_basic.json");
        assertEquals("moonstation14:block/airlock/vertical_all/standard_basic/poses/closed_opaque",
                item.get("parent").getAsString());
        assertTrue(exists(BASE + "poses/closed_opaque.json"));
        assertEquals("moonstation14:item/airlock_vertical_standard_basic",
                read("models/item/airlock.json").get("parent").getAsString());
    }

    @Test
    void importedSubsetDoesNotRegisterFourBlockPocketsOrWelding() {
        assertFalse(exists(BASE + "components/pocket_upper.json"));
        assertFalse(exists(BASE + "components/pocket_lower.json"));
        assertFalse(exists(BASE + "components/weld_bead.json"));
    }

    @Test
    void packagedProvenanceRetainsSpecificUpstreamCopyrightAndLicense() {
        String root = "provenance/airlock_vertical_standard_basic/";
        JsonObject meta = read(root + "source/rsi/standard_basic/meta.json");
        assertEquals("CC-BY-SA-3.0", meta.get("license").getAsString());
        assertEquals("Taken from tgstation at commit https://github.com/tgstation/tgstation/commit/c6e3401f2e7e1e55c57060cdf956a98ef1fefc24",
                meta.get("copyright").getAsString());
        assertTrue(readText(root + "ATTRIBUTION.md").contains("creativecommons.org/licenses/by-sa/3.0/legalcode"));
        var resources = readArray(root + "source/resources.json");
        boolean correctRecord = false;
        for (var entry : resources.getAsJsonArray()) {
            JsonObject record = entry.getAsJsonObject();
            if (record.get("resource").getAsString().endsWith("Standard/basic.rsi"))
                correctRecord = record.get("copyright").getAsString().contains("c6e3401f2e7e1e55c57060cdf956a98ef1fefc24");
        }
        assertTrue(correctRecord, "full source resource index must retain imported RSI attribution");
    }

    @Test
    void importedSubsetMatchesPortableFrozenSourceHashManifest() throws Exception {
        JsonObject manifest = read("provenance/airlock_vertical_standard_basic/import-manifest.json");
        assertEquals("f3ba84e807b61137c054c08fd36c4bebac4a07a0f37cc6ea88af645e12760b02",
                manifest.get("accepted_output_hash_manifest_sha256").getAsString());
        JsonObject files = manifest.getAsJsonObject("files");
        assertEquals(27, files.size());
        for (var entry : files.entrySet()) {
            byte[] data = readBytes(entry.getKey());
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            assertEquals(entry.getValue().getAsString(), java.util.HexFormat.of().formatHex(digest),
                    () -> "Frozen source hash mismatch for " + entry.getKey());
        }
    }

    @Test
    void acceptedClosedItemPoseFitsTheChosenTwoBlockRootBounds() {
        JsonObject pose = read(BASE + "poses/closed_opaque.json");
        assertEquals("moonstation14:block/airlock/vertical_all/standard_basic/poses/closed_opaque",
                read("models/item/airlock_vertical_standard_basic.json").get("parent").getAsString());
        for (var element : pose.getAsJsonArray("elements")) {
            JsonObject box = element.getAsJsonObject();
            var from = box.getAsJsonArray("from");
            var to = box.getAsJsonArray("to");
            assertTrue(from.get(0).getAsDouble() >= 0 && to.get(0).getAsDouble() <= 16);
            assertTrue(from.get(1).getAsDouble() >= 0 && to.get(1).getAsDouble() <= 32);
            assertTrue(from.get(2).getAsDouble() >= 0 && to.get(2).getAsDouble() <= 16);
        }
    }

    private static void assertModelTextures(String path) {
        JsonObject model = read(path);
        JsonObject textures = model.getAsJsonObject("textures");
        for (var entry : textures.entrySet()) {
            String id = entry.getValue().getAsString();
            if (id.startsWith("#")) continue;
            assertTrue(exists("textures/" + id.substring(id.indexOf(':') + 1) + ".png"),
                    () -> "Missing model texture " + id + " referenced by " + path);
        }
    }

    private static boolean exists(String path) {
        try (var stream = AirlockVerticalAssetResourceTest.class.getResourceAsStream(ROOT + path)) {
            return stream != null;
        } catch (Exception e) { throw new AssertionError(e); }
    }

    private static JsonObject read(String path) {
        try (var stream = AirlockVerticalAssetResourceTest.class.getResourceAsStream(ROOT + path)) {
            if (stream == null) throw new AssertionError("Missing resource " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) { throw new AssertionError("Unable to read " + path, e); }
    }

    private static String readText(String path) {
        try (var stream = AirlockVerticalAssetResourceTest.class.getResourceAsStream(ROOT + path)) {
            if (stream == null) throw new AssertionError("Missing resource " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) { throw new AssertionError("Unable to read " + path, e); }
    }

    private static byte[] readBytes(String path) {
        try (var stream = AirlockVerticalAssetResourceTest.class.getResourceAsStream(ROOT + path)) {
            if (stream == null) throw new AssertionError("Missing imported resource " + path);
            return stream.readAllBytes();
        } catch (Exception e) { throw new AssertionError("Unable to read " + path, e); }
    }

    private static com.google.gson.JsonArray readArray(String path) {
        try (var stream = AirlockVerticalAssetResourceTest.class.getResourceAsStream(ROOT + path)) {
            if (stream == null) throw new AssertionError("Missing resource " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonArray();
        } catch (Exception e) { throw new AssertionError("Unable to read " + path, e); }
    }
}
