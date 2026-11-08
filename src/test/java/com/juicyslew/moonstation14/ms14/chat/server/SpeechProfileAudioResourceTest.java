package com.juicyslew.moonstation14.ms14.chat.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SpeechProfileAudioResourceTest {
    @Test void everyExtendedSpeechEventResolvesToAnInstalledSample() throws Exception {
        Path assets = Path.of(System.getProperty("moonstation14.projectDir"), "src/main/resources/assets/moonstation14");
        JsonObject sounds = JsonParser.parseString(Files.readString(assets.resolve("sounds.json"))).getAsJsonObject();
        Map<String, List<String>> profileSamples = Map.ofEntries(
                Map.entry("mouse_squeak", List.of("mouse_squeak")),
                Map.entry("speech/cluck", List.of("animals/chicken_cluck_happy")),
                Map.entry("speech/goat", List.of("animals/goat_bah")),
                Map.entry("speech/monkey", List.of("animals/monkey_scream")),
                Map.entry("speech/parrot", List.of("animals/parrot_raught")),
                Map.entry("speech/cat", List.of("animals/cat_meow2", "animals/cat_meow")),
                Map.entry("speech/dog", List.of("animals/dog_bark1", "animals/dog_bark2", "animals/dog_bark3")),
                Map.entry("speech/lizard", List.of("animals/lizard")),
                Map.entry("speech/lizard_ask", List.of("animals/lizard_ask")),
                Map.entry("speech/lizard_exclaim", List.of("animals/lizard_exclaim")),
                Map.entry("speech/arachnid", List.of("animals/arachnid")),
                Map.entry("speech/arachnid_ask", List.of("animals/arachnid_ask")),
                Map.entry("speech/arachnid_exclaim", List.of("animals/arachnid_exclaim")),
                Map.entry("speech/alto", List.of("animals/alto")),
                Map.entry("speech/alto_ask", List.of("animals/alto_ask")),
                Map.entry("speech/alto_exclaim", List.of("animals/alto_exclaim")),
                Map.entry("speech/wawa", List.of("animals/wawa_statement")),
                Map.entry("speech/wawa_ask", List.of("animals/wawa_question")),
                Map.entry("speech/wawa_exclaim", List.of("animals/wawa_exclaim")));
        for (var entry : profileSamples.entrySet()) {
            assertTrue(sounds.has(entry.getKey()), "missing event " + entry.getKey());
            var samples = sounds.getAsJsonObject(entry.getKey()).getAsJsonArray("sounds");
            assertEquals(entry.getValue().stream().map(id -> "moonstation14:" + id).toList(),
                    samples.asList().stream().map(value -> value.getAsString()).toList(),
                    "event maps to the wrong runtime sound resource: " + entry.getKey());
            for (String soundId : entry.getValue()) {
                Path physicalFile = assets.resolve("sounds/" + soundId + ".ogg");
                assertTrue(Files.isRegularFile(physicalFile), "missing physical sound file " + physicalFile);
                assertTrue(Files.size(physicalFile) > 0, "empty sound file " + physicalFile);
            }
        }
        assertTrue(Files.isRegularFile(assets.resolve("sounds/animals/ATTRIBUTION.md")));
    }
}
