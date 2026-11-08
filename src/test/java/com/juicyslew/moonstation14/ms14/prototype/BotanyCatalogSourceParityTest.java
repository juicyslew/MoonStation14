package com.juicyslew.moonstation14.ms14.prototype;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.botany.ModSeedProfiles;
import com.juicyslew.moonstation14.ms14.botany.SeedProfile;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.*;

class BotanyCatalogSourceParityTest {
    private static final float DELTA = 1e-6f;
    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("moonstation14", path);
    }

    @Test void stagedCandidateMatchesCapturedUpstreamSeedFields() throws Exception {
        var metadata = read("source-metadata.json").getAsJsonObject();
        assertEquals("c9df5ef5d675b0d1d226828bddf6b78c28502d91", metadata.get("sourceRevision").getAsString());
        assertEquals("Resources/Prototypes/Hydroponics/seeds.yml", metadata.get("sourceYaml").getAsString());
        assertEquals("d5a2d9997c6cd735960331b60ec239c61dd3a8f0b97eb0737657645ffc7e3da7", metadata.get("sourceYamlSha256").getAsString());
        assertEquals("cf68bd81ab8b65c7819bd6cd2b2af416244cd894436ad5a910474dd101efb98d", metadata.get("seedPrototypeCsSha256").getAsString());
        assertTrue(metadata.get("capturedBytesVerifiedAgainstPinnedCheckout").getAsBoolean());
        var source = read("source-seed-rows.json").getAsJsonArray();
        assertEquals(71, source.size());
        assertEquals(71, metadata.get("rows").getAsInt());

        var manager = new PrototypeManager();
        manager.register(ModSeedProfiles.TYPE);
        var before = manager.snapshot();
        var decoded = new LinkedHashMap<ResourceLocation, SeedProfile>();
        manager.stage(Map.of(ModSeedProfiles.TYPE, BotanyCatalogCandidateTest.resources()), catalogs ->
                catalogs.get(ModSeedProfiles.TYPE.typeId()).forEach((key, json) -> decoded.put(key,
                        SeedProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow())));
        assertSame(before, manager.snapshot(), "staging source parity must not publish candidate");
        assertEquals(72, decoded.size());

        var defaults = metadata.getAsJsonObject("defaults");
        var ids = new java.util.HashSet<String>();
        var rowChecks = new ArrayList<Executable>();
        for (var element : source) {
            var row = element.getAsJsonObject();
            var sourceId = row.get("id").getAsString();
            var local = snake(sourceId);
            ids.add(local);
            var seed = decoded.get(id(local));
            rowChecks.add(() -> assertAll(sourceId + " -> " + local, () -> {
                assertNotNull(seed, "candidate missing");
                if (seed == null) return;
                var life = seed.lifecycle(); var bio = seed.biology();
                assertAll(
                        () -> assertEquals(row.get("name").getAsString(), seed.sourceName()),
                        () -> assertEquals(row.get("plantRsi").getAsString().substring(row.get("plantRsi").getAsString().lastIndexOf('/') + 1).replace(".rsi", ""), seed.visualKey()),
                        () -> number(row, "endurance", defaults.get("biology").getAsJsonObject().get("endurance"), bio.endurance()),
                        () -> number(row, "growthStages", defaults.get("lifecycle").getAsJsonObject().get("growthStages"), life.growthStages()),
                        () -> number(row, "lifespan", defaults.get("lifecycle").getAsJsonObject().get("lifespan"), life.lifespan()),
                        () -> number(row, "maturation", defaults.get("lifecycle").getAsJsonObject().get("maturation"), life.maturation()),
                        () -> number(row, "production", defaults.get("lifecycle").getAsJsonObject().get("production"), life.production()),
                        () -> number(row, "yield", defaults.get("lifecycle").getAsJsonObject().get("yield"), life.yield()),
                        () -> number(row, "potency", defaults.get("lifecycle").getAsJsonObject().get("potency"), life.potency()),
                        () -> assertEquals(harvest(row, defaults), life.harvest()),
                        () -> assertEquals(bool(row, "viable", defaults.getAsJsonObject("lifecycle")), life.viable()),
                        () -> assertEquals(bool(row, "seedless", defaults.getAsJsonObject("lifecycle")), life.seedless()),
                        () -> assertEquals(bool(row, "ligneous", defaults.getAsJsonObject("lifecycle")), life.ligneous()),
                        () -> number(row, "nutrientConsumption", defaults.getAsJsonObject("biology").get("nutrientConsumption"), bio.nutrientConsumption()),
                        () -> number(row, "waterConsumption", defaults.getAsJsonObject("biology").get("waterConsumption"), bio.waterConsumption()),
                        () -> number(row, "idealHeat", defaults.getAsJsonObject("biology").get("idealHeat"), bio.idealHeat()),
                        () -> number(row, "heatTolerance", defaults.getAsJsonObject("biology").get("heatTolerance"), bio.heatTolerance()),
                        () -> number(row, "lowPressureTolerance", defaults.getAsJsonObject("biology").get("lowPressureTolerance"), bio.lowPressure()),
                        () -> number(row, "highPressureTolerance", defaults.getAsJsonObject("biology").get("highPressureTolerance"), bio.highPressure()),
                        () -> number(row, "weedTolerance", defaults.getAsJsonObject("biology").get("weedTolerance"), bio.weedTolerance()),
                        () -> number(row, "weedHighLevelThreshold", defaults.getAsJsonObject("biology").get("weedHighLevelThreshold"), bio.weedHighLevel()),
                        () -> number(row, "toxinsTolerance", defaults.getAsJsonObject("biology").get("toxinsTolerance"), bio.toxinTolerance()),
                        () -> number(row, "pestTolerance", defaults.getAsJsonObject("biology").get("pestTolerance"), bio.pestTolerance()),
                        () -> assertEquals(mutations(row), seed.mutationTargets()),
                        () -> assertTrue(seed.consumeGasses().isEmpty()),
                        () -> assertTrue(seed.exudeGasses().isEmpty()),
                        () -> assertEquals(chemicals(row, metadata), seed.chemicals()),
                        () -> assertFalse(row.has("canScream"), "source fixture has no supported scream flag"),
                        () -> assertFalse(seed.scream().enabled())
                );
            }));
        }
        assertEquals(71, ids.size(), "captured source IDs must be unique");
        assertEquals(ids, decoded.keySet().stream().filter(key -> !key.equals(id("defaults"))).map(ResourceLocation::getPath).collect(java.util.stream.Collectors.toSet()));
        assertAll("all captured source seed rows", rowChecks);
    }

    private static Map<ResourceLocation, SeedProfile.Chemical> chemicals(JsonObject row, JsonObject metadata) {
        var result = new LinkedHashMap<ResourceLocation, SeedProfile.Chemical>();
        var seedDefaults = metadata.getAsJsonObject("defaults").getAsJsonObject("SeedChemQuantity");
        if (row.has("chemicals")) row.getAsJsonObject("chemicals").entrySet().forEach(entry -> {
            var value = entry.getValue().getAsJsonObject();
            result.put(id(entry.getKey().toLowerCase(Locale.ROOT)), new SeedProfile.Chemical(
                    value.has("Min") ? value.get("Min").getAsFloat() : seedDefaults.get("Min").getAsFloat(),
                    value.has("Max") ? value.get("Max").getAsFloat() : seedDefaults.get("Max").getAsFloat(),
                    value.has("PotencyDivisor") ? value.get("PotencyDivisor").getAsFloat() : seedDefaults.get("PotencyDivisor").getAsFloat(),
                    value.has("Inherent") ? value.get("Inherent").getAsBoolean() : seedDefaults.get("Inherent").getAsBoolean()));
        });
        return result;
    }

    private static boolean bool(JsonObject row, String key, JsonObject defaults) {
        return row.has(key) ? row.get(key).getAsBoolean() : defaults.get(key).getAsBoolean();
    }
    private static SeedProfile.Harvest harvest(JsonObject row, JsonObject defaults) {
        var value = row.has("harvestRepeat") ? row.get("harvestRepeat").getAsString() : defaults.getAsJsonObject("lifecycle").get("harvestRepeat").getAsString();
        return switch (value) { case "Repeat" -> SeedProfile.Harvest.REPEAT; case "SelfHarvest" -> SeedProfile.Harvest.SELF_HARVEST; default -> SeedProfile.Harvest.NO_REPEAT; };
    }
    private static ArrayList<ResourceLocation> mutations(JsonObject row) {
        var result = new ArrayList<ResourceLocation>();
        if (row.has("mutationPrototypes")) row.getAsJsonArray("mutationPrototypes").forEach(target -> result.add(id(snake(target.getAsString()))));
        return result;
    }

    private static void number(JsonObject row, String field, com.google.gson.JsonElement fallback, float actual) {
        assertEquals(row.has(field) ? row.get(field).getAsFloat() : fallback.getAsFloat(), actual, DELTA, field);
    }
    static String snake(String value) { return value.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT); }
    static com.google.gson.JsonElement read(String file) throws Exception {
        var path = "botany-catalog-source-reference/" + file;
        try (var stream = BotanyCatalogSourceParityTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
    }
}
