package com.juicyslew.moonstation14.ms14.reagent;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.component.codec.json.ReagentSchemaAudit;
import com.juicyslew.moonstation14.component.codec.json.MetabolismData;
import com.juicyslew.moonstation14.ms14.metabolism.MetabolismStage;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeResolver;
import com.juicyslew.moonstation14.ms14.status_effect.ModStatusEffects;
import com.juicyslew.moonstation14.ms14.status_effect.prototype.StatusEffectReferenceValidator;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReagentResourceSmokeTest {
    private static final String RESOURCE_ROOT = "data/moonstation14/moonstation14/reagent";

    @Test
    void resolvesProductionReagentsFromClasspathWithoutChangingResources() throws IOException {
        Map<ResourceLocation, JsonObject> raw = loadResources();
        assertEquals(411, raw.size());

        PrototypeCatalog<JsonObject> catalog = PrototypeResolver.resolve(raw, new ReagentPrototypeMergeStrategy());
        assertEquals(406, catalog.size());

        JsonObject beer = catalog.get(id("beer"));
        assertTrue(beer.getAsJsonObject("metabolisms").has("digestion"));

        JsonObject antifreezeDigestion = stage(catalog, "antifreeze", "digestion");
        assertTrue(antifreezeDigestion.has("effects"));
        assertEquals(0.3, antifreezeDigestion.getAsJsonObject("metabolites").get("ethanol").getAsDouble(), 0.0001);

        assertTrue(stage(catalog, "silencer", "digestion").has("metabolites"));
        JsonObject ethanolMetabolisms = catalog.get(id("ethanol")).getAsJsonObject("metabolisms");
        assertTrue(ethanolMetabolisms.has("digestion"));
        assertTrue(ethanolMetabolisms.getAsJsonObject("digestion").has("metabolites"));

        assertTrue(catalog.get(id("juiceapple")).getAsJsonObject("metabolisms").has("digestion"));
        JsonObject artifactGlue = catalog.get(id("artifactglue"));
        JsonObject rawArtifactGlue = raw.get(id("artifactglue"));
        assertEquals(rawArtifactGlue.get("reactiveeffects"), artifactGlue.get("reactiveeffects"));
        assertTrue(artifactGlue.getAsJsonObject("metabolisms").has("bloodstream"));

        assertTrue(catalog.get(id("ammoniablood")).getAsJsonObject("metabolisms").size() >= 2);
        assertFalse(catalog.get(id("foamingagent")).has("metabolisms"));
    }

    @Test
    void actualReagentPrototypeTypePublishesTypedProductionCatalog() throws IOException {
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModReagents.REAGENT_TYPE);

        manager.reload(ModReagents.REAGENT_TYPE, loadResources());

        assertEquals(406, manager.snapshot(ModReagents.REAGENT_TYPE).size());
        assertTrue(manager.snapshot(ModReagents.REAGENT_TYPE).get(id("water")) instanceof ReagentData);
        ReagentData beer = manager.snapshot(ModReagents.REAGENT_TYPE).get(id("beer"));
        assertEquals(0.4f, beer.friction().orElseThrow());
    }

    @Test
    void effectiveProductionStagesHaveExpectedDistributionAndValidatedAmounts() throws IOException {
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModReagents.REAGENT_TYPE);
        manager.reload(ModReagents.REAGENT_TYPE, loadResources());

        Map<MetabolismStage, Integer> counts = new EnumMap<>(MetabolismStage.class);
        int noMetabolism = 0;
        for (ReagentData reagent : manager.snapshot(ModReagents.REAGENT_TYPE).values()) {
            if (reagent.metabolisms().isEmpty()) {
                noMetabolism++;
            }
            for (Map.Entry<MetabolismStage, MetabolismData> entry : reagent.metabolisms().entrySet()) {
                counts.merge(entry.getKey(), 1, Integer::sum);
                assertTrue(Float.isFinite(entry.getValue().rate()) && entry.getValue().rate() > 0f,
                        reagent.id() + " " + entry.getKey());
                for (float ratio : entry.getValue().metabolites().values()) {
                    assertTrue(Float.isFinite(ratio) && ratio >= 0f,
                            reagent.id() + " " + entry.getKey());
                }
            }
        }

        assertEquals(223, counts.getOrDefault(MetabolismStage.DIGESTION, 0));
        assertEquals(148, counts.getOrDefault(MetabolismStage.BLOODSTREAM, 0));
        assertEquals(9, counts.getOrDefault(MetabolismStage.RESPIRATION, 0));
        assertEquals(6, counts.getOrDefault(MetabolismStage.METABOLITES, 0));
        assertEquals(386, counts.values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(39, noMetabolism);
    }

    @Test
    void everyConcreteResolvedReagentDecodesWithContext() throws IOException {
        PrototypeCatalog<JsonObject> catalog = PrototypeResolver.resolve(loadResources(), new ReagentPrototypeMergeStrategy());
        for (ResourceLocation id : catalog.keys()) {
            ReagentSchemaAudit.audit(id, catalog.get(id));
            ReagentSchemaAudit.auditReferences(id, catalog.get(id), catalog.keys());
            var result = ReagentData.CODEC.parse(JsonOps.INSTANCE, catalog.get(id));
            if (result.error().isPresent()) throw new AssertionError("Failed reagent " + id + ": " + result.error().get().message());
            var decoded = result.result().orElseThrow();
            assertEquals(id.getPath(), decoded.id(), id.toString());
            var encoded = ReagentData.CODEC.encodeStart(JsonOps.INSTANCE, decoded).getOrThrow();
            assertEquals(decoded, ReagentData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow(), id.toString());
        }
    }

    @Test
    void inheritedTopLevelFieldsSurviveTypedRoundTrip() throws IOException {
        PrototypeCatalog<JsonObject> catalog = PrototypeResolver.resolve(loadResources(), new ReagentPrototypeMergeStrategy());

        var beer = ReagentData.CODEC.parse(JsonOps.INSTANCE, catalog.get(id("beer"))).getOrThrow();
        assertTrue(beer.slipData().isPresent());
        assertTrue(beer.friction().isPresent());
        assertTrue(beer.tileReactions().isPresent());
        assertEquals(beer, ReagentData.CODEC.parse(JsonOps.INSTANCE,
                ReagentData.CODEC.encodeStart(JsonOps.INSTANCE, beer).getOrThrow()).getOrThrow());

        var juice = ReagentData.CODEC.parse(JsonOps.INSTANCE, catalog.get(id("juiceapple"))).getOrThrow();
        assertTrue(juice.footstepSound().isPresent());
        assertEquals("footstepslime", juice.footstepSound().orElseThrow().collection());
        assertEquals(juice, ReagentData.CODEC.parse(JsonOps.INSTANCE,
                ReagentData.CODEC.encodeStart(JsonOps.INSTANCE, juice).getOrThrow()).getOrThrow());
    }

    @Test
    void everyProductionModifyStatusReferenceResolvesAgainstTheStatusCatalog() throws IOException {
        Map<ResourceLocation, JsonObject> reagents = loadResources();
        Map<ResourceLocation, JsonObject> statuses = loadStatusResources();
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModReagents.REAGENT_TYPE);
        manager.register(ModStatusEffects.STATUS_EFFECT_TYPE);
        manager.reload(Map.of(ModReagents.REAGENT_TYPE, reagents,
                ModStatusEffects.STATUS_EFFECT_TYPE, statuses));

        Map<ResourceLocation, Map<ResourceLocation, JsonObject>> encoded = manager.encodePublishedCatalogs();
        StatusEffectReferenceValidator.validate(encoded);

        Set<String> references = new HashSet<>();
        int count = 0;
        int movementCount = 0;
        for (JsonObject reagent : PrototypeResolver.resolve(reagents, new ReagentPrototypeMergeStrategy()).values()) {
            JsonObject metabolisms = reagent.getAsJsonObject("metabolisms");
            if (metabolisms != null) {
                for (var stage : metabolisms.entrySet()) {
                    var effects = stage.getValue().getAsJsonObject().getAsJsonArray("effects");
                    if (effects != null) for (var effect : effects) {
                        if ("ModifyStatusEffect".equals(effect.getAsJsonObject().get("type").getAsString())) {
                            count++;
                            references.add(effect.getAsJsonObject().get("effectproto").getAsString());
                        }
                        if ("MovementSpeedModifier".equals(effect.getAsJsonObject().get("type").getAsString())) {
                            movementCount++;
                            assertEquals(effect.getAsJsonObject().get("walkspeedmodifier").getAsFloat(),
                                    effect.getAsJsonObject().get("sprintspeedmodifier").getAsFloat());
                            assertTrue(!effect.getAsJsonObject().has("effectproto")
                                    || "reagentspeedstatuseffect".equals(
                                    effect.getAsJsonObject().get("effectproto").getAsString()));
                        }
                    }
                }
            }
            JsonObject reactive = reagent.getAsJsonObject("reactiveeffects");
            if (reactive != null) for (var reaction : reactive.entrySet()) {
                var effects = reaction.getValue().getAsJsonObject().getAsJsonArray("effects");
                if (effects != null) for (var effect : effects) {
                    if ("ModifyStatusEffect".equals(effect.getAsJsonObject().get("type").getAsString())) {
                        count++;
                        references.add(effect.getAsJsonObject().get("effectproto").getAsString());
                    }
                    if ("MovementSpeedModifier".equals(effect.getAsJsonObject().get("type").getAsString())) {
                        movementCount++;
                        assertEquals(effect.getAsJsonObject().get("walkspeedmodifier").getAsFloat(),
                                effect.getAsJsonObject().get("sprintspeedmodifier").getAsFloat());
                        assertTrue(!effect.getAsJsonObject().has("effectproto")
                                || "reagentspeedstatuseffect".equals(
                                effect.getAsJsonObject().get("effectproto").getAsString()));
                    }
                }
            }
        }
        assertEquals(54, count);
        assertEquals(15, references.size());
        assertEquals(13, movementCount);

        Map<String, MovementOccurrence> expectedMovement = Map.ofEntries(
                Map.entry("chloralhydrate.metabolisms.bloodstream.effects[1]", movement(.65f, 2f, "update")),
                Map.entry("desoxyephedrine.metabolisms.bloodstream.effects[2]", movement(1.2f, 2f, "update")),
                Map.entry("ephedrine.metabolisms.bloodstream.effects[0]", movement(1.15f, 2f, "update")),
                Map.entry("fresium.metabolisms.bloodstream.effects[4]", movement(.6f, 2f, "update")),
                Map.entry("fresium.metabolisms.bloodstream.effects[5]", movement(0f, 2f, "update")),
                Map.entry("impedrezene.metabolisms.bloodstream.effects[0]", movement(.65f, 2f, "update")),
                Map.entry("mechanotoxin.metabolisms.bloodstream.effects[1]", movement(.8f, 2f, "update")),
                Map.entry("mechanotoxin.metabolisms.bloodstream.effects[2]", movement(.4f, 2f, "update")),
                Map.entry("nitrousoxide.metabolisms.respiration.effects[3]", movement(.65f, 2f, "update")),
                Map.entry("nocturine.metabolisms.bloodstream.effects[0]", movement(.65f, 2f, "update")),
                Map.entry("stimulants.metabolisms.bloodstream.effects[0]", movement(1.25f, 2f, "update")),
                Map.entry("teargas.metabolisms.bloodstream.effects[5]", movement(.65f, 1.5f, "update")),
                Map.entry("vestine.metabolisms.bloodstream.effects[1]", movement(.8f, 2f, "update")));
        Map<String, MovementOccurrence> actualMovement = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonObject> reagent : reagents.entrySet()) {
            collectMovementOccurrences(reagent.getValue(), reagent.getKey().getPath(), actualMovement);
        }
        assertEquals(expectedMovement, actualMovement,
                "movement inventory must retain reagent/stage/effect identity and semantics");
    }

    private static MovementOccurrence movement(float multiplier, float time, String operation) {
        return new MovementOccurrence(multiplier, time, operation);
    }

    private static void collectMovementOccurrences(JsonElement element, String path,
                                                   Map<String, MovementOccurrence> output) {
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if ("MovementSpeedModifier".equals(object.has("type")
                    ? object.get("type").getAsString() : null)) {
                output.put(path, new MovementOccurrence(
                        object.has("walkspeedmodifier") ? object.get("walkspeedmodifier").getAsFloat() : 1f,
                        object.has("time") ? object.get("time").getAsFloat() : 2f,
                        object.has("subtype") ? object.get("subtype").getAsString() : "update"));
            }
            for (Map.Entry<String, JsonElement> child : object.entrySet()) {
                collectMovementOccurrences(child.getValue(), path + "." + child.getKey(), output);
            }
        } else if (element.isJsonArray()) {
            for (int index = 0; index < element.getAsJsonArray().size(); index++) {
                collectMovementOccurrences(element.getAsJsonArray().get(index), path + "[" + index + "]", output);
            }
        }
    }

    private record MovementOccurrence(float multiplier, float time, String operation) {
    }

    @Test
    void productionGenericStatusInventoryHasOnlyAuditedCompatibilityAliases() throws IOException {
        Map<String, Integer> aliases = new HashMap<>();
        int[] total = {0};
        int[] supportedComponentlessJitterRemove = {0};
        for (JsonObject reagent : loadResources().values()) {
            auditGenericEffects(reagent, aliases, total, supportedComponentlessJitterRemove);
        }

        assertEquals(19, total[0]);
        assertEquals(3, supportedComponentlessJitterRemove[0]);
        assertEquals(Map.of(
                "pressureimmunity/pressureimmunity", 1,
                "stutter/stutteringaccent", 3,
                "ratvarianlanguage/ratvarianlanguage", 1,
                "adrenaline/ignoreslowondamage", 4,
                "muted/muted", 4,
                "temporaryblindness/temporaryblindness", 2,
                "pacified/pacified", 1), aliases);
    }

    private static void auditGenericEffects(JsonElement element, Map<String, Integer> aliases,
                                             int[] total, int[] supportedComponentlessJitterRemove) {
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            JsonElement type = object.get("type");
            if (type != null && "GenericStatusEffect".equals(type.getAsString())) {
                total[0]++;
                String key = object.get("key").getAsString().toLowerCase(java.util.Locale.ROOT);
                String component = object.has("component")
                        ? object.get("component").getAsString().toLowerCase(java.util.Locale.ROOT) : "";
                String alias = key + "/" + component;
                if ("jitter/".equals(alias)) {
                    assertEquals("remove", object.get("subtype").getAsString());
                    supportedComponentlessJitterRemove[0]++;
                } else {
                    aliases.merge(alias, 1, Integer::sum);
                }
            }
            object.entrySet().forEach(entry -> auditGenericEffects(
                    entry.getValue(), aliases, total, supportedComponentlessJitterRemove));
        } else if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(child -> auditGenericEffects(
                    child, aliases, total, supportedComponentlessJitterRemove));
        }
    }

    private static JsonObject stage(PrototypeCatalog<JsonObject> catalog, String reagent, String stage) {
        return catalog.get(id(reagent)).getAsJsonObject("metabolisms").getAsJsonObject(stage);
    }

    static Map<ResourceLocation, JsonObject> loadResources() throws IOException {
        URL root = ReagentResourceSmokeTest.class.getClassLoader().getResource(RESOURCE_ROOT);
        if (root == null) {
            throw new IOException("Classpath resource directory not found: " + RESOURCE_ROOT);
        }
        Map<ResourceLocation, JsonObject> raw = new LinkedHashMap<>();
        if ("file".equals(root.getProtocol())) {
            readDirectory(raw, Path.of(URI.create(root.toString())));
        } else if ("jar".equals(root.getProtocol())) {
            JarURLConnection connection = (JarURLConnection) root.openConnection();
            try (JarFile jar = connection.getJarFile()) {
                jar.stream().filter(entry -> entry.getName().startsWith(RESOURCE_ROOT + "/")
                                && entry.getName().endsWith(".json"))
                        .sorted(java.util.Comparator.comparing(JarEntry::getName))
                        .forEach(entry -> read(raw, entry.getName().substring(entry.getName().lastIndexOf('/') + 1), jar, entry));
            }
        } else if ("union".equals(root.getProtocol())) {
            // ModDev's unit-test classloader exposes mod folders through a
            // union URL. Its directory member is the normal Gradle resource
            // output; derive it from the URL rather than the process cwd.
            String unionPath = root.toString();
            int buildMarker = unionPath.indexOf("/build/");
            if (buildMarker < 0) {
                throw new IOException("Could not locate Gradle build directory in classpath URL: " + root);
            }
            String projectPath = URLDecoder.decode(unionPath.substring("union:".length(), buildMarker), StandardCharsets.UTF_8);
            if (projectPath.startsWith("/") && projectPath.length() > 2 && projectPath.charAt(2) == ':') {
                projectPath = projectPath.substring(1);
            }
            Path resourceDirectory = Path.of(projectPath)
                    .resolve("build/resources/main")
                    .resolve(RESOURCE_ROOT);
            readDirectory(raw, resourceDirectory);
        } else {
            throw new IOException("Unsupported classpath URL: " + root);
        }
        return raw;
    }

    private static Map<ResourceLocation, JsonObject> loadStatusResources() throws IOException {
        String[] ids = {"jitter", "statuseffectseeingrainbow", "statuseffectdrowsiness", "statuseffectbark",
                "statuseffectscrambled", "statuseffectwoozy", "statuseffectdesoxystamina", "statuseffectpainnumbness",
                 "statuseffectstunned", "statuseffectdrunk", "statuseffectowo", "statuseffecthemorrhage",
                 "statuseffectanticoagulant", "statuseffectforcedsleeping", "statuseffectradiationprotection",
                 "statuseffectstimulantsstamina", "reagentspeedstatuseffect", "knockdown"};
        Map<ResourceLocation, JsonObject> result = new LinkedHashMap<>();
        for (String id : ids) {
            String path = "data/moonstation14/moonstation14/status_effect/" + id + ".json";
            try (InputStream input = ReagentResourceSmokeTest.class.getClassLoader().getResourceAsStream(path)) {
                if (input == null) throw new IOException("Missing resource " + path);
                try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                    result.put(ResourceLocation.fromNamespaceAndPath("moonstation14", id),
                            JsonParser.parseReader(reader).getAsJsonObject());
                }
            }
        }
        return result;
    }

    private static void readDirectory(Map<ResourceLocation, JsonObject> raw, Path directory) {
        try (Stream<Path> paths = Files.list(directory)) {
            paths.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .forEach(path -> read(raw, path.getFileName().toString(), path));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not list classpath resource directory " + directory, exception);
        }
    }

    private static void read(Map<ResourceLocation, JsonObject> raw, String filename, Path path) {
        try (InputStream input = Files.newInputStream(path)) {
            read(raw, filename, input);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read " + path, exception);
        }
    }

    private static void read(Map<ResourceLocation, JsonObject> raw, String filename, JarFile jar, JarEntry entry) {
        try (InputStream input = jar.getInputStream(entry)) {
            read(raw, filename, input);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read " + entry.getName(), exception);
        }
    }

    private static void read(Map<ResourceLocation, JsonObject> raw, String filename, InputStream input) {
        try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            String path = filename.substring(0, filename.length() - ".json".length());
            raw.put(id(path), JsonParser.parseReader(reader).getAsJsonObject());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not close resource " + filename, exception);
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("moonstation14", path);
    }
}
