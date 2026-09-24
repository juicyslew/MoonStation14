package com.juicyslew.moonstation14.ms14.status_effect;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.component.codec.json.StatusEffectBehavior;
import com.juicyslew.moonstation14.component.codec.json.StatusEffectData;
import com.juicyslew.moonstation14.component.codec.json.StatusEffectSchemaAudit;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusEffectPrototypeTest {
    private static final String ROOT = "data/moonstation14/moonstation14/status_effect/";
    private static final List<String> IDS = List.of(
            "jitter", "statuseffectseeingrainbow", "statuseffectdrowsiness", "statuseffectbark",
            "statuseffectscrambled", "statuseffectwoozy", "statuseffectdesoxystamina",
            "statuseffectpainnumbness", "statuseffectstunned", "statuseffectdrunk", "statuseffectowo",
            "statuseffecthemorrhage", "statuseffectanticoagulant", "statuseffectforcedsleeping",
            "statuseffectradiationprotection", "statuseffectstimulantsstamina", "reagentspeedstatuseffect",
            "vomiting_slowdown", "knockdown");

    @Test
    void productionCatalogHasExactlyTheMilestoneFourStatuses() throws IOException {
        Map<ResourceLocation, JsonObject> raw = loadResources();
        assertEquals(Set.copyOf(IDS), raw.keySet().stream().map(ResourceLocation::getPath).collect(
                java.util.stream.Collectors.toSet()), "on-disk status IDs must match the canonical set");
        assertEquals(19, raw.size());
        assertEquals(IDS.stream().map(StatusEffectPrototypeTest::id).toList(), raw.keySet().stream().toList());

        PrototypeManager manager = new PrototypeManager();
        manager.register(ModStatusEffects.STATUS_EFFECT_TYPE);
        manager.reload(ModStatusEffects.STATUS_EFFECT_TYPE, raw);

        Map<ResourceLocation, JsonObject> encoded = manager.encodePublishedCatalogs()
                .get(ModStatusEffects.STATUS_EFFECT_TYPE.typeId());
        assertEquals(raw.keySet(), encoded.keySet());
        for (ResourceLocation statusId : raw.keySet()) {
            StatusEffectData directlyDecoded = StatusEffectData.CODEC.parse(JsonOps.INSTANCE, raw.get(statusId))
                    .getOrThrow();
            JsonObject canonical = StatusEffectData.CODEC.encodeStart(JsonOps.INSTANCE, directlyDecoded)
                    .getOrThrow().getAsJsonObject();
            assertEquals(canonical, encoded.get(statusId), statusId + " canonical manager re-encode");
            assertEquals(directlyDecoded, StatusEffectData.CODEC.parse(JsonOps.INSTANCE, canonical).getOrThrow(),
                    statusId + " codec round-trip");
        }

        StatusEffectData jitter = manager.snapshot(ModStatusEffects.STATUS_EFFECT_TYPE).get(id("jitter"));
        assertEquals(List.of(StatusEffectBehavior.CLIENT_JITTER), jitter.behaviors());
        assertEquals(1, jitter.eligibility().size());
        assertFalse(raw.get(id("jitter")).has("id"));
        StatusEffectData movement = manager.snapshot(ModStatusEffects.STATUS_EFFECT_TYPE)
                .get(id("reagentspeedstatuseffect"));
        assertEquals(0.5f, movement.movementSpeedMultiplier().orElseThrow());
        StatusEffectData knockdown = manager.snapshot(ModStatusEffects.STATUS_EFFECT_TYPE)
                .get(id("knockdown"));
        assertEquals(List.of(StatusEffectBehavior.MARKER), knockdown.behaviors());
        assertEquals(List.of(com.juicyslew.moonstation14.component.codec.json.StatusEffectEligibility.LIVING_ENTITY),
                knockdown.eligibility());
        assertTrue(!knockdown.isBeneficial());
    }

    @Test
    void codecAndSchemaRejectLegacyUnknownAndClosedValues() {
        JsonObject valid = JsonParser.parseString("""
                {"translation_key":"status.test","beneficial":false,"color":"0x123456",
                 "behaviors":[],"eligibility":["living_entity"]}
                """).getAsJsonObject();
        StatusEffectData decoded = StatusEffectData.CODEC.parse(JsonOps.INSTANCE, valid).getOrThrow();
        assertEquals(decoded, StatusEffectData.CODEC.parse(JsonOps.INSTANCE,
                StatusEffectData.CODEC.encodeStart(JsonOps.INSTANCE, decoded).getOrThrow()).getOrThrow());

        for (String field : List.of("id", "unknown")) {
            JsonObject invalid = valid.deepCopy();
            invalid.addProperty(field, "bad");
            assertTrue(StatusEffectData.CODEC.parse(JsonOps.INSTANCE, invalid).error().isPresent());
        }

        JsonObject duplicateBehavior = valid.deepCopy();
        duplicateBehavior.add("behaviors", JsonParser.parseString("[\"marker\",\"marker\"]"));
        assertThrows(IllegalArgumentException.class, () -> StatusEffectSchemaAudit.audit(duplicateBehavior));

        JsonObject uppercase = valid.deepCopy();
        uppercase.add("behaviors", JsonParser.parseString("[\"MARKER\"]"));
        assertTrue(StatusEffectData.CODEC.parse(JsonOps.INSTANCE, uppercase).error().isPresent());
    }

    @Test
    void movementDefaultIsRequiredOnlyForMovementBehavior() {
        JsonObject valid = JsonParser.parseString("""
                {"translation_key":"status.speed","color":"0x123456",
                 "behaviors":["movement_speed"],"eligibility":["living_entity"],
                 "movement_speed_multiplier":0.5}
                """).getAsJsonObject();
        assertEquals(0.5f, StatusEffectData.CODEC.parse(JsonOps.INSTANCE, valid).getOrThrow()
                .movementSpeedMultiplier().orElseThrow());

        JsonObject missing = valid.deepCopy();
        missing.remove("movement_speed_multiplier");
        assertTrue(StatusEffectData.CODEC.parse(JsonOps.INSTANCE, missing).error().isPresent());

        JsonObject forbidden = JsonParser.parseString("""
                {"translation_key":"status.marker","color":"0x123456",
                 "behaviors":["marker"],"eligibility":["living_entity"],
                 "movement_speed_multiplier":0.5}
                """).getAsJsonObject();
        assertTrue(StatusEffectData.CODEC.parse(JsonOps.INSTANCE, forbidden).error().isPresent());

        JsonObject negative = valid.deepCopy();
        negative.addProperty("movement_speed_multiplier", -0.1);
        assertTrue(StatusEffectData.CODEC.parse(JsonOps.INSTANCE, negative).error().isPresent());
    }

    private static Map<ResourceLocation, JsonObject> loadResources() throws IOException {
        Map<ResourceLocation, JsonObject> resources = new LinkedHashMap<>();
        Path directory = projectPath("src/main/resources/" + ROOT);
        List<String> files;
        try (Stream<Path> stream = Files.list(directory)) {
            files = stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(path -> path.getFileName().toString().replaceFirst("\\.json$", ""))
                    .sorted(java.util.Comparator.comparing(IDS::indexOf))
                    .toList();
        }
        assertEquals(IDS, files, "status resources on disk must match canonical IDs and order");
        for (String path : files) {
            String resource = ROOT + path + ".json";
            try (var stream = StatusEffectPrototypeTest.class.getClassLoader().getResourceAsStream(resource)) {
                if (stream == null) throw new IOException("Missing resource " + resource);
                JsonObject json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
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
