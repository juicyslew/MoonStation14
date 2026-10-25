package com.juicyslew.moonstation14.ms14.animals;

import com.juicyslew.moonstation14.ms14.mobs.client.AnimalModelAdapters;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import com.google.gson.JsonParser;
import java.util.HashSet;
import java.util.Set;
import java.nio.file.Files;
import java.nio.file.Path;

class AnimalModelAdapterCoverageTest {
    private static Path projectRoot() { return Path.of(System.getProperty("moonstation14.projectDir")); }
    @Test
    void everyResolvedCatalogAppearanceHasAnExplicitNativeAdapter() {
        for (AnimalCatalog.Entry entry : AnimalCatalog.entries()) {
            var adapter = AnimalModelAdapters.bindings().get(entry.modelId());
            assertNotNull(adapter, "missing native model adapter for " + entry.id() + " -> " + entry.modelId());
            assertNotNull(adapter.layer(), "missing model layer for " + entry.id());
            assertNotNull(adapter.definition(), "missing layer definition for " + entry.id());
            String texture = "assets/" + adapter.baseTexture().getNamespace() + "/" + adapter.baseTexture().getPath();
            assertTrue(Files.isRegularFile(projectRoot().resolve("src/main/resources").resolve(texture)),
                    "missing native texture dependency for " + entry.id() + ": " + texture);
        }
    }

    @Test
    void adaptersExposeRequestedPresentationScales() {
        Set<String> compactModels = Set.of("butterfly", "cockroach", "crab", "nymph", "lizard",
                "mothroach", "reindeer_buck", "reindeer_doe");
        for (var entry : AnimalModelAdapters.bindings().entrySet()) {
            float expected = compactModels.contains(entry.getKey()) ? .82F
                    : entry.getKey().equals("spider_tarantula") ? .9F : 1F;
            assertEquals(expected, entry.getValue().presentationScale(), 0F,
                    "presentation scale for " + entry.getKey());
        }
        assertEquals(1F, AnimalModelAdapters.resolve("moproach").presentationScale(), 0F);
    }

    @Test void authoredNativeHandAnchorsAreLimitedToTheThreeResolvedHandedFamilies() {
        assertEquals(Set.of("kobold", "monkey", "scurret"), AnimalModelAdapters.handAnchorModels());
        assertEquals(java.util.List.of("mouse/body/arm_left/forearm_left", "mouse/body/arm_right/forearm_right"),
                AnimalModelAdapters.handAnchorPaths().get("kobold"));
        assertEquals(java.util.List.of("mouse/body/arm_left/forearm_left", "mouse/body/arm_right/forearm_right"),
                AnimalModelAdapters.handAnchorPaths().get("monkey"));
        assertEquals(java.util.List.of("mouse/body/arm_left", "mouse/body/arm_right"),
                AnimalModelAdapters.handAnchorPaths().get("scurret"));
        for (String id : AnimalModelAdapters.handAnchorModels()) {
            assertTrue(AnimalModelAdapters.resolve(id).hasHandAnchors(), id);
        }
    }

    @Test void everyPrototypeAppearanceVariantHasItsOwnAdapterAndTexture() throws Exception {
        Path root = projectRoot().resolve(
                "src/main/resources/data/moonstation14/moonstation14/character");
        Set<String> modelIds = new HashSet<>();
        try (var paths = Files.walk(root)) {
            for (Path file : paths.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".json")).toList()) {
                var prototype = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                if (!prototype.has("components")) continue;
                for (var component : prototype.getAsJsonArray("components")) {
                    var value = component.getAsJsonObject();
                    if (!value.has("type") || !value.get("type").getAsString().equals("AnimalAppearance")) continue;
                    for (var id : value.getAsJsonArray("model_ids")) modelIds.add(id.getAsString());
                }
            }
        }
        assertEquals(40, modelIds.size());
        for (String modelId : modelIds) {
            var adapter = AnimalModelAdapters.bindings().get(modelId);
            assertNotNull(adapter, "missing adapter for prototype model ID " + modelId);
            if (Set.of("hamster", "bee", "butterfly", "cockroach", "mothroach", "moproach").contains(modelId))
                assertTrue(adapter.supportsPortablePresentation(), "portable model lacks native held presentation " + modelId);
            String texture = "assets/" + adapter.baseTexture().getNamespace() + "/" + adapter.baseTexture().getPath();
            assertTrue(Files.isRegularFile(projectRoot().resolve("src/main/resources").resolve(texture)),
                    "missing texture for prototype model ID " + modelId + ": " + texture);
        }
    }
}
