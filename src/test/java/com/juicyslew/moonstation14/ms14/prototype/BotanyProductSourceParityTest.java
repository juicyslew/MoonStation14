package com.juicyslew.moonstation14.ms14.prototype;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.botany.ModProduceProfiles;
import com.juicyslew.moonstation14.ms14.botany.ProduceProfile;
import com.juicyslew.moonstation14.ms14.ingestion.IngestionProfile;
import com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles;
import com.juicyslew.moonstation14.ms14.item_processing.ExtractableProfile;
import com.juicyslew.moonstation14.ms14.item_processing.ModExtractableProfiles;
import com.juicyslew.moonstation14.ms14.item_processing.ModItemProcessingProfiles;
import com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile;
import com.juicyslew.moonstation14.ms14.loose_objects.ModLooseObjectProfiles;
import com.juicyslew.moonstation14.ms14.equipment.EquipmentItemData;
import com.juicyslew.moonstation14.ms14.equipment.InventoryTemplateData;
import com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems;
import com.juicyslew.moonstation14.ms14.storage.grid.ItemFootprintProfile;
import com.juicyslew.moonstation14.ms14.storage.grid.ModItemFootprints;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.combat.MeleeWeaponProfile;
import com.juicyslew.moonstation14.ms14.combat.ModMeleeWeaponProfiles;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Bounded source binding and verified component-default contracts for ordinary produce products. */
class BotanyProductSourceParityTest {
    private static final Map<String, String> PRODUCTS = Map.ofEntries(
            Map.entry("CottonBol", "cotton_bol"), Map.entry("PyrottonBol", "pyrotton_bol"),
            Map.entry("FoodLemon", "lemon"), Map.entry("FoodLemoon", "lemoon"),
            Map.entry("FoodLime", "lime"), Map.entry("FoodOrange", "orange"),
            Map.entry("FoodApple", "apple"), Map.entry("FoodGoldenApple", "golden_apple"),
            Map.entry("FoodCarrot", "carrot"), Map.entry("FoodCabbage", "cabbage"),
            Map.entry("FoodGrape", "grape"), Map.entry("FoodBerries", "berries"),
            Map.entry("FoodGarlic", "garlic"), Map.entry("FoodPineapple", "pineapple"),
            Map.entry("FoodLingzhi", "lingzhi"), Map.entry("FoodGalaxythistle", "galaxythistle"),
            Map.entry("FoodPineappleSlice", "pineapple_slice"), Map.entry("FoodWatermelonSlice", "watermelon_slice"),
            Map.entry("FoodOnion", "onion"), Map.entry("FoodOnionRed", "onion_red"),
            Map.entry("FoodOnionSlice", "onion_slice"), Map.entry("FoodOnionRedSlice", "onion_red_slice"),
            Map.entry("FoodMushroom", "mushroom"), Map.entry("FoodFlyAmanita", "fly_amanita"),
            Map.entry("FoodChiliPepper", "chili"), Map.entry("FoodChillyPepper", "chilly"),
            Map.entry("RiceBushel", "rice_bushel"), Map.entry("FoodSoybeans", "food_soybeans"),
            Map.entry("FoodKoibean", "food_koibean"), Map.entry("FoodPeaPod", "food_pea_pod"),
            Map.entry("FoodLaughinPeaPod", "food_laughin_pea_pod"), Map.entry("FoodWorldPeas", "food_world_peas"),
            Map.entry("OatBushel", "oat_bushel"), Map.entry("Sugarcane", "sugarcane"),
            Map.entry("LeavesTea", "leaves_tea"), Map.entry("LeavesTobacco", "leaves_tobacco"),
            Map.entry("LeavesCannabis", "leaves_cannabis"), Map.entry("LeavesCannabisRainbow", "leaves_cannabis_rainbow"),
            Map.entry("MeatwheatBushel", "meatwheat_bushel"), Map.entry("FoodLily", "lily"),
            Map.entry("FoodCocoaPod", "cocoa_pod"), Map.entry("FoodCocoaBeans", "food_cocoa_beans"),
            Map.entry("FoodMeatWheat", "food_meat_wheat"), Map.entry("FoodEggplant", "eggplant"),
            Map.entry("FoodEgg", "egg"), Map.entry("Eggshells", "eggshells"),
            Map.entry("FoodAmbrosiaVulgaris", "ambrosia_vulgaris"), Map.entry("FoodAmbrosiaDeus", "ambrosia_deus"),
            Map.entry("FoodCorn", "corn"), Map.entry("FoodCornTrash", "food_corn_trash"),
            Map.entry("FoodBungo", "bungo"), Map.entry("FoodBungoPit", "food_bungo_pit"),
            Map.entry("FoodCherry", "cherry"), Map.entry("TrashCherryPit", "trash_cherry_pit"),
            Map.entry("FoodBluePumpkin", "blue_pumpkin"), Map.entry("FoodExtradimensionalOrange", "extradimensional_orange"),
             Map.entry("FoodWatermelon", "watermelon"), Map.entry("FoodHolymelon", "holymelon"),
            Map.entry("FoodHolymelonSlice", "holymelon_slice"), Map.entry("FoodPumpkin", "pumpkin"),
             Map.entry("FoodBanana", "banana"), Map.entry("FoodMimana", "mimana"), Map.entry("FoodBloonion", "bloonion"),
             Map.entry("TrashBananaPeel", "trash_banana_peel"), Map.entry("TrashMimanaPeel", "trash_mimana_peel"),
               Map.entry("FoodGlasstle", "glasstle"), Map.entry("ShardGlass", "shard_glass"),
             Map.entry("Nettle", "nettle"), Map.entry("DeathNettle", "death_nettle"));
    private static final java.util.Set<String> PRODUCE_ABSENCES = java.util.Set.of(
            "FoodPineappleSlice", "FoodWatermelonSlice", "FoodOnionSlice", "FoodOnionRedSlice", "FoodMeatWheat",
            "FoodCornTrash", "FoodBungoPit", "TrashCherryPit", "FoodHolymelonSlice", "TrashBananaPeel", "TrashMimanaPeel", "FoodEgg", "Eggshells", "ShardGlass");
    // Bounded food-product slice/child and non-food captures are intentionally outside this inventory.
    // Eggplant is not present in the raw captures; the matching candidate count is coincidental.
    private static final java.util.Set<String> NULL_SEED_PRODUCE = java.util.Set.of("FoodCocoaBeans");

