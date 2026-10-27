package com.juicyslew.moonstation14.ms14.item_processing;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.prototype.BotanyCatalogCandidateTest;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExtractableProfileTest {
    private static ResourceLocation id(String value) { return ResourceLocation.fromNamespaceAndPath("moonstation14", value); }

    @Test void absentAndExplicitExtractionFieldsDecodeIndependentlyWithWholeUnitAmounts() {
        var empty = ExtractableProfile.CODEC.parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow();
        assertNull(empty.grindableSolutionName());
        assertNull(empty.juiceSolution());
        var explicit = JsonParser.parseString("{\"grindable_solution_name\":\"food\",\"juice_solution\":{\"moonstation14:juicecarrot\":10,\"moonstation14:oculine\":2}}");
        var parsed = ExtractableProfile.CODEC.parse(JsonOps.INSTANCE, explicit).getOrThrow();
        assertEquals("food", parsed.grindableSolutionName());
        assertEquals(Map.of(reagent("juicecarrot"), 1000L, reagent("oculine"), 200L), parsed.juiceSolution().centContents());
        assertEquals("shardglass", ExtractableProfile.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"grindable_solution_name\":\"shardglass\"}")).getOrThrow().grindableSolutionName());
        assertThrows(IllegalArgumentException.class, () -> new ExtractableProfile(" ", null));
        var emptyJuice = ExtractableProfile.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"juice_solution\":{}}" )).getOrThrow();
        assertNotNull(emptyJuice.juiceSolution());
        assertTrue(emptyJuice.juiceSolution().centContents().isEmpty());
    }

    @Test void catalogResolvesFoodGrindCapabilityFromInheritedDefaultsAndSynchronizes() {
        var manager = new PrototypeManager();
        manager.register(ModExtractableProfiles.TYPE);
        var root = new JsonObject(); root.addProperty("id", "moonstation14:food_base"); root.addProperty("abstract", true); root.addProperty("grindable_solution_name", "food");
        var child = new JsonObject(); child.addProperty("parent", "moonstation14:food_base");
        var juice = JsonParser.parseString("{\"parent\":\"moonstation14:food_base\",\"juice_solution\":{\"moonstation14:juicecarrot\":10}}").getAsJsonObject();
        manager.reload(ModExtractableProfiles.TYPE, Map.of(id("food_base"), root, id("plain_food"), child, id("carrot"), juice));
        var resolved = manager.snapshot(ModExtractableProfiles.TYPE);
        assertEquals("food", resolved.get(id("plain_food")).grindableSolutionName());
        assertEquals("food", resolved.get(id("carrot")).grindableSolutionName());
        assertEquals(Map.of(reagent("juicecarrot"), 1000L), resolved.get(id("carrot")).juiceSolution().centContents());
    }

    @Test void authoredProductCandidatesStageAsAnIndependentExtractionCatalog() {
        var manager = new PrototypeManager(); manager.register(ModExtractableProfiles.TYPE);
        var candidates = BotanyCatalogCandidateTest.resources("extractable_profile", "extractable_base", "berries", "cabbage", "carrot", "cotton_bol",
                "apple", "golden_apple", "lemon", "lemoon", "lime", "orange",
                "garlic", "grape", "log", "papercane", "pineapple", "pyrotton_bol", "steel_log");
        var resolved = new java.util.LinkedHashMap<ResourceLocation, ExtractableProfile>();
        var before = manager.snapshot();
        manager.stage(Map.of(ModExtractableProfiles.TYPE, candidates), catalogs ->
                catalogs.get(ModExtractableProfiles.TYPE.typeId()).forEach((key, raw) -> resolved.put(key,
                        ExtractableProfile.CODEC.parse(JsonOps.INSTANCE, raw).getOrThrow())));
        assertSame(before, manager.snapshot());
        assertEquals(17, resolved.size());
        for (String name : new String[]{"berries", "cabbage", "carrot", "apple", "golden_apple",
                "lemon", "lemoon", "lime", "orange", "garlic", "grape", "pineapple"})
            assertEquals("food", resolved.get(id(name)).grindableSolutionName(), name);
        for (String name : new String[]{"cotton_bol", "log", "papercane", "pyrotton_bol", "steel_log"})
            assertEquals("food", resolved.get(id(name)).grindableSolutionName(), name);
        assertEquals(Map.of(reagent("juicecarrot"), 1000L, reagent("oculine"), 200L), resolved.get(id("carrot")).juiceSolution().centContents());
        assertEquals(Map.of(reagent("juicepineapple"), 1000L), resolved.get(id("pineapple")).juiceSolution().centContents());
        assertEquals(Map.of(reagent("juiceberry"), 1000L), resolved.get(id("berries")).juiceSolution().centContents());
        assertNull(resolved.get(id("log")).juiceSolution());
    }

    private static net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> reagent(String path) {
        return net.minecraft.resources.ResourceKey.create(com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_REGISTRY_KEY, id(path));
    }

}
