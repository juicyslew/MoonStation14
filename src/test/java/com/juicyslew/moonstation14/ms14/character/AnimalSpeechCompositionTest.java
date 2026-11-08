package com.juicyslew.moonstation14.ms14.character;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.character.components.SpeechComponent;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AnimalSpeechCompositionTest {
    @Test void newAnimalFallbackIsResolvedWithoutChangingLegacyNpcComposition() throws Exception {
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

        var bat = profiles.get(ResourceLocation.parse("moonstation14:bat")).component(SpeechComponent.class).orElseThrow();
        assertEquals("Squeak", bat.speechSound());
        var duck = profiles.get(ResourceLocation.parse("moonstation14:duck")).component(SpeechComponent.class).orElseThrow();
        assertEquals("duck", duck.accent());
        assertEquals("", duck.speechSound(), "duck's SS14 replacement accent is text-only");
        var kobold = profiles.get(ResourceLocation.parse("moonstation14:kobold")).component(SpeechComponent.class).orElseThrow();
        assertEquals("kobold", kobold.accent());
        assertEquals("Lizard", kobold.speechSound());
        var snail = profiles.get(ResourceLocation.parse("moonstation14:snail"));
        assertTrue(snail.component(SpeechComponent.class).isPresent());
        assertTrue(snail.component(com.juicyslew.moonstation14.ms14.character.components.RespiratorComponent.class).isEmpty());

        var mouse = profiles.get(ResourceLocation.parse("moonstation14:mouse")).component(SpeechComponent.class).orElseThrow();
        assertEquals("mouse_squeak", mouse.speechSound(), "legacy mouse retains its explicit profile");
        JsonObject mouseJson = data.get(ResourceLocation.parse("moonstation14:mouse"));
        assertEquals("moonstation14:supported_npc", mouseJson.get("parent").getAsString());
        var goat = profiles.get(ResourceLocation.parse("moonstation14:goat")).component(SpeechComponent.class).orElseThrow();
        assertEquals("Goat", goat.speechSound());
        var gorilla = profiles.get(ResourceLocation.parse("moonstation14:gorilla")).component(SpeechComponent.class).orElseThrow();
        assertEquals("", gorilla.speechSound(), "speech fallback does not invent an animal voice");
    }
}
