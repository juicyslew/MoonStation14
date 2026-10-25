package com.juicyslew.moonstation14.resource;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
            assertTrue(AcceptedAssetHash.matchesAcceptedHash(bytes, path, file.get("sha256").getAsString()),
                    "Source-byte mismatch: " + path);
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

    @Test
    void acceptedTextHashesNormalizeOnlyLineEndingsAndBinaryHashRemainsRaw() throws Exception {
        for (String extension : new String[]{".json", ".mcmeta", ".obj", ".mtl"}) {
            byte[] lf = ("content" + extension + "\n").getBytes(StandardCharsets.UTF_8);
            byte[] crlf = ("content" + extension + "\r\n").getBytes(StandardCharsets.UTF_8);
            String crlfPin = AcceptedAssetHash.sha256(crlf);
            String lfPin = AcceptedAssetHash.sha256(lf);
            assertTrue(AcceptedAssetHash.matchesAcceptedHash(lf, "asset" + extension, crlfPin), extension);
            assertTrue(AcceptedAssetHash.matchesAcceptedHash(crlf, "asset" + extension, crlfPin), extension);
            assertTrue(AcceptedAssetHash.matchesAcceptedHash(lf, "asset" + extension, lfPin), extension);
            byte[] changed = ("changed" + extension + "\r\n").getBytes(StandardCharsets.UTF_8);
            byte[] addedNewline = ("content" + extension + "\r\n").getBytes(StandardCharsets.UTF_8);
            byte[] noTerminalNewline = ("content" + extension).getBytes(StandardCharsets.UTF_8);
            assertFalse(AcceptedAssetHash.matchesAcceptedHash(changed, "asset" + extension, crlfPin), extension);
            assertFalse(AcceptedAssetHash.matchesAcceptedHash(noTerminalNewline, "asset" + extension, crlfPin), extension);
            assertFalse(AcceptedAssetHash.matchesAcceptedHash(addedNewline, "asset" + extension,
                    AcceptedAssetHash.sha256(noTerminalNewline)), extension);
            assertNotEquals(AcceptedAssetHash.sha256(lf), AcceptedAssetHash.sha256(crlf));
        }
        byte[] binary = new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
        assertTrue(AcceptedAssetHash.matchesAcceptedHash(binary, "image.png", AcceptedAssetHash.sha256(binary)));
        assertFalse(AcceptedAssetHash.matchesAcceptedHash(binary, "image.png", AcceptedAssetHash.sha256(binary) + "x"));
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
