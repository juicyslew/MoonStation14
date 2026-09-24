package com.juicyslew.moonstation14.ms14.alert;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.component.codec.json.AlertData;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertResourceCoverageTest {
    private static final String ALERT_DIRECTORY =
            "src/main/resources/data/moonstation14/moonstation14/alert";
    private static final Set<String> EXPECTED_IDS = Set.of("toxins", "thirsty", "parched", "peckish", "starving");

    @Test
    void everyProductionAlertReloadsAndTypedCodecRoundTrips() throws IOException {
        Map<ResourceLocation, JsonObject> raw = loadResources();
        assertEquals(EXPECTED_IDS, raw.keySet().stream().map(ResourceLocation::getPath).collect(
                java.util.stream.Collectors.toSet()), "on-disk alert IDs must match the expected set");

        PrototypeManager manager = new PrototypeManager();
        manager.register(ModAlerts.ALERT_TYPE);
        // Reload invokes ModAlerts' strict AlertSchemaAudit JSON catalog validator.
        manager.reload(ModAlerts.ALERT_TYPE, raw);

        Map<ResourceLocation, JsonObject> encoded = manager.encodePublishedCatalogs()
                .get(ModAlerts.ALERT_TYPE.typeId());
        assertEquals(raw.keySet(), encoded.keySet());
        for (ResourceLocation alertId : raw.keySet()) {
            AlertData directlyDecoded = AlertData.CODEC.parse(JsonOps.INSTANCE, raw.get(alertId)).getOrThrow();
            JsonObject canonical = AlertData.CODEC.encodeStart(JsonOps.INSTANCE, directlyDecoded)
                    .getOrThrow().getAsJsonObject();
            assertEquals(canonical, encoded.get(alertId), alertId + " canonical manager re-encode");
            assertEquals(directlyDecoded, AlertData.CODEC.parse(JsonOps.INSTANCE, canonical).getOrThrow(),
                    alertId + " codec round-trip");
        }

        JsonObject english = JsonParser.parseString(Files.readString(
                projectPath("src/main/resources/assets/moonstation14/lang/en_us.json"), StandardCharsets.UTF_8))
                .getAsJsonObject();
        for (String alert : Set.of("thirsty", "parched", "peckish", "starving")) {
            JsonObject definition = raw.get(id(alert));
            String nameKey = definition.get("name_translation_key").getAsString();
            String descriptionKey = definition.get("description_translation_key").getAsString();
            assertTrue(english.has(nameKey), "missing English alert name: " + nameKey);
            assertTrue(english.has(descriptionKey), "missing English alert description: " + descriptionKey);
        }
    }

    private static Map<ResourceLocation, JsonObject> loadResources() throws IOException {
        Map<ResourceLocation, JsonObject> resources = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(projectPath(ALERT_DIRECTORY))) {
            for (Path file : files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(java.util.Comparator.comparing(path -> path.getFileName().toString()))
                    .toList()) {
                String filename = file.getFileName().toString();
                String path = filename.substring(0, filename.length() - ".json".length());
                JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                resources.put(id(path), json);
            }
        }
        return resources;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("moonstation14", path);
    }

    private static Path projectPath(String path) throws IOException {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(path);
            if (Files.exists(candidate)) return candidate;
            current = current.getParent();
        }
        throw new IOException("Could not locate project resource path " + path);
    }
}
