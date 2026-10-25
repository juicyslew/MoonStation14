package com.juicyslew.moonstation14.ms14.prototype;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.botany.ModSeedProfiles;
import com.juicyslew.moonstation14.ms14.botany.SeedProfile;
import com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutionProfile;
import com.juicyslew.moonstation14.ms14.reagent.ModItemFoodSolutionProfiles;
import org.junit.jupiter.api.Test;
import net.minecraft.resources.ResourceLocation;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

public class BotanyCatalogCandidateTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }

    @Test void mainCatalogActivatesAllSourceRowsAndMutationEdgesWithoutPublishingProducts() {
        var source = resources();
        var names = source.keySet().stream().map(ResourceLocation::getPath).toArray(String[]::new);
        var main = mainResources("seed_profile", names);
        var manager = new PrototypeManager(); manager.register(ModSeedProfiles.TYPE);
        manager.reload(ModSeedProfiles.TYPE, main);
        var resolved = manager.snapshot(ModSeedProfiles.TYPE);
        var candidateManager = new PrototypeManager(); candidateManager.register(ModSeedProfiles.TYPE);
        candidateManager.reload(ModSeedProfiles.TYPE, source);
        var candidate = candidateManager.snapshot(ModSeedProfiles.TYPE);
        assertEquals(72, resolved.size());
        assertEquals(71, resolved.asMap().keySet().stream().filter(key -> !key.equals(id("defaults"))).count());
        assertEquals(100f, resolved.get(id("defaults")).biology().endurance());
        assertEquals(.75f, resolved.get(id("defaults")).biology().nutrientConsumption());
        assertEquals(.5f, resolved.get(id("defaults")).biology().waterConsumption());
        assertEquals(293f, resolved.get(id("defaults")).biology().idealHeat());
        assertEquals(id("unbound_seed_packet"), resolved.get(id("defaults")).packet());
        for (var key : resolved.asMap().keySet()) {
            if (key.equals(id("defaults"))) continue;
            var actual = resolved.get(key); var expected = candidate.get(key);
            assertEquals(expected, actual, key.toString());
            for (var target : actual.mutationTargets()) assertNotNull(resolved.get(target), key + " -> " + target);
        }
        assertEquals(34, resolved.asMap().values().stream().mapToInt(profile -> profile.mutationTargets().size()).sum());
    }

    @Test void allAuthoredProfilesAndMutationEdgesMatchReferenceInventory() throws Exception {
        var authored = resources();
        assertEquals(72, authored.size());
        var references = readJsonResource("botany-catalog-source-reference/source-seed-rows.json").getAsJsonArray();
        var ids = new java.util.HashSet<String>();
        var edges = 0;
        var distinctTargets = new java.util.HashSet<String>();
        for (var rowElement : references) {
            var row = rowElement.getAsJsonObject();
            var name = row.get("id").getAsString().replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.ROOT);
            assertTrue(ids.add(name), name);
            assertTrue(authored.containsKey(id(name)), "missing candidate " + name);
            var targets = new java.util.ArrayList<String>();
            var mutationPrototypes = row.getAsJsonArray("mutationPrototypes");
            if (mutationPrototypes != null) mutationPrototypes.forEach(target -> targets.add(target.getAsString()
                    .replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.ROOT)));
            var actual = authored.get(id(name)).getAsJsonArray("mutation_targets");
            var actualTargets = new java.util.ArrayList<String>();
            if (actual != null) actual.forEach(target -> actualTargets.add(target.getAsString().substring("moonstation14:".length())));
            assertEquals(targets, actualTargets, name);
            edges += targets.size(); distinctTargets.addAll(targets);
        }
        assertEquals(71, ids.size());
        assertEquals(ids, authored.keySet().stream().filter(key -> !key.equals(id("defaults"))).map(ResourceLocation::getPath).collect(java.util.stream.Collectors.toSet()));
        assertEquals(34, edges); assertEquals(31, distinctTargets.size());
    }

    @Test void specialIdentityAndBiologyAnchorsRemainDistinctAndCanonical() {
        var seeds = stageDecodedProfiles(resources());
        assertEquals("seeds-capfruit-name", seeds.get(id("fake_capfruit")).sourceName());
        assertEquals(seeds.get(id("fake_capfruit")).sourceName(), seeds.get(id("real_capfruit")).sourceName());
        assertNotEquals(seeds.get(id("fake_capfruit")).packet(), seeds.get(id("real_capfruit")).packet());
        assertNotEquals(seeds.get(id("fake_capfruit")).product(), seeds.get(id("real_capfruit")).product());
        assertEquals(id("pea_seeds"), seeds.get(id("world_pea")).packet());
        assertEquals(id("pea_seeds"), seeds.get(id("pea")).packet());
        assertNotEquals(seeds.get(id("world_pea")).product(), seeds.get(id("pea")).product());
        assertEquals(new SeedProfile.Chemical(1,15,6,true), seeds.get(id("death_nettle")).chemicals().get(id("sulfuricacid")));
        assertEquals(new SeedProfile.Chemical(1,15,6,true), seeds.get(id("death_nettle")).chemicals().get(id("fluorosulfuricacid")));
        assertEquals(0f, seeds.get(id("rainbow_cannabis")).chemicals().get(id("happiness")).potencyDivisor());
        assertFalse(seeds.get(id("rainbow_cannabis")).chemicals().containsKey(id("colorful_reagent")));
        assertEquals(.75f, seeds.get(id("ambrosia_vulgaris")).biology().nutrientConsumption());
        assertEquals(.5f, seeds.get(id("chili")).biology().waterConsumption());
        assertEquals(.5f, seeds.get(id("chilly")).biology().waterConsumption());
        for (var seed : seeds.values()) seed.chemicals().keySet().forEach(reagent -> assertReagentExists(reagent.getPath()));
    }

    @Test void allSeventyOneProfilesDecodeViaStageWithoutPublicationAndRejectInvalidTarget() {
        var manager = new PrototypeManager(); manager.register(ModSeedProfiles.TYPE);
        var before = manager.snapshot(); var decoded = new java.util.concurrent.atomic.AtomicReference<Map<ResourceLocation, SeedProfile>>();
        manager.stage(Map.of(ModSeedProfiles.TYPE, resources()), catalogs -> decoded.set(decode(catalogs.get(ModSeedProfiles.TYPE.typeId()))));
        assertSame(before, manager.snapshot()); assertEquals(72, decoded.get().size());
        var invalid = resources(); invalid.get(id("towercap")).add("mutation_targets", JsonParser.parseString("[\"moonstation14:missing\"]"));
        assertThrows(RuntimeException.class, () -> manager.stage(Map.of(ModSeedProfiles.TYPE, invalid)));
        assertSame(before, manager.snapshot());
    }

    private static Map<ResourceLocation, SeedProfile> decode(Map<ResourceLocation, JsonObject> raw) {
        var out = new LinkedHashMap<ResourceLocation, SeedProfile>();
        raw.forEach((key, value) -> out.put(key, SeedProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, value).getOrThrow()));
        return out;
    }
    private static Map<ResourceLocation, SeedProfile> stageDecodedProfiles(Map<ResourceLocation, JsonObject> raw) {
        var manager = new PrototypeManager(); manager.register(ModSeedProfiles.TYPE);
        var decoded = new java.util.concurrent.atomic.AtomicReference<Map<ResourceLocation, SeedProfile>>();
        manager.stage(Map.of(ModSeedProfiles.TYPE, raw), catalogs -> decoded.set(decode(catalogs.get(ModSeedProfiles.TYPE.typeId()))));
        return decoded.get();
    }
    public static Map<ResourceLocation, ItemFoodSolutionProfile> stageFoodSolutions(Map<ResourceLocation, JsonObject> raw) {
        var manager = new PrototypeManager(); manager.register(ModItemFoodSolutionProfiles.TYPE);
        var before = manager.snapshot();
        var decoded = new java.util.concurrent.atomic.AtomicReference<Map<ResourceLocation, ItemFoodSolutionProfile>>();
        manager.stage(Map.of(ModItemFoodSolutionProfiles.TYPE, raw), catalogs -> {
            var values = new LinkedHashMap<ResourceLocation, ItemFoodSolutionProfile>();
            catalogs.get(ModItemFoodSolutionProfiles.TYPE.typeId()).forEach((key, value) -> values.put(key,
                    ItemFoodSolutionProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, value).getOrThrow()));
            decoded.set(Map.copyOf(values));
        });
        assertSame(before, manager.snapshot(), "stage must not publish candidate food solutions");
        return decoded.get();
    }
    private static com.google.gson.JsonElement readJsonResource(String path) {
        try (var stream = BotanyCatalogCandidateTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (java.io.IOException exception) { throw new AssertionError(exception); }
    }
    @Test void fiveAuthoredProfilesResolveThroughStageWithoutPublishingAndRejectBadTarget() {
        var manager = new PrototypeManager();
        manager.register(ModSeedProfiles.TYPE);
        var before = manager.snapshot();
        var raw = resources();
        var decoded = new java.util.concurrent.atomic.AtomicReference<Map<ResourceLocation, SeedProfile>>(Map.of());
        manager.stage(Map.of(ModSeedProfiles.TYPE, raw), catalogs -> {
            var json = catalogs.get(ModSeedProfiles.TYPE.typeId());
            assertNotNull(json);
            var values = new LinkedHashMap<ResourceLocation, SeedProfile>();
            json.forEach((key, value) -> values.put(key, SeedProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, value).getOrThrow()));
            decoded.set(Map.copyOf(values));
        });
        assertSame(before, manager.snapshot(), "stage must not publish candidate data");
        var seeds = decoded.get();
        assertEquals(72, seeds.size());
        assertEquals(71, seeds.keySet().stream().filter(key -> !key.equals(id("defaults"))).count());
        var defaults = seeds.get(id("defaults"));
        assertEquals(100f, defaults.biology().endurance());
        assertEquals(.75f, defaults.biology().nutrientConsumption());
        assertEquals(.5f, defaults.biology().waterConsumption());
        assertEquals(293f, defaults.biology().idealHeat());
        assertEquals(10f, defaults.biology().heatTolerance());
        assertEquals(81f, defaults.biology().lowPressure());
        assertEquals(121f, defaults.biology().highPressure());
        assertEquals(5f, defaults.biology().weedTolerance());
        assertEquals(10f, defaults.biology().weedHighLevel());
        assertEquals(4f, defaults.biology().toxinTolerance());
        assertEquals(5f, defaults.biology().pestTolerance());
        assertTrue(defaults.lifecycle().viable()); assertFalse(defaults.lifecycle().seedless());
        assertEquals("__unbound_seed_name__", defaults.sourceName());
        var expected = Map.of("papercane", new float[]{60,6,6,3,10}, "towercap", new float[]{80,15,3,5,1},
                "steelcap", new float[]{80,15,3,3,1}, "cotton", new float[]{25,8,3,3,5}, "pyrotton", new float[]{25,8,3,2,5});
        var names = new java.util.HashSet<String>();
        expected.forEach((name, values) -> {
            var seed = seeds.get(id(name));
            assertNotNull(seed); assertTrue(names.add(seed.sourceName()));
            assertEquals(name, seed.visualKey());
            assertEquals(id(name + "_seeds"), seed.packet());
            assertEquals(values[0], seed.lifecycle().lifespan()); assertEquals(values[1], seed.lifecycle().maturation());
            assertEquals(values[2], seed.lifecycle().production()); assertEquals((int)values[3], seed.lifecycle().yield());
            assertEquals(values[4], seed.lifecycle().potency());
            assertEquals(3, seed.lifecycle().growthStages());
            assertEquals(name.equals("papercane") ? SeedProfile.Harvest.REPEAT : SeedProfile.Harvest.NO_REPEAT, seed.lifecycle().harvest());
        });
        assertTrue(seeds.get(id("towercap")).lifecycle().ligneous());
        assertTrue(seeds.get(id("steelcap")).lifecycle().ligneous());
        assertEquals(.60f, seeds.get(id("towercap")).biology().waterConsumption());
        assertEquals(.50f, seeds.get(id("towercap")).biology().nutrientConsumption());
        assertEquals(288f, seeds.get(id("towercap")).biology().idealHeat());
        assertEquals(.60f, seeds.get(id("steelcap")).biology().waterConsumption());
        assertEquals(.80f, seeds.get(id("steelcap")).biology().nutrientConsumption());
        assertEquals(288f, seeds.get(id("steelcap")).biology().idealHeat());
        assertEquals(298f, seeds.get(id("papercane")).biology().idealHeat());
        assertEquals(.60f, seeds.get(id("cotton")).biology().waterConsumption());
        assertEquals(.80f, seeds.get(id("pyrotton")).biology().waterConsumption());
        assertEquals(id("papercane"), seeds.get(id("papercane")).product());
        assertEquals(id("log"), seeds.get(id("towercap")).product());
        assertEquals(id("steel_log"), seeds.get(id("steelcap")).product());
        assertEquals(id("cotton_bol"), seeds.get(id("cotton")).product());
        assertEquals(id("pyrotton_bol"), seeds.get(id("pyrotton")).product());
        assertEquals(java.util.List.of(id("steelcap")), seeds.get(id("towercap")).mutationTargets());
        assertEquals(java.util.List.of(id("pyrotton")), seeds.get(id("cotton")).mutationTargets());
        assertEquals(Map.of(id("fiber"), new SeedProfile.Chemical(5,10,20,true)), seeds.get(id("cotton")).chemicals());
        assertEquals(Map.of(id("fiber"), new SeedProfile.Chemical(5,10,20,true), id("phlogiston"), new SeedProfile.Chemical(4,8,30,true)), seeds.get(id("pyrotton")).chemicals());
        assertFruitCatalog(seeds);
        assertVegetableCatalog(seeds);
        assertMutationCatalog(seeds);
        var bad = resources(); bad.get(id("towercap")).add("mutation_targets", JsonParser.parseString("[\"moonstation14:missing\"]"));
        assertThrows(RuntimeException.class, () -> manager.stage(Map.of(ModSeedProfiles.TYPE, bad)));
        assertSame(before, manager.snapshot(), "failed stage must retain published state");
    }

    @Test void elevenVegetableProfilesResolveThroughExistingStageAndCodec() {
        var manager = new PrototypeManager(); manager.register(ModSeedProfiles.TYPE);
        var decoded = new java.util.concurrent.atomic.AtomicReference<Map<ResourceLocation, SeedProfile>>(Map.of());
        manager.stage(Map.of(ModSeedProfiles.TYPE, vegetableResources()), catalogs -> {
            var values = new LinkedHashMap<ResourceLocation, SeedProfile>();
            catalogs.get(ModSeedProfiles.TYPE.typeId()).forEach((key, value) -> values.put(key,
                    SeedProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, value).getOrThrow()));
            decoded.set(Map.copyOf(values));
        });
        assertVegetableCatalog(decoded.get());
    }

    private static void assertVegetableCatalog(Map<ResourceLocation, SeedProfile> seeds) {
        var expected = Map.ofEntries(
                Map.entry("carrots", new float[]{25,10,3,3,10}), Map.entry("cabbage", new float[]{50,7,5,3,10}),
                Map.entry("eggplant", new float[]{25,6,6,2,20}), Map.entry("eggy", new float[]{75,6,12,2,20}),
                Map.entry("onion", new float[]{25,8,6,2,20}), Map.entry("onionred", new float[]{25,8,6,2,20}),
                Map.entry("bloonion", new float[]{25,15,3,3,10}), Map.entry("pumpkin", new float[]{55,10,4,2,10}),
                Map.entry("blue_pumpkin", new float[]{55,10,4,2,10}), Map.entry("watermelon", new float[]{55,12,3,1,1}),
                Map.entry("holymelon", new float[]{55,12,3,1,1}));
        var edges = Map.of("eggplant", java.util.List.of("eggy"), "onion", java.util.List.of("onionred", "bloonion"),
                "pumpkin", java.util.List.of("blue_pumpkin"), "watermelon", java.util.List.of("holymelon"));
        assertEquals(11, expected.size());
        expected.forEach((name, values) -> {
            var seed = seeds.get(id(name)); assertNotNull(seed, name);
            assertEquals(sourceSeedName(name), seed.sourceName());
            assertEquals(id(name + "_seeds"), seed.packet());
            assertEquals(values[0], seed.lifecycle().lifespan()); assertEquals(values[1], seed.lifecycle().maturation());
            assertEquals(values[2], seed.lifecycle().production()); assertEquals((int) values[3], seed.lifecycle().yield());
            assertEquals(values[4], seed.lifecycle().potency());
            int stages = switch (name) { case "cabbage" -> 1; case "bloonion" -> 4; case "eggy", "watermelon", "holymelon" -> 6; default -> 3; };
            assertEquals(stages, seed.lifecycle().growthStages(), name);
            assertEquals(name.equals("eggplant") || name.equals("eggy") ? SeedProfile.Harvest.REPEAT : SeedProfile.Harvest.NO_REPEAT, seed.lifecycle().harvest());
            assertEquals(edges.getOrDefault(name, java.util.List.of()).stream().map(BotanyCatalogCandidateTest::id).toList(), seed.mutationTargets());
            assertTrue(seed.consumeGasses().isEmpty(), name); assertTrue(seed.exudeGasses().isEmpty(), name);
            seed.chemicals().forEach((reagent, chemical) -> { assertTrue(chemical.min() > 0, name + reagent); assertTrue(chemical.max() >= chemical.min(), name + reagent); assertTrue(chemical.potencyDivisor() > 0, name + reagent); assertTrue(chemical.inherent(), name + reagent); assertReagentExists(reagent.getPath()); });
        });
        assertEquals(id("carrot"), seeds.get(id("carrots")).product()); assertEquals("carrot", seeds.get(id("carrots")).visualKey());
        assertEquals(id("egg"), seeds.get(id("eggy")).product()); assertNotEquals(seeds.get(id("eggy")).product(), seeds.get(id("eggplant")).product());
        assertEquals(id("onion_red"), seeds.get(id("onionred")).product()); assertEquals("onion_red", seeds.get(id("onionred")).visualKey());
        assertEquals(sourceSeedName("blue_pumpkin"), seeds.get(id("blue_pumpkin")).sourceName());
        assertEquals(.60f, seeds.get(id("carrots")).biology().waterConsumption());
        assertEquals(.60f, seeds.get(id("onion")).biology().waterConsumption()); assertEquals(.60f, seeds.get(id("onionred")).biology().waterConsumption());
        assertEquals(.60f, seeds.get(id("bloonion")).biology().waterConsumption()); assertEquals(.50f, seeds.get(id("bloonion")).biology().nutrientConsumption());
        assertEquals(.50f, seeds.get(id("eggy")).biology().nutrientConsumption());
        assertEquals(288f, seeds.get(id("pumpkin")).biology().idealHeat());
        for (String name : new String[]{"eggplant", "eggy", "onion", "onionred"}) assertEquals(298f, seeds.get(id(name)).biology().idealHeat(), name);
        assertChemical(seeds, "bloonion", "potassium", 1, 5, 20); assertChemical(seeds, "bloonion", "phosphorus", 1, 5, 20);
        assertChemical(seeds, "bloonion", "sugar", 1, 5, 20); assertChemical(seeds, "bloonion", "allicin", 1, 10, 10);
    }

    private static String sourceSeedName(String name) {
        return switch (name) {
            case "onionred" -> "seeds-onionred-name";
            case "blue_pumpkin" -> "seeds-blue-pumpkin-name";
            case "blue_tomato" -> "seeds-bluetomato-name";
            case "blood_tomato" -> "seeds-bloodtomato-name";
            case "killer_tomato" -> "seeds-killertomato-name";
            case "spacemans_trumpet" -> "seeds-spacemans-trumpet-name";
            case "tea_plant" -> "seeds-teaplant-name";
            default -> "seeds-" + name.replace("_", "") + "-name";
        };
    }

    private static void assertChemical(Map<ResourceLocation, SeedProfile> seeds, String plant, String reagent, float min, float max, float divisor) {
        assertEquals(new SeedProfile.Chemical(min, max, divisor, true), seeds.get(id(plant)).chemicals().get(id(reagent)));
    }

    private static void assertMutationCatalog(Map<ResourceLocation, SeedProfile> seeds) {
        var expected = Map.ofEntries(
                Map.entry("wheat", new float[]{25,6,3,3,5}), Map.entry("tomato", new float[]{25,8,6,2,10}),
                Map.entry("potato", new float[]{30,10,3,3,10}), Map.entry("poppy", new float[]{25,10,3,3,10}),
                Map.entry("aloe", new float[]{25,10,3,3,10}), Map.entry("meatwheat", new float[]{25,6,3,3,5}),
                Map.entry("blue_tomato", new float[]{25,8,6,2,10}), Map.entry("blood_tomato", new float[]{60,8,6,2,10}),
                Map.entry("killer_tomato", new float[]{25,15,6,2,10}), Map.entry("lily", new float[]{25,10,3,3,10}),
                Map.entry("spacemans_trumpet", new float[]{20,14,3,2,10}));
        var edges = Map.of("wheat", java.util.List.of("meatwheat"), "tomato", java.util.List.of("blue_tomato", "blood_tomato"),
                "blood_tomato", java.util.List.of("killer_tomato"), "poppy", java.util.List.of("lily"), "lily", java.util.List.of("spacemans_trumpet"));
        var products = Map.of("wheat", "wheat_bushel", "meatwheat", "meatwheat_bushel", "killer_tomato", "mob_tomato_killer");
        expected.forEach((name, values) -> {
            var seed = seeds.get(id(name)); assertNotNull(seed, name);
            assertEquals(sourceSeedName(name), seed.sourceName());
            assertEquals(id(name + "_seeds"), seed.packet());
            assertEquals(id(products.getOrDefault(name, name)), seed.product());
            assertEquals(name.equals("killer_tomato") ? "tomatokiller" : name, seed.visualKey());
            assertEquals(values[0], seed.lifecycle().lifespan()); assertEquals(values[1], seed.lifecycle().maturation());
            assertEquals(values[2], seed.lifecycle().production()); assertEquals((int) values[3], seed.lifecycle().yield());
            assertEquals(values[4], seed.lifecycle().potency());
            int stages = switch (name) { case "potato" -> 4; case "aloe" -> 5; case "killer_tomato" -> 2; case "poppy", "lily" -> 3; case "spacemans_trumpet" -> 4; default -> 6; };
            assertEquals(stages, seed.lifecycle().growthStages(), name);
            assertEquals(name.equals("tomato") || name.endsWith("_tomato") && !name.equals("killer_tomato") ? SeedProfile.Harvest.REPEAT : name.equals("killer_tomato") ? SeedProfile.Harvest.REPEAT : SeedProfile.Harvest.NO_REPEAT, seed.lifecycle().harvest());
            assertEquals(edges.getOrDefault(name, java.util.List.of()).stream().map(BotanyCatalogCandidateTest::id).toList(), seed.mutationTargets());
            assertTrue(seed.consumeGasses().isEmpty(), name); assertTrue(seed.exudeGasses().isEmpty(), name);
            assertFalse(seed.chemicals().isEmpty(), name);
            seed.chemicals().forEach((reagent, chemical) -> assertReagentExists(reagent.getPath()));
        });
        assertEquals(3, seeds.get(id("lily")).lifecycle().growthStages());
        assertEquals(id("mob_tomato_killer"), seeds.get(id("killer_tomato")).product(), "reference only; not an asserted local mob registration");
        assertChemical(seeds, "wheat", "flour", 5, 20, 20); assertChemical(seeds, "meatwheat", "uncookedanimalproteins", 5, 20, 20);
        assertChemical(seeds, "tomato", "nutriment", 1, 7, 14); assertChemical(seeds, "blue_tomato", "spacelube", 5, 15, 10);
        assertChemical(seeds, "blood_tomato", "blood", 1, 10, 10); assertChemical(seeds, "lily", "bicaridine", 1, 20, 5);
        assertEquals(id("meatwheat"), seeds.get(id("wheat")).mutationTargets().get(0));
    }

    private static Map<ResourceLocation, JsonObject> vegetableResources() {
        var result = new LinkedHashMap<ResourceLocation, JsonObject>();
        for (String name : new String[]{"defaults", "carrots", "cabbage", "eggplant", "eggy", "onion", "onionred", "bloonion", "pumpkin", "blue_pumpkin", "watermelon", "holymelon"}) {
            String path = "botany-catalog-candidate/seed_profile/" + name + ".json";
            try (var stream = BotanyCatalogCandidateTest.class.getClassLoader().getResourceAsStream(path)) {
                assertNotNull(stream, path); result.put(id(name), JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (java.io.IOException exception) { throw new AssertionError(exception); }
        }
        return result;
    }

    private static void assertFruitCatalog(Map<ResourceLocation, SeedProfile> seeds) {
        var expected = Map.of("banana", new float[]{50,6,6,2,1}, "mimana", new float[]{50,6,6,2,1},
                "lemon", new float[]{55,6,6,3,10}, "lemoon", new float[]{90,8,6,4,1}, "lime", new float[]{55,6,6,3,10},
                "orange", new float[]{55,6,6,3,10}, "extradimensional_orange", new float[]{55,6,6,3,10},
                "apple", new float[]{55,6,6,3,10}, "golden_apple", new float[]{55,6,6,3,10});
        var edges = Map.of("lemon", java.util.List.of("lemoon", "lime", "orange"), "lime", java.util.List.of("orange", "lemon"),
                "orange", java.util.List.of("extradimensional_orange", "lemon", "lime"), "banana", java.util.List.of("mimana"),
                "apple", java.util.List.of("golden_apple"));
        expected.forEach((name, values) -> {
            var seed = seeds.get(id(name)); assertNotNull(seed, name);
            assertEquals("seeds-" + (name.equals("extradimensional_orange") ? "extradimensionalorange" : name.replace("_", "")) + "-name", seed.sourceName());
            assertEquals(values[0], seed.lifecycle().lifespan()); assertEquals(values[1], seed.lifecycle().maturation());
            assertEquals(values[2], seed.lifecycle().production()); assertEquals((int) values[3], seed.lifecycle().yield());
            assertEquals(values[4], seed.lifecycle().potency()); assertTrue(seed.lifecycle().harvest() == SeedProfile.Harvest.REPEAT);
            assertEquals(6, seed.lifecycle().growthStages());
            assertEquals(edges.getOrDefault(name, java.util.List.of()).stream().map(BotanyCatalogCandidateTest::id).toList(), seed.mutationTargets());
            assertTrue(seed.consumeGasses().isEmpty(), name); assertTrue(seed.exudeGasses().isEmpty(), name);
            assertFalse(seed.chemicals().isEmpty(), name);
        });
        assertEquals(.60f, seeds.get(id("banana")).biology().waterConsumption());
        assertEquals(298f, seeds.get(id("banana")).biology().idealHeat());
        assertEquals(.75f, seeds.get(id("golden_apple")).biology().waterConsumption());
        assertEquals(.75f, seeds.get(id("golden_apple")).biology().nutrientConsumption());
        assertTrue(seeds.get(id("mimana")).chemicals().containsKey(id("mutetoxin")));
        assertTrue(seeds.get(id("golden_apple")).chemicals().containsKey(id("doctorsdelight")));
        assertReagentExists("mutetoxin"); assertReagentExists("doctorsdelight");
        assertFalse(seeds.containsKey(id("doctors_delight"))); assertFalse(seeds.containsKey(id("mute_toxin")));
    }

    private static void assertReagentExists(String name) {
        String path = "data/moonstation14/moonstation14/reagent/" + name + ".json";
        try (var stream = BotanyCatalogCandidateTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            assertEquals(name, JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject().get("id").getAsString());
        } catch (java.io.IOException exception) { throw new AssertionError(exception); }
    }

    public static Map<ResourceLocation, JsonObject> resources() {
        return resources("seed_profile", "defaults", "papercane", "towercap", "steelcap", "cotton", "pyrotton", "banana", "mimana", "lemon", "lemoon", "lime", "orange", "extradimensional_orange", "apple", "golden_apple", "carrots", "cabbage", "eggplant", "eggy", "onion", "onionred", "bloonion", "pumpkin", "blue_pumpkin", "watermelon", "holymelon", "wheat", "tomato", "potato", "poppy", "aloe", "meatwheat", "blue_tomato", "blood_tomato", "killer_tomato", "lily", "spacemans_trumpet", "oat", "sugarcane", "tea_plant", "tobacco", "cannabis", "rainbow_cannabis", "chili", "chilly", "lingzhi", "ambrosia_vulgaris", "ambrosia_deus", "galaxythistle", "death_nettle", "fake_capfruit", "real_capfruit", "gatfruit", "world_pea", "pea", "rice", "soybeans", "koibean", "corn", "cocoa", "grape", "cherry", "pineapple", "berries", "bungo", "anomaly_berry", "chanterelle", "fly_amanita", "glasstle", "garlic", "nettle", "laughin_pea");
    }

    public static Map<ResourceLocation, JsonObject> resources(String family, String... names) {
        return resourcesFrom("botany-catalog-candidate", family, names);
    }

    public static Map<ResourceLocation, JsonObject> mainResources(String family, String... names) {
        return resourcesFrom("data/moonstation14/moonstation14", family, names);
    }

    private static Map<ResourceLocation, JsonObject> resourcesFrom(String root, String family, String... names) {
        var result = new LinkedHashMap<ResourceLocation, JsonObject>();
        for (String name : names) {
            String path = root + "/" + family + "/" + name + ".json";
            try (var stream = BotanyCatalogCandidateTest.class.getClassLoader().getResourceAsStream(path)) {
                assertNotNull(stream, path);
                result.put(id(name), JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (java.io.IOException exception) { throw new AssertionError(exception); }
        }
        return result;
    }
}
