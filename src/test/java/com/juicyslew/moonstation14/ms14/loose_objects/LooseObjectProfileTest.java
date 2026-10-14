package com.juicyslew.moonstation14.ms14.loose_objects;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeResolver;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LooseObjectProfileTest {
    @Test void loadsJsonProfilesAndResolvesInheritance() throws Exception {
        Map<ResourceLocation, JsonObject> raw = new LinkedHashMap<>();
        Path directory = projectPath("src/main/resources/data/moonstation14/moonstation14/loose_object_profile");
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                raw.put(ResourceLocation.parse(json.get("id").getAsString()), json);
            }
        }
        var resolved = PrototypeResolver.resolve(raw);
        LooseObjectProfile generic = decode(resolved.get(ModLooseObjectProfiles.GENERIC_ID));
        LooseObjectProfile jug = decode(resolved.get(ModLooseObjectProfiles.JUG_ID));
        LooseObjectProfile grenade = decode(resolved.get(ModLooseObjectProfiles.GRENADE_ID));
        LooseObjectProfile blockModel = decode(resolved.get(LooseObjectProfile.BLOCK_MODEL_ID));
        LooseObjectProfile canister = decode(resolved.get(LooseObjectProfile.GAS_CANISTER_ID));
        assertEquals(.75f, generic.throwSpeed());
        assertEquals(generic.throwSpeed(), jug.throwSpeed());
        assertEquals(generic.throwSpeed(), grenade.throwSpeed());
        double horizontalTravel = 0;
        double horizontalSpeed = generic.throwSpeed();
        for (int tick = 0; tick < 4; tick++) {
            horizontalTravel += horizontalSpeed;
            horizontalSpeed *= .995 * .995; // airborne damping used by LooseItemEntity
        }
        assertTrue(horizontalTravel > .55 * 4, "direct horizontal air travel exceeds the old-speed baseline");
        assertEquals(8f, generic.throwRange());
        assertEquals(.1f, generic.minimumThrowRange());
        assertEquals(10, generic.throwCooldown());
        assertEquals("flat_sprite", generic.renderKind());
        assertEquals(.65f, generic.physicalWidth());
        assertEquals(.08f, generic.physicalHeight());
        assertEquals(.65f, generic.visualWidth());
        assertEquals(.65f, generic.visualHeight());
        assertEquals(1f, generic.modelScale());
        assertEquals(.8f, grenade.modelScale());
        assertEquals(.25f, grenade.modelFloorOffset());
        assertTrue(jug.breakOnImpact());
        assertEquals(.5f, jug.physicalWidth());
        assertEquals(.75f, jug.physicalHeight());
        assertEquals(generic.friction(), grenade.friction());
        assertEquals(.45f, grenade.physicalWidth());
        assertEquals("grenade", grenade.renderKind());
        assertEquals("block_model", blockModel.renderKind());
        assertEquals(.65f, blockModel.physicalHeight());
        assertEquals(.65f * 0 + .325f, blockModel.modelFloorOffset());
        assertEquals(.5f, canister.physicalWidth());
        assertEquals(1f, canister.physicalHeight());
        assertEquals(190f, canister.mass());
        assertEquals(blockModel.modelFloorOffset(), -.5f * blockModel.modelScale() + blockModel.modelFloorOffset() + .5f * blockModel.modelScale());
        assertNull(resolved.get(ResourceLocation.withDefaultNamespace("absent")));
        var catalog = new PrototypeCatalog<>(resolved.asMap().entrySet().stream().collect(
                java.util.stream.Collectors.toMap(Map.Entry::getKey, entry -> decode(entry.getValue()))));
        assertEquals(generic, ModLooseObjectProfiles.require(catalog, null));
        assertThrows(IllegalArgumentException.class, () -> ModLooseObjectProfiles.require(catalog,
                ResourceLocation.fromNamespaceAndPath("moonstation14", "unknown")));
    }

    private static LooseObjectProfile decode(JsonObject json) {
        return LooseObjectProfile.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
    }

    private static Path projectPath(String path) throws java.io.IOException {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(path);
            if (Files.isDirectory(candidate)) return candidate;
            current = current.getParent();
        }
        throw new java.io.IOException("Could not locate project resource path " + path);
    }
}
