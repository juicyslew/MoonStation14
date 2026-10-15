package com.juicyslew.moonstation14.ms14.character;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.character.components.PortableBodyComponent;
import com.juicyslew.moonstation14.ms14.character.components.CanEscapeInventoryComponent;
import com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeResolver;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.*;

class PortableBodyPrototypeTest {
    @Test void mouseOptsIntoResolvedPortableBodyCapabilityAndHumansDoNot() throws Exception {
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModCharacters.CHARACTER_TYPE);
        manager.reload(ModCharacters.CHARACTER_TYPE, java.util.Map.of(
                id("supported_simple_mob"), read("supported_simple_mob"),
                id("supported_animal"), read("supported_animal"),
                id("supported_npc"), read("supported_npc"),
                id("mouse"), read("mouse"), id("human"), read("human")));
        var chars = manager.snapshot(ModCharacters.CHARACTER_TYPE);
        assertEquals(LooseObjectProfile.MOUSE_TINY_ID,
                chars.get(id("mouse")).component(PortableBodyComponent.class).orElseThrow().profileId());
        assertEquals(5f, chars.get(id("mouse")).component(CanEscapeInventoryComponent.class).orElseThrow()
                .baseResistTimeSeconds(), "resolved mouse composition inherits the pinned five-second default");
        assertTrue(chars.get(id("human")).component(CanEscapeInventoryComponent.class).isEmpty());
        assertTrue(chars.get(id("human")).component(PortableBodyComponent.class).isEmpty());

        var raw = new LinkedHashMap<ResourceLocation, JsonObject>();
        Path dir = projectPath("src/main/resources/data/moonstation14/moonstation14/loose_object_profile");
        try (var files = Files.list(dir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                raw.put(ResourceLocation.parse(json.get("id").getAsString()), json);
            }
        }
        var resolved = PrototypeResolver.resolve(raw);
        assertEquals(.4f, LooseObjectProfile.CODEC.parse(JsonOps.INSTANCE,
                resolved.get(LooseObjectProfile.MOUSE_TINY_ID)).getOrThrow().physicalWidth());
        assertEquals(.3f, LooseObjectProfile.CODEC.parse(JsonOps.INSTANCE,
                resolved.get(LooseObjectProfile.MOUSE_TINY_ID)).getOrThrow().physicalHeight());
        assertNotNull(resolved.get(LooseObjectProfile.PORTABLE_BODY_KEY_ID), "internal adapter has a supported resolved profile");
    }

    private static ResourceLocation id(String path) { return ResourceLocation.parse("moonstation14:" + path); }
    private JsonObject read(String name) throws Exception {
        try (var stream = getClass().getClassLoader().getResourceAsStream(
                "data/moonstation14/moonstation14/character/" + name + ".json")) {
            assertNotNull(stream, name);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    private static Path projectPath(String path) throws java.io.IOException {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(path);
            if (Files.isDirectory(candidate)) return candidate;
            current = current.getParent();
        }
        throw new java.io.IOException("Could not locate project path " + path);
    }
}
