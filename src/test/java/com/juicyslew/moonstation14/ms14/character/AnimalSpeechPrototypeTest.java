package com.juicyslew.moonstation14.ms14.character;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.animals.AnimalCatalog;
import com.juicyslew.moonstation14.ms14.character.components.SpeechComponent;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AnimalSpeechPrototypeTest {
    @Test void everyCatalogHostResolvesTheNewAnimalSpeechFallbackWithoutChangingLegacyProfiles() throws Exception {
        Path root = Path.of(System.getProperty("moonstation14.projectDir"),
                "src/main/resources/data/moonstation14/moonstation14/character");
        Map<ResourceLocation, JsonObject> data = new LinkedHashMap<>();
        try (var paths = Files.walk(root)) {
            for (Path file : paths.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".json")).toList()) {
                String name = root.relativize(file).toString().replace('\\', '/');
                data.put(ResourceLocation.fromNamespaceAndPath("moonstation14", name.substring(0, name.length() - 5)),
                        JsonParser.parseString(Files.readString(file)).getAsJsonObject());
            }
        }
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModCharacters.CHARACTER_TYPE);
        manager.reload(ModCharacters.CHARACTER_TYPE, data);
        var profiles = manager.snapshot(ModCharacters.CHARACTER_TYPE);

        assertEquals(36, AnimalCatalog.entries().size());
        for (var host : AnimalCatalog.entries()) {
            var resolved = profiles.asMap().values().stream()
                    .filter(profile -> profile.hostEntityTypes().contains(host.host()))
                    .findFirst().orElseThrow(() -> new AssertionError("unbound animal host " + host.host()));
            assertTrue(resolved.component(SpeechComponent.class).isPresent(),
                    "resolved animal profile lacks speech aggregate: " + host.id());
        }

        var mouse = profiles.get(ResourceLocation.parse("moonstation14:mouse"));
        var mouseSpeech = mouse.component(SpeechComponent.class).orElseThrow();
        assertEquals("mouse", mouseSpeech.accent());
        assertEquals("mouse_squeak", mouseSpeech.speechSound());
        assertEquals("moonstation14:supported_npc", data.get(ResourceLocation.parse("moonstation14:mouse"))
                .get("parent").getAsString());

        var nativePig = profiles.get(ResourceLocation.parse("moonstation14:pig"));
        assertTrue(data.get(ResourceLocation.parse("moonstation14:pig")).has("host_entity_types"));
        assertTrue(nativePig.component(SpeechComponent.class).isEmpty(),
                "native Minecraft pig must not inherit the new animal wrapper");
        assertFalse(data.get(ResourceLocation.parse("moonstation14:pig")).has("parent"));
    }
}
