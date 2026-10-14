package com.juicyslew.moonstation14.resource;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CanisterAssetImportResourceTest {
    private static final String ROOT = "/assets/moonstation14/";

    @Test
    void importedFilesMatchAcceptedSourceHashesAndContainRequiredStateDependencies() throws Exception {
        JsonObject manifest = read("models/block/canisters/canister-import-manifest.json");
        assertEquals("USER_ACCEPTED_AS_IS_rounded_R2_complete24", manifest.get("acceptance").getAsString());
        JsonArray files = manifest.getAsJsonArray("files");
        var names = new java.util.HashSet<String>();
        for (var element : files) {
            JsonObject file = element.getAsJsonObject();
            String path = file.get("path").getAsString();
            byte[] bytes;
            try (var stream = getClass().getResourceAsStream(ROOT + path)) {
                assertNotNull(stream, "Missing imported asset " + path);
                bytes = stream.readAllBytes();
            }
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            assertEquals(file.get("sha256").getAsString(), hash, "Source-byte mismatch: " + path);
            names.add(path);
        }
        for (String color : new String[]{"yellow", "orange"}) {
            for (String required : new String[]{"components/shell.obj", "components/shell.mtl", "components/shell_mask.obj",
                    "components/head.json", "components/connector.json", "components/lock_locked.json",
                    "components/lock_unlocked.json", "components/pressure_0.json", "components/pressure_1.json",
                    "components/pressure_2.json", "components/pressure_3.json", "components/tank_inserted.json"}) {
                assertEquals(true, names.contains("models/block/canisters/" + color + "/" + required),
                        "Missing required " + color + " dependency " + required);
            }
            assertEquals(true, names.contains("textures/block/canisters/" + color + "_pressure0.png.mcmeta"));
            assertEquals(false, names.contains("models/block/canisters/" + color + "/default.json"),
                    "Do not combine the whole-state inspection model with independently selected components");
        }
    }

    private JsonObject read(String path) {
        try (var stream = getClass().getResourceAsStream(ROOT + path)) {
            assertNotNull(stream, "Missing " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception exception) {
            throw new AssertionError("Unable to read " + path, exception);
        }
    }
}
