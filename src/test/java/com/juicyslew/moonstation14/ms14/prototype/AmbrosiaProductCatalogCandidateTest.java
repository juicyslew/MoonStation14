package com.juicyslew.moonstation14.ms14.prototype;

import com.juicyslew.moonstation14.ms14.botany.ModProduceProfiles;
import com.juicyslew.moonstation14.ms14.botany.ModSeedProfiles;
import com.juicyslew.moonstation14.ms14.botany.ProduceProfile;
import com.juicyslew.moonstation14.ms14.botany.SeedProfile;
import com.juicyslew.moonstation14.ms14.ingestion.IngestionProfile;
import com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles;
import com.juicyslew.moonstation14.ms14.item_processing.ExtractableProfile;
import com.juicyslew.moonstation14.ms14.item_processing.ModExtractableProfiles;
import com.juicyslew.moonstation14.ms14.equipment.EquipmentItemData;
import com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems;
import com.juicyslew.moonstation14.ms14.equipment.InventoryTemplateData;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AmbrosiaProductCatalogCandidateTest {
    private static final String[] NAMES = {"ambrosia_vulgaris", "ambrosia_deus"};
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }

    @Test void ambrosiaProfilesResolveSourceFoodAndIdentityCompositionWithoutPublishing() {
        var manager = new PrototypeManager();
        manager.register(ModProduceProfiles.TYPE); manager.register(ModIngestionProfiles.TYPE);
        manager.register(ModExtractableProfiles.TYPE); manager.register(ModSeedProfiles.TYPE);
        manager.register(ModEquipmentItems.TYPE);
        var before = manager.snapshot();
        var produce = new LinkedHashMap<ResourceLocation, ProduceProfile>();
        var food = new LinkedHashMap<ResourceLocation, IngestionProfile>();
        var extracts = new LinkedHashMap<ResourceLocation, ExtractableProfile>();
        var seeds = new LinkedHashMap<ResourceLocation, SeedProfile>();
        var equipment = new LinkedHashMap<ResourceLocation, EquipmentItemData>();
        var sources = BotanyCatalogCandidateTest.stageFoodSolutions(BotanyCatalogCandidateTest.resources("item_food_solution_profile", NAMES));
        manager.stage(Map.of(
                ModProduceProfiles.TYPE, resources("produce_profile", "produce_base"),
                ModIngestionProfiles.TYPE, resources("ingestion_profile", "food_base"),
                ModExtractableProfiles.TYPE, resources("extractable_profile", "extractable_base"),
                ModEquipmentItems.TYPE, equipmentResources(),
                ModSeedProfiles.TYPE, BotanyCatalogCandidateTest.resources()), catalogs -> {
            catalogs.get(ModProduceProfiles.TYPE.typeId()).forEach((key, raw) -> produce.put(key, ProduceProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
            catalogs.get(ModIngestionProfiles.TYPE.typeId()).forEach((key, raw) -> food.put(key, IngestionProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
            catalogs.get(ModExtractableProfiles.TYPE.typeId()).forEach((key, raw) -> extracts.put(key, ExtractableProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
            catalogs.get(ModSeedProfiles.TYPE.typeId()).forEach((key, raw) -> seeds.put(key, SeedProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
            catalogs.get(ModEquipmentItems.TYPE.typeId()).forEach((key, raw) -> equipment.put(key, EquipmentItemData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
        });
        assertSame(before, manager.snapshot());
        assertEquals(2, produce.size()); assertEquals(2, food.size()); assertEquals(2, extracts.size());
        assertEquals(2, equipment.size());
        assertEquals(Map.of(id("bicaridine"), 500L, id("kelotane"), 500L, id("nutriment"), 200L, id("vitamin"), 200L), sources.get(id(NAMES[0])).contentsCents());
        assertEquals(1400, sources.get(id(NAMES[0])).capacityCents());
        assertEquals(Map.of(id("omnizine"), 300L, id("spacedrugs"), 500L, id("nutriment"), 200L), sources.get(id(NAMES[1])).contentsCents());
        assertEquals(1000, sources.get(id(NAMES[1])).capacityCents());
        for (String name : NAMES) {
            var edible = food.get(id(name)).edible();
            assertEquals(IngestionProfile.Kind.FOOD, edible.kind()); assertEquals(500, edible.doseCents());
            assertEquals(20, edible.delayTicks()); assertEquals(60, edible.forceFeedDelayTicks());
            assertFalse(edible.requiresSpecialDigestion()); assertTrue(edible.destroyOnEmpty()); assertTrue(edible.trash().isEmpty());
            assertEquals(id(name), produce.get(id(name)).seed()); assertTrue(sources.containsKey(id(name)));
            assertEquals(1f, produce.get(id(name)).potencyVisuals().minimumScale()); assertEquals(2f, produce.get(id(name)).potencyVisuals().maximumScale());
            assertEquals("food", extracts.get(id(name)).grindableSolutionName()); assertNull(extracts.get(id(name)).juiceSolution());
            assertEquals(id(name), seeds.get(id(name)).product());
        }
        assertEquals(java.util.List.of(id("ambrosia_deus")), seeds.get(id("ambrosia_vulgaris")).mutationTargets());
        for (String name : NAMES) {
            var clothing = equipment.get(id(name)).clothing().orElseThrow();
            assertEquals(java.util.List.of(InventoryTemplateData.SlotFlag.HEAD), clothing.slotFlags());
            assertEquals(EquipmentItemData.AppearanceRegion.HEAD, clothing.region());
            assertTrue(clothing.quickEquip(), "upstream ClothingComponent quickEquip default is true");
            assertTrue(clothing.wornModel().isEmpty(), "no art or worn rendering asserted");
            assertNull(getClass().getClassLoader().getResource("data/moonstation14/moonstation14/equipment_item/" + name + ".json"));
        }
        var loader = getClass().getClassLoader();
        for (String family : new String[]{"produce_profile", "ingestion_profile", "extractable_profile", "item_food_solution_profile", "equipment_item"})
            for (String name : NAMES) assertNull(loader.getResource("data/moonstation14/moonstation14/" + family + "/" + name + ".json"));
    }

    private static Map<ResourceLocation, com.google.gson.JsonObject> resources(String family, String base) {
        var names = new String[NAMES.length + 1]; names[0] = base; System.arraycopy(NAMES, 0, names, 1, NAMES.length);
        return BotanyCatalogCandidateTest.resources(family, names);
    }

    private static Map<ResourceLocation, com.google.gson.JsonObject> equipmentResources() {
        var out = new LinkedHashMap<ResourceLocation, com.google.gson.JsonObject>();
        out.put(id("base_item"), read("data/moonstation14/moonstation14/equipment_item/base_item.json"));
        out.put(id("base_clothing"), read("data/moonstation14/moonstation14/equipment_item/base_clothing.json"));
        out.putAll(BotanyCatalogCandidateTest.resources("equipment_item", "clothing_head_base"));
        out.putAll(BotanyCatalogCandidateTest.resources("equipment_item", NAMES));
        return out;
    }

    private static com.google.gson.JsonObject read(String path) {
        try (var stream = AmbrosiaProductCatalogCandidateTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException exception) { throw new AssertionError(exception); }
    }
}
