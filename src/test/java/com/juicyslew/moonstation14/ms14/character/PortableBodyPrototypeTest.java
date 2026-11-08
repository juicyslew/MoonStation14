package com.juicyslew.moonstation14.ms14.character;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.character.components.PortableBodyComponent;
import com.juicyslew.moonstation14.ms14.character.components.CanEscapeInventoryComponent;
import com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile;
import com.juicyslew.moonstation14.ms14.loose_objects.ModLooseObjectProfiles;
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
        // Resolve the actual complete catalog: mouse's parent chain and its composed defaults
        // must be exercised exactly as they are when the gameplay catalog is published.
        manager.reload(ModCharacters.CHARACTER_TYPE, discoverCharacterDefinitions());
        var chars = manager.snapshot(ModCharacters.CHARACTER_TYPE);
        assertEquals(LooseObjectProfile.MOUSE_TINY_ID,
                chars.get(id("mouse")).component(PortableBodyComponent.class).orElseThrow().profileId());
        assertEquals(5f, chars.get(id("mouse")).component(CanEscapeInventoryComponent.class).orElseThrow()
                .baseResistTimeSeconds(), "resolved mouse composition inherits the pinned five-second default");
        assertTrue(chars.get(id("human")).component(CanEscapeInventoryComponent.class).isEmpty());
        assertTrue(chars.get(id("human")).component(PortableBodyComponent.class).isEmpty());

        var raw = new LinkedHashMap<ResourceLocation, JsonObject>();
        Path dir = projectPath("src/main/resources/data/moonstation14/moonstation14/loose_object_profile");
        try (var files = Files.walk(dir)) {
            for (Path file : files.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                String relative = dir.relativize(file).toString().replace('\\', '/');
                raw.put(id(relative.substring(0, relative.length() - 5)), json);
            }
        }
        // This test is about tiny-profile inheritance/dimensions. Resolve the full raw
        // prototype set (including inherited fields), then decode only these profiles;
        // whole-family cross-references are admitted by the catalog tests.
        var resolved = PrototypeResolver.resolve(raw);
        LooseObjectProfile mouseProfile = LooseObjectProfile.CODEC.parse(JsonOps.INSTANCE,
                resolved.get(LooseObjectProfile.MOUSE_TINY_ID)).getOrThrow();
        assertEquals(.4f, mouseProfile.physicalWidth());
        assertEquals(.3f, mouseProfile.physicalHeight());
        for (String profile : java.util.List.of("bee_tiny", "butterfly_tiny", "cockroach_tiny", "hamster_tiny", "mothroach_normal")) {
            var id = id(profile);
            var parsed = LooseObjectProfile.CODEC.parse(JsonOps.INSTANCE, resolved.get(id)).getOrThrow();
            assertTrue(parsed.physicalWidth() > 0 && parsed.physicalHeight() > 0, profile);
            assertEquals("item", parsed.geometry(), profile);
            assertEquals("flat_sprite", parsed.renderKind(), profile);
        }
        assertEquals(.4f, LooseObjectProfile.CODEC.parse(JsonOps.INSTANCE, resolved.get(id("hamster_tiny"))).getOrThrow().physicalWidth());
        assertTrue(LooseObjectProfile.CODEC.parse(JsonOps.INSTANCE, resolved.get(id("mothroach_normal"))).getOrThrow().physicalWidth() > .4f);
        assertNotNull(LooseObjectProfile.CODEC.parse(JsonOps.INSTANCE,
                resolved.get(LooseObjectProfile.PORTABLE_BODY_KEY_ID)).getOrThrow(), "internal adapter has a supported resolved profile");
        assertEquals(5f, CanEscapeInventoryComponent.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"type\":\"CanEscapeInventory\"}")).getOrThrow().baseResistTimeSeconds());
        assertEquals(3f, CanEscapeInventoryComponent.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"type\":\"CanEscapeInventory\",\"baseResistTime\":3}")).getOrThrow().baseResistTimeSeconds());
    }

    private static ResourceLocation id(String path) { return ResourceLocation.parse("moonstation14:" + path); }
    private static java.util.Map<ResourceLocation, JsonObject> discoverCharacterDefinitions() throws Exception {
        Path dir = projectPath("src/main/resources/data/moonstation14/moonstation14/character");
        var definitions = new LinkedHashMap<ResourceLocation, JsonObject>();
        try (var files = Files.walk(dir)) {
            for (Path file : files.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                String relative = dir.relativize(file).toString().replace('\\', '/');
                definitions.put(id(relative.substring(0, relative.length() - 5)), json);
            }
        }
        return definitions;
    }
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
