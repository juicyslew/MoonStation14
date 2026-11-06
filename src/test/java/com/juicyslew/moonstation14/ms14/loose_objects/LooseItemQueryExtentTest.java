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

/** Exercises the extent against the live resolved profile snapshots, not source JSON guesses. */
class LooseItemQueryExtentTest {
    @Test void productionSnapshotCalculationUsesResolvedInheritedProfilesAndEmptySnapshotFailsClosed() throws Exception {
        Map<ResourceLocation, JsonObject> raw = new LinkedHashMap<>();
        Path directory = projectPath("src/main/resources/data/moonstation14/moonstation14/loose_object_profile");
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                raw.put(ResourceLocation.parse(json.get("id").getAsString()), json);
            }
        }
        var resolved = PrototypeResolver.resolve(raw);
        var profiles = new LinkedHashMap<ResourceLocation, LooseObjectProfile>();
        resolved.asMap().forEach((id, json) -> profiles.put(id,
                LooseObjectProfile.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow()));
        var catalog = new PrototypeCatalog<>(profiles);
        double expected = max(catalog);
        assertTrue(expected > 0);
        assertEquals(expected, LooseItemQueryGeometry.maximumProfileRadius(catalog), 1e-6);
        var jug = catalog.get(LooseObjectProfile.JUG_ID);
        assertNotNull(jug);
        assertTrue(expected >= radius(jug), "inherited jug dimensions participate in extent");
        assertTrue(catalog.values().stream().anyMatch(p -> !p.equals(catalog.get(LooseObjectProfile.GENERIC_ID))
                && radius(p) > radius(catalog.get(LooseObjectProfile.GENERIC_ID))), "nongeneric profile determines max");
        assertEquals(0, LooseItemQueryGeometry.maximumProfileRadius(new PrototypeCatalog<>(new LinkedHashMap<>())));
        assertEquals(0, LooseItemQueryGeometry.maximumProfileRadius((PrototypeCatalog<LooseObjectProfile>) null));
    }

    private static double max(PrototypeCatalog<LooseObjectProfile> catalog) {
        return catalog.values().stream().mapToDouble(LooseItemQueryExtentTest::radius).max().orElse(0);
    }
    private static double radius(LooseObjectProfile profile) {
        double half = profile.physicalWidth() / 2.0;
        return Math.sqrt(2 * half * half + (double) profile.physicalHeight() * profile.physicalHeight());
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
