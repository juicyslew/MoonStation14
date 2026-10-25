package com.juicyslew.moonstation14.ms14.item_processing;

import com.juicyslew.moonstation14.ms14.botany.ModProduceProfiles;
import com.juicyslew.moonstation14.ms14.botany.ProduceProfile;
import com.juicyslew.moonstation14.ms14.ingestion.IngestionProfile;
import com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles;
import com.juicyslew.moonstation14.ms14.prototype.BotanyCatalogCandidateTest;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutionProfile;
import com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutions;
import com.juicyslew.moonstation14.ms14.reagent.ModItemFoodSolutionProfiles;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.item.custom.PrototypeEdibleItem;
import com.juicyslew.moonstation14.ms14.loose_objects.ModLooseObjectProfiles;
import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static org.junit.jupiter.api.Assertions.*;

class FixedCropOutputCompositionTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }

    @Test void ordinaryFoodProfilesInheritTheResolvedForceFeedDefault() {
        var manager = new PrototypeManager();
        manager.register(ModIngestionProfiles.TYPE);
        manager.register(ModItemFoodSolutionProfiles.TYPE);
        var sources = BotanyCatalogCandidateTest.mainResources("ingestion_profile", "edible_defaults", "food_base",
                "wheat_bushel", "tomato", "potato", "poppy", "aloe", "food_meat_wheat", "food_cocoa_beans");
        var before = manager.snapshot();
        var resolved = new LinkedHashMap<ResourceLocation, IngestionProfile>();
        manager.stage(Map.of(ModIngestionProfiles.TYPE, sources), catalogs -> catalogs
                .get(ModIngestionProfiles.TYPE.typeId()).forEach((key, value) ->
                        resolved.put(key, IngestionProfile.CODEC.parse(JsonOps.INSTANCE, value).getOrThrow())));
        assertSame(before, manager.snapshot());
        for (String name : new String[]{"wheat_bushel", "tomato", "potato", "poppy", "aloe",
                "food_meat_wheat", "food_cocoa_beans"}) {
            assertEquals(60, resolved.get(id(name)).edible().forceFeedDelayTicks(), name);
        }
    }

    @Test void fixedFoodOutputsResolveTheirIndependentCompositionsAndInheritedDefaults() {
        var manager = new PrototypeManager();
        manager.register(ModIngestionProfiles.TYPE);
        manager.register(ModItemFoodSolutionProfiles.TYPE);
        manager.register(ModExtractableProfiles.TYPE);
        manager.register(ModProduceProfiles.TYPE);
        Map<PrototypeType<?>, Map<ResourceLocation, JsonObject>> sources = new LinkedHashMap<>();
        sources.put(
                ModIngestionProfiles.TYPE, BotanyCatalogCandidateTest.mainResources("ingestion_profile",
                        "edible_defaults", "food_base", "food_meat_wheat", "food_cocoa_beans"));
        sources.put(ModItemFoodSolutionProfiles.TYPE, BotanyCatalogCandidateTest.mainResources("item_food_solution_profile",
                "food_meat_wheat", "food_cocoa_beans"));
        sources.put(ModExtractableProfiles.TYPE, BotanyCatalogCandidateTest.mainResources("extractable_profile",
                "extractable_base", "food_meat_wheat", "food_cocoa_beans"));
        sources.put(ModProduceProfiles.TYPE, BotanyCatalogCandidateTest.mainResources("produce_profile",
                "produce_base", "food_cocoa_beans"));
        var before = manager.snapshot();
        var ingestion = new LinkedHashMap<ResourceLocation, IngestionProfile>();
        var extraction = new LinkedHashMap<ResourceLocation, ExtractableProfile>();
        var produce = new LinkedHashMap<ResourceLocation, ProduceProfile>();
        var foodSolutions = new LinkedHashMap<ResourceLocation, ItemFoodSolutionProfile>();
        manager.stage(sources, catalogs -> {
            catalogs.get(ModIngestionProfiles.TYPE.typeId()).forEach((key, value) ->
                    ingestion.put(key, IngestionProfile.CODEC.parse(JsonOps.INSTANCE, value).getOrThrow()));
            catalogs.get(ModExtractableProfiles.TYPE.typeId()).forEach((key, value) ->
                    extraction.put(key, ExtractableProfile.CODEC.parse(JsonOps.INSTANCE, value).getOrThrow()));
            catalogs.get(ModProduceProfiles.TYPE.typeId()).forEach((key, value) ->
                    produce.put(key, ProduceProfile.CODEC.parse(JsonOps.INSTANCE, value).getOrThrow()));
            catalogs.get(ModItemFoodSolutionProfiles.TYPE.typeId()).forEach((key, value) ->
                    foodSolutions.put(key, ItemFoodSolutionProfile.CODEC.parse(JsonOps.INSTANCE, value).getOrThrow()));
        });
        assertSame(before, manager.snapshot());
        assertEquals(2, ingestion.size());
        assertEquals(2, extraction.size());
        assertEquals(1, produce.size());
        assertFalse(ingestion.containsKey(id("edible_defaults")));
        assertFalse(extraction.containsKey(id("extractable_base")));
        assertFalse(produce.containsKey(id("produce_base")));

        var meat = ingestion.get(id("food_meat_wheat")).edible();
        assertEquals(IngestionProfile.Kind.FOOD, meat.kind());
        assertEquals(500, meat.doseCents());
        assertEquals(20, meat.delayTicks());
        assertEquals(60, meat.forceFeedDelayTicks());
        assertTrue(meat.destroyOnEmpty());
        assertFalse(meat.requiresSpecialDigestion());
        // Source-derived refillable food solutions intentionally retain their reaction capability.
        assertEquals(new ItemFoodSolutionProfile(2000,
                Map.of(id("uncookedanimalproteins"), 100L), true, "food", true), foodSolutions.get(id("food_meat_wheat")));
        assertTrue(meat.trash().isEmpty());

        var cocoa = ingestion.get(id("food_cocoa_beans")).edible();
        assertEquals(IngestionProfile.Kind.FOOD, cocoa.kind());
        assertEquals(500, cocoa.doseCents());
        assertEquals(20, cocoa.delayTicks());
        assertEquals(60, cocoa.forceFeedDelayTicks());
        assertTrue(cocoa.destroyOnEmpty());
        assertFalse(cocoa.requiresSpecialDigestion());
        assertEquals(new ItemFoodSolutionProfile(1400, Map.of(
                id("nutriment"), 200L, id("vitamin"), 100L,
                id("cocoapowder"), 200L), true, "food", true), foodSolutions.get(id("food_cocoa_beans")));
        assertTrue(cocoa.trash().isEmpty());

        assertEquals("food", extraction.get(id("food_meat_wheat")).grindableSolutionName());
        assertNull(extraction.get(id("food_meat_wheat")).juiceSolution());
        assertEquals("food", extraction.get(id("food_cocoa_beans")).grindableSolutionName());
        assertEquals(Map.of(ModReagents.createKey("cocoapowder"), 200L),
                extraction.get(id("food_cocoa_beans")).juiceSolution().centContents());
        assertNull(produce.get(id("food_cocoa_beans")).seed());
        assertNull(produce.get(id("food_cocoa_beans")).seed());
        assertEquals(.5f, produce.get(id("food_cocoa_beans")).potencyVisuals().minimumScale());
        assertEquals(4.5f, produce.get(id("food_cocoa_beans")).potencyVisuals().maximumScale());
    }

    @Test void canonicalReagentResourcesExistWithoutPhantomAliases() {
        assertEquals("uncookedanimalproteins", BotanyCatalogCandidateTest.mainResources("reagent", "uncookedanimalproteins").get(id("uncookedanimalproteins")).get("id").getAsString());
        assertEquals("cocoapowder", BotanyCatalogCandidateTest.mainResources("reagent", "cocoapowder").get(id("cocoapowder")).get("id").getAsString());
        assertNull(getClass().getClassLoader().getResource("data/moonstation14/moonstation14/reagent/uncooked_animal_proteins.json"));
        assertNull(getClass().getClassLoader().getResource("data/moonstation14/moonstation14/reagent/cocoa_powder.json"));
    }

    @Test void fixedFoodItemsMaterializeOnlyTheirResolvedReagentSourceOnce() {
        var profiles = stagedOutputProfiles();
        verifyStack(ModItems.FOOD_MEAT_WHEAT.get(), id("food_meat_wheat"), profiles.foodSolutions().get(id("food_meat_wheat")), Map.of("uncookedanimalproteins", 100L));
        verifyStack(ModItems.FOOD_COCOA_BEANS.get(), id("food_cocoa_beans"), profiles.foodSolutions().get(id("food_cocoa_beans")), Map.of("nutriment", 200L, "vitamin", 100L, "cocoapowder", 200L));
    }

    @Test void fixedFoodLooseObjectProfilesResolveThroughTheirGenericParent() {
        var manager = new PrototypeManager();
        manager.register(ModLooseObjectProfiles.PROFILE_TYPE);
        var raw = BotanyCatalogCandidateTest.mainResources("loose_object_profile", "generic", "food_meat_wheat", "food_cocoa_beans");
        assertEquals("moonstation14:generic", raw.get(id("food_meat_wheat")).get("parent").getAsString());
        assertEquals("moonstation14:generic", raw.get(id("food_cocoa_beans")).get("parent").getAsString());
        var resolved = new LinkedHashMap<ResourceLocation, com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile>();
        manager.stage(Map.of(ModLooseObjectProfiles.PROFILE_TYPE, raw), catalogs -> catalogs.get(ModLooseObjectProfiles.PROFILE_TYPE.typeId()).forEach((key, value) -> resolved.put(key, com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile.CODEC.parse(JsonOps.INSTANCE, value).getOrThrow())));
        assertNotNull(resolved.get(id("food_meat_wheat")));
        assertNotNull(resolved.get(id("food_cocoa_beans")));
        assertEquals(id("food_meat_wheat"), new net.minecraft.world.item.ItemStack(ModItems.FOOD_MEAT_WHEAT.get()).get(ModDataComponents.LOOSE_OBJECT_PROFILE.get()));
        assertEquals(id("food_cocoa_beans"), new net.minecraft.world.item.ItemStack(ModItems.FOOD_COCOA_BEANS.get()).get(ModDataComponents.LOOSE_OBJECT_PROFILE.get()));
    }

    @Test void fixedFoodAssetsBindModelsAndRetainPinnedAttribution() throws Exception {
        assertAsset("food_meat_wheat", "d897a7b0a93e77866e3f9441749f0695425ab0f46f01c273d12ea78c3a086e27", "clump", "tgstation");
        assertAsset("food_cocoa_beans", "a2221792f20f217dbbfd429a0e3eb17aa5c1f81915260dbf0286c34c0e3d5cf9", "cocoa", "FillerVK");
    }

    @Test void produceAudioAssetsMatchRegisteredEventsAndPackagedSamples() throws Exception {
        var sounds = JsonParser.parseString(new String(resource("assets/moonstation14/sounds.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        assertAudioAsset(sounds, "botany/produce/slime_squish", "slime_squish", "b596151e4cde4bb66c260c30374f18eb59c79ff06766c20eb152ac3deda70125", com.juicyslew.moonstation14.sounds.ModSounds.BOTANY_PRODUCE_SLIME_SQUISH.get());
        assertAudioAsset(sounds, "botany/produce/packetrip", "packetrip", "9c3be8c9e7b0ae36db086130c05f76ed6a9f04c9fce55afc4327390aa406d2c0", com.juicyslew.moonstation14.sounds.ModSounds.BOTANY_PRODUCE_PACKETRIP.get());
    }

    @Test void slipAudioMatchesRegisteredEventAndPackagedProvenance() throws Exception {
        var sounds = JsonParser.parseString(new String(resource("assets/moonstation14/sounds.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        String key = "ss14/effects/slip";
        assertEquals(id(key), com.juicyslew.moonstation14.sounds.ModSounds.SLIP.get().getLocation());
        assertEquals("moonstation14:" + key, sounds.getAsJsonObject(key).getAsJsonArray("sounds").get(0).getAsString());
        String asset = "assets/moonstation14/sounds/ss14/effects/slip.ogg";
        byte[] bytes = resource(asset);
        assertEquals(4306, bytes.length);
        assertEquals("780f8a16ffc94dd8a75cc3be491ad642755b6ee458179e6d30de52c77ec19bbb",
                java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        String notice = new String(resource(asset + ".license.txt"), StandardCharsets.UTF_8);
        assertTrue(notice.contains("Resources/Audio/Effects/slip.ogg"));
        assertTrue(notice.contains("c9df5ef5d675b0d1d226828bddf6b78c28502d91"));
        assertTrue(notice.contains("UNKNOWN / UNRESOLVED"));
    }

    private static void assertAudioAsset(JsonObject sounds, String eventKey, String file, String hash, net.minecraft.sounds.SoundEvent event) throws Exception {
        assertEquals(id(eventKey), event.getLocation());
        assertEquals("moonstation14:" + eventKey, sounds.getAsJsonObject(eventKey).getAsJsonArray("sounds").get(0).getAsString());
        String asset = "assets/moonstation14/sounds/botany/produce/" + file + ".ogg";
        assertEquals(hash, java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(resource(asset))));
        assertNotNull(FixedCropOutputCompositionTest.class.getClassLoader().getResource(asset + ".license.txt"));
    }

    private static void verifyStack(net.minecraft.world.item.Item item, ResourceLocation profile, ItemFoodSolutionProfile source, Map<String, Long> expected) {
        var stack = new net.minecraft.world.item.ItemStack(item);
        assertEquals(1, stack.getMaxStackSize());
        assertEquals(profile, stack.get(ModDataComponents.LOOSE_OBJECT_PROFILE.get()));
        assertNull(stack.get(ModDataComponents.PLANT_SEED_REFERENCE.get()));
        assertNull(stack.get(ModDataComponents.GENETIC_VALUE.get()));
        assertNull(stack.get(ModDataComponents.PRODUCE_INSTANCE.get()));
        ItemFoodSolutions.initialize(stack, source);
        var reagent = stack.get(ModDataComponents.REAGENT.get());
        var actual = new LinkedHashMap<String, Long>();
        reagent.centContents().forEach((key, value) -> actual.put(key.location().getPath(), value));
        assertEquals(expected, actual);
        stack.set(ModDataComponents.REAGENT.get(), new com.juicyslew.moonstation14.ms14.reagent.ReagentComponent());
        ItemFoodSolutions.initialize(stack, source);
        assertTrue(stack.get(ModDataComponents.REAGENT.get()).centContents().isEmpty());
        assertNull(stack.get(ModDataComponents.GENETIC_VALUE.get()));
        assertNull(stack.get(ModDataComponents.PLANT_SEED_REFERENCE.get()));
    }

    private static StagedOutputProfiles stagedOutputProfiles() {
        var manager = new PrototypeManager();
        manager.register(ModIngestionProfiles.TYPE);
        manager.register(ModItemFoodSolutionProfiles.TYPE);
        var sources = BotanyCatalogCandidateTest.mainResources("ingestion_profile", "edible_defaults", "food_base", "food_meat_wheat", "food_cocoa_beans");
        var ingestion = new LinkedHashMap<ResourceLocation, IngestionProfile>();
        var solutionSources = BotanyCatalogCandidateTest.mainResources("item_food_solution_profile", "food_meat_wheat", "food_cocoa_beans");
        var foodSolutions = new LinkedHashMap<ResourceLocation, ItemFoodSolutionProfile>();
        manager.stage(Map.of(ModIngestionProfiles.TYPE, sources, ModItemFoodSolutionProfiles.TYPE, solutionSources), catalogs -> {
            catalogs.get(ModIngestionProfiles.TYPE.typeId()).forEach((key, value) -> ingestion.put(key, IngestionProfile.CODEC.parse(JsonOps.INSTANCE, value).getOrThrow()));
            catalogs.get(ModItemFoodSolutionProfiles.TYPE.typeId()).forEach((key, value) -> foodSolutions.put(key, ItemFoodSolutionProfile.CODEC.parse(JsonOps.INSTANCE, value).getOrThrow()));
        });
        return new StagedOutputProfiles(ingestion, foodSolutions);
    }
    private record StagedOutputProfiles(Map<ResourceLocation, IngestionProfile> ingestion, Map<ResourceLocation, ItemFoodSolutionProfile> foodSolutions) { }

    private static void assertAsset(String name, String hash, String state, String author) throws Exception {
        String root = "assets/moonstation14/";
        var model = JsonParser.parseString(new String(resource(root + "models/item/" + name + ".json"), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("minecraft:item/generated", model.get("parent").getAsString());
        assertEquals("moonstation14:item/" + name, model.getAsJsonObject("textures").get("layer0").getAsString());
        assertEquals(hash, java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(resource(root + "textures/item/" + name + ".png"))));
        String notice = new String(resource(root + "textures/item/" + name + ".png.attribution.txt"), StandardCharsets.UTF_8);
        assertTrue(notice.contains("c9df5ef5d675b0d1d226828bddf6b78c28502d91"));
        assertTrue(notice.contains("CC-BY-SA-3.0"));
        assertTrue(notice.contains(state));
        var metadata = JsonParser.parseString(new String(resource(root + "attributions/" + name + "-source-meta.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("CC-BY-SA-3.0", metadata.get("license").getAsString());
        assertTrue(metadata.get("copyright").getAsString().contains(author));
    }
    private static byte[] resource(String path) {
        try (var stream = FixedCropOutputCompositionTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return stream.readAllBytes();
        } catch (java.io.IOException exception) { throw new AssertionError(exception); }
    }
}