    @Test void capturedProductRowsAndVerifiedDefaultsAreBoundedAndComplete() throws Exception {
        var rows = capturedRows();
        var metadata = BotanyCatalogSourceParityTest.read("products/metadata-defaults.json").getAsJsonObject();
        var additions = BotanyCatalogSourceParityTest.read("products/additions-metadata.json").getAsJsonObject();
        var conversion = BotanyCatalogSourceParityTest.read("products/conversion-metadata.json").getAsJsonObject();
        var fruitPit = BotanyCatalogSourceParityTest.read("products/fruit-pit-metadata.json").getAsJsonObject();
        var bluePumpkin = BotanyCatalogSourceParityTest.read("products/blue-pumpkin-metadata.json").getAsJsonObject();
        var bananaOrange = BotanyCatalogSourceParityTest.read("products/banana-orange-metadata.json").getAsJsonObject();
        var melonPumpkin = BotanyCatalogSourceParityTest.read("products/melon-pumpkin-metadata.json").getAsJsonObject();
        var egg = BotanyCatalogSourceParityTest.read("products/egg-metadata.json").getAsJsonObject();
        var bloonion = BotanyCatalogSourceParityTest.read("products/bloonion-metadata.json").getAsJsonObject();
        var nettle = BotanyCatalogSourceParityTest.read("products/nettle-metadata.json").getAsJsonObject();
        assertEquals(24, BotanyCatalogSourceParityTest.read("products/entity-rows.json").getAsJsonArray().size());
        assertEquals(25, BotanyCatalogSourceParityTest.read("products/additions-entity-rows.json").getAsJsonArray().size());
        assertEquals(24, metadata.get("capturedRawRecordCount").getAsInt());
        assertEquals("c9df5ef5d675b0d1d226828bddf6b78c28502d91", metadata.get("sourceRevision").getAsString());
        assertEquals("c9df5ef5d675b0d1d226828bddf6b78c28502d91", additions.get("sourceRevision").getAsString());
        assertEquals(18, metadata.get("capturedConcreteProductCount").getAsInt());
        assertEquals(22, additions.get("capturedConcreteProductCount").getAsInt());
        assertEquals(25, additions.get("capturedRawRecordCount").getAsInt());
        assertEquals(3, additions.get("capturedAdditionalAncestorCount").getAsInt());
        var medicinal = BotanyCatalogSourceParityTest.read("products/ordinary-medicinal-metadata.json").getAsJsonObject();
        assertEquals(3, BotanyCatalogSourceParityTest.read("products/ordinary-medicinal-entity-rows.json").getAsJsonArray().size());
        assertEquals(3, medicinal.get("capturedConcreteProductCount").getAsInt());
        assertEquals(3, medicinal.get("capturedRawRecordCount").getAsInt());
        assertEquals(0, medicinal.get("capturedAdditionalAncestorCount").getAsInt());
        assertEquals("c9df5ef5d675b0d1d226828bddf6b78c28502d91", medicinal.get("sourceRevision").getAsString());
        assertEquals("a28ed494a096cf63f7887f45e4967e89600b60022fd2b49af22e131f9bafb385",
                medicinal.getAsJsonObject("sourceFiles").getAsJsonObject("Resources/Prototypes/Entities/Objects/Consumable/Food/produce.yml").get("sha256").getAsString());
        assertEquals(6, BotanyCatalogSourceParityTest.read("products/fruit-pit-entity-rows.json").getAsJsonArray().size());
        assertEquals(6, fruitPit.get("capturedConcreteProductCount").getAsInt());
        assertEquals(6, fruitPit.get("capturedRawRecordCount").getAsInt());
        assertEquals(0, fruitPit.get("capturedAdditionalAncestorCount").getAsInt());
        assertEquals(62, fruitPit.get("fullUnionParentClosureRecordCount").getAsInt());
        assertEquals("c9df5ef5d675b0d1d226828bddf6b78c28502d91", fruitPit.get("sourceRevision").getAsString());
        assertEquals("a28ed494a096cf63f7887f45e4967e89600b60022fd2b49af22e131f9bafb385",
                fruitPit.getAsJsonObject("sourceFiles").getAsJsonObject("Resources/Prototypes/Entities/Objects/Consumable/Food/produce.yml").get("sha256").getAsString());
        assertEquals(1, BotanyCatalogSourceParityTest.read("products/blue-pumpkin-entity-rows.json").getAsJsonArray().size());
        assertEquals(1, bluePumpkin.get("capturedConcreteProductCount").getAsInt());
        assertEquals(1, bluePumpkin.get("capturedRawRecordCount").getAsInt());
        assertEquals(0, bluePumpkin.get("capturedAdditionalAncestorCount").getAsInt());
        assertEquals(63, bluePumpkin.get("fullUnionParentClosureRecordCount").getAsInt());
        assertEquals("c9df5ef5d675b0d1d226828bddf6b78c28502d91", bluePumpkin.get("sourceRevision").getAsString());
        assertEquals("a28ed494a096cf63f7887f45e4967e89600b60022fd2b49af22e131f9bafb385",
                bluePumpkin.getAsJsonObject("sourceFiles").getAsJsonObject("Resources/Prototypes/Entities/Objects/Consumable/Food/produce.yml").get("sha256").getAsString());
        assertEquals("FoodProduceBase", bluePumpkin.getAsJsonObject("parentResolution").get("FoodBluePumpkin").getAsString());
        assertEquals(5, BotanyCatalogSourceParityTest.read("products/banana-orange-entity-rows.json").getAsJsonArray().size());
        assertEquals(5, bananaOrange.get("capturedConcreteProductCount").getAsInt());
        assertEquals(5, bananaOrange.get("capturedRawRecordCount").getAsInt());
        assertEquals(0, bananaOrange.get("capturedAdditionalAncestorCount").getAsInt());
        assertEquals(68, bananaOrange.get("fullUnionParentClosureRecordCount").getAsInt());
        assertEquals("c9df5ef5d675b0d1d226828bddf6b78c28502d91", bananaOrange.get("sourceRevision").getAsString());
        assertEquals("a28ed494a096cf63f7887f45e4967e89600b60022fd2b49af22e131f9bafb385",
                bananaOrange.getAsJsonObject("sourceFiles").getAsJsonObject("Resources/Prototypes/Entities/Objects/Consumable/Food/produce.yml").get("sha256").getAsString());
        assertEquals("FoodProduceBase", bananaOrange.getAsJsonObject("parentResolution").get("FoodExtradimensionalOrange").getAsString());
        assertEquals(3, BotanyCatalogSourceParityTest.read("products/egg-entity-rows.json").getAsJsonArray().size());
        assertEquals(2, egg.get("capturedConcreteProductCount").getAsInt());
        assertEquals(3, egg.get("capturedRawRecordCount").getAsInt());
        assertEquals(81, egg.get("fullUnionParentClosureRecordCount").getAsInt());
        assertEquals(3, BotanyCatalogSourceParityTest.read("products/bloonion-entity-rows.json").getAsJsonArray().size());
        assertEquals(1, bloonion.get("capturedConcreteProductCount").getAsInt());
        assertEquals(3, bloonion.get("capturedRawRecordCount").getAsInt());
        assertEquals(84, bloonion.get("fullUnionParentClosureRecordCount").getAsInt());
        assertEquals("c9df5ef5d675b0d1d226828bddf6b78c28502d91", egg.get("sourceRevision").getAsString());
        assertEquals("a405cdd2ff9582c4d4e3c58353d48b56af9a0236a4b2ec4070b7e1478779ab4b",
                egg.getAsJsonObject("sourceFiles").getAsJsonObject("Resources/Prototypes/Entities/Objects/Consumable/Food/egg.yml").get("sha256").getAsString());
        assertEquals("Egg", component(rawRecordMap(rows), "FoodEgg", "SolutionContainerManager").getAsJsonObject("solutions").getAsJsonObject("food").getAsJsonArray("reagents").get(0).getAsJsonObject().get("ReagentId").getAsString());
        assertEquals(3, BotanyCatalogSourceParityTest.read("products/nettle-entity-rows.json").getAsJsonArray().size());
        assertEquals(2, nettle.get("capturedConcreteProductCount").getAsInt());
        assertEquals(3, nettle.get("capturedRawRecordCount").getAsInt());
        assertEquals(90, nettle.get("fullUnionParentClosureRecordCount").getAsInt());
        assertEquals(90, rows.size());
        assertEquals(10, BotanyCatalogSourceParityTest.read("products/melon-pumpkin-entity-rows.json").getAsJsonArray().size());
        assertEquals(7, melonPumpkin.get("capturedConcreteProductCount").getAsInt());
        assertEquals(10, melonPumpkin.get("capturedRawRecordCount").getAsInt());
        assertEquals(3, melonPumpkin.get("capturedAdditionalAncestorCount").getAsInt());
        assertEquals(78, melonPumpkin.get("fullUnionParentClosureRecordCount").getAsInt());
        assertEquals("c9df5ef5d675b0d1d226828bddf6b78c28502d91", melonPumpkin.get("sourceRevision").getAsString());
        var melonHashes = melonPumpkin.getAsJsonObject("sourceFiles");
        assertEquals("a28ed494a096cf63f7887f45e4967e89600b60022fd2b49af22e131f9bafb385", melonHashes.getAsJsonObject("Resources/Prototypes/Entities/Objects/Consumable/Food/produce.yml").get("sha256").getAsString());
        assertEquals("9b9fd3841ffe47c6d30c6ca56f7e798bf2398cfeed1bde8ca82dbc1df3f2c21a", melonHashes.getAsJsonObject("Resources/Prototypes/Entities/Clothing/Head/hats.yml").get("sha256").getAsString());
        assertEquals("50a62f1a608cf8ef2a01166cec4b0bb126b679e5166029a72c75035256bbeaf4", melonHashes.getAsJsonObject("Resources/Prototypes/Entities/Clothing/Head/base_clothinghead.yml").get("sha256").getAsString());
        assertEquals("e86689d00e3615ac01d2d9b5b7089eafdf7557afdd6e3e199a591afe080e9463", melonHashes.getAsJsonObject("Resources/Prototypes/Entities/Clothing/base_clothing.yml").get("sha256").getAsString());
        assertEquals("a10d7cbb4e64d4f31553fd0cd8aeb980edead33093d4bbf40bde6fe8180fe8b8", melonHashes.getAsJsonObject("Resources/Prototypes/Entities/Objects/Decoration/jackolantern.yml").get("sha256").getAsString());
        assertEquals("9990749b40110047da2408474f4930ab621885c9665fa5777ffac3c1b6aee539", melonHashes.getAsJsonObject("Resources/Prototypes/Entities/Objects/Consumable/Food/ingredients.yml").get("sha256").getAsString());
        assertEquals(3, conversion.get("capturedConcreteProductCount").getAsInt());
        assertEquals(4, conversion.get("capturedRawRecordCount").getAsInt());
        assertEquals(1, conversion.get("capturedAdditionalAncestorCount").getAsInt());
        assertEquals("c9df5ef5d675b0d1d226828bddf6b78c28502d91", conversion.get("sourceRevision").getAsString());
        var defaults = metadata.getAsJsonObject("defaults");
        var edible = defaults.getAsJsonObject("EdibleComponent");
        assertEquals(5, edible.get("transferAmountDefaultUnits").getAsInt());
        assertEquals(1, edible.get("delayDefaultSeconds").getAsInt());
        assertEquals(3, edible.get("forceFeedDelayDefaultSeconds").getAsInt());
        assertTrue(edible.get("destroyOnEmptyDefault").getAsBoolean());
        assertFalse(edible.get("requiresSpecialDigestionDefault").getAsBoolean());
        assertEquals(1, defaults.getAsJsonObject("PotencyVisualsComponent").get("minimumScaleDefault").getAsInt());
        assertEquals(2, defaults.getAsJsonObject("PotencyVisualsComponent").get("maximumScaleDefault").getAsInt());
        assertTrue(defaults.getAsJsonObject("ExtractableComponent").get("grindableSolutionNameDefault").isJsonNull());
        var seen = new java.util.HashSet<String>();
        for (var element : rows) {
            var wrapper = element.getAsJsonObject();
            var sourceId = wrapper.get("id").getAsString();
            if (!PRODUCTS.containsKey(sourceId)) continue; // Captured ancestor closure is not additional products.
            assertTrue(seen.add(sourceId), "duplicate source product " + sourceId);
            assertFalse(PRODUCTS.get(sourceId).isBlank());
            assertEquals(sourceId, wrapper.getAsJsonObject("record").get("id").getAsString());
        }
        assertEquals(PRODUCTS.keySet(), seen);
        assertEquals(69, seen.size());
    }

