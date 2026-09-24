package com.juicyslew.moonstation14.ms14.effect;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EmoteResourcesTest {
    @Test void soundCollectionsResolveEverySelectedResourceAndManifestCoversAllAssets() throws Exception {
        Path project = Path.of(System.getProperty("user.dir"));
        while (!Files.exists(project.resolve("src/main/resources/assets/moonstation14/sounds.json"))
                && project.getParent() != null) project = project.getParent();
        Path root = project.resolve("src/main/resources/assets/moonstation14");
        JsonObject sounds = JsonParser.parseString(Files.readString(root.resolve("sounds.json"))).getAsJsonObject();
        int references = 0;
        for (String id : Set.of("cough", "crying", "hew", "honk", "laugh", "scream", "weh", "whistle", "yawn")) {
            var event = sounds.getAsJsonObject("emote/" + id);
            assertNotNull(event);
            for (var variant : event.getAsJsonArray("sounds")) {
                String path = variant.getAsString().substring("moonstation14:".length());
                assertTrue(Files.exists(root.resolve("sounds").resolve(path + ".ogg")), path);
                references++;
            }
        }
        assertEquals(34, references);
        Path emotes = root.resolve("sounds/emote");
        var files = Files.walk(emotes).filter(path -> path.toString().endsWith(".ogg")).toList();
        assertEquals(34, files.size());
        assertFalse(Files.exists(emotes.resolve("whistle/whistle_4.ogg")));
        String manifest = Files.readString(project.resolve("docs/third-party-emote-audio.md"));
        Map<String, String> manifestHashes = new HashMap<>();
        for (String line : manifest.lines().toList()) {
            if (!line.startsWith("| ") || !line.endsWith(" |")) continue;
            String[] columns = line.substring(2, line.length() - 2).split(" \\| ", -1);
            if (columns.length == 5 && columns[4].matches("[0-9a-f]{64}")) {
                assertEquals("none", columns[3], "local modification status for " + columns[0]);
                assertNull(manifestHashes.put(columns[0], columns[4]), "duplicate checksum row " + columns[0]);
            }
        }
        assertEquals(34, manifestHashes.size());
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        for (Path file : files) {
            String destination = emotes.relativize(file).toString().replace('\\', '/');
            assertTrue(manifestHashes.containsKey(destination), "one checksum row for " + destination);
            String actualHash = HexFormat.of().formatHex(sha256.digest(Files.readAllBytes(file)));
            assertEquals(manifestHashes.get(destination), actualHash, destination);
        }
        JsonObject lang = JsonParser.parseString(Files.readString(root.resolve("lang/en_us.json"))).getAsJsonObject();
        for (String id : Set.of("cough", "crying", "hew", "honk", "laugh", "scream", "weh", "whistle", "yawn")) {
            assertTrue(lang.has("sounds.moonstation14.emote." + id));
            assertTrue(lang.has("moonstation14.emote." + id));
        }
    }
}