    @Test void resolvedFoodAndExtractionMatchCapturedRawSource() throws Exception {
        var rows = capturedRows();
        var rawRows = rawRecordMap(rows);
        var orangeFood = component(rawRows, "FoodExtradimensionalOrange", "SolutionContainerManager").getAsJsonObject("solutions").getAsJsonObject("food");
        assertEquals(Map.of(id("haloperidol"), 500L, id("nutriment"), 500L, id("vitamin"), 400L),
                reagents(orangeFood.getAsJsonArray("reagents")), "orange source starts with 14u Haloperidol5/Nutriment5/Vitamin4");
        assertEquals(1400L, capacityCents(orangeFood.get("maxVol"), 1400L));
        var orangeJuice = component(rawRows, "FoodExtradimensionalOrange", "Extractable").getAsJsonObject("juiceSolution");
        assertEquals(Map.of(id("juiceorange"), 1000L), reagents(orangeJuice.getAsJsonArray("reagents")));
        var orangeVisuals = component(rawRows, "FoodExtradimensionalOrange", "PotencyVisuals");
        assertEquals(.5f, orangeVisuals.get("minimumScale").getAsFloat());
        assertEquals(1f, orangeVisuals.get("maximumScale").getAsFloat());
        var rawProduceAbsences = new java.util.HashSet<String>();
        for (var sourceId : PRODUCTS.keySet()) if (component(rawRows, sourceId, "Produce") == null) rawProduceAbsences.add(sourceId);
        assertEquals(PRODUCE_ABSENCES, rawProduceAbsences, "bounded source Produce absences");
        assertNull(component(rawRows, "FoodBluePumpkin", "Butcherable"), "blue pumpkin has no orange-pumpkin butchery");
        assertNull(component(rawRows, "FoodBluePumpkin", "SliceableFood"), "blue pumpkin has no slice output");
        var manager = new PrototypeManager(); manager.register(ModIngestionProfiles.TYPE); manager.register(ModExtractableProfiles.TYPE); manager.register(ModProduceProfiles.TYPE);
        var before = manager.snapshot();
        var food = new LinkedHashMap<ResourceLocation, IngestionProfile>();
        var extracts = new LinkedHashMap<ResourceLocation, ExtractableProfile>();
        var produce = new LinkedHashMap<ResourceLocation, ProduceProfile>();
          var ingestionNames = new ArrayList<String>(PRODUCTS.values()); ingestionNames.remove("food_cocoa_beans"); ingestionNames.remove("food_meat_wheat"); ingestionNames.remove("food_corn"); ingestionNames.remove("food_corn_trash"); ingestionNames.remove("bloonion"); ingestionNames.remove("shard_glass"); ingestionNames.remove("death_nettle"); ingestionNames.add(0, "produce_slice_base"); ingestionNames.add(0, "ruminant_edible_base"); ingestionNames.add(0, "food_base");
        var extractionNames = new ArrayList<String>(PRODUCTS.values()); extractionNames.remove("food_cocoa_beans"); extractionNames.remove("food_meat_wheat"); extractionNames.remove("food_corn_trash"); extractionNames.remove("egg"); extractionNames.remove("eggshells"); extractionNames.add(0, "extractable_base");
        var produceNames = new ArrayList<String>();
        produceNames.add("produce_base");
        for (var entry : PRODUCTS.entrySet()) if (!PRODUCE_ABSENCES.contains(entry.getKey()) && !entry.getValue().equals("food_cocoa_beans")) produceNames.add(entry.getValue());
        produceNames.add("egg"); // Botany's resolved runtime EnsureComp<Produce> adaptation; source FoodEgg has no Produce.
        ingestionNames.add("clothing_head_base");
        ingestionNames.add("trash_banana_peel"); ingestionNames.add("trash_mimana_peel");
        var ingestionResources = new LinkedHashMap<>(BotanyCatalogCandidateTest.resources("ingestion_profile", ingestionNames.toArray(String[]::new)));
        var extractResources = new LinkedHashMap<>(BotanyCatalogCandidateTest.resources("extractable_profile", extractionNames.toArray(String[]::new)));
        var produceResources = new LinkedHashMap<>(BotanyCatalogCandidateTest.resources("produce_profile", produceNames.toArray(String[]::new)));
        ingestionResources.putAll(BotanyCatalogCandidateTest.mainResources("ingestion_profile", "food_cocoa_beans", "food_meat_wheat"));
        extractResources.putAll(BotanyCatalogCandidateTest.mainResources("extractable_profile", "food_cocoa_beans", "food_meat_wheat"));
        ingestionResources.putAll(BotanyCatalogCandidateTest.mainResources("ingestion_profile", "poppy"));
        extractResources.putAll(BotanyCatalogCandidateTest.mainResources("extractable_profile", "poppy"));
        produceResources.putAll(BotanyCatalogCandidateTest.mainResources("produce_profile", "poppy"));
        produceResources.putAll(BotanyCatalogCandidateTest.mainResources("produce_profile", "food_cocoa_beans"));
          var sourceNames = new ArrayList<String>(PRODUCTS.values()); sourceNames.remove("food_cocoa_beans"); sourceNames.remove("food_meat_wheat");
        var sourceResources = BotanyCatalogCandidateTest.resources("item_food_solution_profile", sourceNames.toArray(String[]::new));
        sourceResources.putAll(BotanyCatalogCandidateTest.resources("item_food_solution_profile", "produce_slice_base"));
        sourceResources.putAll(BotanyCatalogCandidateTest.mainResources("item_food_solution_profile", "tomato", "poppy", "food_cocoa_beans", "food_meat_wheat"));
        var sourceProfiles = BotanyCatalogCandidateTest.stageFoodSolutions(sourceResources);
        PrototypeResolver.resolve(ingestionResources, new com.juicyslew.moonstation14.ms14.ingestion.IngestionProfileMergeStrategy()).asMap()
                .forEach((key, raw) -> food.put(key, IngestionProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
        manager.stage(Map.of(
                ModExtractableProfiles.TYPE, extractResources,
                ModProduceProfiles.TYPE, produceResources), catalogs -> {
            catalogs.get(ModExtractableProfiles.TYPE.typeId()).forEach((key, raw) -> extracts.put(key, ExtractableProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
            catalogs.get(ModProduceProfiles.TYPE.typeId()).forEach((key, raw) -> produce.put(key, ProduceProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
        });
        assertSame(before, manager.snapshot()); assertEquals(65, PRODUCTS.values().stream().filter(name -> food.containsKey(id(name))).count());
        assertFalse(food.containsKey(id("food_corn_trash")), "non-edible cob has no ingestion policy");
          assertEquals(67, extracts.size()); assertEquals(57, produce.size());
          assertEquals(66, PRODUCTS.values().stream().filter(name -> extracts.containsKey(id(name))).count(),
                "product-bound Extractable compositions include every product except the non-Extractable cob");
          assertEquals(56, PRODUCTS.values().stream().filter(name -> produce.containsKey(id(name))).count(),
                 "source Produce bindings plus the FoodEgg runtime EnsureComp adaptation");
        var failures = new java.util.ArrayList<org.junit.jupiter.api.function.Executable>();
         var metadata = BotanyCatalogSourceParityTest.read("products/metadata-defaults.json").getAsJsonObject().getAsJsonObject("defaults");
        var edibleDefaults = metadata.getAsJsonObject("EdibleComponent");
        var visualDefaults = metadata.getAsJsonObject("PotencyVisualsComponent");
        for (var entry : PRODUCTS.entrySet()) {
            String sourceId = entry.getKey(), localId = entry.getValue();
             var actualSource = sourceProfiles.get(id(localId));
             var sourceFood = component(rawRows, sourceId, "SolutionContainerManager");
            var solutions = sourceFood == null ? null : sourceFood.getAsJsonObject("solutions");
            var foodSolution = solutions == null ? null : solutions.getAsJsonObject("food");
             JsonObject selectedSolution = sourceId.equals("ShardGlass") && solutions != null
                     ? solutions.getAsJsonObject(actualSource.solutionName()) : foodSolution;
             Map<ResourceLocation,Long> expectedContents = reagents(selectedSolution == null ? null : selectedSolution.getAsJsonArray("reagents"));
            long sum = expectedContents.values().stream().mapToLong(Long::longValue).sum();
            long capacity = foodSolution == null || !foodSolution.has("maxVol") ? sum : capacityCents(foodSolution.get("maxVol"), sum);
            String rawExpected = sourceId;
            var actualFood = food.get(id(localId));
             boolean refillable = component(rawRows, sourceId, "RefillableSolution") != null;
             var sourceRefillable = component(rawRows, sourceId, "RefillableSolution");
              String refillableSolutionName = sourceRefillable == null ? null
                      : sourceRefillable.has("solution") ? sourceRefillable.get("solution").getAsString() : "default";
             boolean canReact = foodSolution == null || !foodSolution.has("canReact") || foodSolution.get("canReact").getAsBoolean();
              failures.add(() -> { assertNotNull(actualSource, rawExpected + " food source profile"); assertEquals(expectedContents, actualSource.contentsCents(), rawExpected + " food contents"); assertEquals(capacity, actualSource.capacityCents(), rawExpected + " food capacity"); assertEquals(canReact, actualSource.canReact(), rawExpected + " food canReact (upstream Solution default=true)"); assertEquals(refillable, actualSource.refillable(), rawExpected + " food refillable (captured RefillableSolution component)"); if (refillable) assertEquals(refillableSolutionName, actualSource.solutionName(), rawExpected + " RefillableSolution solution name"); });
             boolean shouldHaveProduce = !PRODUCE_ABSENCES.contains(sourceId);
             boolean botanyEnsuresProduce = sourceId.equals("FoodEgg");
            var sourceProduce = component(rawRows, sourceId, "Produce");
            var sourceVisual = component(rawRows, sourceId, "PotencyVisuals");
            var actualProduce = produce.get(id(localId));
            var sourceEdible = component(rawRows, sourceId, "Edible");
            String seed = sourceProduce == null || !sourceProduce.has("seedId") || sourceProduce.get("seedId").isJsonNull() ? null : sourceProduce.get("seedId").getAsString();
            if (shouldHaveProduce) assertNotNull(sourceVisual, sourceId + " missing source PotencyVisuals component");
            String target = produceTargetSolution(sourceProduce, metadata.getAsJsonObject("ProduceComponent").get("targetSolutionDefault").getAsString());
            float minScale = sourceVisual != null && sourceVisual.has("minimumScale") ? sourceVisual.get("minimumScale").getAsFloat() : visualDefaults.get("minimumScaleDefault").getAsFloat();
            float maxScale = sourceVisual != null && sourceVisual.has("maximumScale") ? sourceVisual.get("maximumScale").getAsFloat() : visualDefaults.get("maximumScaleDefault").getAsFloat();
              final String sourceSolutionName = sourceId.equals("ShardGlass") ? actualSource.solutionName()
                      : sourceEdible != null && sourceEdible.has("solution") ? sourceEdible.get("solution").getAsString() : edibleDefaults.get("solutionDefault").getAsString();
            int dose = edibleDose(sourceEdible, edibleDefaults);
            int delay = sourceEdible != null && sourceEdible.has("delay") ? secondsToTicks(sourceEdible.get("delay")) : secondsToTicks(edibleDefaults.get("delayDefaultSeconds"));
            int forceDelay = sourceEdible != null && sourceEdible.has("forceFeedDelay") ? secondsToTicks(sourceEdible.get("forceFeedDelay")) : secondsToTicks(edibleDefaults.get("forceFeedDelayDefaultSeconds"));
            boolean destroy = sourceEdible != null && sourceEdible.has("destroyOnEmpty") ? sourceEdible.get("destroyOnEmpty").getAsBoolean() : edibleDefaults.get("destroyOnEmptyDefault").getAsBoolean();
            boolean special = sourceEdible != null && sourceEdible.has("requiresSpecialDigestion") ? sourceEdible.get("requiresSpecialDigestion").getAsBoolean() : edibleDefaults.get("requiresSpecialDigestionDefault").getAsBoolean();
            JsonArray sourceTrash = sourceEdible != null && sourceEdible.has("trash") ? sourceEdible.getAsJsonArray("trash") : edibleDefaults.getAsJsonArray("trashDefault");
            failures.add(() -> {
                assertEquals(shouldHaveProduce, sourceProduce != null, sourceId + " Produce component presence");
                 if (!shouldHaveProduce) {
                     assertNull(sourceVisual, sourceId + " source PotencyVisuals component");
                     if (botanyEnsuresProduce) {
                         assertNotNull(actualProduce, "Botany runtime GenerateProduct ensures a resolved Produce composition for FoodEgg");
                         assertEquals(id("eggy"), actualProduce.seed(), "runtime adaptation keeps source seed reference");
                         assertNull(actualProduce.potencyVisuals(), "FoodEgg has no local potency-visual consumer");
                     } else assertNull(actualProduce, sourceId + " source-absent produce metadata");
                 }
                else {
                    assertNotNull(actualProduce, sourceId + " produce profile");
                    if (NULL_SEED_PRODUCE.contains(sourceId)) assertNull(seed, sourceId + " source seed");
                    else assertNotNull(seed, sourceId + " source seed");
                    if (seed == null) assertNull(actualProduce.seed(), sourceId + " seed");
                    else assertEquals(id(BotanyCatalogSourceParityTest.snake(seed)), actualProduce.seed(), sourceId + " seed");
                    assertEquals("food", target, sourceId + " source Produce target");
                    assertNotNull(actualProduce.potencyVisuals(), sourceId + " inherited potency visuals");
                    assertEquals(minScale, actualProduce.potencyVisuals().minimumScale());
                    assertEquals(maxScale, actualProduce.potencyVisuals().maximumScale());
                }
                  boolean shouldHaveEdible = !Set.of("FoodCornTrash", "FoodBloonion", "ShardGlass", "DeathNettle").contains(sourceId);
                assertEquals(shouldHaveEdible, sourceEdible != null, sourceId + " Edible component presence");
                if (!shouldHaveEdible) assertNull(actualFood, sourceId + " must not have ingestion profile");
                else assertNotNull(sourceEdible, sourceId + " Edible inherited component");
                 if (shouldHaveEdible) assertEquals(actualSource.solutionName(), sourceSolutionName, sourceId + " Edible solution");
                if (shouldHaveEdible) assertEquals(dose, actualFood.edible().doseCents(), sourceId + " transfer amount");
                if (shouldHaveEdible) assertEquals(delay, actualFood.edible().delayTicks(), sourceId + " eating delay");
                if (shouldHaveEdible) assertEquals(forceDelay, actualFood.edible().forceFeedDelayTicks(), sourceId + " force-feed delay");
                if (shouldHaveEdible) assertEquals(destroy, actualFood.edible().destroyOnEmpty(), sourceId + " destroyOnEmpty");
                if (shouldHaveEdible) assertEquals(special, actualFood.edible().requiresSpecialDigestion(), sourceId + " special digestion");
                var expectedTrash = new ArrayList<ResourceLocation>();
                if (sourceTrash != null) sourceTrash.forEach(value -> expectedTrash.add(id(BotanyCatalogSourceParityTest.snake(value.getAsString()))));
                if (shouldHaveEdible) { assertEquals(expectedTrash, actualFood.edible().trash(), sourceId + " edible trash"); assertEquals(IngestionProfile.Kind.FOOD, actualFood.edible().kind(), sourceId + " ordinary food adaptation"); }
            });
            var ex = component(rawRows, sourceId, "Extractable");
            assertNotNull(sourceFood, sourceId + " missing SolutionContainerManager");
             boolean shouldHaveExtractable = !Set.of("FoodCornTrash", "FoodEgg", "Eggshells").contains(sourceId);
            assertEquals(shouldHaveExtractable, ex != null, sourceId + " Extractable component presence");
            String grind = ex == null || !ex.has("grindableSolutionName") || ex.get("grindableSolutionName").isJsonNull() ? null : ex.get("grindableSolutionName").getAsString();
            JsonObject juice = ex == null || !ex.has("juiceSolution") || ex.get("juiceSolution").isJsonNull() ? null : ex.getAsJsonObject("juiceSolution");
            var juiceContents = juiceReagents(juice == null ? null : juice.getAsJsonArray("reagents"));
            var actualEx = extracts.get(id(localId));
            failures.add(() -> { if (!shouldHaveExtractable) assertNull(actualEx, rawExpected + " must not have Extractable profile"); else { assertNotNull(actualEx, rawExpected); assertEquals(grind, actualEx.grindableSolutionName(), rawExpected + " grindableSolutionName"); if (juice == null) assertNull(actualEx.juiceSolution(), rawExpected + " juiceSolution"); else { assertNotNull(actualEx.juiceSolution(), rawExpected + " juiceSolution"); assertEquals(juiceContents, actualEx.juiceSolution().centContents(), rawExpected + " juice contents"); } } });
        }
        assertAll("captured source product comparisons", failures);
        assertNotNull(food.get(id("poppy"))); assertNotNull(extracts.get(id("poppy"))); assertNotNull(produce.get(id("poppy")));
        assertSame(before, manager.snapshot());
    }

    @Test void sourceFieldSemanticsUseSerializedTargetAndExactConversions() {
        var defaults = new JsonObject(); defaults.addProperty("targetSolutionDefault", "default_food");
        var raw = new JsonObject(); raw.addProperty("targetSolution", "override_food"); raw.addProperty("solutionName", "wrong_key");
        assertEquals("override_food", produceTargetSolution(raw, defaults.get("targetSolutionDefault").getAsString()));
        raw.remove("targetSolution");
        assertEquals("default_food", produceTargetSolution(raw, defaults.get("targetSolutionDefault").getAsString()));
        assertEquals(101L, capacityCents(JsonParser.parseString("1.01"), 400L));
        assertEquals(1L, capacityCents(JsonParser.parseString("0.01"), 400L));
        assertEquals(20, secondsToTicks(JsonParser.parseString("1")));
        assertThrows(ArithmeticException.class, () -> secondsToTicks(JsonParser.parseString("0.025")));
        var edibleDefaults = new JsonObject(); edibleDefaults.addProperty("transferAmountDefaultUnits", 5);
        assertEquals(500, edibleDose(new JsonObject(), edibleDefaults));
        var nullTransfer = new JsonObject(); nullTransfer.add("transferAmount", com.google.gson.JsonNull.INSTANCE);
        var unsupported = assertThrows(AssertionError.class, () -> edibleDose(nullTransfer, edibleDefaults));
        assertTrue(unsupported.getMessage().contains("whole-solution"));
    }

    @Test void capturedButcherableOutputsMatchResolvedItemProcessingProfiles() throws Exception {
        var rows = rawRecordMap(capturedRows());
        var names = List.of("corn", "bungo", "cherry", "banana", "mimana", "watermelon", "holymelon", "pumpkin");
        var manager = new PrototypeManager();
        manager.register(ModItemProcessingProfiles.TYPE);
        var resolved = new LinkedHashMap<ResourceLocation, com.juicyslew.moonstation14.ms14.item_processing.ItemProcessingProfile>();
        // These candidate outputs are mapped by explicit source-linked IDs, not by Food-prefix aliasing.
        var resources = new LinkedHashMap<>(BotanyCatalogCandidateTest.resources("item_processing_profile", names.toArray(String[]::new)));
        // Candidate-only resolver deliberately avoids production output-registration validation: some fixed
        // SS14 secondary outputs are represented as candidate IDs rather than registered Minecraft items.
        var rawProfiles = PrototypeResolver.resolve(resources, null).asMap();
        rawProfiles.forEach((key, raw) -> resolved.put(key,
                com.juicyslew.moonstation14.ms14.item_processing.ItemProcessingProfile.CODEC
                        .parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
        var sourceIds = Map.of("corn", "FoodCorn", "bungo", "FoodBungo", "cherry", "FoodCherry",
                "banana", "FoodBanana", "mimana", "FoodMimana", "watermelon", "FoodWatermelon",
                "holymelon", "FoodHolymelon", "pumpkin", "FoodPumpkin");
        for (var entry : sourceIds.entrySet()) {
            var source = component(rows, entry.getValue(), "Butcherable");
            assertNotNull(source, entry.getValue() + " source Butcherable");
            // ButcherableComponent.cs defaults: Knife and 8 seconds when YAML omits either field.
            String type = source.has("butcheringType") ? source.get("butcheringType").getAsString() : "Knife";
            float delay = source.has("delay") ? source.get("delay").getAsFloat() : 8f;
            var expected = new ArrayList<com.juicyslew.moonstation14.ms14.item_processing.ItemProcessingProfile.ItemOutput>();
            var spawned = source.getAsJsonArray("spawned");
            for (var output : spawned) {
                var row = output.getAsJsonObject();
                String sourceOutput = row.get("id").getAsString();
                String mapped = sourceOutput;
                if (sourceOutput.equals("FoodCornTrash")) mapped = "food_corn_trash";
                else if (sourceOutput.equals("FoodBungoPit")) mapped = "food_bungo_pit";
                else if (sourceOutput.equals("TrashCherryPit")) mapped = "trash_cherry_pit";
                else if (sourceOutput.equals("TrashBananaPeel")) mapped = "trash_banana_peel";
                else if (sourceOutput.equals("TrashMimanaPeel")) mapped = "trash_mimana_peel";
                else if (sourceOutput.equals("ClothingHeadHatWatermelon")) mapped = "clothing_head_hat_watermelon";
                else if (sourceOutput.equals("ClothingHeadHatHolyWatermelon")) mapped = "clothing_head_hat_holy_watermelon";
                else if (sourceOutput.equals("CarvedPumpkin")) mapped = "carved_pumpkin";
                else if (sourceOutput.equals("PumpkinSeeds")) mapped = "pumpkin_seeds";
                expected.add(new com.juicyslew.moonstation14.ms14.item_processing.ItemProcessingProfile.ItemOutput(id(mapped), row.has("number") ? row.get("number").getAsInt() : 1));
            }
            var actual = resolved.get(id(entry.getKey())).butcherable();
            assertNotNull(actual, entry.getKey());
            assertEquals(com.juicyslew.moonstation14.ms14.item_processing.ItemProcessingProfile.Butcherable.Type.valueOf(type.toUpperCase(Locale.ROOT)), actual.type());
            assertEquals(delay, actual.delaySeconds());
            assertEquals(expected, actual.outputs(), entry.getKey() + " ordered outputs");
        }
        var extraNames = List.of(PRODUCTS.get("FoodOnion"), PRODUCTS.get("FoodOnionRed"), PRODUCTS.get("FoodWatermelon"),
                PRODUCTS.get("FoodHolymelon"), PRODUCTS.get("FoodPineapple"), PRODUCTS.get("FoodCocoaPod"), "meatwheat_bushel");
        resources.putAll(BotanyCatalogCandidateTest.resources("item_processing_profile", extraNames.toArray(String[]::new)));
        rawProfiles = PrototypeResolver.resolve(resources, null).asMap();
        resolved.clear();
        rawProfiles.forEach((key, raw) -> resolved.put(key,
                com.juicyslew.moonstation14.ms14.item_processing.ItemProcessingProfile.CODEC
                        .parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
        var sliceCases = Map.of("FoodOnion", "onion", "FoodOnionRed", "onion_red",
                "FoodWatermelon", "watermelon", "FoodHolymelon", "holymelon", "FoodPineapple", "pineapple");
        int sliceCount = 0;
        for (var entry : sliceCases.entrySet()) {
            var source = component(rows, entry.getKey(), "SliceableFood");
            if (source == null) continue;
            var profile = resolved.get(id(entry.getValue()));
            assertNotNull(profile, entry.getKey() + " resolved candidate profile " + entry.getValue());
            var actual = profile.sliceableFood();
            assertNotNull(actual, entry.getKey());
            String output = source.get("slice").getAsString();
            assertEquals(id(PRODUCTS.get(output)), actual.output(), entry.getKey() + " slice output");
            assertEquals(source.has("count") ? source.get("count").getAsInt() : 5, actual.count(), entry.getKey());
            assertEquals(source.has("sliceTime") ? source.get("sliceTime").getAsFloat() : 1f, actual.delaySeconds(), entry.getKey());
            sliceCount++;
        }
        assertEquals(5, sliceCount, "captured source sliceable bindings represented by these profiles");
        // Source MeatwheatBushel is the conversion entity; FoodMeatWheat is its spawned output.
        var conversions = Map.of("FoodCocoaPod", PRODUCTS.get("FoodCocoaPod"), "MeatwheatBushel", "meatwheat_bushel");
        int spawnCount = 0;
        for (var entry : conversions.entrySet()) {
            var source = component(rows, entry.getKey(), "SpawnItemsOnUse");
            assertNotNull(source, entry.getKey() + " captured SpawnItemsOnUse");
            var actual = resolved.get(id(entry.getValue())).spawnItemsOnUse();
            assertNotNull(actual, entry.getKey());
            var items = source.getAsJsonArray("items");
            assertEquals(items.size(), actual.outputs().size());
            for (int i = 0; i < items.size(); i++) {
                var item = items.get(i).getAsJsonObject();
                assertEquals(id(PRODUCTS.get(item.get("id").getAsString())), actual.outputs().get(i).output());
                assertEquals(item.has("amount") ? item.get("amount").getAsInt() : 1, actual.outputs().get(i).count());
            }
            assertEquals(1, actual.uses(), entry.getKey() + " SpawnItemsOnUse uses default");
            var sourceSound = source.getAsJsonObject("sound").get("path").getAsString();
            String expectedSound = sourceSound.endsWith("packetrip.ogg") ? "botany/produce/packetrip"
                    : sourceSound.endsWith("slime_squish.ogg") ? "slime_squish" : null;
            assertNotNull(expectedSound, entry.getKey() + " explicitly supported source sound mapping");
            assertEquals(id(expectedSound), actual.sound(), entry.getKey() + " source sound");
            spawnCount++;
        }
        assertEquals(2, spawnCount, "both captured SpawnItemsOnUse conversion entities");
    }

    @Test void capturedAmbrosiaHeadwearMatchesResolvedEquipmentAndClothingDefault() throws Exception {
        var rows = rawRecordMap(capturedRows());
        var manager = new PrototypeManager(); manager.register(ModEquipmentItems.TYPE);
        var resources = new LinkedHashMap<ResourceLocation, JsonObject>();
        resources.putAll(BotanyCatalogCandidateTest.mainResources("equipment_item", "base_item", "base_clothing"));
        resources.putAll(BotanyCatalogCandidateTest.resources("equipment_item", "clothing_head_base", "ambrosia_vulgaris", "ambrosia_deus"));
        var resolved = new LinkedHashMap<ResourceLocation, EquipmentItemData>();
        var before = manager.snapshot();
        manager.stage(Map.of(ModEquipmentItems.TYPE, resources), catalogs ->
                catalogs.get(ModEquipmentItems.TYPE.typeId()).forEach((key, raw) ->
                        resolved.put(key, EquipmentItemData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow())));
        assertSame(before, manager.snapshot());
        for (var pair : Map.of("FoodAmbrosiaVulgaris", "ambrosia_vulgaris", "FoodAmbrosiaDeus", "ambrosia_deus").entrySet()) {
            var clothing = component(rows, pair.getKey(), "Clothing");
            assertEquals(List.of("HEAD"), stringList(clothing.getAsJsonArray("slots")), pair.getKey());
            assertFalse(clothing.has("quickEquip"), pair.getKey() + " relies on ClothingComponent.cs quickEquip=true default");
            var actual = resolved.get(id(pair.getValue())).clothing().orElseThrow();
            assertEquals(List.of(InventoryTemplateData.SlotFlag.HEAD), actual.slotFlags());
            assertTrue(actual.quickEquip(), pair.getKey() + " source default from ClothingComponent.cs:28-29");
        }
    }

    @Test void capturedPoppyAndLilyHeadwearMatchesMainAndInheritedEquipmentProfiles() throws Exception {
        var rows = rawRecordMap(capturedRows());
        var manager = new PrototypeManager(); manager.register(ModEquipmentItems.TYPE);
        var resources = new LinkedHashMap<ResourceLocation, JsonObject>();
        resources.putAll(BotanyCatalogCandidateTest.mainResources("equipment_item", "base_item", "base_clothing", "poppy"));
        resources.putAll(BotanyCatalogCandidateTest.resources("equipment_item", "lily"));
        var resolved = new LinkedHashMap<ResourceLocation, EquipmentItemData>();
        var before = manager.snapshot();
        manager.stage(Map.of(ModEquipmentItems.TYPE, resources), catalogs ->
                catalogs.get(ModEquipmentItems.TYPE.typeId()).forEach((key, raw) ->
                        resolved.put(key, EquipmentItemData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow())));
        assertSame(before, manager.snapshot());
        for (var pair : Map.of("FoodPoppy", "poppy", "FoodLily", "lily").entrySet()) {
            var source = component(rows, pair.getKey(), "Clothing");
            assertEquals(List.of("HEAD"), stringList(source.getAsJsonArray("slots")), pair.getKey());
            assertEquals(false, source.get("quickEquip").getAsBoolean(), pair.getKey() + " authored quickEquip=false");
            var actual = resolved.get(id(pair.getValue())).clothing().orElseThrow();
            assertEquals(List.of(InventoryTemplateData.SlotFlag.HEAD), actual.slotFlags());
            assertFalse(actual.quickEquip(), pair.getKey());
        }
        assertEquals("FoodProduceBase", rows.get("FoodPoppy").get("parent").getAsString());
        assertEquals("FoodPoppy", rows.get("FoodLily").get("parent").getAsString());
    }

    @Test void capturedPoppyResolvedSourceItemSizeMatchesEquipmentAndFootprintProjections() throws Exception {
        var rows = rawRecordMap(capturedRows());
        var sourceItem = component(rows, "FoodPoppy", "Item");
        assertNotNull(sourceItem, "FoodPoppy inherits Item from FoodBase/BaseItem");
        String sourceSize = sourceItem.get("size").getAsString();

        var equipmentManager = new PrototypeManager(); equipmentManager.register(ModEquipmentItems.TYPE);
        var equipmentResources = BotanyCatalogCandidateTest.mainResources("equipment_item", "base_item", "base_clothing", "poppy");
        equipmentManager.reload(ModEquipmentItems.TYPE, equipmentResources);
        assertEquals(sourceSize, equipmentManager.snapshot(ModEquipmentItems.TYPE).get(id("poppy")).item().size());

        var footprintManager = new PrototypeManager(); footprintManager.register(ModItemFootprints.TYPE);
        var footprintResources = BotanyCatalogCandidateTest.mainResources("item_footprint", "small", "poppy");
        footprintManager.reload(ModItemFootprints.TYPE, footprintResources);
        var footprint = footprintManager.snapshot(ModItemFootprints.TYPE).get(id("poppy"));
        assertEquals(ItemFootprintProfile.ItemSize.valueOf(sourceSize), footprint.itemSize().orElseThrow());
        assertEquals(2, footprint.cells().size(), "retain inherited Small geometry");
    }

    @Test void capturedBotanyOutputItemSizesMatchResolvedFootprintProfiles() throws Exception {
        var rows = rawRecordMap(capturedRows());
        var resources = new LinkedHashMap<>(BotanyCatalogCandidateTest.mainResources("item_footprint", "tiny", "small"));
        var productionItems = Set.of("food_meat_wheat", "food_cocoa_beans");
        for (String name : PRODUCTS.values()) {
            String path = productionItems.contains(name)
                    ? "data/moonstation14/moonstation14/item_footprint/" + name + ".json"
                    : "botany-catalog-candidate/item_footprint/" + name + ".json";
            try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
                assertNotNull(stream, name + " footprint profile");
                resources.put(id(name), JsonParser.parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject());
            }
        }
        var manager = new PrototypeManager(); manager.register(ModItemFootprints.TYPE);
        var before = manager.snapshot();
        var staged = new LinkedHashMap<ResourceLocation, ItemFootprintProfile>();
        manager.stage(Map.of(ModItemFootprints.TYPE, resources), catalogs ->
                catalogs.get(ModItemFootprints.TYPE.typeId()).forEach((key, raw) -> staged.put(key,
                        ItemFootprintProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow())));
        assertSame(before, manager.snapshot(), "candidate staging must not publish a global catalog");
        int tiny = 0, small = 0;
        for (var entry : PRODUCTS.entrySet()) {
            var sourceItem = component(rows, entry.getKey(), "Item");
            assertNotNull(sourceItem, entry.getKey() + " captured resolved Item");
            String sourceSize = sourceItem.get("size").getAsString();
            assertTrue(sourceSize.equals("Tiny") || sourceSize.equals("Small"), entry.getKey() + " captured source size");
            var actual = staged.get(id(entry.getValue()));
            assertNotNull(actual, entry.getValue() + " staged footprint");
            assertEquals(ItemFootprintProfile.ItemSize.valueOf(sourceSize), actual.itemSize().orElseThrow());
            int expectedCells = sourceSize.equals("Tiny") ? 1 : 2;
            assertEquals(expectedCells, actual.cells().size(), entry.getValue() + " Minecraft geometry adaptation");
            if (sourceSize.equals("Tiny")) tiny++; else small++;
        }
        assertEquals(8, tiny, "captured Tiny source items including FoodEgg, Eggshells, and ShardGlass");
          assertEquals(61, small, "captured Small source items including nettles");
    }

    @Test void capturedBananaPeelSlipAndHeadwearSourceFieldsMatchCandidateDefaults() throws Exception {
        var rows = rawRecordMap(capturedRows());
        var defaults = BotanyCatalogSourceParityTest.read("products/metadata-defaults.json").getAsJsonObject().getAsJsonObject("defaults");
        var banana = component(rows, "TrashBananaPeel", "Item");
        assertEquals("Small", banana.get("size").getAsString());
        var bananaClothing = component(rows, "TrashBananaPeel", "Clothing");
        assertEquals(List.of("HEAD"), stringList(bananaClothing.getAsJsonArray("slots")));
        assertFalse(bananaClothing.get("quickEquip").getAsBoolean());
        var mimanaClothing = component(rows, "TrashMimanaPeel", "Clothing");
        assertEquals(List.of("HEAD"), stringList(mimanaClothing.getAsJsonArray("slots")));
        assertFalse(mimanaClothing.get("quickEquip").getAsBoolean(), "mimana inherits banana's false quickEquip");
        assertNull(component(rows, "TrashBananaPeel", "Produce"));
        assertNull(component(rows, "TrashMimanaPeel", "Produce"));
        assertNull(component(rows, "TrashBananaPeel", "Seed"));
        assertNull(component(rows, "TrashMimanaPeel", "Seed"));
        for (var sourceId : List.of("TrashBananaPeel", "TrashMimanaPeel")) {
            var fixtures = component(rows, sourceId, "Fixtures").getAsJsonObject("fixtures");
            var bounds = fixtures.getAsJsonObject("fix1").getAsJsonObject("shape").getAsJsonObject("value").get("bounds").getAsString();
            assertEquals("-0.2,-0.2,0.2,0.2", bounds, sourceId + " source collision bounds; local width adaptation is 0.4");
            assertEquals(.2f, component(rows, sourceId, "StepTrigger").get("intersectRatio").getAsFloat());
        }
        // RequiredSpeed and Slippery payload defaults are C# component defaults; captured YAML omits them.
        // Verified source defaults: StepTrigger requiredSpeed=3.5, active=true, ignoreWeightless=false;
        // Slippery stun=.5, knockdown=1.5, launch=1.5, friction=.5 for .5s, stamina=25, affectsSliding=false.
        var bananaSlip = component(rows, "TrashBananaPeel", "Slippery");
        var mimanaSlip = component(rows, "TrashMimanaPeel", "Slippery");
        assertFalse(bananaSlip.has("slipSound"), "banana uses the Slippery C# sound default");
        assertEquals(-100, mimanaSlip.getAsJsonObject("slipSound").getAsJsonObject("params").get("volume").getAsInt());
        var looseResources = new LinkedHashMap<>(BotanyCatalogCandidateTest.mainResources("loose_object_profile", "generic"));
        looseResources.putAll(BotanyCatalogCandidateTest.resources("loose_object_profile", "trash_banana_peel"));
        var resolvedLoose = PrototypeResolver.resolve(looseResources, ModLooseObjectProfiles.PROFILE_TYPE.mergeStrategy().orElse(null));
        var typed = LooseObjectProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                resolvedLoose.asMap().get(id("trash_banana_peel"))).getOrThrow();
        assertEquals(.4f, typed.physicalWidth());
        assertEquals(.2d, typed.stepTrigger().intersectionRatio());
        assertEquals(3.5d, typed.stepTrigger().requiredSpeed());
        assertEquals(.5d, typed.slippery().stunSeconds());
        assertEquals(1.5d, typed.slippery().knockdownSeconds());
        assertEquals(1.5d, typed.slippery().launchVelocityMultiplier());
        assertEquals(.5d, typed.slippery().frictionFactor());
        assertEquals(.5d, typed.slippery().frictionDurationSeconds());
        assertEquals(25d, typed.slippery().staminaDamage());
        assertFalse(typed.slippery().affectsSliding());
        assertEquals("moonstation14:ss14/effects/slip", typed.slippery().soundEvent().toString());
        assertEquals(0d, typed.slippery().soundVolumeDb());
        var metadataRef = BotanyCatalogSourceParityTest.read("products/banana-orange-metadata.json").getAsJsonObject();
        assertTrue(metadataRef.get("sourceRevision").getAsString().equals("c9df5ef5d675b0d1d226828bddf6b78c28502d91"));
    }

    @Test void capturedProduceItemDamageMatchesResolvedLooseObjectCandidates() throws Exception {
        var rows = rawRecordMap(capturedRows());
        var resources = new LinkedHashMap<>(BotanyCatalogCandidateTest.mainResources("loose_object_profile", "generic"));
        resources.putAll(BotanyCatalogCandidateTest.resources("loose_object_profile", "blue_pumpkin", "watermelon", "holymelon", "pumpkin", "egg", "bloonion"));
        var candidates = new LinkedHashMap<ResourceLocation, LooseObjectProfile>();
        PrototypeResolver.resolve(resources, ModLooseObjectProfiles.PROFILE_TYPE.mergeStrategy().orElseThrow()).asMap().forEach((key, raw) ->
                candidates.put(key, LooseObjectProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
        var ids = Map.of("FoodBluePumpkin", "blue_pumpkin", "FoodWatermelon", "watermelon",
                "FoodHolymelon", "holymelon", "FoodPumpkin", "pumpkin", "FoodEgg", "egg");
        var failures = new ArrayList<org.junit.jupiter.api.function.Executable>();
        for (var pair : ids.entrySet()) {
            var sourceId = pair.getKey();
            var source = component(rows, sourceId, "Damageable");
            assertNotNull(source, sourceId + " inherited Damageable");
            var actual = candidates.get(id(pair.getValue())).itemDamage();
            assertNotNull(actual, pair.getValue());
            var container = source.get("damageContainer").getAsString();
            assertEquals("Biological", container, sourceId + " source damage container");
            // SS14-dev c9df5ef5.../Resources/Prototypes/Damage/containers.yml:2-8
            // defines Biological as Brute/Burn/Toxin/Airloss/Genetic. Damageable YAML
            // supplies only the container ID, not this group list.
            var groups = switch (container) {
                case "Biological" -> Set.of("brute", "burn", "toxin", "airloss", "genetic");
                default -> throw new AssertionError("unmapped source DamageContainer " + container);
            };
            failures.add(() -> assertEquals(groups, actual.allowedGroups(), sourceId + " resolved source container groups"));
            // BaseItem supplies the defaults when source omits DamageOnHighSpeedImpact;
            // melon children author a lower speed and blunt payload explicitly.
            var impact = component(rows, sourceId, "DamageOnHighSpeedImpact");
            JsonObject expectedImpact = impact == null ? null : impact.getAsJsonObject("damage").getAsJsonObject("types");
            Map<String, Float> impactDamage = expectedImpact == null ? Map.of("blunt", 5f) : damageMap(expectedImpact);
            float minimumSpeed = impact != null && impact.has("minimumSpeed") ? impact.get("minimumSpeed").getAsFloat() : 20f;
            failures.add(() -> assertEquals(impactDamage, actual.highSpeedImpact(), sourceId + " DamageOnHighSpeedImpact"));
            failures.add(() -> assertEquals(minimumSpeed, actual.minimumImpactSpeed(), sourceId + " minimumSpeed"));
            // DamageOnHighSpeedImpact.cs defaults impact factor=.5, cooldown=2s;
            // local loose-object timing is 20 ticks/second.
            failures.add(() -> assertEquals(.5f, actual.impactFactor(), sourceId));
            failures.add(() -> assertEquals(40, actual.impactCooldownTicks(), sourceId));
            var land = component(rows, sourceId, "DamageOnLand");
            Map<String, Float> landDamage = land == null ? Map.of() : damageMap(land.getAsJsonObject("damage").getAsJsonObject("types"));
            failures.add(() -> assertEquals(landDamage, actual.throwLandDamage(), sourceId + " resolved ItemHeftyBase DamageOnLand"));
            var destructible = component(rows, sourceId, "Destructible");
            JsonObject selectedThreshold = null;
            for (var candidate : destructible.getAsJsonArray("thresholds")) {
                var row = candidate.getAsJsonObject();
                if (row.getAsJsonObject("trigger").get("__yaml_tag__").getAsString().endsWith("DamageTrigger")) {
                    selectedThreshold = row; break;
                }
            }
            assertNotNull(selectedThreshold, sourceId + " captured DamageTrigger threshold");
            float threshold = selectedThreshold.getAsJsonObject("trigger").getAsJsonObject("value").get("damage").getAsFloat();
            failures.add(() -> assertEquals(threshold, actual.destructionThreshold(), sourceId + " source DamageTrigger"));
            var behaviors = selectedThreshold.getAsJsonArray("behaviors");
            var supportedBehaviorOrder = new ArrayList<String>();
            String sourceSound = null, spillSolution = null;
            JsonObject sourceSoundSpec = null;
            for (var behaviorElement : behaviors) {
                var behavior = behaviorElement.getAsJsonObject();
                var tag = behavior.get("__yaml_tag__").getAsString();
                var value = behavior.getAsJsonObject("value");
                if (tag.endsWith("PlaySoundBehavior")) {
                    supportedBehaviorOrder.add("sound");
                    sourceSoundSpec = value.getAsJsonObject("sound");
                    sourceSound = sourceSoundSpec.has("collection") ? sourceSoundSpec.get("collection").getAsString() : sourceSoundSpec.get("path").getAsString();
                } else if (tag.endsWith("SpillBehavior")) {
                    supportedBehaviorOrder.add("spill");
                    spillSolution = value.get("solution").getAsString();
                } else if (tag.endsWith("SpawnEntitiesBehavior")) {
                    supportedBehaviorOrder.add("spawn");
                    if (sourceId.equals("FoodEgg")) {
                        var spawn = value.getAsJsonObject("spawn");
                        assertEquals(Set.of("Eggshells"), spawn.keySet());
                        var shellOutput = spawn.getAsJsonObject("Eggshells");
                        assertEquals(1, shellOutput.get("min").getAsInt());
                        assertEquals(1, shellOutput.get("max").getAsInt());
                        assertEquals(List.of(new com.juicyslew.moonstation14.ms14.item_processing.ItemProcessingProfile.ItemOutput(id("eggshells"), 1)), actual.destructionOutputs());
                    }
                } else if (tag.endsWith("DoActsBehavior")) {
                    supportedBehaviorOrder.add("destruction");
                    assertEquals(List.of("Destruction"), stringList(value.getAsJsonArray("acts")), sourceId + " final destruction act");
                }
            }
            if (sourceId.equals("FoodEgg")) assertEquals(List.of("sound", "spill", "spawn", "destruction"), supportedBehaviorOrder, sourceId + " ordered supported destruction effects");
            else assertEquals(List.of("sound", "spill", "destruction"), supportedBehaviorOrder, sourceId + " ordered supported destruction effects");
            assertEquals("food", spillSolution, sourceId + " source SpillBehavior solution");
            failures.add(() -> assertTrue(actual.spillCurrentReagents(), sourceId + " source SpillBehavior"));
            // Adapter mapping: SS14 desecration collection is represented by the local
            // botany/produce/desecration event; other sound collections are unsupported.
            ResourceLocation expectedSound = switch (sourceSound) {
                case "desecration" -> id("botany/produce/desecration");
                default -> throw new AssertionError("unsupported source destruction sound " + sourceSound);
            };
            failures.add(() -> assertEquals(expectedSound, actual.destructionSound(), sourceId + " mapped source sound"));
            // SoundSpecifier.Params defaults to AudioParams.Default (AudioParams.cs:18-20, 103),
            // whose gain is 0 dB and pitch is 1. LooseItemEntity calls the shared dispatch's
            // no-parameter overload; its shared gain/pitch defaults are 1/1. Convert source
            // dB to the typed destruction sound gain; pitch remains fixed at one.
            var sourceParams = sourceSoundSpec.has("params") ? sourceSoundSpec.getAsJsonObject("params") : new JsonObject();
            float volumeDb = sourceParams.has("volume") ? sourceParams.get("volume").getAsFloat() : 0f;
            float gain = (float) Math.pow(10d, volumeDb / 20d);
            failures.add(() -> assertEquals(gain,
                    actual.destructionSoundGain(), sourceId + " AudioParams volume mapped from dB"));
            failures.add(() -> assertEquals(1f,
                    com.juicyslew.moonstation14.ms14.explosion.grenade.network.ControlledSoundDispatch.DEFAULT_PITCH,
                    sourceId + " destruction sound fixed pitch"));
        }
        assertAll("captured source item damage comparisons", failures);
        assertBloonionDamageExplosion(rows, candidates.get(id("bloonion")));
    }

    @Test void nettleMeleeAndSupportedContactFieldsMatchCapturedSource() throws Exception {
        var rows = rawRecordMap(capturedRows());
        var resources = BotanyCatalogCandidateTest.resources("melee_weapon_profile", "nettle", "death_nettle");
        var resolved = new LinkedHashMap<ResourceLocation, MeleeWeaponProfile>();
        var before = new PrototypeManager(); before.register(ModMeleeWeaponProfiles.TYPE);
        var snapshot = before.snapshot();
        PrototypeResolver.resolve(resources, ModMeleeWeaponProfiles.TYPE.mergeStrategy().orElseThrow()).asMap()
                .forEach((key, raw) -> resolved.put(key, MeleeWeaponProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
        assertSame(snapshot, before.snapshot(), "isolated candidate resolution must not publish globally");
        for (var entry : Map.of("Nettle", "nettle", "DeathNettle", "death_nettle").entrySet()) {
            var source = component(rows, entry.getKey(), "MeleeWeapon");
            var actual = resolved.get(id(entry.getValue()));
            assertNotNull(source); assertNotNull(actual);
            var sourceDamage = damageMap(source.getAsJsonObject("damage").getAsJsonObject("types"));
            assertEquals(sourceDamage, actual.weapon().damage(), entry.getKey() + " MeleeWeapon damage");
            assertEquals(1f, actual.weapon().attackRate(), entry.getKey() + " MeleeWeapon omitted attackRate default (SS14 MeleeWeaponComponent)");
            assertEquals(1.5f, actual.weapon().range(), entry.getKey() + " MeleeWeapon omitted range default (SS14 MeleeWeaponComponent)");
            assertFalse(actual.weapon().resistanceBypass()); assertFalse(actual.weapon().autoAttack());
            assertFalse(actual.weapon().mustBeEquippedToUse());
            var injector = component(rows, entry.getKey(), "MeleeChemicalInjector");
            assertNotNull(injector);
            var solution = actual.chemicalInjector().orElseThrow();
            assertEquals(injector.get("solution").getAsString(), solution.solutionName());
            assertEquals(injector.get("transferAmount").getAsInt() * 100, solution.transferCents());
            assertEquals(injector.get("pierceArmor").getAsBoolean(), solution.pierceArmor());
            var onHit = component(rows, entry.getKey(), "DamageOnHit");
            assertEquals(onHit == null ? Map.of() : damageMap(onHit.getAsJsonObject("damage").getAsJsonObject("types")), actual.damageOnHit());
        }
        assertNull(component(rows, "Nettle", "DamageOnHit"));
        assertEquals(Map.of("blunt", 5f), resolved.get(id("death_nettle")).damageOnHit());
        var contact = component(rows, "DeathNettle", "DamageOnInteract");
        assertEquals(Map.of("heat", 4f, "caustic", 4f), damageMap(contact.getAsJsonObject("damage").getAsJsonObject("types")));
        assertEquals(2, contact.get("interactTimer").getAsInt());
        assertTrue(contact.get("throw").getAsBoolean()); assertEquals(3, contact.get("throwSpeed").getAsInt());
        assertFalse(contact.get("ignoreResistances").getAsBoolean());
        assertEquals(.1f, contact.get("stunChance").getAsFloat()); assertEquals(1.5f, contact.get("stunSeconds").getAsFloat());
        assertEquals("powered-light-component-burn-hand", contact.get("popupText").getAsString());
        assertEquals("/Audio/Effects/lightburn.ogg", contact.get("interactSound").getAsString());
        var looseResources = new LinkedHashMap<>(BotanyCatalogCandidateTest.mainResources("loose_object_profile", "generic"));
        looseResources.putAll(BotanyCatalogCandidateTest.resources("loose_object_profile", "nettle", "death_nettle"));
        var looseResolved = PrototypeResolver.resolve(looseResources, ModLooseObjectProfiles.PROFILE_TYPE.mergeStrategy().orElseThrow());
        var deathLoose = LooseObjectProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                looseResolved.asMap().get(id("death_nettle"))).getOrThrow();
        var sourceInorganic = component(rows, "DeathNettle", "Damageable").get("damageContainer").getAsString();
        assertEquals("Inorganic", sourceInorganic);
        var glasstleMetadata = BotanyCatalogSourceParityTest.read("products/glasstle-metadata.json").getAsJsonObject();
        var inorganicGroups = glasstleMetadata.getAsJsonObject("sourceDamageDefaults").getAsJsonArray("containers").get(0).getAsJsonObject().getAsJsonArray("supportedGroups");
        var expectedGroups = new java.util.HashSet<String>(); inorganicGroups.forEach(value -> expectedGroups.add(value.getAsString().toLowerCase(Locale.ROOT)));
        assertEquals(expectedGroups, deathLoose.itemDamage().allowedGroups());
        var destructible = component(rows, "DeathNettle", "Destructible");
        var trigger = destructible.getAsJsonArray("thresholds").get(0).getAsJsonObject();
        assertEquals(25f, trigger.getAsJsonObject("trigger").getAsJsonObject("value").get("damage").getAsFloat());
        assertEquals(25f, deathLoose.itemDamage().destructionThreshold());
        assertEquals(id("ss14/voice/diona/salute"), deathLoose.itemDamage().destructionSound());
        assertEquals(-5f, deathLoose.itemDamage().destructionSoundVolumeDb());
        var behaviorOrder = new ArrayList<String>();
        for (var behavior : trigger.getAsJsonArray("behaviors")) {
            var b = behavior.getAsJsonObject(); var tag = b.get("__yaml_tag__").getAsString();
            if (tag.endsWith("PlaySoundBehavior")) behaviorOrder.add("sound");
            else if (tag.endsWith("DoActsBehavior")) behaviorOrder.add("destruction");
            else fail("unexpected DeathNettle destruction behavior " + tag);
        }
        assertEquals(List.of("sound", "destruction"), behaviorOrder);
        assertFalse(deathLoose.itemDamage().spillCurrentReagents());
        var actualContact = deathLoose.damageOnInteract(); assertNotNull(actualContact);
        assertEquals(damageMap(contact.getAsJsonObject("damage").getAsJsonObject("types")), actualContact.damage());
        assertEquals(contact.get("interactTimer").getAsInt(), actualContact.interactTimerSeconds());
        assertEquals(contact.get("ignoreResistances").getAsBoolean(), actualContact.ignoreResistances());
        assertEquals(contact.get("throw").getAsBoolean(), actualContact.throwSource());
        assertEquals(contact.get("throwSpeed").getAsFloat(), actualContact.throwSpeed());
        assertEquals(contact.get("stunChance").getAsFloat(), actualContact.stunChance());
        assertEquals(contact.get("stunSeconds").getAsFloat(), actualContact.stunSeconds());
        assertEquals(id("ss14/effects/lightburn"), actualContact.soundEvent());
        assertEquals(contact.get("popupText").getAsString(), actualContact.popupText());
    }

    @Test void capturedGlasstleDamageAndDestructionMatchResolvedCandidate() throws Exception {
        var rows = rawRecordMap(capturedRows());
        var resources = new LinkedHashMap<>(BotanyCatalogCandidateTest.mainResources("loose_object_profile", "generic"));
        resources.putAll(BotanyCatalogCandidateTest.resources("loose_object_profile", "glasstle", "shard_glass"));
        var resolved = PrototypeResolver.resolve(resources, ModLooseObjectProfiles.PROFILE_TYPE.mergeStrategy().orElseThrow()).asMap();
        var actual = LooseObjectProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, resolved.get(id("glasstle"))).getOrThrow().itemDamage();
        var source = component(rows, "FoodGlasstle", "Damageable");
        assertNotNull(source);
        assertEquals("Inorganic", source.get("damageContainer").getAsString());
        var sourceMetadata = BotanyCatalogSourceParityTest.read("products/glasstle-metadata.json").getAsJsonObject();
        var inorganic = sourceMetadata.getAsJsonObject("sourceDamageDefaults").getAsJsonArray("containers").get(0).getAsJsonObject();
        assertEquals("Inorganic", inorganic.get("id").getAsString());
        assertEquals(lowerStrings(inorganic.getAsJsonArray("supportedGroups")), actual.allowedGroups());
        assertEquals(lowerStrings(inorganic.getAsJsonArray("supportedTypes")), actual.allowedTypes());
        var impact = component(rows, "FoodGlasstle", "DamageOnHighSpeedImpact");
        var types = impact.getAsJsonObject("damage").getAsJsonObject("types");
        assertEquals(damageMap(types), actual.highSpeedImpact());
        float minimumSpeed = impact.has("minimumSpeed") ? impact.get("minimumSpeed").getAsFloat() : 20f;
        assertEquals(minimumSpeed, actual.minimumImpactSpeed());
        assertEquals(.5f, actual.impactFactor());
        assertEquals(40, actual.impactCooldownTicks());
        assertTrue(actual.throwLandDamage().isEmpty());
        var destructible = component(rows, "FoodGlasstle", "Destructible");
        JsonObject threshold = null;
        for (var row : destructible.getAsJsonArray("thresholds")) {
            var candidate = row.getAsJsonObject();
            if (candidate.getAsJsonObject("trigger").get("__yaml_tag__").getAsString().endsWith("DamageTrigger")) { threshold = candidate; break; }
        }
        assertNotNull(threshold);
        assertEquals(threshold.getAsJsonObject("trigger").getAsJsonObject("value").get("damage").getAsFloat(), actual.destructionThreshold());
        var behaviors = threshold.getAsJsonArray("behaviors");
        var order = new ArrayList<String>();
        for (var entry : behaviors) {
            var behavior = entry.getAsJsonObject();
            var tag = behavior.get("__yaml_tag__").getAsString();
            var value = behavior.getAsJsonObject("value");
            if (tag.endsWith("PlaySoundBehavior")) {
                order.add("sound");
                var sound = value.getAsJsonObject("sound");
                var sourceSound = sound.has("collection") ? sound.get("collection").getAsString() : sound.get("path").getAsString();
                assertEquals("GlassBreak", sourceSound);
                assertEquals(sound.getAsJsonObject("params").get("volume").getAsFloat(), actual.destructionSoundVolumeDb());
                // Upstream GlassBreak sound collection maps to the registered local SS14 glass-break event.
                assertEquals(id("ss14/effects/glass_break"), actual.destructionSound());
            } else if (tag.endsWith("SpawnEntitiesBehavior")) {
                order.add("spawn");
                var spawn = value.getAsJsonObject("spawn").getAsJsonObject("ShardGlass");
                assertEquals(1, spawn.get("min").getAsInt()); assertEquals(1, spawn.get("max").getAsInt());
                assertEquals(List.of(new com.juicyslew.moonstation14.ms14.item_processing.ItemProcessingProfile.ItemOutput(id("shard_glass"), 1)), actual.destructionOutputs());
            } else if (tag.endsWith("DoActsBehavior")) {
                order.add("destruction");
                assertEquals(List.of("Destruction"), stringList(value.getAsJsonArray("acts")));
            } else fail("unsupported captured glasstle destruction behavior " + tag);
        }
        assertEquals(List.of("sound", "spawn", "destruction"), order);
        assertFalse(actual.spillCurrentReagents());
    }

    @Test void capturedShardGlassStepSlipAndDamageMatchResolvedCandidate() throws Exception {
        var rows = rawRecordMap(capturedRows());
        var metadata = BotanyCatalogSourceParityTest.read("products/glasstle-metadata.json").getAsJsonObject();
        assertEquals(90, rows.size());
        assertEquals("ShardBase", metadata.getAsJsonObject("parentResolution").get("ShardGlass").getAsString());
        assertEquals("BaseItem", metadata.getAsJsonObject("parentResolution").get("ShardBase").getAsString());
        var resources = new LinkedHashMap<>(BotanyCatalogCandidateTest.mainResources("loose_object_profile", "generic"));
        resources.putAll(BotanyCatalogCandidateTest.resources("loose_object_profile", "shard_glass"));
        var resolved = PrototypeResolver.resolve(resources, ModLooseObjectProfiles.PROFILE_TYPE.mergeStrategy().orElseThrow());
        var actual = LooseObjectProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                resolved.asMap().get(id("shard_glass"))).getOrThrow();
        var trigger = component(rows, "ShardGlass", "StepTrigger");
        assertEquals(.2, trigger.get("intersectRatio").getAsDouble());
        assertTrue(actual.stepTrigger().active());
        assertEquals(.2, actual.stepTrigger().intersectionRatio());
        assertEquals(3.5, actual.stepTrigger().requiredSpeed());
        assertFalse(actual.stepTrigger().ignoreWeightless());
        assertTrue(actual.preventableStepTrigger());
        assertNotNull(component(rows, "ShardGlass", "TriggerOnStepTrigger"));
        assertNotNull(component(rows, "ShardGlass", "DeleteOnTrigger"));
        var sourceDamage = component(rows, "ShardGlass", "DamageOnTrigger");
        assertTrue(sourceDamage.get("targetUser").getAsBoolean());
        assertEquals(Map.of("piercing", 4.5f), actual.stepDamage().damage());
        assertTrue(actual.stepDamage().deleteSource());
        assertNull(component(rows, "ShardGlass", "DamageOnStepTrigger"));

        var sourceSlip = component(rows, "ShardGlass", "Slippery");
        assertEquals(0d, sourceSlip.getAsJsonObject("slipData").get("launchForwardsMultiplier").getAsDouble());
        assertEquals("/Audio/Effects/glass_step.ogg", sourceSlip.getAsJsonObject("slipSound").get("path").getAsString());
        assertEquals(0d, actual.slippery().launchVelocityMultiplier());
        assertEquals(id("ss14/effects/glass_step"), actual.slippery().soundEvent());
        assertEquals(.5d, actual.slippery().stunSeconds());
        assertEquals(1.5d, actual.slippery().knockdownSeconds());
        assertEquals(.5d, actual.slippery().frictionFactor());
        assertEquals(.5d, actual.slippery().frictionDurationSeconds());
        assertEquals(25d, actual.slippery().staminaDamage());
        assertFalse(actual.slippery().affectsSliding());
        assertNull(component(rows, "ShardGlass", "StepOnTrigger"), "no source StepOn consumer is introduced");

        var damageable = component(rows, "ShardGlass", "Damageable");
        assertEquals("Inorganic", damageable.get("damageContainer").getAsString());
        assertEquals("Glass", damageable.get("damageModifierSet").getAsString());
        var sourceDamageMetadata = metadata.getAsJsonObject("sourceDamageDefaults");
        var inorganic = sourceDamageMetadata.getAsJsonArray("containers").get(0).getAsJsonObject();
        assertEquals(lowerStrings(inorganic.getAsJsonArray("supportedGroups")), actual.itemDamage().allowedGroups());
        assertEquals(lowerStrings(inorganic.getAsJsonArray("supportedTypes")), actual.itemDamage().allowedTypes());
        var glass = sourceDamageMetadata.getAsJsonArray("modifierSets").get(0).getAsJsonObject();
        var coeffs = damageMap(glass.getAsJsonObject("coefficients"));
        var flats = damageMap(glass.getAsJsonObject("flatReductions"));
        assertEquals(Map.of("blunt",1.2f,"slash",.5f,"piercing",1f,"heat",.8f,"shock",0f), coeffs);
        assertEquals(Map.of("slash",5f,"piercing",5f,"heat",5f), actual.itemDamage().damageModifiers().flatReductions());
        assertEquals(5f, flats.get("structural"), "full source metadata retains unsupported Structural reduction");
        assertFalse(actual.itemDamage().damageModifiers().flatReductions().containsKey("structural"),
                "local damage policy explicitly filters unsupported Structural damage key");
        assertEquals(coeffs, actual.itemDamage().damageModifiers().coefficients());
        var impact = component(rows, "ShardGlass", "DamageOnHighSpeedImpact");
        assertEquals(Map.of("blunt",5f), actual.itemDamage().highSpeedImpact());
        assertEquals(20f, actual.itemDamage().minimumImpactSpeed());
        assertTrue(actual.itemDamage().throwLandDamage().isEmpty());
        var destructible = component(rows, "ShardGlass", "Destructible");
        var threshold = destructible.getAsJsonArray("thresholds").get(0).getAsJsonObject();
        assertEquals(100f, threshold.getAsJsonObject("trigger").getAsJsonObject("value").get("damage").getAsFloat());
        assertEquals(100f, actual.itemDamage().destructionThreshold());
        assertTrue(actual.itemDamage().destructionOutputs().isEmpty());
        assertNull(actual.itemDamage().destructionSound());
    }

    private static Set<String> lowerStrings(JsonArray values) {
        var result = new java.util.LinkedHashSet<String>();
        for (var value : values) result.add(value.getAsString().toLowerCase(Locale.ROOT));
        return Set.copyOf(result);
    }

    private static void assertBloonionDamageExplosion(Map<String, JsonObject> rows, LooseObjectProfile actual) {
        assertNotNull(actual, "resolved Bloonion loose-object candidate");
        assertEquals("BaseStructureDynamic", rows.get("FoodBloonion").get("parent").getAsString());
        assertEquals("BaseStructure", rows.get("BaseStructureDynamic").get("parent").getAsString());
        assertNull(component(rows, "FoodBloonion", "DamageOnHighSpeedImpact"));
        assertNull(component(rows, "FoodBloonion", "DamageOnLand"));
        var damage = actual.itemDamage(); assertNotNull(damage);
        assertTrue(damage.highSpeedImpact().isEmpty());
        assertTrue(damage.throwLandDamage().isEmpty());
        assertEquals(Set.of("brute", "burn", "toxin", "airloss", "genetic"), damage.allowedGroups());
        var sourceDestructible = component(rows, "FoodBloonion", "Destructible");
        var threshold = sourceDestructible.getAsJsonArray("thresholds").get(0).getAsJsonObject();
        assertEquals(1f, threshold.getAsJsonObject("trigger").getAsJsonObject("value").get("damage").getAsFloat());
        var tags = new ArrayList<String>();
        for (var item : threshold.getAsJsonArray("behaviors")) tags.add(item.getAsJsonObject().get("__yaml_tag__").getAsString());
        assertEquals(List.of("!type:SpillBehavior", "!type:TriggerBehavior", "!type:DoActsBehavior"), tags);
        assertEquals("food", threshold.getAsJsonArray("behaviors").get(0).getAsJsonObject().getAsJsonObject("value").get("solution").getAsString());
        assertEquals(List.of("Destruction"), stringList(threshold.getAsJsonArray("behaviors").get(2).getAsJsonObject().getAsJsonObject("value").getAsJsonArray("acts")));
        assertNotNull(component(rows, "FoodBloonion", "ExplodeOnTrigger"));
        var explosive = component(rows, "FoodBloonion", "Explosive");
        var proposal = damage.destructionExplosion(); assertNotNull(proposal);
        assertEquals(id("default"), proposal.explosionType());
        assertEquals(.1d, proposal.totalIntensity()); assertEquals(1d, proposal.intensitySlope()); assertEquals(.001d, proposal.maxIntensity());
        assertTrue(damage.spillCurrentReagents()); assertNull(damage.destructionSound()); assertTrue(damage.destructionOutputs().isEmpty());
        assertEquals("Default", explosive.get("explosionType").getAsString());
        var fixture = component(rows, "FoodBloonion", "Fixtures").getAsJsonObject("fixtures").getAsJsonObject("fix1");
        var shape = fixture.getAsJsonObject("shape").getAsJsonObject("value");
        assertEquals(.2f, shape.get("radius").getAsFloat()); assertEquals("0, 0.35", shape.get("position").getAsString());
        assertEquals(80, fixture.get("density").getAsInt());
        assertEquals(.4f, actual.physicalWidth(), "supported circle diameter mapping only");
    }

    private static List<String> stringList(JsonArray array) {
        var values = new ArrayList<String>(); array.forEach(value -> values.add(value.getAsString())); return values;
    }

    @Test void rawComponentCompositionRecursivelyMergesMappingsAndReplacesSequences() {
        var parent = JsonParser.parseString("{\"solutions\":{\"food\":{\"maxVol\":20,\"reagents\":[{\"ReagentId\":\"Nut\",\"Quantity\":5}]}}}").getAsJsonObject();
        var child = JsonParser.parseString("{\"solutions\":{\"food\":{\"reagents\":[{\"ReagentId\":\"Protein\",\"Quantity\":1}],\"quantity\":0.6}}}").getAsJsonObject();
        var merged = overlay(parent, child);
        var food = merged.getAsJsonObject("solutions").getAsJsonObject("food");
        assertEquals(20, food.get("maxVol").getAsInt());
        assertEquals("Protein", food.getAsJsonArray("reagents").get(0).getAsJsonObject().get("ReagentId").getAsString());
        assertEquals(1, food.getAsJsonArray("reagents").size());
        assertEquals(0.6, food.get("quantity").getAsDouble());
    }

    private static String produceTargetSolution(JsonObject rawComponent, String defaultValue) {
        return rawComponent != null && rawComponent.has("targetSolution") && !rawComponent.get("targetSolution").isJsonNull()
                ? rawComponent.get("targetSolution").getAsString() : defaultValue;
    }
    private static long capacityCents(JsonElement rawMaxVolume, long reagentSumCents) {
        var volume = new BigDecimal(rawMaxVolume.getAsString());
        if (volume.signum() == 0) return reagentSumCents;
        if (volume.signum() < 0) throw new AssertionError("negative maxVol is unsupported");
        return volume.multiply(BigDecimal.valueOf(100)).longValueExact();
    }
    private static int secondsToTicks(JsonElement seconds) {
        return new BigDecimal(seconds.getAsString()).multiply(BigDecimal.valueOf(20)).intValueExact();
    }
    private static int edibleDose(JsonObject sourceEdible, JsonObject edibleDefaults) {
        if (sourceEdible != null && sourceEdible.has("transferAmount")) {
            if (sourceEdible.get("transferAmount").isJsonNull()) throw new AssertionError("NULL transferAmount represents unsupported whole-solution transfer");
            return new BigDecimal(sourceEdible.get("transferAmount").getAsString()).multiply(BigDecimal.valueOf(100)).intValueExact();
        }
        return edibleDefaults.get("transferAmountDefaultUnits").getAsInt() * 100;
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }
    private static Map<String, Float> damageMap(JsonObject types) {
        var values = new LinkedHashMap<String, Float>();
        types.entrySet().forEach(entry -> values.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue().getAsFloat()));
        return Map.copyOf(values);
    }
    private static Map<ResourceLocation,Long> reagents(JsonArray array) {
        var out = new LinkedHashMap<ResourceLocation,Long>(); if (array == null) return out;
        for (JsonElement e : array) { var row=e.getAsJsonObject(); String reagent=row.get("ReagentId").getAsString().toLowerCase(Locale.ROOT); if (reagent.equals("pulpedbananapeel")) reagent="pulped_banana_peel"; long cents=new BigDecimal(row.get("Quantity").getAsString()).multiply(BigDecimal.valueOf(100)).longValueExact(); out.merge(id(reagent), cents, Long::sum); }
        return Map.copyOf(out);
    }
    private static Map<net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData>,Long> juiceReagents(JsonArray array) {
        var out = new LinkedHashMap<net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData>,Long>();
        if (array != null) for (var entry : reagents(array).entrySet()) out.put(ModReagents.createKey(entry.getKey().getPath()), entry.getValue());
        return Map.copyOf(out);
    }
    private static JsonObject component(Map<String,JsonObject> rows, String id, String type) {
        JsonObject record=rows.get(id); assertNotNull(record, "missing captured row " + id);
        JsonObject result=null;
        if (record.has("parent") && !record.get("parent").isJsonNull()) {
            var parents = record.get("parent");
            if (parents.isJsonArray()) {
                for (var parentElement : parents.getAsJsonArray()) {
                    String parent = parentElement.getAsString(); assertTrue(rows.containsKey(parent), "unsupported captured parent " + parent);
                    result = overlay(result, component(rows, parent, type));
                }
            } else {
                assertTrue(parents.isJsonPrimitive() && parents.getAsJsonPrimitive().isString(), "unsupported captured parent shape for " + id);
                String parent=parents.getAsString(); assertTrue(rows.containsKey(parent), "unsupported captured parent " + parent);
                result=component(rows,parent,type);
            }
        }
        JsonArray components=record.getAsJsonArray("components"); assertNotNull(components, "missing components " + id);
        for(JsonElement e:components) { assertTrue(e.isJsonObject(), "component is not object in " + id); JsonObject c=e.getAsJsonObject(); if(c.has("type") && type.equals(c.get("type").getAsString())) result=overlay(result,c); }
        return result != null && result.size() == 0 ? null : result;
    }
    private static JsonObject overlay(JsonObject parent, JsonObject child) {
        if (child == null) return parent == null ? new JsonObject() : parent.deepCopy();
        var result=parent == null ? new JsonObject() : parent.deepCopy();
        for(var e:child.entrySet()) {
            var old = result.get(e.getKey()); var value = e.getValue();
            result.add(e.getKey(), old != null && old.isJsonObject() && value.isJsonObject()
                    ? overlay(old.getAsJsonObject(), value.getAsJsonObject()) : value.deepCopy());
        }
        return result;
    }

    private static JsonArray capturedRows() throws Exception {
        var rows = new JsonArray();
        for (var file : List.of("products/entity-rows.json", "products/additions-entity-rows.json", "products/conversion-entity-rows.json", "products/fruit-pit-entity-rows.json", "products/blue-pumpkin-entity-rows.json", "products/banana-orange-entity-rows.json"))
            BotanyCatalogSourceParityTest.read(file).getAsJsonArray().forEach(row -> rows.add(row.deepCopy()));
        BotanyCatalogSourceParityTest.read("products/bloonion-entity-rows.json").getAsJsonArray().forEach(row -> {
            var record = row.getAsJsonObject(); var wrapped = new JsonObject();
            wrapped.add("id", record.get("id").deepCopy());
            wrapped.addProperty("sourcePath", "Resources/Prototypes/Entities/Objects/Consumable/Food/produce.yml");
            wrapped.add("record", record.deepCopy()); rows.add(wrapped);
        });
        BotanyCatalogSourceParityTest.read("products/ordinary-medicinal-entity-rows.json").getAsJsonArray().forEach(row -> rows.add(row.deepCopy()));
        BotanyCatalogSourceParityTest.read("products/melon-pumpkin-entity-rows.json").getAsJsonArray().forEach(row -> rows.add(row.deepCopy()));
        BotanyCatalogSourceParityTest.read("products/egg-entity-rows.json").getAsJsonArray().forEach(row -> {
            var record = row.getAsJsonObject();
            var wrapped = new JsonObject();
            wrapped.add("id", record.get("id").deepCopy());
            wrapped.addProperty("sourcePath", "Resources/Prototypes/Entities/Objects/Consumable/Food/egg.yml");
            wrapped.add("record", record.deepCopy());
            rows.add(wrapped);
        });
        BotanyCatalogSourceParityTest.read("products/glasstle-entity-rows.json").getAsJsonArray().forEach(row -> {
            var record = row.getAsJsonObject(); var wrapped = new JsonObject();
            wrapped.add("id", record.get("id").deepCopy());
            wrapped.addProperty("sourcePath", record.get("id").getAsString().equals("FoodGlasstle")
                    ? "Resources/Prototypes/Entities/Objects/Consumable/Food/produce.yml"
                    : "Resources/Prototypes/Entities/Objects/Materials/shards.yml");
            wrapped.add("record", record.deepCopy()); rows.add(wrapped);
        });
        BotanyCatalogSourceParityTest.read("products/nettle-entity-rows.json").getAsJsonArray().forEach(row -> rows.add(row.deepCopy()));
        assertEquals(90, rows.size(), "raw source rows including verified nettle parent closure");
        rawRecordMap(rows); // Reject duplicate IDs before any map insertion can hide them.
        return rows;
    }

    private static LinkedHashMap<String, JsonObject> rawRecordMap(JsonArray rows) {
        var rawRows = new LinkedHashMap<String, JsonObject>();
        for (var element : rows) {
            var wrapper = element.getAsJsonObject(); var key = wrapper.get("id").getAsString();
            assertFalse(rawRows.containsKey(key), "duplicate captured source id " + key);
            rawRows.put(key, wrapper.getAsJsonObject("record"));
        }
        return rawRows;
    }
}
